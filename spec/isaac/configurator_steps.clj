(ns isaac.configurator-steps
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defthen defwhen helper!]]
    [isaac.comm.registry :as comm-registry]
    [isaac.config.change-source :as change-source]
    [isaac.cron.scheduler :as scheduler]
    [isaac.fs :as fs]
    [isaac.hooks :as hooks]
    [isaac.server.app :as app]
    [isaac.spec-helper :as helper]))

(helper! isaac.configurator-steps)

(declare isaac-edn-path)
(declare with-server-fs)

(defn- ->slot-key [name]
  (keyword name))

(defn- live-instance [slot-name]
  (when-let [tree (app/comm-tree)]
    (get-in @tree [:comms (->slot-key slot-name)])))

(defn- live-node [path]
  (when-let [tree (app/comm-tree)]
    (get-in @tree path)))

(defn- parse-state-value [value]
  (cond
    (re-matches #"-?\d+" value)         (parse-long value)
    (= "true" (str/lower-case value))   true
    (= "false" (str/lower-case value))  false
    (str/starts-with? value "[")        (edn/read-string value)
    (str/starts-with? value "{")        (edn/read-string value)
    (str/starts-with? value ":")        (edn/read-string value)
    (str/starts-with? value "\"")       (edn/read-string value)
    :else                                value))

(defn- read-state [instance]
  ;; telly is a fixture module loaded via module-loader (not as a plain :require)
  ;; — soft-resolve avoids deftype-class-redefinition mismatches when the module
  ;; is reloaded between scenarios.
  (let [telly? (requiring-resolve 'isaac.comm.telly/telly?)
        state  (requiring-resolve 'isaac.comm.telly/state)]
    (cond
      (telly? instance) (state instance)
      :else             {})))

(defn- get-by-dotted-path [m path]
  (let [keys (mapv keyword (str/split path #"\."))]
    (get-in m keys)))

(defn comm-is-registered [impl]
  (let [ns-sym      (symbol (str "isaac.comm." impl))
         _           (require ns-sym)
         make-factory (requiring-resolve (symbol (str ns-sym "/make")))]
    (let [module-id    (keyword (str "isaac.comm." impl))
          module-root  (str (System/getProperty "user.dir") "/modules/" (name module-id))
          module-coord {:local/root (str "modules/" (name module-id))}]
      (when (.isDirectory (java.io.File. module-root))
        (g/update! :server-config #(assoc-in (or % {}) [:modules module-id] module-coord))
        (with-server-fs
          (fn []
            (let [path    (isaac-edn-path)
                  current (if (fs/exists? path) (edn/read-string (fs/slurp path)) {})]
              (fs/mkdirs (fs/parent path))
              (fs/spit path (pr-str (assoc-in current [:modules module-id] module-coord))))))))
    (comm-registry/register-factory! impl make-factory))
  (g/should (comm-registry/registered? impl)))

(defn- expectations-met? [name table]
  (when-let [instance (live-instance name)]
    (let [state (read-state instance)]
      (every? (fn [row]
                (let [row-map  (zipmap (:headers table) row)
                      path     (get row-map "path")
                      expected (parse-state-value (get row-map "value"))]
                  (= expected (get-by-dotted-path state path))))
              (:rows table)))))

(defn comm-exists-with-state [name table]
  (helper/await-condition #(expectations-met? name table))
  (let [instance (live-instance name)]
    (g/should-not-be-nil instance)
    (let [state (read-state instance)]
      (doseq [row (:rows table)]
        (let [row-map  (zipmap (:headers table) row)
              path     (get row-map "path")
              expected (parse-state-value (get row-map "value"))
              actual   (get-by-dotted-path state path)]
          (g/should= expected actual))))))

(defn comm-does-not-exist [name]
  (helper/await-condition #(nil? (live-instance name)))
  (g/should-be-nil (live-instance name)))

(defn hook-registry-entry-has [name table]
  (helper/await-condition #(when-let [entry (hooks/lookup-hook name)]
                             (every? (fn [row]
                                       (let [row-map  (zipmap (:headers table) row)
                                             path     (get row-map "path")
                                             expected (parse-state-value (get row-map "value"))]
                                         (= expected (get-by-dotted-path (:entry entry) path))))
                                     (:rows table))))
  (let [entry (hooks/lookup-hook name)]
    (g/should-not-be-nil entry)
    (doseq [row (:rows table)]
      (let [row-map  (zipmap (:headers table) row)
            path     (get row-map "path")
            expected (parse-state-value (get row-map "value"))
            actual   (get-by-dotted-path (:entry entry) path)]
        (g/should= expected actual)))))

(defn cron-job-has [name table]
  (helper/await-condition #(when-let [instance (live-node [:cron])]
                             (let [state (scheduler/job-state instance name)]
                               (and state
                                    (every? (fn [row]
                                              (let [row-map  (zipmap (:headers table) row)
                                                    path     (get row-map "path")
                                                    expected (parse-state-value (get row-map "value"))]
                                                (= expected (get-by-dotted-path state path))))
                                            (:rows table))))))
  (let [instance (live-node [:cron])
        state    (scheduler/job-state instance name)]
    (g/should-not-be-nil state)
    (doseq [row (:rows table)]
      (let [row-map  (zipmap (:headers table) row)
            path     (get row-map "path")
            expected (parse-state-value (get row-map "value"))
            actual   (get-by-dotted-path state path)]
        (g/should= expected actual)))))

;; --- config update step (delta-merge with #delete sentinel) -------------

(defn- isaac-edn-path []
  (let [home (or (g/get :runtime-state-dir)
                 (str (g/get :state-dir) "/.isaac"))]
    (str home "/config/isaac.edn")))

(defn- deep-merge [a b]
  (cond
    (and (map? a) (map? b)) (merge-with deep-merge a b)
    (some? b)                b
    :else                    a))

(defn- read-current-cfg []
  (let [path     (isaac-edn-path)
        on-disk  (when (fs/exists? path)
                   (try (edn/read-string (fs/slurp path))
                        (catch Exception _ nil)))
        in-mem   (g/get :server-config)]
    (deep-merge (or on-disk {}) (or in-mem {}))))

(defn- dissoc-in [m path]
  (cond
    (empty? path) m
    (= 1 (count path)) (dissoc m (first path))
    :else (let [parent-path (vec (butlast path))
                leaf        (last path)
                parent      (get-in m parent-path)]
            (if (map? parent)
              (assoc-in m parent-path (dissoc parent leaf))
              m))))

(defn- apply-update [cfg path-str value-str]
  (let [keys (mapv keyword (str/split path-str #"\."))]
    (if (= "#delete" (str/trim (str value-str)))
      (dissoc-in cfg keys)
      ;; Config update rows represent on-disk config values, so leave bare
      ;; words as strings instead of assertion-style keywords.
      (assoc-in cfg keys
                (cond
                  (re-matches #"-?\d+" value-str) (parse-long value-str)
                  (= "true" (str/lower-case value-str)) true
                  (= "false" (str/lower-case value-str)) false
                  (or (str/starts-with? value-str "[")
                      (str/starts-with? value-str "{")
                      (str/starts-with? value-str ":")
                      (str/starts-with? value-str "\""))
                  (edn/read-string value-str)
                  :else value-str)))))

(defn- with-server-fs [f]
  (if-let [mem (g/get :mem-fs)]
    (binding [fs/*fs* mem] (f))
    (f)))

(defn- notify-change! [path]
  (when-let [source (g/get :config-change-source)]
    (change-source/notify-path! source path)))

(defn config-updated [table]
  (with-server-fs
    (fn []
      (let [path (isaac-edn-path)
            cfg  (reduce (fn [acc row]
                           (let [row-map (zipmap (:headers table) row)
                                 p       (get row-map "path")
                                 v       (get row-map "value")]
                             (apply-update acc p v)))
                         (read-current-cfg)
                         (:rows table))]
        (fs/mkdirs (fs/parent path))
        (fs/spit path (pr-str cfg))
        (notify-change! path)))))

(defn server-not-running []
  (g/should-not (app/running?)))

;; --- step definitions ---------------------------------------------------

(defgiven "the {impl:string} comm is registered" isaac.configurator-steps/comm-is-registered
  "Loads the plugin namespace so its (register-factory! ...) self-registers
   the impl in isaac.comm.registry/*registry*. Test helper for comm impls
   that self-register on namespace load.")

(defthen "the comm {name:string} exists with state:" isaac.configurator-steps/comm-exists-with-state
  "Asserts that an instance lives at [:comms <name>] in the server's object
    tree and that its state map matches each row (dotted path -> value).")

(defthen "the comm {name:string} does not exist" isaac.configurator-steps/comm-does-not-exist)

(defthen "the hook {name:string} registry entry has:" isaac.configurator-steps/hook-registry-entry-has)

(defthen "the cron job {name:string} has:" isaac.configurator-steps/cron-job-has)

(defwhen "config is updated:" isaac.configurator-steps/config-updated
  "Delta-merges path/value rows into config/isaac.edn. A value of \"#delete\"
   removes the key from the config tree. Triggers a config reload via the
   bound change source.")

(defthen "the Isaac server is not running" isaac.configurator-steps/server-not-running)
