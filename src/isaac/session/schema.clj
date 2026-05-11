(ns isaac.session.schema
  (:require
    [c3kit.apron.schema :as schema]))

(defn- kebabize-legacy-keys [entry]
  (cond-> entry
    (contains? entry :createdAt) (-> (assoc :created-at (:createdAt entry)) (dissoc :createdAt))
    (contains? entry :chatType)  (-> (assoc :chat-type (:chatType entry)) (dissoc :chatType))))

(def Origin
  {:name   :session-origin
  :type   :map
  :schema {:kind {:type :keyword}
            :channel-id {:type :string}
            :guild-id {:type :string}
            :name {:type :string}}})

(def CompactionState
  {:name   :session-compaction-state
  :type   :map
  :schema {:async?               {:type :boolean}
           :consecutive-failures {:type :int}
           :strategy             {:type :keyword}
           :tail                 {:type :int}
           :threshold            {:type :int}}})

(def Session
  {:name   :session
   :type   :map
   :schema {:id                  {:type :string :required? true :validate schema/present? :message "must be present"}
            :key                 {:type :string}
            :name                {:type :string :required? true :validate schema/present? :message "must be present"}
            :sessionId           {:type :string}
            :session-file        {:type :string}
            :origin              {:type :map :schema (:schema Origin)}
            :crew                {:type :string}
            :model               {:type :string}
            :provider            {:type :string}
            :effort              {:type :int}
            :channel             {:type :string}
            :chat-type           {:type :string}
            :cwd                 {:type :string}
            :created-at          {:type :string}
            :updated-at          {:type :string}
            :last-channel        {:type :string}
            :last-to             {:type :string}
            :compaction-count    {:type :int}
            :compaction-disabled {:type :boolean}
            :compaction          {:type :map :schema (:schema CompactionState)}
            :input-tokens        {:type :int}
            :output-tokens       {:type :int}
            :total-tokens        {:type :int}
            :last-input-tokens   {:type :int}
            :cache-read          {:type :int}
            :cache-write         {:type :int}}})

(defn conform-read [entry]
  (schema/conform Session (kebabize-legacy-keys entry)))

(defn conform! [entry]
  (schema/conform! Session (kebabize-legacy-keys entry)))
