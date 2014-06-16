(ns donkey.persistence.metadata
  (:use korma.core)
  (:require [korma.db :as korma]
            [donkey.util.db :as db])
  (:import [java.util UUID]))


(defn- ->enum-val
  [val]
  (raw (str \' val \')))


;; FAVORITES

(defentity ^{:private true} favorites)

(defn is-favorite
  [owner target-id]
  (-> (korma/with-db db/metadata
        (select favorites
          (aggregate (count :*) :cnt)
          (where {:target_id target-id :owner_id owner})))
    first :cnt pos?))

(defn select-favorites-of-type
  [owner target-type]
  (map :target_id
       (korma/with-db db/metadata
         (select favorites
           (fields :target_id)
           (where {:target_type (->enum-val target-type)
                   :owner_id    owner})))))

(defn insert-favorite
  [owner target-id target-type]
  (korma/with-db db/metadata
    (insert favorites
      (values {:target_id   target-id
               :target_type (->enum-val target-type)
               :owner_id    owner}))))

(defn delete-favorite
  [owner target-id]
  (korma/with-db db/metadata
    (delete favorites
      (where {:target_id target-id :owner_id owner}))))


;; TAGS

(defentity ^{:private true} attached_tags)

(defentity ^{:private true} tags
  (entity-fields :id :owner_id :value :description))

(defn filter-tags-owned-by-user
  [owner tag-ids]
  (map :id
       (korma/with-db db/metadata
         (select tags
           (fields :id)
           (where {:owner_id owner :id [in tag-ids]})))))

(defn get-tags-by-value
  [owner value]
  (korma/with-db db/metadata
    (select tags
      (where {:owner_id owner :value [like value]}))))

(defn get-tag
  [tag-id]
  (korma/with-db db/metadata
    (select tags
      (where {:id (UUID/fromString tag-id)}))))

(defn insert-user-tag
  [owner value description]
  (korma/with-db db/metadata
    (insert tags
      (values {:value       value
               :description description
               :owner_id    owner}))))

(defn update-user-tag
  [tag-id updates]
  (korma/with-db db/metadata
    (update tags
      (set-fields updates)
      (where {:id (UUID/fromString tag-id)}))))

(defn select-attached-tags
  [user target-id]
  (korma/with-db db/metadata
    (select tags
      (fields :id :value :description)
      (where {:owner_id user
              :id       [in (subselect attached_tags
                              (fields :tag_id)
                              (where {:target_id   target-id
                                      :detached_on nil}))]}))))

(defn filter-attached-tags
  [target-id tag-ids]
  (map :tag_id
       (korma/with-db db/metadata
         (select attached_tags
           (fields :tag_id)
           (where {:target_id   target-id
                   :detached_on nil
                   :tag_id      [in tag-ids]})))))

(defn insert-attached-tags
  [attacher target-id target-type tag-ids]
  (when-not (empty? tag-ids)
    (let [new-values (map #(hash-map :tag_id      %
                                     :target_id   target-id
                                     :target_type (->enum-val target-type)
                                     :attacher_id attacher)
                          tag-ids)]
      (korma/with-db db/metadata
        (insert attached_tags (values new-values))))))

(defn mark-tags-detached
  [detacher target-id tag-ids]
  (korma/with-db db/metadata
    (update attached_tags
      (set-fields {:detacher_id detacher
                   :detached_on (sqlfn now)})
      (where {:target_id   target-id
              :detached_on nil
              :tag_id      [in tag-ids]}))))


;; TEMPLATES

(defn find-existing-metadata-template-avu
  "Finds an existing AVU by ID, or by attribute, target_id, and owner_id if no ID is given."
  [{avu-id :id, :as avu}]
  (korma/with-db db/metadata
    (first
     (select :avus
             (where (if avu-id
                      {:id (UUID/fromString avu-id)}
                      (-> (select-keys avu [:attribute :target_id :owner_id])
                          (assoc :target_type (->enum-val "data")))))))))

(defn get-avus-for-metadata-template
  "Gets AVUs for the given Metadata Template."
  [user-id data-id template-id]
  (korma/with-db db/metadata
    (select :avus
            (join [:template_instances :t]
                  {:t.avu_id :avus.id})
            (where {:t.template_id template-id
                    :avus.target_id data-id
                    :avus.target_type (->enum-val "data")
                    :avus.owner_id user-id}))))

(defn get-metadata-template-ids
  "Finds Metadata Template IDs associated with the given user's data item."
  [user-id data-id]
  (korma/with-db db/metadata
    (select [:template_instances :t]
            (fields :template_id)
            (join :avus
                  {:t.avu_id :avus.id})
            (where {:avus.target_id data-id
                    :avus.target_type (->enum-val "data")
                    :avus.owner_id user-id})
            (group :template_id))))

(defn remove-avu-template-instances
  "Removes the given Metadata Template AVU associations."
  [template-id avu-ids]
  (korma/with-db db/metadata
    (delete :template_instances
            (where {:template_id template-id
                    :avu_id [in avu-ids]}))))

(defn add-template-instances
  "Associates the given AVU with the given Metadata Template ID."
  [template-id avu-ids]
  (korma/with-db db/metadata
    (korma/transaction
     (remove-avu-template-instances template-id avu-ids)
     (insert :template_instances
             (values (map #(hash-map :template_id template-id
                                     :avu_id %)
                          avu-ids))))))

(defn add-metadata-template-avus
  "Adds the given AVUs to the Metadata database."
  [avus]
  (korma/with-db db/metadata
    (insert :avus (values (map #(assoc % :target_type (->enum-val "data")) avus)))))

(defn update-avu
  "Updates the attribute, value, unit, and modified_on fields of the given AVU."
  [avu]
  (korma/with-db db/metadata
    (update :avus
            (set-fields (-> (select-keys avu [:attribute :value :unit])
                            (assoc :modified_on (sqlfn now))))
            (where (select-keys avu [:id])))))

(defn remove-avus
  "Removes AVUs with the given IDs from the Metadata database."
  [avu-ids]
  (korma/with-db db/metadata
    (delete :avus (where {:id [in avu-ids]}))))

(defn remove-avu
  "Removes the AVU with the given ID from the Metadata database."
  [avu-id]
  (korma/with-db db/metadata
    (delete :avus (where {:id avu-id}))))
