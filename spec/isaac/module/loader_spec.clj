(ns isaac.module.loader-spec
  (:require
    [c3kit.apron.env :as c3env]
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

(defn- mod-deps! [path]
  (fs/mkdirs (fs/parent path))
  (fs/spit path "{:paths [\"src\" \"resources\"]}"))

(defn- unload-telly! []
  (when-let [ns-obj (find-ns 'isaac.comm.telly)]
    (remove-ns (ns-name ns-obj))))

(defn- reset-comm-registry! []
  (reset! comm-registry/*registry* (comm-registry/fresh-registry)))

(describe "module loader"

  (describe "discover!"

    (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

    (it "returns an empty index when :modules is absent"
      (let [{:keys [index errors]} (sut/discover! {} ctx)]
        (should= {} index)
        (should= [] errors)))

     (it "returns an empty index when :modules is empty"
       (let [{:keys [index errors]} (sut/discover! {:modules {}} ctx)]
         (should= {} index)
         (should= [] errors)))

     (it "builds an index entry for a valid module"
       (mod-dir! "/state/.isaac/modules/isaac.comm.pigeon")
       (mod-deps! "/state/.isaac/modules/isaac.comm.pigeon/deps.edn")
      (mod-manifest! "/state/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn"
                      "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
      (let [{:keys [index errors]} (sut/discover! {:modules {:isaac.comm.pigeon {:local/root "/state/.isaac/modules/isaac.comm.pigeon"}}} ctx)]
        (should= [] errors)
        (should= :isaac.comm.pigeon (get-in index [:isaac.comm.pigeon :manifest :id]))
        (should= "/state/.isaac/modules/isaac.comm.pigeon" (get-in index [:isaac.comm.pigeon :path]))))

    (it "reads local/root manifests without adding deps during discovery"
      (mod-dir! "/state/.isaac/modules/isaac.comm.pigeon")
      (mod-deps! "/state/.isaac/modules/isaac.comm.pigeon/deps.edn")
      (mod-manifest! "/state/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn"
                     "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
      (let [calls (atom [])]
        (with-redefs [isaac.module.loader/add-module-deps! (fn [id coord]
                                                             (swap! calls conj [id coord]))]
          (let [{:keys [index errors]} (sut/discover! {:modules {:isaac.comm.pigeon {:local/root "/state/.isaac/modules/isaac.comm.pigeon"}}} ctx)]
            (should= [] errors)
            (should-not-be-nil (get index :isaac.comm.pigeon))
            (should= [] @calls)))))

    (it "accepts string or symbol-like keys once normalized to keywords"
      (mod-dir! "/state/.isaac/modules/isaac.comm.pigeon")
      (mod-deps! "/state/.isaac/modules/isaac.comm.pigeon/deps.edn")
      (mod-manifest! "/state/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn"
                      "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
      (let [{:keys [index errors]} (sut/discover! {:modules {:isaac.comm.pigeon {:local/root "/state/.isaac/modules/isaac.comm.pigeon"}}} ctx)]
        (should= [] errors)
        (should-not-be-nil (get index :isaac.comm.pigeon))))

    (it "adds an error when a local/root path is not found"
      (let [{:keys [index errors]} (sut/discover! {:modules {:isaac.comm.ghost {:local/root "/state/.isaac/modules/isaac.comm.ghost"}}} ctx)]
        (should= nil (get index :isaac.comm.ghost))
        (should= 1 (count errors))
        (should= "modules[\"isaac.comm.ghost\"]" (:key (first errors)))
        (should= "local/root path does not resolve" (:value (first errors)))))

    (it "adds errors when a manifest fails schema validation"
      (mod-dir! "/state/.isaac/modules/isaac.comm.pigeon")
      (mod-deps! "/state/.isaac/modules/isaac.comm.pigeon/deps.edn")
      (mod-manifest! "/state/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn"
                      "{:id :isaac.comm.pigeon :entry isaac.comm.pigeon}")
      (let [{:keys [index errors]} (sut/discover! {:modules {:isaac.comm.pigeon {:local/root "/state/.isaac/modules/isaac.comm.pigeon"}}} ctx)]
        (should= nil (get index :isaac.comm.pigeon))
        (should (some #(and (= "module-index[\"isaac.comm.pigeon\"].version" (:key %))
                            (= "must be present" (:value %)))
                      errors))))

    (it "reports a cycle error in :requires"
      (mod-dir! "/state/.isaac/modules/mod.a")
      (mod-dir! "/state/.isaac/modules/mod.b")
      (mod-deps! "/state/.isaac/modules/mod.a/deps.edn")
      (mod-deps! "/state/.isaac/modules/mod.b/deps.edn")
      (mod-manifest! "/state/.isaac/modules/mod.a/resources/isaac-manifest.edn"
                      "{:id :mod.a :version \"1\" :entry mod.a :requires [:mod.b]}")
      (mod-manifest! "/state/.isaac/modules/mod.b/resources/isaac-manifest.edn"
                      "{:id :mod.b :version \"1\" :entry mod.b :requires [:mod.a]}")
      (let [{:keys [errors]} (sut/discover! {:modules {:mod.a {:local/root "/state/.isaac/modules/mod.a"}
                                                        :mod.b {:local/root "/state/.isaac/modules/mod.b"}}} ctx)]
        (should (some #(re-find #"cycle" (:value %)) errors)))))

  (describe "activate!"

    (around [it]
      (binding [fs/*fs* (fs/mem-fs)]
        (reset! @#'isaac.module.loader/loaded-module-coords* #{})
        (reset-comm-registry!)
        (sut/clear-activations!)
        (reset! c3env/-overrides {})
        (unload-telly!)
        (it)
        (reset! @#'isaac.module.loader/loaded-module-coords* #{})
        (reset! c3env/-overrides {})
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

    (it "adds local/root deps on first activation"
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly")
            module-index {:isaac.comm.telly {:coord {:local/root telly-dir}
                                             :path  telly-dir
                                             :manifest {:entry 'isaac.comm.telly}}}
            calls       (atom [])]
        (with-redefs [isaac.module.loader/add-module-deps! (fn [id coord]
                                                             (swap! calls conj [id coord]))]
          (sut/activate! :isaac.comm.telly module-index)
          (should= [[:isaac.comm.telly {:local/root telly-dir}]] @calls))))

    (it "does not add the same local/root deps twice across activation resets"
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly-cache-test")
            module-index {:isaac.comm.telly {:coord {:local/root telly-dir}
                                             :path  telly-dir
                                             :manifest {:entry 'isaac.comm.telly}}}
            calls       (atom [])]
        (with-redefs [isaac.module.loader/add-module-deps! (fn [id coord]
                                                             (swap! calls conj [id coord]))]
          (sut/activate! :isaac.comm.telly module-index)
          (sut/clear-activations!)
          (sut/activate! :isaac.comm.telly module-index)
          (should= [[:isaac.comm.telly {:local/root telly-dir}]] @calls))))

  )
