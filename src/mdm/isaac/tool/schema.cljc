(ns mdm.isaac.tool.schema
  "Schema definitions for tools."
  (:require [c3kit.apron.schema :as s]))

;; Permission types for tools
(def valid-permissions #{:internal    ; only Isaac can invoke (vs external MCP)
                         :read-only   ; tool only reads data
                         :admin})     ; requires elevated permissions

;; Parameter definition schema
(def param
  {:type     {:type :keyword
              :validate keyword?
              :message "type must be a keyword"}
   :required {:type :boolean}})

;; Tool definition schema
(def tool
  {:name        {:type :keyword
                 :validate keyword?
                 :message "name must be a keyword"}
   :description {:type :string
                 :validate string?
                 :message "description must be a string"}
   :params      {:type :map}
   :permissions {:type :any :validate #(or (nil? %)
                                            (and (set? %)
                                                 (every? valid-permissions %)))}
   :execute     {:type :fn}})
