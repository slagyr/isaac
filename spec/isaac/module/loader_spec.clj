(ns isaac.module.loader-spec
  (:require
    [c3kit.apron.env :as c3env]
    [isaac.comm.acp.websocket]
    [isaac.comm.registry :as comm-registry]
    [isaac.fs :as fs]
    [isaac.hooks]
    [isaac.logger :as log]
    [isaac.module.manifest]
    [isaac.module.loader :as sut]
    [isaac.server.routes]
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

(defn- local-manifest-path [id]
  (let [root           (mod-root id)
        resources-path (str root "/resources/isaac-manifest.edn")
        src-path       (str root "/src/isaac-manifest.edn")]
    (cond
      (fs/exists? resources-path) resources-path
      (fs/exists? src-path) src-path
      :else nil)))

(defn- discover-local! [ids]
  (with-redefs [isaac.module.loader/add-module-deps! (fn [_ _])
                isaac.module.loader/manifest-resource local-manifest-path]
    (sut/discover! {:modules (into {} (map (fn [id] [id (mod-coord id)]) ids))} ctx)))

(defn- unload-telly! []
  (when-let [ns-obj (find-ns 'isaac.comm.telly)]
    (remove-ns (ns-name ns-obj))))

(defn- reset-comm-registry! []
  (reset! comm-registry/*registry* (comm-registry/fresh-registry)))

(def valid-comm-manifest
  {:id      :isaac.comm.pigeon
   :version "0.1.0"
   :comm    {:pigeon {:factory 'isaac.comm.pigeon/make}}})

(describe "module loader"

  (describe "core-index"

    (before
      (sut/clear-caches!))

    (after
      (sut/clear-caches!))

    (it "reads the core manifest only once"
      (let [resource-calls (atom 0)
            read-calls     (atom 0)]
        (with-redefs-fn {#'isaac.module.loader/manifest-resource (fn [_]
                                                                   (swap! resource-calls inc)
                                                                   :core-resource)
                         #'isaac.module.manifest/read-manifest    (fn [_]
                                                                   (swap! read-calls inc)
                                                                   {:id :isaac.core :version "1.0.0"})}
          #(do
             (should= {:isaac.core {:coord {}
                                    :manifest {:id :isaac.core :version "1.0.0"}
                                    :path nil}}
                      (sut/core-index))
             (should= (sut/core-index) (sut/core-index))))
        (should= 1 @resource-calls)
        (should= 1 @read-calls))))

  (describe "discover!"

    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example]
      (binding [fs/*fs* (fs/mem-fs)]
        (reset! @#'isaac.module.loader/loaded-module-coords* #{})
        (example)
        (reset! @#'isaac.module.loader/loaded-module-coords* #{})))

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

    (it "discovers local/root manifests from src via classpath loading"
      (let [cwd         (System/getProperty "user.dir")
            module-root "modules/isaac.comm.srcnest"
            result      (sut/discover! {:modules {:isaac.comm.srcnest {:local/root module-root}}}
                                      (assoc ctx :cwd cwd))]
        (should= [] (:errors result))
        (should= :isaac.comm.srcnest (get-in result [:index :isaac.comm.srcnest :manifest :id]))
        (should= module-root (get-in result [:index :isaac.comm.srcnest :path]))))

    (it "adds an error when a local/root path is not found"
      (let [{:keys [index errors]} (sut/discover! {:modules {:isaac.comm.ghost {:local/root "/state/.isaac/modules/isaac.comm.ghost"}}} ctx)]
        (should= nil (get index :isaac.comm.ghost))
        (should= "modules[\"isaac.comm.ghost\"]" (:key (first errors)))
        (should= "local/root path does not resolve" (:value (first errors)))))

    (it "adds an error when a local/root path has no matching manifest on its classpath"
      (mod-dir! (mod-root :isaac.comm.ghost))
      (mod-deps! (str (mod-root :isaac.comm.ghost) "/deps.edn"))
      (let [{:keys [index errors]} (discover-local! [:isaac.comm.ghost])]
        (should= nil (get index :isaac.comm.ghost))
        (should= "modules[\"isaac.comm.ghost\"]" (:key (first errors)))
        (should= "manifest: could not read" (:value (first errors)))))

    (it "reads a local/root manifest directly when no deps.edn is present"
      (let [root (mod-root :isaac.comm.broken)]
        (mod-dir! root)
        (mod-manifest! (str root "/resources/isaac-manifest.edn") (pr-str {:id :isaac.comm.broken :version "0.1.0"}))
        (let [calls (atom [])]
          (with-redefs [isaac.module.loader/add-module-deps! (fn [id coord]
                                                               (swap! calls conj [id coord]))]
            (let [{:keys [index errors]} (sut/discover! {:modules {:isaac.comm.broken {:local/root root}}} ctx)]
              (should= [] errors)
              (should= :isaac.comm.broken (get-in index [:isaac.comm.broken :manifest :id]))
              (should= [] @calls))))))

    (it "adds module deps only once per coordinate across repeated discovery"
      (write-local-module! :isaac.comm.pigeon valid-comm-manifest)
      (let [calls            (atom [])
            classpath-ready? (atom false)]
        (with-redefs [isaac.module.loader/manifest-resource (fn [id]
                                                              (when (and @classpath-ready?
                                                                         (= id :isaac.comm.pigeon))
                                                                (str (mod-root :isaac.comm.pigeon) "/resources/isaac-manifest.edn")))
                      isaac.module.loader/add-module-deps!   (fn [id coord]
                                                              (swap! calls conj [id coord])
                                                              (reset! classpath-ready? true))]
          (let [first-result  (sut/discover! {:modules {:isaac.comm.pigeon (mod-coord :isaac.comm.pigeon)}} ctx)
                second-result (sut/discover! {:modules {:isaac.comm.pigeon (mod-coord :isaac.comm.pigeon)}} ctx)]
            (should= [] (:errors first-result))
            (should= [] (:errors second-result))
            (should= [[:isaac.comm.pigeon (mod-coord :isaac.comm.pigeon)]] @calls)))))

    (it "adds errors when a manifest fails schema validation"
      (write-local-module! :isaac.comm.pigeon {:id :isaac.comm.pigeon})
      (let [{:keys [index errors]} (discover-local! [:isaac.comm.pigeon])]
        (should= nil (get index :isaac.comm.pigeon))
        (should (some #(= "module-index[\"isaac.comm.pigeon\"].version" (:key %)) errors))))

    (it "builds an index entry for two independent modules"
      (write-local-module! :mod.a {:id :mod.a :version "1"})
      (write-local-module! :mod.b {:id :mod.b :version "1"})
      (let [{:keys [index errors]} (discover-local! [:mod.a :mod.b])]
        (should= [] errors)
        (should= #{:mod.a :mod.b :isaac.core} (set (keys index))))))

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
                                             :manifest {:comm {:telly {:factory 'isaac.comm.telly/make}}}}}]
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
                                             :manifest {:comm {:telly {:factory 'isaac.comm.telly/make}}}}}]
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
                                             :manifest {:comm {:telly {:factory 'isaac.comm.telly/make}}}}}
            calls       (atom [])]
        (with-redefs [isaac.module.loader/add-module-deps! (fn [id coord]
                                                             (swap! calls conj [id coord]))]
          (sut/activate! :isaac.comm.telly module-index)
            (should= [[:isaac.comm.telly {:local/root telly-dir}]] @calls))))

    (it "registers exact and prefix routes declared in the manifest"
      (let [module-index {:isaac.routes.bibelot {:manifest {:route {[:get "/acp"]      'isaac.comm.acp.websocket/handler
                                                                   [:post "/hooks/*"] 'isaac.hooks/handler}}}}
            calls       (atom [])]
        (with-redefs [isaac.server.routes/register-route!        (fn [method path handler]
                                                                   (swap! calls conj [:exact method path handler]))
                      isaac.server.routes/register-prefix-route! (fn [path handler]
                                                                   (swap! calls conj [:prefix path handler]))]
          (sut/activate! :isaac.routes.bibelot module-index)
          (should= [[:exact :get "/acp" #'isaac.comm.acp.websocket/handler]
                    [:prefix "/hooks/" #'isaac.hooks/handler]]
                   @calls))))

    (it "fails activation when a declarative route handler cannot be resolved"
      (let [module-index {:isaac.routes.bibelot {:manifest {:route {[:get "/bogus"] 'isaac.missing/handler}}}}]
        (should-throw clojure.lang.ExceptionInfo
                      (sut/activate! :isaac.routes.bibelot module-index))))

    (it "does not add the same local/root deps twice across activation resets"
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly-cache-test")
            module-index {:isaac.comm.telly {:coord {:local/root telly-dir}
                                             :path  telly-dir
                                             :manifest {:comm {:telly {:factory 'isaac.comm.telly/make}}}}}
            calls       (atom [])]
        (with-redefs [isaac.module.loader/add-module-deps! (fn [id coord]
                                                             (swap! calls conj [id coord]))]
          (sut/activate! :isaac.comm.telly module-index)
          (sut/clear-activations!)
          (sut/activate! :isaac.comm.telly module-index)
          (should= [[:isaac.comm.telly {:local/root telly-dir}]] @calls)))))

  (describe "comm-kinds"

    (it "returns empty when module index has no comm entries"
      (should= [] (sut/comm-kinds {})))

    (it "returns sorted comm kind name from a module"
      (let [index {:my.mod {:manifest {:comm {:telly {:factory 'foo/make}}}}}]
        (should= ["telly"] (sut/comm-kinds index))))

    (it "filters out entries with :configurable? false"
      (let [index {:my.mod {:manifest {:comm {:internal {:factory 'foo/make :configurable? false}
                                              :external {:factory 'bar/make}}}}}]
        (should= ["external"] (sut/comm-kinds index))))

    (it "aggregates and sorts kinds from multiple modules"
      (let [index {:mod-a {:manifest {:comm {:bravo {:factory 'a/make}}}}
                   :mod-b {:manifest {:comm {:alpha {:factory 'b/make}}}}}]
        (should= ["alpha" "bravo"] (sut/comm-kinds index))))

    (it "with no args falls back to core-index"
      (let [index {:isaac.core {:coord {} :manifest {:id :isaac.core :version "1"
                                                      :comm {:widget {:factory 'foo/make}}}}}]
        (binding [sut/*core-index-override* index]
          (should= ["widget"] (sut/comm-kinds)))))))
