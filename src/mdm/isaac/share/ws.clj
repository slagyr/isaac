(ns mdm.isaac.share.ws
  "WebSocket handlers for shares."
  (:require [c3kit.wire.apic :as apic]
            [mdm.isaac.share.core :as share]
            [mdm.isaac.thought.core :as thought]))

(defn ws-unread
  "Returns all unread shares."
  [_message]
  (apic/ok (share/unread)))

(defn ws-ack
  "Acknowledges a share by id."
  [{:keys [params]}]
  (let [shares (thought/find-by-type :share)
        share (first (filter #(= (:id params) (:id %)) shares))]
    (if share
      (do
        (share/acknowledge! share)
        (apic/ok))
      (apic/fail))))
