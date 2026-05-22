(ns isaac.cli.common
  "Shared helpers for CLI command namespaces."
  (:require
    [isaac.cli :as registry]))

(defn standard-run-fn
  "Standard help/errors/run dispatch for CLI commands.
   parse-fn takes raw-args and returns {:options … :errors …}.
   run-fn   takes the merged opts map (without :_raw-args) and returns an exit code."
  [command-name parse-fn run-fn opts]
  (let [{:keys [options errors]} (parse-fn (or (:_raw-args opts) []))]
    (cond
      (:help options)
      (do (println (registry/command-help (registry/get-command command-name))) 0)

      (seq errors)
      (do (doseq [error errors] (println error)) 1)

      :else
      (run-fn (merge (dissoc opts :_raw-args) options)))))

(defn build-cfg [crew models]
  {:crew   (into {} (map (fn [[id c]]
                           [(str id)
                            (cond-> {}
                              (:soul c)  (assoc :soul (:soul c))
                              (:model c) (assoc :model (:model c)))])
                         crew))
   :models (into {} (map (fn [[id m]]
                           [(str id)
                            {:model          (:model m)
                             :provider       (:provider m)
                             :context-window (:context-window m)}])
                         models))})
