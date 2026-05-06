(ns isaac.lifecycle-spec
  (:require
    [c3kit.apron.env :as c3env]
    [isaac.comm.registry :as comm-registry]
    [isaac.fs :as fs]
    [isaac.lifecycle :as sut]
    [isaac.logger :as log]
    [isaac.module.loader :as module-loader]
    [speclj.core :refer :all]))

(describe "lifecycle"

(defn- unload-telly! []
  (when-let [ns-obj (find-ns 'isaac.comm.telly)]
    (remove-ns (ns-name ns-obj))))

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (binding [fs/*fs* (fs/mem-fs)
              comm-registry/*registry* (atom (comm-registry/fresh-registry))]
      (module-loader/clear-activations!)
      (reset! c3env/-overrides {})
      (unload-telly!)
      (example)
      (reset! c3env/-overrides {})
      (module-loader/clear-activations!)
      (unload-telly!)))

  (it "activates a declared module when a comm impl is first needed"
    (let [tree*    (atom {})
          host     {:module-index {:isaac.comm.telly {:manifest {:entry 'isaac.comm.telly
                                                                 :extends {:comm {:telly {}}}}}}}
          registry @comm-registry/*registry*]
      (log/capture-logs
        (sut/reconcile! tree* host nil {:comms {:bert {:impl :telly}}} registry)
        (should-not-be-nil (get-in @tree* [:comms :bert]))
        (should (some #(= :module/activated (:event %)) @log/captured-logs))
        (should (some #(and (= :telly/started (:event %))
                            (= "bert" (:module %)))
                      @log/captured-logs)))))

  (it "logs activation failure and leaves the slot inert when the module load fails"
    (let [tree*    (atom {})
          host     {:module-index {:isaac.comm.telly {:manifest {:entry 'isaac.comm.telly
                                                                  :extends {:comm {:telly {}}}}}}}
          registry @comm-registry/*registry*]
      (c3env/override! "ISAAC_TELLY_FAIL_ON_LOAD" "true")
      (log/capture-logs
        (sut/reconcile! tree* host nil {:comms {:bert {:impl :telly}}} registry)
        (should= {} @tree*)
        (should (some #(= :module/activation-failed (:event %)) @log/captured-logs)))))

  (it "stops an existing slot when it is removed from config"
    (let [stopped  (atom nil)
          instance (reify sut/Lifecycle
                     (on-startup! [_ _] nil)
                     (on-config-change! [_ old new]
                       (reset! stopped [old new])))
          tree*    (atom {:comms {:bert instance}})
          registry {:path [:comms] :impls {}}
          old-cfg  {:comms {:bert {:impl :telly :token "abc"}}}]
      (sut/reconcile! tree* {} old-cfg {:comms {}} registry)
      (should= {:comms {}} @tree*)
      (should= [{:impl :telly :token "abc"} nil] @stopped)))

  (it "restarts a slot when its impl changes"
    (let [events   (atom [])
          old-inst (reify sut/Lifecycle
                     (on-startup! [_ _] nil)
                     (on-config-change! [_ old new]
                       (swap! events conj [:old old new])))
          new-inst (reify sut/Lifecycle
                     (on-startup! [_ slice]
                       (swap! events conj [:new slice]))
                     (on-config-change! [_ _ _] nil))
          tree*    (atom {:comms {:bert old-inst}})
          registry @comm-registry/*registry*
          old-cfg  {:comms {:bert {:impl :discord :token "abc"}}}
          new-cfg  {:comms {:bert {:impl :telly}}}]
      (comm-registry/register-factory! "telly" (fn [_] new-inst))
      (sut/reconcile! tree* {} old-cfg new-cfg registry)
      (should= new-inst (get-in @tree* [:comms :bert]))
      (should= [[:old {:impl :discord :token "abc"} nil]
                [:new {:impl :telly}]]
               @events)))

  (it "updates an existing slot in place when only the slice changes"
    (let [changes  (atom nil)
          instance (reify sut/Lifecycle
                     (on-startup! [_ _] nil)
                     (on-config-change! [_ old new]
                       (reset! changes [old new])))
          tree*    (atom {:comms {:bert instance}})
          registry {:path [:comms] :impls {}}
          old-cfg  {:comms {:bert {:impl :telly :token "abc"}}}
          new-cfg  {:comms {:bert {:impl :telly :token "xyz"}}}]
      (sut/reconcile! tree* {} old-cfg new-cfg registry)
      (should= instance (get-in @tree* [:comms :bert]))
      (should= [{:impl :telly :token "abc"}
                {:impl :telly :token "xyz"}]
               @changes)))

  (it "leaves a new slot inert when no factory can be resolved"
    (let [tree*    (atom {})
          registry {:path [:comms] :impls {}}
          new-cfg  {:comms {:bert {:impl :ghost}}}]
      (sut/reconcile! tree* {} nil new-cfg registry)
      (should= {} @tree*)))

  (it "starts a slot when impl changes and no existing instance is present"
    (let [events   (atom [])
          new-inst (reify sut/Lifecycle
                     (on-startup! [_ slice]
                       (swap! events conj [:new slice]))
                     (on-config-change! [_ _ _] nil))
          tree*    (atom {})
          registry @comm-registry/*registry*
          old-cfg  {:comms {:bert {:impl :discord :token "abc"}}}
          new-cfg  {:comms {:bert {:impl :telly}}}]
      (comm-registry/register-factory! "telly" (fn [_] new-inst))
      (sut/reconcile! tree* {} old-cfg new-cfg registry)
      (should= new-inst (get-in @tree* [:comms :bert]))
      (should= [[:new {:impl :telly}]] @events)))

  (it "does nothing for slice changes when no instance exists"
    (let [tree*    (atom {})
          registry {:path [:comms] :impls {}}
          old-cfg  {:comms {:bert {:impl :telly :token "abc"}}}
          new-cfg  {:comms {:bert {:impl :telly :token "xyz"}}}]
      (sut/reconcile! tree* {} old-cfg new-cfg registry)
      (should= {} @tree*))))
