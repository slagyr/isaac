(ns isaac.config.cli.mutate-common
  "Shared helpers for 'config set' and 'config unset'."
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [isaac.config.cli.common :as common]
    [isaac.config.mutate :as mutate]
    [isaac.config.schema :as config-schema]
    [isaac.logger :as log]))

(defn target-spec-for [path-str]
  (config-schema/schema-for-data-path path-str))

(defn parse-set-value [spec raw-value]
  (cond
    (re-matches #"-?\d+" raw-value)
    (parse-long raw-value)

    (#{"false" "nil" "true"} raw-value)
    (edn/read-string raw-value)

    (str/starts-with? raw-value ":")
    (edn/read-string raw-value)

    (and spec (:coerce spec) (re-matches #"[A-Za-z_][A-Za-z0-9_-]*" raw-value))
    (keyword raw-value)

    :else
    raw-value))

(defn read-stdin-value []
  (try
    {:value (edn/read-string (slurp *in*))}
    (catch Exception _
      {:error "stdin must contain valid EDN"})))

(defn- format-errors [errors]
  (str/join "; " (map (fn [{:keys [key value]}] (str key " - " value)) errors)))

(defn log-mutation! [level event file path-str & kvs]
  (apply log/log* level event file 0 :path path-str kvs))

(defn- print-status-error! [status path-str]
  (binding [*out* *err*]
    (println (case status
               :missing-path      "missing path"
               :missing-entity-id "missing entity id"
               :invalid-path      (str "invalid path: " path-str)
               :not-found         (str "not found: " path-str)
               (str "config error: " (name status))))))

(defn handle-mutate-result! [operation path-str result value]
  (common/print-warnings! (:warnings result))
  (case (:status result)
    :ok
    (do
      (case operation
        :set   (log-mutation! :info :config/set   (:file result) path-str :value value)
        :unset (log-mutation! :info :config/unset (:file result) path-str))
      0)

    :invalid
    (do
      (common/print-errors! (:errors result) "error")
      (when (= :set operation)
        (log-mutation! :error :config/set-failed "config" path-str :error (format-errors (:errors result))))
      1)

    :invalid-config
    (do
      (common/print-errors! (:errors result) "error")
      1)

    (do
      (print-status-error! (:status result) path-str)
      1)))

(defn set-config! [home path-str raw-value]
  (let [value-result (if (= "-" raw-value)
                       (read-stdin-value)
                       {:value (parse-set-value (target-spec-for path-str) raw-value)})]
    (if (:error value-result)
      (do
        (binding [*out* *err*]
          (println (:error value-result)))
        (log-mutation! :error :config/set-failed "config" path-str :error (:error value-result))
        1)
      (let [value  (:value value-result)
            result (mutate/set-config home path-str value)]
        (handle-mutate-result! :set path-str result value)))))

(defn unset-config! [home path-str]
  (handle-mutate-result! :unset path-str (mutate/unset-config home path-str) nil))
