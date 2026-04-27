(ns isaac.features.steps.server-spec
  (:require
    [gherclj.core :as g]
    [isaac.features.steps.server :as sut]
    [isaac.fs :as fs]
    [isaac.server.app :as app]
    [speclj.core :refer :all]))

(describe "server feature steps"

  (around [it]
    (g/reset!)
    (binding [fs/*fs* (fs/mem-fs)]
      (it))
    (g/reset!))

  (it "loads config from disk when no in-memory injections are present"
    (let [started      (atom nil)
          virtual-home "/target/test-state"
          real-home    (str (System/getProperty "user.dir") virtual-home)
          cfg          {:server {:port 7788}}]
      (g/assoc! :mem-fs fs/*fs*)
      (g/assoc! :state-dir virtual-home)
      (fs/mkdirs (str virtual-home "/.isaac/config"))
      (fs/spit (str virtual-home "/.isaac/config/isaac.edn") (pr-str cfg))
      (with-redefs [app/start! (fn [opts]
                                 (reset! started opts)
                                 {:port 7788 :host "0.0.0.0"})
                    app/stop!  (fn [] nil)]
        (sut/server-running))
      (should= 7788 (get-in (:cfg @started) [:server :port]))
      (should= real-home (:state-dir @started))))

  (it "writes isaac EDN files relative to state-dir"
    (g/assoc! :mem-fs fs/*fs*)
    (g/assoc! :state-dir "/target/test-state")
    (sut/isaac-edn-file-exists "config/crew/marvin.edn"
                               {:headers ["path" "value"]
                                :rows    [["model" "grover"]
                                          ["soul" "Paranoid android."]]})
    (should= {:model :grover
              :soul  "Paranoid android."}
             (read-string (fs/slurp "/target/test-state/.isaac/config/crew/marvin.edn"))))

  (it "writes non-config isaac EDN files under the state dir"
    (g/assoc! :mem-fs fs/*fs*)
    (g/assoc! :state-dir "/target/test-state")
    (sut/edn-isaac-file-contains "comm/discord/routing.edn"
                                 {:headers ["path" "value"]
                                  :rows    [["C999.123" "primary"]]})
    (should= {"C999" {"123" "primary"}}
             (read-string (fs/slurp "/target/test-state/comm/discord/routing.edn")))))
