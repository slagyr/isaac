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

(def valid-comm-manifest
  {:id       :isaac.comm.pigeon
   :version  "0.1.0"
   :requires []
   :extends  {:comm {:pigeon {:isaac/factory 'isaac.comm.pigeon/make}}}})

(describe "module loader"

  (describe "discover!"

    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example] (binding [fs/*fs* (fs/mem-fs)] (example)))

    (it "includes the core manifest even when :modules is absent"
      (let [{:keys [index errors]} (sut/discover! {} ctx)]
        (should= [] errors)
        (should= :isaac.core (get-in index [:isaac.core :manifest :id]))))

    (it "builds an index entry for a valid local module"
      (write-local-module! :isaac.comm.pigeon valid-comm-manifest)
      (let [{:keys [index errors]} (discover-local! [:isaac.comm.pigeon])]
        (should= [] errors)
        (should= :isaac.comm.pigeon (get-in index [:isaac.comm.pigeon :manifest :id]))
        (should= (mod-root :isaac.comm.pigeon) (get-in index [:isaac.comm.pigeon :path]))))

    (it "reads local/root manifests without adding deps during discovery"
      (write-local-module! :isaac.comm.pigeon valid-comm-manifest)
      (let [calls (atom [])]
        (with-redefs [isaac.module.loader/add-module-deps! (fn [id coord]
                                                             (swap! calls conj [id coord]))]
          (let [{:keys [index errors]} (discover-local! [:isaac.comm.pigeon])]
            (should= [] errors)
            (should-not-be-nil (get index :isaac.comm.pigeon))
            (should= [] @calls)))))

    (it "adds an error when a local/root path is not found"
      (let [{:keys [index errors]} (sut/discover! {:modules {:isaac.comm.ghost {:local/root "/state/.isaac/modules/isaac.comm.ghost"}}} ctx)]
        (should= nil (get index :isaac.comm.ghost))
        (should= "modules[\"isaac.comm.ghost\"]" (:key (first errors)))
        (should= "local/root path does not resolve" (:value (first errors)))))

    (it "adds errors when a manifest fails schema validation"
      (write-local-module! :isaac.comm.pigeon {:id :isaac.comm.pigeon})
      (let [{:keys [index errors]} (discover-local! [:isaac.comm.pigeon])]
        (should= nil (get index :isaac.comm.pigeon))
        (should (some #(= "module-index[\"isaac.comm.pigeon\"].version" (:key %)) errors))))

    (it "reports a cycle error in :requires"
      (write-local-module! :mod.a {:id :mod.a :version "1" :requires [:mod.b]})
      (write-local-module! :mod.b {:id :mod.b :version "1" :requires [:mod.a]})
      (let [{:keys [index errors]} (discover-local! [:mod.a :mod.b])]
        (should= #{:mod.a :mod.b :isaac.core} (set (keys index)))
        (should= [{:key "modules[\"mod.a\"]"
                   :value "requires cycle detected involving mod.a"}]
                 errors))))

  (describe "activate!"

    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example]
      (binding [fs/*fs* (fs/mem-fs)]
        (reset! @#'isaac.module.loader/loaded-module-coords* #{})
        (reset-comm-registry!)
        (sut/clear-activations!)
        (reset! c3env/-overrides {})
        (unload-telly!)
        (example)
        (reset! @#'isaac.module.loader/loaded-module-coords* #{})
        (reset! c3env/-overrides {})
        (sut/clear-activations!)
        (reset-comm-registry!)
        (unload-telly!)))

    (it "registers comm factories from manifest factories and logs activation once"
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly")
            module-index {:isaac.comm.telly {:dir telly-dir
                                             :manifest {:extends {:comm {:telly {:isaac/factory 'isaac.comm.telly/make}}}}}}]
        (log/capture-logs
          (sut/activate! :isaac.comm.telly module-index)
          (sut/activate! :isaac.comm.telly module-index)
          (let [events (filter #(= :module/activated (:event %)) @log/captured-logs)]
            (should= 1 (count events))
            (should= "isaac.comm.telly" (:module (first events)))))
        (should (comm-registry/registered? "telly"))))

    (it "wraps namespace load failures in structured error data and logs them"
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly")
            module-index {:isaac.comm.telly {:dir telly-dir
                                             :manifest {:extends {:comm {:telly {:isaac/factory 'isaac.comm.telly/make}}}}}}]
        (c3env/override! "ISAAC_TELLY_FAIL_ON_LOAD" "true")
        (log/capture-logs
          (let [error (try
                        (sut/activate! :isaac.comm.telly module-index)
                        (catch clojure.lang.ExceptionInfo e
                          e))
                event (first (filter #(= :module/activation-failed (:event %)) @log/captured-logs))]
            (should= :module/activation-failed (:type (ex-data error)))
            (should= :isaac.comm.telly (:module-id (ex-data error)))
            (should= nil (:bootstrap (ex-data error)))
            (should-not-be-nil event)
            (should= "isaac.comm.telly" (:module event))))))

    (it "adds local/root deps on first activation"
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly")
            module-index {:isaac.comm.telly {:coord {:local/root telly-dir}
                                             :path  telly-dir
                                             :manifest {:extends {:comm {:telly {:isaac/factory 'isaac.comm.telly/make}}}}}}
            calls       (atom [])]
        (with-redefs [isaac.module.loader/add-module-deps! (fn [id coord]
                                                             (swap! calls conj [id coord]))]
          (sut/activate! :isaac.comm.telly module-index)
          (should= [[:isaac.comm.telly {:local/root telly-dir}]] @calls))))

    (it "does not add the same local/root deps twice across activation resets"
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly-cache-test")
            module-index {:isaac.comm.telly {:coord {:local/root telly-dir}
                                             :path  telly-dir
                                             :manifest {:extends {:comm {:telly {:isaac/factory 'isaac.comm.telly/make}}}}}}
            calls       (atom [])]
        (with-redefs [isaac.module.loader/add-module-deps! (fn [id coord]
                                                             (swap! calls conj [id coord]))]
          (sut/activate! :isaac.comm.telly module-index)
          (sut/clear-activations!)
          (sut/activate! :isaac.comm.telly module-index)
          (should= [[:isaac.comm.telly {:local/root telly-dir}]] @calls))))))
