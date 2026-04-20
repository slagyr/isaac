(ns isaac.cli.crew
  (:require
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.cli.registry :as registry]
    [isaac.config.loader :as config]))

(def option-spec
  [["-h" "--help" "Show help"]])

(defn- parse-option-map [raw-args]
  (let [{:keys [options errors]} (tools-cli/parse-opts raw-args option-spec)]
    {:options (->> options (remove (comp nil? val)) (into {}))
     :errors  errors}))

(defn- build-cfg [crew models]
  {:crew   (into {} (map (fn [[id c]]
                           [(str id)
                            (cond-> {}
                              (:soul c)  (assoc :soul (:soul c))
                              (:model c) (assoc :model (:model c)))])
                         crew))
   :models (into {} (map (fn [[id m]]
                           [(str id)
                            {:model         (:model m)
                             :provider      (:provider m)
                             :context-window (:context-window m)}])
                         models))})

(defn- soul-source [crew-cfg crew-id home]
  (if-let [s (:soul crew-cfg)]
    (if (> (count s) 40)
      (str (subs s 0 37) "...")
      s)
    (str home "/.isaac/crew/" crew-id "/SOUL.md")))

(defn- derive-home [opts]
  (if-let [sd (:state-dir opts)]
    (or (.getParent (java.io.File. sd))
        (System/getProperty "user.home"))
    (System/getProperty "user.home")))

(defn resolve-crew
  "Returns a seq of {:name :model :provider :soul-source} for display."
  [opts]
  (let [{:keys [crew models]} opts
        home      (derive-home opts)
        cfg       (if crew
                    (build-cfg crew models)
                    (config/load-config {:home home}))
        cfg       (config/normalize-config cfg)
        crew-map  (cond-> (:crew cfg)
                    (not (contains? (:crew cfg) "main")) (assoc "main" {}))]
    (map (fn [[crew-id crew-member]]
           (let [model-id    (or (:model crew-member) (get-in cfg [:defaults :model]))
                 model-cfg   (get-in cfg [:models model-id])
                 model-name  (or (:model model-cfg) model-id "-")
                 provider    (or (:provider model-cfg) "-")]
             {:name        crew-id
              :model       model-name
              :provider    provider
              :soul-source (soul-source crew-member crew-id home)}))
         crew-map)))

(defn format-crew [rows]
  (let [cols    [[:name "Name"] [:model "Model"] [:provider "Provider"] [:soul-source "Soul"]]
        widths  (map (fn [[k header]]
                       (apply max (count header) (map #(count (str (get % k ""))) rows)))
                     cols)
        pad     (fn [s w] (str s (apply str (repeat (- w (count s)) " "))))
        header  (str/join "  " (map (fn [[_ h] w] (pad h w)) cols widths))
        rule    (str/join "  " (map (fn [_ w] (apply str (repeat w "─"))) cols widths))
        lines   (map (fn [row]
                       (str/join "  " (map (fn [[k _] w] (pad (str (get row k "")) w)) cols widths)))
                      rows)]
    (str/join "\n" (concat [header rule] lines))))

(defn run [opts]
  (println (format-crew (resolve-crew opts)))
  0)

(defn run-fn [{:keys [_raw-args] :as opts}]
  (let [{:keys [options errors]} (parse-option-map (or _raw-args []))]
    (cond
      (:help options)
      (do
        (println (registry/command-help (registry/get-command "crew")))
        0)

      (seq errors)
      (do
        (doseq [error errors] (println error))
        1)

      :else
      (run (dissoc opts :_raw-args)))))

(registry/register!
  {:name        "crew"
   :usage       "crew"
   :desc        "List configured crew members"
   :option-spec option-spec
   :run-fn      run-fn})
