(ns metadactyl.routes.domain.oauth
  (:use [ring.swagger.schema :only [describe]]
        [schema.core :only [defschema]]))

(defschema StateInfo
  {:state_info (describe String "Arbitrary state information string.")})
