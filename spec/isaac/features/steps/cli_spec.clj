(ns isaac.features.steps.cli-spec
  (:require
    [gherclj.core :as g]
    [isaac.features.steps.acp :as acp]
    [isaac.features.steps.cli :as sut]
    [isaac.fs :as fs]
    [isaac.main :as main]
    [speclj.core :refer :all]))

(describe "cli feature steps"

  (around [it]
    (g/reset!)
    (binding [fs/*fs* (fs/mem-fs)]
      (it))
    (g/reset!))

  (it "sets ACP proxy EOF grace to zero for loopback proxy runs"
    (let [captured (atom nil)]
      (g/assoc! :server-config {:acp {:proxy-transport "loopback"}})
      (g/assoc! :state-dir "/target/test-state")
      (g/assoc! :acp-remote-connection-factory (fn [_ _] nil))
      (with-redefs [acp/ensure-loopback-proxy! (fn [] nil)
                    main/run                   (fn [_]
                                                 (reset! captured main/*extra-opts*)
                                                 0)]
        (sut/isaac-run "acp --remote ws://test/acp"))
      (should= 0 (:acp-proxy-eof-grace-ms @captured)))))
