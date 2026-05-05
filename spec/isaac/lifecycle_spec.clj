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

  (around [it]
    (binding [fs/*fs* (fs/mem-fs)
              comm-registry/*registry* (atom (comm-registry/fresh-registry))]
      (module-loader/clear-activations!)
      (c3env/override! "ISAAC_TELLY_FAIL_ON_LOAD" nil)
      (unload-telly!)
      (it)
      (c3env/override! "ISAAC_TELLY_FAIL_ON_LOAD" nil)
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
        (should (some #(= :module/activation-failed (:event %)) @log/captured-logs))))))
