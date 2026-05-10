(ns isaac.session.store.memory
  (:require
    [clojure.set :as set]
    [clojure.string :as str]
    [isaac.session.store :as store])
  (:import
    (java.time Instant ZoneOffset)
    (java.time.format DateTimeFormatter)
    (java.util UUID)))

;; region ----- Helpers (mirror file-impl private helpers) -----

(def ^:private ts-formatter
  (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss"))

(defn- new-id []
  (subs (str (UUID/randomUUID)) 0 8))

(defn- now-iso []
  (.format ts-formatter (.atOffset (Instant/now) ZoneOffset/UTC)))

(defn- slugify [s]
  (let [slug (-> (or s "")
                 str/lower-case
                 (str/replace #"[^a-z0-9]+" "-")
                 (str/replace #"^-+|-+$" ""))]
    (if (str/blank? slug) "session" slug)))

(defn- session-id [identifier]
  (slugify identifier))

(defn- entry-defaults [opts]
  (merge {:crew      (or (:crew opts) "main")
          :channel   (:channel opts)
          :chat-type (or (:chat-type opts) (:chatType opts))}
         (into {} (remove (comp nil? val) opts))))

(defn- text-blocks? [content]
  (and (vector? content)
       (every? map? content)
       (every? #(contains? % :type) content)))

(def ^:private text-content-roles #{"user"})

(defn- normalize-message-content [role content]
  (if (contains? text-content-roles role)
    (cond
      (string? content) [{:type "text" :text content}]
      (text-blocks? content) content
      :else content)
    content))

(defn- normalize-message [message]
  (let [role (:role message)]
    (cond-> (assoc message :content (normalize-message-content role (:content message)))
      (keyword? (:error message)) (update :error str))))

(defn- get-val [m k]
  (or (get m k) (get m (name k))))

(defn- last-entry-id [transcript]
  (:id (last transcript)))

(defn- entry-toolcall-ids [entry]
  (let [message (:message entry)
        content (:content message)]
    (cond
      (= "toolCall" (:type message))
      (keep :id [message])
      (sequential? content)
      (->> content (filter #(= "toolCall" (:type %))) (keep :id))
      :else nil)))

(defn- drop-orphan-toolcalls [transcript]
  (let [tool-call-ids   (->> transcript
                             (filter #(= "message" (:type %)))
                             (mapcat entry-toolcall-ids)
                             set)
        tool-result-ids (->> transcript
                             (filter #(= "toolResult" (get-in % [:message :role])))
                             (keep #(or (get-in % [:message :toolCallId])
                                        (get-in % [:message :id])
                                        (:id %)))
                             set)
        orphans         (set/difference tool-call-ids tool-result-ids)]
    (if (empty? orphans)
      transcript
      (let [remove?     (fn [e]
                          (and (= "message" (:type e))
                               (seq (set/intersection orphans (set (entry-toolcall-ids e))))))
            removed-ids (->> transcript (filter remove?) (map :id) set)
            kept        (vec (remove remove? transcript))
            remap       (loop [remaining transcript last-kept nil mapping {}]
                          (if (empty? remaining)
                            mapping
                            (let [e (first remaining)]
                              (if (contains? removed-ids (:id e))
                                (recur (rest remaining) last-kept (assoc mapping (:id e) last-kept))
                                (recur (rest remaining) (:id e) mapping)))))]
        (mapv (fn [e]
                (if-let [new-parent (get remap (:parentId e))]
                  (assoc e :parentId new-parent)
                  e))
              kept)))))

;; endregion

;; region ----- MemorySessionStore -----
;;
;; state atom shape: {:sessions   {id entry}
;;                    :transcripts {id [transcript-entries]}}
;;
;; Transcript entries match the file store wire format exactly so that
;; specs using get-transcript can check :type, :message, :id etc. without
;; modification.

(deftype MemorySessionStore [state]
  store/SessionStore

  (open-session! [_ name opts]
    (let [opts     (entry-defaults opts)
          id       (session-id (or name "session"))
          existing (get-in @state [:sessions id])]
      (if existing
        existing
        (let [now          (now-iso)
              header-id    (new-id)
              session-file (str id ".jsonl")
              header       {:type      "session"
                            :id        header-id
                            :timestamp now
                            :version   3
                            :cwd       (or (:cwd opts) (System/getProperty "user.dir"))}
              entry        {:id                id
                            :key               id
                            :name              (or name id)
                            :session-file      session-file
                            :created-at        now
                            :updated-at        now
                            :crew              (:crew opts)
                            :channel           (:channel opts)
                            :chat-type         (:chat-type opts)
                            :cwd               (or (:cwd opts) (System/getProperty "user.dir"))
                            :origin            (:origin opts)
                            :compaction-count  0
                            :input-tokens      0
                            :last-input-tokens 0
                            :output-tokens     0
                            :total-tokens      0}]
          (swap! state #(-> %
                            (assoc-in [:sessions id] entry)
                            (assoc-in [:transcripts id] [header])))
          entry))))

  (delete-session! [_ name]
    (let [id (session-id name)]
      (when (get-in @state [:sessions id])
        (swap! state #(-> % (update :sessions dissoc id) (update :transcripts dissoc id)))
        true)))

  (list-sessions [_]
    (->> (vals (:sessions @state)) (sort-by :id) vec))

  (list-sessions-by-agent [_ agent]
    (->> (vals (:sessions @state))
         (sort-by :id)
         (filter #(= agent (:crew %)))
         vec))

  (most-recent-session [_]
    (->> (vals (:sessions @state)) (sort-by :updated-at) last))

  (get-session [_ name]
    (get-in @state [:sessions (session-id name)]))

  (get-transcript [_ name]
    (let [id (session-id name)]
      (when (get-in @state [:sessions id])
        (get-in @state [:transcripts id] []))))

  (update-session! [_ name updates]
    (let [id (session-id name)]
      (swap! state update-in [:sessions id]
             (fn [entry]
               (let [updates (if-let [compaction (:compaction updates)]
                               (assoc updates :compaction (merge (or (:compaction entry) {}) compaction))
                               updates)]
                 (merge entry updates))))
      (get-in @state [:sessions id])))

  (append-message! [_ name message]
    (let [id             (session-id name)
          transcript     (get-in @state [:transcripts id] [])
          parent-id      (last-entry-id transcript)
          msg-id         (new-id)
          now            (now-iso)
          session        (get-in @state [:sessions id])
          resolved-agent (or (:crew message)
                             (when (#{"assistant" "error" "toolResult"} (:role message)) (:crew session))
                             (when (= "assistant" (:role message)) "main"))
          normalized-msg (normalize-message (cond-> message
                                              resolved-agent (assoc :crew resolved-agent)))
          entry          (cond-> {:type      "message"
                                  :id        msg-id
                                  :parentId  parent-id
                                  :timestamp now
                                  :message   normalized-msg}
                           (:tokens message) (assoc :tokens (:tokens message)))]
      (swap! state (fn [s]
                     (-> s
                         (update-in [:transcripts id] (fnil conj []) entry)
                         (update-in [:sessions id]
                                    (fn [sess]
                                      (cond-> (assoc sess :updated-at now)
                                        (get-val message :channel) (assoc :last-channel (get-val message :channel))
                                        (get-val message :to)      (assoc :last-to (get-val message :to))
                                        resolved-agent             (assoc :crew resolved-agent)))))))
      entry))

  (append-error! [_ name error-entry]
    (let [id       (session-id name)
          transcript (get-in @state [:transcripts id] [])
          parent-id (last-entry-id transcript)
          error-id  (new-id)
          now       (now-iso)
          entry     (cond-> {:type      "error"
                              :id        error-id
                              :parentId  parent-id
                              :timestamp now
                              :content   (:content error-entry)
                              :error     (:error error-entry)
                              :model     (:model error-entry)
                              :provider  (:provider error-entry)}
                      (:ex-class error-entry) (assoc :ex-class (:ex-class error-entry)))]
      (swap! state (fn [s]
                     (-> s
                         (update-in [:transcripts id] (fnil conj []) entry)
                         (assoc-in [:sessions id :updated-at] now))))
      entry))

  (append-compaction! [_ name {:keys [summary firstKeptEntryId tokensBefore]}]
    (let [id            (session-id name)
          transcript    (get-in @state [:transcripts id] [])
          parent-id     (last-entry-id transcript)
          compaction-id (new-id)
          now           (now-iso)
          entry         {:type             "compaction"
                         :id               compaction-id
                         :parentId         parent-id
                         :timestamp        now
                         :summary          summary
                         :firstKeptEntryId firstKeptEntryId
                         :tokensBefore     tokensBefore}]
      (swap! state (fn [s]
                     (-> s
                         (update-in [:transcripts id] (fnil conj []) entry)
                         (update-in [:sessions id]
                                    #(-> % (assoc :updated-at now) (update :compaction-count inc))))))
      entry))

  (splice-compaction! [_ name {:keys [compactedEntryIds firstKeptEntryId summary tokensBefore]}]
    (let [id               (session-id name)
          transcript       (get-in @state [:transcripts id] [])
          compacted-ids    (set compactedEntryIds)
          removable-ids    (->> transcript
                                (filter #(and (= "message" (:type %))
                                              (contains? compacted-ids (:id %))))
                                (map :id)
                                set)
          insert-at        (or (some (fn [[idx e]]
                                       (when (contains? removable-ids (:id e)) idx))
                                     (map-indexed vector transcript))
                               (count transcript))
          first-kept-idx   (when firstKeptEntryId
                             (some (fn [[idx e]]
                                     (when (= firstKeptEntryId (:id e)) idx))
                                   (map-indexed vector transcript)))
          before           (subvec transcript 0 insert-at)
          compaction-id    (new-id)
          now              (now-iso)
          compaction-entry {:type             "compaction"
                            :id               compaction-id
                            :parentId         (:id (last before))
                            :timestamp        now
                            :summary          summary
                            :firstKeptEntryId firstKeptEntryId
                            :tokensBefore     tokensBefore}
          after            (->> (subvec transcript (or first-kept-idx (count transcript)))
                                (remove #(contains? removable-ids (:id %)))
                                (mapv (fn [e]
                                        (if (contains? removable-ids (:parentId e))
                                          (assoc e :parentId compaction-id)
                                          e))))
          new-transcript   (drop-orphan-toolcalls
                             (into before (cons compaction-entry after)))]
      (swap! state (fn [s]
                     (-> s
                         (assoc-in [:transcripts id] new-transcript)
                         (update-in [:sessions id]
                                    #(-> % (assoc :updated-at now) (update :compaction-count inc))))))
      compaction-entry))

  (truncate-after-compaction! [_ name]
    (let [id         (session-id name)
          transcript (get-in @state [:transcripts id] [])
          compaction (->> transcript (filter #(= "compaction" (:type %))) last)]
      (when compaction
        (let [first-kept-id  (:firstKeptEntryId compaction)
              compaction-id  (:id compaction)
              removed-ids    (loop [remaining transcript ids #{}]
                               (if (empty? remaining)
                                 ids
                                 (let [e (first remaining)]
                                   (cond
                                     (= (:id e) compaction-id)                       ids
                                     (and first-kept-id (= (:id e) first-kept-id))   ids
                                     (= "message" (:type e))                         (recur (rest remaining) (conj ids (:id e)))
                                     :else                                            (recur (rest remaining) ids)))))
              remap          (loop [remaining transcript last-kept nil mapping {}]
                               (if (empty? remaining)
                                 mapping
                                 (let [e (first remaining)]
                                   (if (contains? removed-ids (:id e))
                                     (recur (rest remaining) last-kept (assoc mapping (:id e) last-kept))
                                     (recur (rest remaining) (:id e) mapping)))))
              new-transcript (into []
                                   (keep (fn [e]
                                           (when-not (contains? removed-ids (:id e))
                                             (if-let [new-parent (get remap (:parentId e))]
                                               (assoc e :parentId new-parent)
                                               e))))
                                   transcript)]
          (swap! state assoc-in [:transcripts id] new-transcript))))))

;; endregion

(defn create-store []
  (->MemorySessionStore (atom {:sessions {} :transcripts {}})))

(defn store-state [^MemorySessionStore store]
  @(.-state store))
