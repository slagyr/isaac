(ns isaac.spec-helper
  (:require [isaac.logger :as log]
            [speclj.core :refer :all]))

(defn with-captured-logs []
  (around [it] (log/capture-logs (it))))

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

