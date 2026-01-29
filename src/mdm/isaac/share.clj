(ns mdm.isaac.share
  "Sharing system - enables Isaac to share thoughts with friends."
  (:require [mdm.isaac.thought :as thought]))

(defn create!
  "Create a share thought and print it to stdout.
   Returns the saved share."
  [content embedding]
  (let [share (thought/save {:kind :thought
                             :type :share
                             :content content
                             :embedding embedding})]
    (println (str "\n[Isaac wants to share]: " content "\n"))
    share))

(defn unread
  "Get all shares that haven't been acknowledged (read-at is nil)."
  []
  (->> (thought/find-by-type :share)
       (filter #(nil? (:read-at %)))))

(defn acknowledge!
  "Mark a share as read by setting :read-at timestamp.
   Returns the updated share."
  [share]
  (thought/save (assoc share :read-at (System/currentTimeMillis))))
