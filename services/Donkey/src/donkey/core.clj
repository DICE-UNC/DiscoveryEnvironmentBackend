(ns donkey.core
  (:gen-class)
  (:use [clojure.java.io :only [file]]
        [clojure-commons.lcase-params :only [wrap-lcase-params]]
        [clojure-commons.middleware :only [wrap-log-requests]]
        [clojure-commons.query-params :only [wrap-query-params]]
        [compojure.core]
        [ring.middleware.keyword-params]
        [donkey.routes.admin]
        [donkey.routes.callbacks]
        [donkey.routes.data]
        [donkey.routes.fileio]
        [donkey.routes.metadata]
        [donkey.routes.misc]
        [donkey.routes.notification]
        [donkey.routes.pref]
        [donkey.routes.session]
        [donkey.routes.tree-viewer]
        [donkey.routes.user-info]
        [donkey.routes.collaborator]
        [donkey.routes.filesystem]
        [donkey.routes.search]
        [donkey.routes.coge]
        [donkey.routes.oauth]
        [donkey.routes.favorites]
        [donkey.routes.tags]
        [donkey.routes.comments]
        [donkey.auth.user-attributes]
        [donkey.util.service]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [compojure.route :as route]
            [ring.adapter.jetty :as jetty]
            [donkey.util.config :as config]
            [clojure.tools.nrepl.server :as nrepl]
            [me.raynes.fs :as fs]
            [common-cli.core :as ccli]
            [donkey.services.filesystem.icat :as icat]
            [donkey.util :as util]
            [donkey.util.transformers :as transform]
            [service-logging.thread-context :as tc]))


(defn delayed-handler
  [routes-fn]
  (fn [req]
    (let [handler ((memoize routes-fn))]
      (handler req))))

(defn secured-routes-no-context
  []
  (util/flagged-routes
    (app-category-routes)
    (apps-routes)
    (app-comment-routes)
    (analysis-routes)
    (reference-genomes-routes)
    (tool-routes)))

(defn secured-routes
  []
  (util/flagged-routes
    (secured-notification-routes)
    (secured-metadata-routes)
    (secured-pref-routes)
    (secured-collaborator-routes)
    (secured-user-info-routes)
    (secured-tree-viewer-routes)
    (secured-data-routes)
    (secured-session-routes)
    (secured-fileio-routes)
    (secured-filesystem-routes)
    (secured-filesystem-metadata-routes)
    (secured-coge-routes)
    (secured-search-routes)
    (secured-oauth-routes)
    (secured-favorites-routes)
    (secured-tag-routes)
    (data-comment-routes)
    (route/not-found (unrecognized-path-response))))


(defn admin-routes
  []
  (util/flagged-routes
    (secured-admin-routes)
    (admin-data-comment-routes)
    (admin-category-routes)
    (admin-apps-routes)
    (admin-app-comment-routes)
    (admin-filesystem-metadata-routes)
    (admin-notification-routes)
    (admin-reference-genomes-routes)
    (admin-tool-routes)
    (route/not-found (unrecognized-path-response))))

(defn cas-store-user
  [routes]
  (let [f (if (System/getenv "IPLANT_CAS_FAKE") fake-store-current-user store-current-user)]
    (f routes
       config/cas-server
       config/server-name
       config/pgt-callback-base
       config/pgt-callback-path)))

(defn cas-store-admin-user
  [routes]
  (let [f (if (System/getenv "IPLANT_CAS_FAKE") fake-store-current-user store-current-admin-user)]
    (f routes
      config/cas-server
      config/server-name
      config/group-attr-name
      config/get-allowed-groups
      config/pgt-callback-base
      config/pgt-callback-path)))


(defn- wrap-user-info
  [handler]
  (fn [request]
    (let [user-info (transform/add-current-user-to-map {})
          request   (assoc request :user-info user-info)]
      (tc/set-context! user-info)
      (handler request))))


(def secured-handler-no-context
  (-> (delayed-handler secured-routes-no-context)
    util/trap-handler
    wrap-log-requests
    wrap-user-info
    (cas-store-user)))


(def secured-handler
  (-> (delayed-handler secured-routes)
    util/trap-handler
    wrap-log-requests
    wrap-user-info
    (cas-store-user)))


(def admin-handler
  (-> (delayed-handler admin-routes)
    util/trap-handler
    wrap-log-requests
    wrap-user-info
    (cas-store-admin-user)))


(defn donkey-routes
  []
  (util/flagged-routes
    (unsecured-misc-routes)
    (unsecured-notification-routes)
    (unsecured-tree-viewer-routes)
    (unsecured-fileio-routes)
    (unsecured-callback-routes)
    (context "/admin" [] admin-handler)
    (context "/secured" [] secured-handler)
    secured-handler-no-context
    (route/not-found (unrecognized-path-response))))


(defn- start-nrepl
  []
  (nrepl/start-server :port 7888))

(defn- iplant-conf-dir-file
  [filename]
  (when-let [conf-dir (System/getenv "IPLANT_CONF_DIR")]
    (let [f (file conf-dir filename)]
      (when (.isFile f) (.getPath f)))))

(defn- cwd-file
  [filename]
  (let [f (file filename)]
    (when (.isFile f) (.getPath f))))

(defn- classpath-file
  [filename]
  (-> (Thread/currentThread)
      (.getContextClassLoader)
      (.findResource filename)
      (.toURI)
      (file)))

(defn- no-configuration-found
  [filename]
  (throw (RuntimeException. (str "configuration file " filename " not found"))))

(defn- find-configuration-file
  []
  (let [conf-file "donkey.properties"]
    (or (iplant-conf-dir-file conf-file)
        (cwd-file conf-file)
        (classpath-file conf-file)
        (no-configuration-found conf-file))))

(defn load-configuration-from-file
  "Loads the configuration properties from a file."
  ([]
     (load-configuration-from-file (find-configuration-file)))
  ([path]
     (config/load-config-from-file path)))

(defn lein-ring-init
  "This function is used by leiningen ring plugin to initialize donkey."
  []
  (load-configuration-from-file)
  (icat/configure-icat)
  (start-nrepl))

(defn repl-init
  "This function is used to manually initialize donkey from the leiningen REPL."
  []
  (load-configuration-from-file)
  (icat/configure-icat))

(defn cli-options
  []
  [["-c" "--config PATH" "Path to the config file"
    :default "/etc/iplant/de/donkey.properties"]
   ["-v" "--version" "Print out the version number."]
   ["-h" "--help"]])

(def svc-info
  {:desc "DE service for business logic"
   :app-name "donkey"
   :group-id "org.iplantc"
   :art-id "donkey"
   :service "donkey"})

(defn site-handler
  [routes-fn]
  (-> (delayed-handler routes-fn)
    wrap-keyword-params
    wrap-lcase-params
    wrap-query-params
    (tc/wrap-thread-context svc-info)))

(def app
  (site-handler donkey-routes))

(defn -main
  [& args]
  (tc/set-context! svc-info)
  (let [{:keys [options]} (ccli/handle-args svc-info args cli-options)]
    ;; comment this is a destructuring...see http://blog.jayfields.com/2010/07/clojure-destructuring.html - options will contain the keys of the command line options
    (when-not (fs/exists? (:config options))
      (ccli/exit 1 (str "The config file does not exist.")))
    (when-not (fs/readable? (:config options))
      (ccli/exit 1 "The config file is not readable."))
    (config/load-config-from-file (:config options))
    (icat/configure-icat)
    (jetty/run-jetty app {:port (config/listen-port)})))
