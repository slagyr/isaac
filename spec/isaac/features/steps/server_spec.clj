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
      (should= (str real-home "/.isaac") (:state-dir @started))))

  (it "can skip binding a real port for reload-only scenarios"
    (let [started      (atom nil)
          virtual-home "/target/test-state"
          real-home    (str (System/getProperty "user.dir") virtual-home)
          cfg          {:server {:port 7788}}]
      (g/assoc! :mem-fs fs/*fs*)
      (g/assoc! :state-dir virtual-home)
      (g/assoc! :bind-server-port? false)
      (fs/mkdirs (str virtual-home "/.isaac/config"))
      (fs/spit (str virtual-home "/.isaac/config/isaac.edn") (pr-str cfg))
      (with-redefs [app/start! (fn [opts]
                                 (reset! started opts)
                                 {:port 0 :host "0.0.0.0"})
                     app/stop!  (fn [] nil)]
        (sut/server-running))
      (should= 0 (:port @started))
      (should= (str real-home "/.isaac") (:state-dir @started))))

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

  (it "deletes config keys with #delete"
    (g/assoc! :mem-fs fs/*fs*)
    (g/assoc! :state-dir "/target/test-state")
    (g/assoc! :server-config {:comms {:discord {:token "shh" :name "isaac"}}})
    (fs/mkdirs "/target/test-state/.isaac/config")
    (fs/spit "/target/test-state/.isaac/config/isaac.edn"
             (pr-str {:comms {:discord {:token "shh" :name "isaac"}}}))
    (sut/configure {:headers ["key" "value"]
                    :rows    [["comms.discord.token" "#delete"]]})
    (should= {:comms {:discord {:name "isaac"}}
              :key   "value"}
             (g/get :server-config))
    (should= {:comms {:discord {:name "isaac"}}
              :key   "value"}
             (read-string (fs/slurp "/target/test-state/.isaac/config/isaac.edn"))))

  (it "deletes isaac EDN file keys with #delete"
    (g/assoc! :mem-fs fs/*fs*)
    (g/assoc! :state-dir "/target/test-state")
    (fs/mkdirs "/target/test-state/.isaac/config/crew")
    (fs/spit "/target/test-state/.isaac/config/crew/marvin.edn"
             (pr-str {:model :grover
                      :soul  "Paranoid android."
                      :tools {:allow [:bash]}}))
    (sut/isaac-edn-file-exists "config/crew/marvin.edn"
                               {:headers ["path" "value"]
                                :rows    [["soul" "#delete"]
                                          ["model" "snuffy"]]})
    (should= {:model :snuffy
              :tools {:allow [:bash]}}
             (read-string (fs/slurp "/target/test-state/.isaac/config/crew/marvin.edn"))))

  (it "deletes EDN isaac file keys with #delete in write mode"
    (g/assoc! :mem-fs fs/*fs*)
    (g/assoc! :state-dir "/target/test-state")
    (fs/mkdirs "/target/test-state/.isaac/delivery/pending")
    (fs/spit "/target/test-state/.isaac/delivery/pending/7f3a.edn"
             (pr-str {"status" "pending"
                      "attempt" 1}))
    (sut/edn-isaac-file-contains "delivery/pending/7f3a.edn"
                                 {:headers ["path" "value"]
                                  :rows    [["status" "#delete"]
                                            ["attempt" "2"]]})
    (should= {"attempt" 2}
             (read-string (fs/slurp "/target/test-state/.isaac/delivery/pending/7f3a.edn"))))

)
