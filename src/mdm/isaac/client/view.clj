(ns mdm.isaac.client.view
  "View rendering for Isaac terminal client.
   Pure functions that transform state into strings."
  (:require [clojure.string :as str]
            [mdm.isaac.client.core :as core]))

;; Status indicators
(def ^:private status-icons
  {:connected    "[+]"
   :disconnected "[-]"
   :connecting   "[~]"})

(def ^:private goal-status-icons
  {:active    "[*]"
   :resolved  "[v]"
   :abandoned "[x]"})

(def ^:private type-icons
  {:thought  "."
   :insight  "!"
   :question "?"
   :share    ">"})

(defn render-status
  "Renders the status bar showing connection and thinking status."
  [state]
  (let [conn-status (:connection-status state)
        conn-text   (if (= :connected conn-status) "Connected" "Disconnected")
        conn-icon   (get status-icons conn-status "[-]")
        thinking    (core/current-thinking state)]
    (str "Isaac " conn-icon " " conn-text
         (when thinking
           (str " | " thinking)))))

(defn render-goals
  "Renders the goals panel."
  [state]
  (let [goals (:goals state)]
    (str "== Goals ==\n"
         (if (empty? goals)
           "  No goals"
           (->> goals
                (map (fn [g]
                       (let [icon (get goal-status-icons (:status g) "[?]")]
                         (str "  " icon " " (:content g)
                              " [" (name (:status g)) "]"))))
                (str/join "\n"))))))

(defn render-thoughts
  "Renders the thoughts panel."
  [state]
  (let [thoughts (:thoughts state)]
    (str "== Thoughts ==\n"
         (if (empty? thoughts)
           "  No thoughts"
           (->> thoughts
                (map (fn [t]
                       (let [icon (get type-icons (:type t) ".")]
                         (str "  " icon " " (:content t)))))
                (str/join "\n"))))))

(defn render-shares
  "Renders the shares panel."
  [state]
  (let [shares (:shares state)]
    (str "== Shares ==\n"
         (if (empty? shares)
           "  No shares"
           (->> shares
                (map (fn [s]
                       (str "  > " (:content s))))
                (str/join "\n"))))))

(defn render-input
  "Renders the input line."
  [state]
  (str "> " (:input state) "_"))

(defn render-help
  "Renders help text showing key bindings."
  []
  (str "q:quit | Tab:switch panel | Enter:send | /goals /thoughts /shares"))

(defn view
  "Main view function - renders entire UI."
  [state]
  (str/join "\n\n"
            [(render-status state)
             (render-goals state)
             (render-thoughts state)
             (render-shares state)
             (render-input state)
             (render-help)]))
