(ns isaac.cli.agents
  (:require
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.cli.registry :as registry]
    [isaac.config.resolution :as config]))

(def option-spec
  [["-h" "--help" "Show help"]])

(defn- parse-option-map [raw-args]
  (let [{:keys [options errors]} (tools-cli/parse-opts raw-args option-spec)]
    {:options (->> options (remove (comp nil? val)) (into {}))
     :errors  errors}))

;; region ----- Config Building -----

(defn- build-cfg [agents models]
  {:crew   (into {} (map (fn [[id a]]
                           [(str id)
                            (cond-> {}
                              (:soul a)  (assoc :soul (:soul a))
                              (:model a) (assoc :model (:model a)))])
                         agents))
   :models (into {} (map (fn [[id m]]
                           [(str id)
                            {:model         (:model m)
                             :provider      (:provider m)
                             :contextWindow (:contextWindow m)}])
                         models))})

;; endregion

;; region ----- Data -----

(defn- soul-source [agent-cfg agent-id home]
  (if-let [s (:soul agent-cfg)]
    (if (> (count s) 40)
      (str (subs s 0 37) "...")
      s)
    (str home "/.isaac/agents/" agent-id "/SOUL.md")))

(defn- derive-home [opts]
  (if-let [sd (:state-dir opts)]
    (or (.getParent (java.io.File. sd))
        (System/getProperty "user.home"))
    (System/getProperty "user.home")))

(defn resolve-agents
  "Returns a seq of {:name :model :provider :soul-source} for display."
  [opts]
  (let [{:keys [agents models]} opts
        home      (derive-home opts)
        cfg       (if agents
                    (build-cfg agents models)
                    (config/load-config {:home home}))
        cfg       (config/normalize-config cfg)
        crew-map  (:crew cfg)]
    (if (empty? crew-map)
      (let [model-id   (get-in cfg [:defaults :model])
            model-cfg  (get-in cfg [:models model-id])]
        [{:name "main" :model (or (:model model-cfg) model-id "-") :provider (or (:provider model-cfg) "-") :soul-source ""}])
      (map (fn [[agent-id agent]]
             (let [model-id    (or (:model agent) (get-in cfg [:defaults :model]))
                   model-cfg   (get-in cfg [:models model-id])]
               {:name        agent-id
                :model       (or (:model model-cfg) model-id "-")
                :provider    (or (:provider model-cfg) "-")
                :soul-source (soul-source agent agent-id home)}))
           crew-map))))

;; endregion

;; region ----- Formatting -----

(defn format-agents
  "Format a seq of agent maps as a columnar table string."
  [rows]
  (let [headers ["Name" "Model" "Provider" "Soul"]
        cols    [[:name "Name"] [:model "Model"] [:provider "Provider"] [:soul-source "Soul"]]
        widths  (map (fn [[k header]]
                       (apply max (count header) (map #(count (str (get % k ""))) rows)))
                     cols)
        pad     (fn [s w] (str s (apply str (repeat (- w (count s)) " "))))
        header  (str/join "  " (map (fn [[k h] w] (pad h w)) cols widths))
        lines   (map (fn [row]
                       (str/join "  " (map (fn [[k _] w] (pad (str (get row k "")) w))
                                           cols widths)))
                     rows)]
    (str/join "\n" (concat [header] lines))))

;; endregion

;; region ----- Command -----

(defn run [opts]
  (let [rows (resolve-agents opts)]
    (println (format-agents rows))
    0))

(defn run-fn [{:keys [_raw-args] :as opts}]
  (let [{:keys [options errors]} (parse-option-map (or _raw-args []))]
    (cond
      (:help options)
      (do
        (println (registry/command-help (registry/get-command "agents")))
        0)

      (seq errors)
      (do
        (doseq [error errors] (println error))
        1)

      :else
      (run (dissoc opts :_raw-args)))))

(registry/register!
  {:name        "agents"
   :usage       "agents"
   :desc        "List configured agents"
   :option-spec option-spec
   :run-fn      run-fn})

;; endregion
