(ns mdm.isaac.share.core
  "Sharing system - enables Isaac to share thoughts with friends."
  (:require [c3kit.bucket.api :as db]
            [mdm.isaac.thought.core :as thought]
            [mdm.isaac.ui :as ui]))

(defn create!
  "Create a share thought and notify via UI.
   Options:
     :ui - UI implementation for output (defaults to console)"
  ([content embedding] (create! content embedding {}))
  ([content embedding {:keys [ui] :or {ui ui/default-ui}}]
   (let [share (db/tx {:kind      :thought
                       :type      :share
                       :content   content
                       :embedding embedding})]
     (ui/inform ui (str "\n[Isaac wants to share]: " content "\n"))
     share)))

(defn unread
  "Get all shares that haven't been acknowledged (read-at is nil)."
  []
  (->> (thought/find-by-type :share)
       (filter #(nil? (:read-at %)))))

(defn acknowledge!
  "Mark a share as read by setting :read-at timestamp.
   Returns the updated share."
  [share]
  (db/tx (assoc share :read-at (System/currentTimeMillis))))
