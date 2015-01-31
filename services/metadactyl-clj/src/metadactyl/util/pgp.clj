(ns donkey.util.pgp
  (:use [slingshot.slingshot :only [throw+]])
  (:require [clj-pgp.core :as pgp]
            [clj-pgp.keyring :as keyring]
            [clj-pgp.message :as pgp-msg]
            [clojure.java.io :as io]
            [clojure-commons.error-codes :as ce]
            [metadactyl.util.config :as config]))

(def ^:private keyring
  (memoize (fn [] (-> (config/keyring-path) io/file keyring/load-secret-keyring))))

(def ^:private get-public-key
  (memoize (fn [] (first (keyring/list-public-keys (keyring))))))

(def ^:private get-private-key
  (memoize
   (fn []
     (some-> (keyring)
             (keyring/get-secret-key (pgp/hex-id (get-public-key)))
             (pgp/unlock-key (config/key-password))))))

(defn encrypt
  [s]
  (pgp-msg/encrypt (.getBytes s) (get-public-key)
                   :algorithm :aes-256
                   :compress  :zip))

(defn decrypt
  [bs]
  (if-let [private-key (get-private-key)]
    (String. (pgp-msg/decrypt bs private-key))
    (throw+ {:error_code ce/ERR_CONFIG_INVALID
             :message    "unable to unlock the private encryption key"})))
