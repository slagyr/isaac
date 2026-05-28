(ns isaac.slash.registry
  (:require
    [isaac.config.api :as config]
    [isaac.logger :as log]
    [isaac.module.loader :as module-loader]
    [isaac.nexus :as nexus]
    [isaac.prompt.catalog :as prompt-catalog]
    [isaac.slash.builtin :as builtin]))

(defonce ^:private commands* (atom {}))

(defn- ensure-builtins! []
  (builtin/ensure-registered!))

(defn registered-command [name]
  (get @commands* (str name)))

(defn register! [{:keys [name] :as command}]
  (let [name     (str name)
        previous (get @commands* name)]
    (swap! commands* assoc name (assoc command :name name))
    (if previous
      (log/warn :slash/override :command name)
      (log/info :slash/registered :command name :module name))
    name))

(defn unregister! [name]
  (swap! commands* dissoc (str name)))

(defn clear! []
  (reset! commands* {})
  (module-loader/deactivate-core!))

(defn- activate-all! [module-index]
  (doseq [[module-id entry] module-index
          :when (seq (get-in entry [:manifest :slash-commands]))]
    (module-loader/activate! module-id module-index)))

(defn- prompt-catalog-opts [opts]
  (let [state-dir (or (:state-dir opts)
                      (nexus/get :state-dir)
                      (config/state-dir))
        fs*       (or (:fs opts) (nexus/get :fs))]
    (when (and state-dir fs*)
      {:config    (or (:config opts)
                      (config/snapshot "slash command advertisement resolves prompt-template commands"))
       :cwd       (:cwd opts)
       :fs        fs*
       :state-dir state-dir})))

(defn- prompt-template-commands [opts]
  (if-let [catalog-opts (prompt-catalog-opts opts)]
    (->> (prompt-catalog/resolve-catalog catalog-opts)
         :commands
         vals
         (map #(select-keys % [:description :name :params])))
    []))

(defn- advertised-commands [opts]
  (let [registered   (->> (vals @commands*)
                          (map #(dissoc % :handler)))
        claimed      (into #{} (map :name) registered)
        templated    (remove #(contains? claimed (:name %)) (prompt-template-commands opts))]
    (->> (concat registered templated)
         (sort-by :name)
         vec)))

(defn lookup
  ([name]
   (ensure-builtins!)
   (registered-command name))
  ([name module-index]
   (ensure-builtins!)
   (activate-all! module-index)
   (registered-command name)))

(defn all-commands
  ([]
   (ensure-builtins!)
   (advertised-commands nil))
  ([module-index]
   (all-commands module-index nil))
  ([module-index opts]
   (ensure-builtins!)
   (activate-all! module-index)
   (advertised-commands opts)))

;; Module-loader registration: dispatched by module.loader when activating a
;; manifest's :slash-commands extension.
(module-loader/register-handler! :slash-commands #'register!)
