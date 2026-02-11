(ns mdm.isaac.tui.ansi
  "ANSI escape code helpers for terminal color and formatting.
   Pure functions - no side effects."
  (:require [clojure.string :as str]))

(def ^:private esc "\u001b[")
(def ^:private reset (str esc "0m"))

(defn- wrap [code text]
  (str esc code "m" text reset))

;; Text styles
(defn bold [text] (wrap "1" text))
(defn dim [text] (wrap "2" text))
(defn inverse [text] (wrap "7" text))

;; Colors
(defn red [text] (wrap "31" text))
(defn green [text] (wrap "32" text))
(defn yellow [text] (wrap "33" text))
(defn blue [text] (wrap "34" text))
(defn cyan [text] (wrap "36" text))

;; Utility functions

(defn strip-ansi
  "Removes ANSI escape sequences from text."
  [text]
  (str/replace text #"\u001b\[[0-9;]*m" ""))

(defn visible-length
  "Returns the visible length of text, excluding ANSI codes."
  [text]
  (count (strip-ansi text)))

(defn pad-right
  "Pads text with spaces to reach visible width, or truncates if too long.
   Handles ANSI escape codes correctly."
  [text width]
  (let [vis-len (visible-length text)]
    (cond
      (= vis-len width) text
      (> vis-len width) (subs (strip-ansi text) 0 width)
      :else (str text (apply str (repeat (- width vis-len) \space))))))

(defn horizontal-line
  "Creates a horizontal line of dashes."
  [width]
  (apply str (repeat width \-)))

(defn blank-line
  "Creates a line of spaces."
  [width]
  (apply str (repeat width \space)))
