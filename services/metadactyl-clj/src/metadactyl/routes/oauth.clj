(ns metadactyl.routes.oauth
  (:use [compojure.api.sweet]
        [metadactyl.routes.domain.oauth :only [StateInfo]]
        [ring.swagger.schema :only [describe]])
  (:require [clojure-commons.error-codes :as ce]
            [metadactyl.routes.params :as mrp]))

(defroutes* oauth
  (GET* "/access-code/agave" [:as {:keys [uri]}]
        :query   [params mrp/OAuthCallbackparams]
        :summary "OAuth authorization callback for Agave."
        :return  StateInfo
        :notes   "This service provides the callback for the Authorizaton Code Grant Flow in the
                  OAuth 2.0 specification."
        (ce/trap uri #{:state_info ""})))
