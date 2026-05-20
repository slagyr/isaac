(ns isaac.cli.greeter)

(defn run-fn [{:keys [_raw-args]}]
  (println "Hello from the greeter module!")
  0)

(defn make-command []
  {:name    "greet"
   :usage   "greet"
   :desc    "Print a greeting (contributed by the greeter module)"
   :run-fn  run-fn})
