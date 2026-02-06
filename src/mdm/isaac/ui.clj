(ns mdm.isaac.ui
  "UI abstraction for output messages.
   Allows tests to use a mock implementation instead of console output.")

(defprotocol UI
  "Protocol for user interface output."
  (inform [this message] "Display an informational message")
  (warn [this message] "Display a warning message")
  (error [this message] "Display an error message"))

;; Production implementation - prints to console
(defrecord ConsoleUI []
  UI
  (inform [_ msg] (println msg))
  (warn [_ msg] (println "[WARN]" msg))
  (error [_ msg] (println "[ERROR]" msg)))

;; Testing implementation - stores messages for inspection
(defrecord MockUI [messages]
  UI
  (inform [_ msg] (swap! messages conj {:type :info :msg msg}))
  (warn [_ msg] (swap! messages conj {:type :warn :msg msg}))
  (error [_ msg] (swap! messages conj {:type :error :msg msg})))

;; Default UI instance for production use
(def default-ui (->ConsoleUI))
