(ns isaac.hail.cli
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.cli :as registry]
    [isaac.cli.common :as cli-common]
    [isaac.hail.queue :as queue]))

(def ^:private hail-option-spec
  [["-h" "--help" "Show help"]])

(def ^:private send-option-spec
  [["-h" "--help" "Show help"]
   [nil "--band NAME" "Band name"]
   [nil "--payload EDN" "Payload EDN, or '-' to read payload from stdin"]
   [nil "--json" "Print the full hail record as JSON"]
   [nil "--edn" "Print the full hail record as EDN"]])

(defn- hail-help []
  (str "Usage: isaac hail <subcommand> [options]\n\n"
       "Send and inspect hail records.\n\n"
       "Subcommands:\n"
       "  send    Persist a hail record to hail/pending\n"))

(defn- send-help []
  (str "Usage: isaac hail send --band <name> [--payload <edn>|-] [--json|--edn]\n"
       "       isaac hail send -\n\n"
       "Persist a hail record to hail/pending.\n\n"
       "Options:\n"
       "  -h, --help           Show help\n"
       "      --band NAME      Band name\n"
       "      --payload EDN    Payload EDN, or '-' to read payload from stdin\n"
       "      --json           Print the full hail record as JSON\n"
       "      --edn            Print the full hail record as EDN\n"))

(defn- slurp-stdin []
  (let [content (slurp *in*)]
    (when-not (str/blank? content)
      content)))

(defn- read-edn [text]
  (edn/read-string text))

(defn- parse-send-opts [args]
  (let [{:keys [arguments errors options]} (tools-cli/parse-opts args send-option-spec :in-order true)
        whole-hail?                        (= ["-"] arguments)
        errors                             (cond-> errors
                                             (and (not whole-hail?) (nil? (:band options)))
                                             (conj "Missing required option --band")
                                             (and (:json options) (:edn options))
                                             (conj "Choose either --json or --edn, not both"))]
    {:arguments arguments
     :errors    errors
     :options   (->> options
                     (remove (comp nil? val))
                     (into {}))}))

(defn- whole-hail-stdin? [arguments]
  (= ["-"] arguments))

(defn- build-hail [{:keys [arguments options]}]
  (if (whole-hail-stdin? arguments)
    (assoc (read-edn (or (slurp-stdin) "{}")) :from :cli)
    (cond-> {:frequency {:band (:band options)}
             :from      :cli}
      (:payload options) (assoc :payload (if (= "-" (:payload options))
                                           (read-edn (or (slurp-stdin) "nil"))
                                           (read-edn (:payload options)))))))

(defn- print-record! [record options]
  (cond
    (:json options) (cli-common/print-json! record)
    (:edn options)  (cli-common/print-edn! record)
    :else           (println (:id record))))

(defn- run-send [args]
  (let [{:keys [errors options] :as parsed} (parse-send-opts args)]
    (cond
      (:help options)
      (do (println (send-help)) 0)

      (seq errors)
      (do
        (doseq [error errors]
          (binding [*out* *err*]
            (println error)))
        1)

      :else
      (let [record (queue/send! (build-hail parsed))]
        (print-record! record options)
        0))))

(defn run [args]
  (let [{:keys [arguments errors options]} (tools-cli/parse-opts args hail-option-spec :in-order true)]
    (cond
      (:help options)
      (do (println (hail-help)) 0)

      (seq errors)
      (do
        (doseq [error errors]
          (binding [*out* *err*]
            (println error)))
        1)

      (= "send" (first arguments))
      (run-send (rest arguments))

      :else
      (do
        (binding [*out* *err*]
          (println (str "Unknown hail subcommand: " (or (first arguments) ""))))
        1))))

(defn run-fn [{:keys [_raw-args]}]
  (run (or _raw-args [])))

(defn read-pending [id]
  (queue/read-pending id))

(registry/register!
  {:name        "hail"
   :usage       "hail <subcommand> [options]"
   :desc        "Produce hail records"
   :help-text   hail-help
   :option-spec hail-option-spec
   :run-fn      run-fn})
