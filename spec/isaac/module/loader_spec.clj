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

(defn- mod-root [id]
  (str "/state/.isaac/modules/" (name id)))

(defn- mod-coord [id]
  {:local/root (mod-root id)})

(defn- write-local-module! [id manifest]
  (let [root (mod-root id)]
    (mod-dir! root)
    (mod-deps! (str root "/deps.edn"))
    (mod-manifest! (str root "/resources/isaac-manifest.edn") (pr-str manifest))))

(defn- discover-local! [ids]
  (sut/discover! {:modules (into {} (map (fn [id] [id (mod-coord id)]) ids))} ctx))

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
      (write-local-module! :isaac.comm.pigeon
                           {:id      :isaac.comm.pigeon
                            :version "0.1.0"
                            :entry   'isaac.comm.pigeon})
      (let [{:keys [index errors]} (discover-local! [:isaac.comm.pigeon])]
        (should= [] errors)
        (should= :isaac.comm.pigeon (get-in index [:isaac.comm.pigeon :manifest :id]))
        (should= (mod-root :isaac.comm.pigeon) (get-in index [:isaac.comm.pigeon :path]))))

    (it "reads local/root manifests without adding deps during discovery"
      (write-local-module! :isaac.comm.pigeon
                           {:id      :isaac.comm.pigeon
                            :version "0.1.0"
                            :entry   'isaac.comm.pigeon})
      (let [calls (atom [])]
        (with-redefs [isaac.module.loader/add-module-deps! (fn [id coord]
                                                             (swap! calls conj [id coord]))]
          (let [{:keys [index errors]} (discover-local! [:isaac.comm.pigeon])]
            (should= [] errors)
            (should-not-be-nil (get index :isaac.comm.pigeon))
            (should= [] @calls)))))

    (it "accepts string or symbol-like keys once normalized to keywords"
      (write-local-module! :isaac.comm.pigeon
                           {:id      :isaac.comm.pigeon
                            :version "0.1.0"
                            :entry   'isaac.comm.pigeon})
      (let [{:keys [index errors]} (discover-local! [:isaac.comm.pigeon])]
        (should= [] errors)
        (should-not-be-nil (get index :isaac.comm.pigeon))))

    (it "adds an error when a local/root path is not found"
      (let [{:keys [index errors]} (sut/discover! {:modules {:isaac.comm.ghost {:local/root "/state/.isaac/modules/isaac.comm.ghost"}}} ctx)]
        (should= nil (get index :isaac.comm.ghost))
        (should= 1 (count errors))
        (should= "modules[\"isaac.comm.ghost\"]" (:key (first errors)))
        (should= "local/root path does not resolve" (:value (first errors)))))

    (it "adds errors when a manifest fails schema validation"
      (write-local-module! :isaac.comm.pigeon
                           {:id :isaac.comm.pigeon :entry 'isaac.comm.pigeon})
      (let [{:keys [index errors]} (discover-local! [:isaac.comm.pigeon])]
        (should= nil (get index :isaac.comm.pigeon))
        (should (some #(and (= "module-index[\"isaac.comm.pigeon\"].version" (:key %))
                            (= "must be present" (:value %)))
                      errors))))

    (it "reports a cycle error in :requires"
      (write-local-module! :mod.a {:id :mod.a :version "1" :entry 'mod.a :requires [:mod.b]})
      (write-local-module! :mod.b {:id :mod.b :version "1" :entry 'mod.b :requires [:mod.a]})
      (let [{:keys [index errors]} (discover-local! [:mod.a :mod.b])]
        (should= #{:mod.a :mod.b} (set (keys index)))
        (should= [{:key "modules[\"mod.a\"]"
                   :value "requires cycle detected involving mod.a"}]
                 errors))))

  (describe "discover-resolved"

    (it "returns a manifest read error when no matching classpath resource exists"
      (with-redefs [isaac.module.loader/add-module-deps! (fn [_ _] nil)
                    isaac.module.loader/manifest-resource (fn [_] nil)]
        (let [result (@#'sut/discover-resolved ctx :isaac.comm.ghost {:mvn/version "0.1.0"})]
          (should= [{:key "modules[\"isaac.comm.ghost\"]" :value "manifest: could not read"}]
                   (:errors result))))))

    (it "returns a manifest read error when the manifest content is unreadable"
      (with-redefs [isaac.module.loader/add-module-deps! (fn [_ _] nil)
                    isaac.module.loader/manifest-resource (fn [_] :fake-url)
                    isaac.module.loader/read-manifest-edn (fn [_] nil)]
        (let [result (@#'sut/discover-resolved ctx :isaac.comm.ghost {:mvn/version "0.1.0"})]
          (should= [{:key "modules[\"isaac.comm.ghost\"]" :value "manifest: could not read"}]
                   (:errors result))))))

    (it "returns schema validation errors for an invalid resolved manifest"
      (with-redefs [isaac.module.loader/add-module-deps! (fn [_ _] nil)
                    isaac.module.loader/manifest-resource (fn [_] :fake-url)
                    isaac.module.loader/read-manifest-edn (fn [_] {:id :isaac.comm.ghost :entry 'isaac.comm.ghost})]
        (let [result (@#'sut/discover-resolved ctx :isaac.comm.ghost {:mvn/version "0.1.0"})]
          (should (some #(= "module-index[\"isaac.comm.ghost\"].version" (:key %))
                        (:errors result))))))

    (it "wraps dependency loading failures as module errors"
      (with-redefs [isaac.module.loader/add-module-deps! (fn [_ _] (throw (Exception. "boom")))]
        (let [result (@#'sut/discover-resolved ctx :isaac.comm.ghost {:mvn/version "0.1.0"})]
          (should= [{:key "modules[\"isaac.comm.ghost\"]" :value "boom"}]
                   (:errors result)))))

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
            (should= "isaac.comm.telly" (:module event))))))

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

    (it "calls -isaac-init on the entry namespace after require, when present"
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly")
            module-index {:isaac.comm.telly {:dir telly-dir :manifest {:entry 'isaac.comm.telly}}}
            init-called  (atom false)]
        (with-redefs [isaac.module.loader/call-isaac-init! (fn [_] (reset! init-called true))]
          (sut/activate! :isaac.comm.telly module-index))
        (should= true @init-called)))

    (it "activates successfully when entry namespace has no -isaac-init"
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly")
            module-index {:isaac.comm.telly {:dir telly-dir :manifest {:entry 'isaac.comm.telly}}}]
        (with-redefs [isaac.module.loader/call-isaac-init! (fn [_] nil)]
          (should= :activated (sut/activate! :isaac.comm.telly module-index)))))

  )
