(ns isaac.config.configurator-spec
  (:require
    [c3kit.apron.env :as c3env]
    [isaac.api]
    [isaac.config.schema.root :as schema]
    [isaac.comm.registry :as comm-registry]
    [isaac.fs :as fs]
    [isaac.config.configurator :as sut]
    [isaac.logger :as log]
    [isaac.marigold :as marigold]
    [isaac.module.loader :as module-loader]
    [isaac.server.app :as app]
    [isaac.nexus :as nexus]
    [speclj.core :refer :all]))

(describe "configurator"

(defn- unload-telly! []
  (when-let [ns-obj (find-ns 'isaac.comm.telly)]
    (remove-ns (ns-name ns-obj)))
  (let [loaded-libs (var-get #'clojure.core/*loaded-libs*)]
    (dosync (alter loaded-libs disj 'isaac.comm.telly))))

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (nexus/-with-nested-nexus {:fs (fs/mem-fs)}
      (binding [comm-registry/*registry* (atom (comm-registry/fresh-registry))]
        (module-loader/clear-activations!)
        (reset! c3env/-overrides {})
        (unload-telly!)
        (example)
        (reset! c3env/-overrides {})
        (module-loader/clear-activations!)
        (unload-telly!))))

  (it "activates a declared module when a comm impl is first needed"
    (let [host     {:module-index {:isaac.comm.telly {:manifest {:isaac.server/comm {:telly {:factory 'isaac.comm.telly/make}}}}}}
          registry @comm-registry/*registry*]
      (log/capture-logs
        (sut/reconcile! host nil {:comms {:bert {:type :telly}}} registry)
        (should-not-be-nil (nexus/get-in [:comms :bert]))
        (should (some #(= :module/activated (:event %)) @log/captured-logs))
        (should (some #(and (= :telly/started (:event %))
                            (= "bert" (:module %)))
                      @log/captured-logs)))))

  (it "logs activation failure and leaves the slot inert when the module load fails"
    (let [host     {:module-index {:isaac.comm.telly {:manifest {:isaac.server/comm {:telly {:factory 'isaac.comm.telly/make}}}}}}
          registry @comm-registry/*registry*]
      (c3env/override! "ISAAC_TELLY_FAIL_ON_LOAD" "true")
      (log/capture-logs
        (sut/reconcile! host nil {:comms {:bert {:type :telly}}} registry)
        (should-be-nil (nexus/get-in [:comms :bert]))
        (should (some #(= :module/activation-failed (:event %)) @log/captured-logs)))))

  (it "stops an existing slot when it is removed from config"
    (let [stopped  (atom nil)
          instance (reify sut/Reconfigurable
                     (on-startup! [_ _] nil)
                     (on-config-change! [_ old new]
                       (reset! stopped [old new])))
          registry {:path [:comms] :impls {}}
          old-cfg  {:comms {:bert {:type :telly :token "abc"}}}]
      (nexus/register! [:comms :bert] instance)
      (sut/reconcile! {} old-cfg {:comms {}} registry)
      (should-be-nil (nexus/get-in [:comms :bert]))
      (should= [{:type :telly :token "abc"} nil] @stopped)))

  (it "restarts a slot when its impl changes"
    (let [events   (atom [])
          old-inst (reify sut/Reconfigurable
                     (on-startup! [_ _] nil)
                     (on-config-change! [_ old new]
                       (swap! events conj [:old old new])))
           new-inst (reify sut/Reconfigurable
                      (on-startup! [_ slice]
                        (swap! events conj [:new slice]))
                      (on-config-change! [_ _ _] nil))
           registry @comm-registry/*registry*
           old-cfg  {:comms {:bert {:type (keyword marigold/longwave) :token "abc"}}}
           new-cfg  {:comms {:bert {:type :telly}}}]
      (comm-registry/register-factory! "telly" (fn [_] new-inst))
      (nexus/register! [:comms :bert] old-inst)
      (sut/reconcile! {} old-cfg new-cfg registry)
      (should= new-inst (nexus/get-in [:comms :bert]))
      (should= [[:old {:type (keyword marigold/longwave) :token "abc"} nil]
                [:new {:type :telly}]]
               @events)))

  (it "updates an existing slot in place when only the slice changes"
    (let [changes  (atom nil)
           instance (reify sut/Reconfigurable
                      (on-startup! [_ _] nil)
                      (on-config-change! [_ old new]
                        (reset! changes [old new])))
           registry {:path [:comms] :impls {}}
           old-cfg  {:comms {:bert {:type :telly :token marigold/helm-api}}}
           new-cfg  {:comms {:bert {:type :telly :token "xyz"}}}]
      (nexus/register! [:comms :bert] instance)
      (sut/reconcile! {} old-cfg new-cfg registry)
      (should= instance (nexus/get-in [:comms :bert]))
      (should= [{:type :telly :token marigold/helm-api}
                {:type :telly :token "xyz"}]
               @changes)))

  (it "leaves a new slot inert when no factory can be resolved"
    (let [registry {:path [:comms] :impls {}}
          new-cfg  {:comms {:bert {:type :ghost}}}]
      (sut/reconcile! {} nil new-cfg registry)
      (should-be-nil (nexus/get-in [:comms :bert]))))

  (it "starts a slot when impl changes and no existing instance is present"
    (let [events   (atom [])
           new-inst (reify sut/Reconfigurable
                      (on-startup! [_ slice]
                        (swap! events conj [:new slice]))
                      (on-config-change! [_ _ _] nil))
           registry @comm-registry/*registry*
           old-cfg  {:comms {:bert {:type (keyword marigold/longwave) :token "abc"}}}
           new-cfg  {:comms {:bert {:type :telly}}}]
      (comm-registry/register-factory! "telly" (fn [_] new-inst))
      (sut/reconcile! {} old-cfg new-cfg registry)
      (should= new-inst (nexus/get-in [:comms :bert]))
      (should= [[:new {:type :telly}]] @events)))

  (it "does not log comm activation for non-comm components"
    (let [instance (reify sut/Reconfigurable
                     (on-startup! [_ _] nil)
                     (on-config-change! [_ _ _] nil))
          registry {:kind    :component
                    :path    [:cron]
                    :factory (fn [_] instance)}]
      (log/capture-logs
        (sut/reconcile! {} nil {:cron {:health-check {:expr "0 0 * * *"}}} registry)
        (should (some #(= :lifecycle/started (:event %)) @log/captured-logs))
        (should-not (some #(= :comm/activated (:event %)) @log/captured-logs)))))

  (it "does nothing for slice changes when no instance exists"
    (let [registry {:path [:comms] :impls {}}
          old-cfg  {:comms {:bert {:type :telly :token marigold/helm-api}}}
          new-cfg  {:comms {:bert {:type :telly :token "xyz"}}}]
      (sut/reconcile! {} old-cfg new-cfg registry)
      (should-be-nil (nexus/get-in [:comms :bert]))))

  (describe "schema ownership"

    (defn- owned-paths []
      (->> (app/registries)
           (map :path)
           set))

    (defn- entity-collection-entry? [[_ entry]]
      (and (= :map (:type entry))
           (:key-spec entry)
           (:value-spec entry)
           (let [value-spec (:value-spec entry)]
             (and (= :map (:type value-spec))
                  (or (:name value-spec)
                      (seq (:schema value-spec)))))))

    (it "every config-driven entity collection has a lifecycle owner or is marked snapshot-only"
      (let [owned-paths (owned-paths)]
        (doseq [[key entry] (filter entity-collection-entry? (:schema schema/root))]
          (when-not (or (contains? owned-paths [key])
                        (:snapshot-only? entry))
            (throw (ex-info (str "key `" key "` has no owner — register a Reconfigurable for `[:" (name key) "]` or add `:snapshot-only? true` to its schema entry")
                            {:key key})))))))

    (it "marks crew models and providers as snapshot-only"
      (should= true (get-in schema/root [:schema :crew :snapshot-only?]))
      (should= true (get-in schema/root [:schema :models :snapshot-only?]))
      (should= true (get-in schema/root [:schema :providers :snapshot-only?]))))
