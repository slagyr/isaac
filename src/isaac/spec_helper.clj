(ns isaac.spec-helper
  (:require
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]))

(defmacro with-captured-logs []
  '(speclj.core/around [it] (isaac.logger/capture-logs (it))))

(defn file-session-store [state-dir]
  (file-store/create-store state-dir))

(defn create-session!
  ([state-dir identifier]
   (create-session! state-dir identifier {}))
  ([state-dir identifier opts]
   (store/open-session! (file-session-store state-dir) identifier opts)))

(defn list-sessions
  ([state-dir]
   (store/list-sessions (file-session-store state-dir)))
  ([state-dir crew-id]
   (store/list-sessions-by-agent (file-session-store state-dir) crew-id)))

(defn most-recent-session [state-dir]
  (store/most-recent-session (file-session-store state-dir)))

(defn get-session [state-dir session-key]
  (store/get-session (file-session-store state-dir) session-key))

(defn get-transcript [state-dir session-key]
  (store/get-transcript (file-session-store state-dir) session-key))

(defn update-session! [state-dir session-key updates]
  (store/update-session! (file-session-store state-dir) session-key updates))

(defn append-message! [state-dir session-key message]
  (store/append-message! (file-session-store state-dir) session-key message))

(defn append-error! [state-dir session-key error-entry]
  (store/append-error! (file-session-store state-dir) session-key error-entry))

(defn append-compaction! [state-dir session-key compaction]
  (store/append-compaction! (file-session-store state-dir) session-key compaction))

(defn splice-compaction! [state-dir session-key compaction]
  (store/splice-compaction! (file-session-store state-dir) session-key compaction))

(defn update-tokens! [state-dir session-key {:keys [cache-read cache-write] :as updates}]
  (let [entry         (or (get-session state-dir session-key) {})
        input-tokens  (:input-tokens updates 0)
        output-tokens (:output-tokens updates 0)]
    (update-session! state-dir session-key
                     (cond-> {:input-tokens      (+ (or (:input-tokens entry) 0) input-tokens)
                              :last-input-tokens input-tokens
                              :output-tokens     (+ (or (:output-tokens entry) 0) output-tokens)
                               :total-tokens      (+ (+ (or (:input-tokens entry) 0) input-tokens)
                                                     (+ (or (:output-tokens entry) 0) output-tokens))}
                       cache-read  (assoc :cache-read (+ (or (:cache-read entry) 0) cache-read))
                       cache-write (assoc :cache-write (+ (or (:cache-write entry) 0) cache-write))))))

(defn await-condition
  "Polls pred every 1ms until it returns truthy or timeout-ms elapses (default 1000).
  Use this instead of Thread/sleep whenever waiting for async state to change."
  ([pred] (await-condition pred 1000))
  ([pred timeout-ms]
   (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
     (loop []
       (when (and (not (pred)) (< (System/currentTimeMillis) deadline))
         (Thread/sleep 1)
         (recur))))))
