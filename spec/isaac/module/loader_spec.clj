(ns isaac.module.loader-spec
  (:require
    [c3kit.apron.env :as c3env]
    [isaac.comm.discord :as discord]
    [isaac.comm.registry :as comm-registry]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.module.loader :as sut]
    [speclj.core :refer :all]))

(def ctx {:state-dir "/state/.isaac" :cwd "/workspace"})

(defn- mod-dir! [path]
  (fs/mkdirs path))

(defn- mod-manifest! [path content]
  (fs/mkdirs (fs/parent path))
  (fs/spit path content))

(defn- unload-telly! []
  (when-let [ns-obj (find-ns 'isaac.comm.telly)]
    (remove-ns (ns-name ns-obj))))

(defn- reset-comm-registry! []
  (reset! comm-registry/*registry* (comm-registry/fresh-registry))
  (comm-registry/register-factory! "discord" discord/make))

(describe "module loader"

  (describe "discover!"

    (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

    (it "returns an empty index when :modules is absent"
      (let [{:keys [index errors]} (sut/discover! {} ctx)]
        (should= {} index)
        (should= [] errors)))

    (it "returns an empty index when :modules is empty"
      (let [{:keys [index errors]} (sut/discover! {:modules []} ctx)]
        (should= {} index)
        (should= [] errors)))

    (it "builds an index entry for a valid module"
      (mod-dir! "/state/.isaac/modules/isaac.comm.pigeon")
      (mod-manifest! "/state/.isaac/modules/isaac.comm.pigeon/module.edn"
                     "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
      (let [{:keys [index errors]} (sut/discover! {:modules [:isaac.comm.pigeon]} ctx)]
        (should= [] errors)
        (should= :isaac.comm.pigeon (get-in index [:isaac.comm.pigeon :manifest :id]))
        (should= "modules/isaac.comm.pigeon" (get-in index [:isaac.comm.pigeon :path]))))

    (it "normalizes symbol module ids (as written in EDN config files)"
      (mod-dir! "/state/.isaac/modules/isaac.comm.pigeon")
      (mod-manifest! "/state/.isaac/modules/isaac.comm.pigeon/module.edn"
                     "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
      (let [{:keys [index errors]} (sut/discover! {:modules ['isaac.comm.pigeon]} ctx)]
        (should= [] errors)
        (should-not-be-nil (get index :isaac.comm.pigeon))))

    (it "adds an error when a module directory is not found"
      (let [{:keys [index errors]} (sut/discover! {:modules [:isaac.comm.ghost]} ctx)]
        (should= nil (get index :isaac.comm.ghost))
        (should= 1 (count errors))
        (should= "modules[\"isaac.comm.ghost\"]" (:key (first errors)))
        (should= "module directory not found" (:value (first errors)))))

    (it "adds errors when a manifest fails schema validation"
      (mod-dir! "/state/.isaac/modules/isaac.comm.pigeon")
      (mod-manifest! "/state/.isaac/modules/isaac.comm.pigeon/module.edn"
                     "{:id :isaac.comm.pigeon :entry isaac.comm.pigeon}")
      (let [{:keys [index errors]} (sut/discover! {:modules [:isaac.comm.pigeon]} ctx)]
        (should= nil (get index :isaac.comm.pigeon))
        (should (some #(and (= "module-index[\"isaac.comm.pigeon\"].version" (:key %))
                            (= "must be present" (:value %)))
                      errors))))

    (it "reports a duplicate-id error for repeated module entries"
      (mod-dir! "/state/.isaac/modules/isaac.comm.pigeon")
      (mod-manifest! "/state/.isaac/modules/isaac.comm.pigeon/module.edn"
                     "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
      (let [{:keys [errors]} (sut/discover! {:modules [:isaac.comm.pigeon :isaac.comm.pigeon]} ctx)]
        (should (some #(= "duplicate module id" (:value %)) errors))))

    (it "reports a cycle error in :requires"
      (mod-dir! "/state/.isaac/modules/mod.a")
      (mod-dir! "/state/.isaac/modules/mod.b")
      (mod-manifest! "/state/.isaac/modules/mod.a/module.edn"
                     "{:id :mod.a :version \"1\" :entry mod.a :requires [:mod.b]}")
      (mod-manifest! "/state/.isaac/modules/mod.b/module.edn"
                     "{:id :mod.b :version \"1\" :entry mod.b :requires [:mod.a]}")
      (let [{:keys [errors]} (sut/discover! {:modules [:mod.a :mod.b]} ctx)]
        (should (some #(re-find #"cycle" (:value %)) errors)))))

  (describe "activate!"

    (around [it]
      (binding [fs/*fs* (fs/mem-fs)]
        (reset-comm-registry!)
        (sut/clear-activations!)
        (c3env/override! "ISAAC_TELLY_FAIL_ON_LOAD" nil)
        (unload-telly!)
        (it)
        (c3env/override! "ISAAC_TELLY_FAIL_ON_LOAD" nil)
        (sut/clear-activations!)
        (reset-comm-registry!)
        (unload-telly!)))

    (it "requires the entry namespace and logs activation once"
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly")
            module-index {:isaac.comm.telly {:dir telly-dir :manifest {:entry 'isaac.comm.telly}}}]
        (log/capture-logs
          (sut/activate! :isaac.comm.telly module-index)
          (sut/activate! :isaac.comm.telly module-index)
          (let [events (filter #(= :module/activated (:event %)) @log/captured-logs)]
            (should= 1 (count events))
            (should= "isaac.comm.telly" (:module (first events)))))
        (should (comm-registry/registered? "telly"))))

    (it "wraps load failures in structured error data and logs them"
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly")
            module-index {:isaac.comm.telly {:dir telly-dir :manifest {:entry 'isaac.comm.telly}}}]
        (c3env/override! "ISAAC_TELLY_FAIL_ON_LOAD" "true")
        (log/capture-logs
          (let [error (try
                        (sut/activate! :isaac.comm.telly module-index)
                        (catch clojure.lang.ExceptionInfo e
                          e))
                event (first (filter #(= :module/activation-failed (:event %)) @log/captured-logs))]
            (should= :module/activation-failed (:type (ex-data error)))
            (should= :isaac.comm.telly (:module-id (ex-data error)))
            (should= 'isaac.comm.telly (:entry (ex-data error)))
            (should-not-be-nil event)
            (should= "isaac.comm.telly" (:module event)))))))

  )
