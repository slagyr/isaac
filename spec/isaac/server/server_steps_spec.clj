(ns isaac.server.server-steps-spec
  (:require
    [gherclj.core :as g]
    [isaac.fs :as fs]
    [isaac.marigold :as marigold]
    [isaac.server.app :as app]
    [isaac.nexus :as nexus]
    [isaac.server.server-steps :as sut]
    [speclj.core :refer :all]))

(def test-state-dir "/target/test-state")
(def test-crew-id marigold/first-mate)
(def test-model-id (keyword marigold/helm-mark-iii))
(def updated-model-id (keyword marigold/helm-spark))

(describe "server feature steps"

  #_{:clj-kondo/ignore [:invalid-arity]}
  (around [it]
    (g/reset!)
    (nexus/-with-nested-nexus {:fs (fs/mem-fs)}
      (it))
    (g/reset!))

  (it "loads config from the in-memory fs at the virtual home"
    (let [started      (atom nil)
          virtual-home "/target/test-state"
          cfg          {:server {:port 7788}}]
      (g/assoc! :mem-fs (nexus/get :fs))
      (g/assoc! :state-dir virtual-home)
      (fs/mkdirs (nexus/get :fs) (str virtual-home "/.isaac/config"))
      (fs/spit (nexus/get :fs) (str virtual-home "/.isaac/config/isaac.edn") (pr-str cfg))
      (with-redefs [app/start! (fn [opts]
                                 (reset! started opts)
                                 {:port 7788 :host "0.0.0.0"})
                     app/stop!  (fn [] nil)]
        (sut/server-running))
      (should= 7788 (get-in (:cfg @started) [:server :port]))
      (should= (str virtual-home "/.isaac") (:state-dir @started))))

  (it "can skip binding a real port for reload-only scenarios"
    (let [started      (atom nil)
          virtual-home "/target/test-state"
          cfg          {:server {:port 7788}}]
      (g/assoc! :mem-fs (nexus/get :fs))
      (g/assoc! :state-dir virtual-home)
      (g/assoc! :bind-server-port? false)
      (fs/mkdirs (nexus/get :fs) (str virtual-home "/.isaac/config"))
      (fs/spit (nexus/get :fs) (str virtual-home "/.isaac/config/isaac.edn") (pr-str cfg))
      (with-redefs [app/start! (fn [opts]
                                 (reset! started opts)
                                 {:port 0 :host "0.0.0.0"})
                     app/stop!  (fn [] nil)]
        (sut/server-running))
      (should= 0 (:port @started))
      (should= (str virtual-home "/.isaac") (:state-dir @started))))

  (it "uses an isolated default home when no state-dir or isaac-home is set"
    (let [started (atom nil)]
      (with-redefs [app/start! (fn [opts]
                                 (reset! started opts)
                                 {:port 7788 :host "0.0.0.0"})
                    app/stop!  (fn [] nil)]
        (sut/server-running))
      (should= (str (System/getProperty "user.dir") "/target/test-state/server-default-home/.isaac")
               (:state-dir @started))
      (should= (str (System/getProperty "user.dir") "/target/test-state/server-default-home")
               (g/get :state-dir))))

  (it "writes isaac EDN files relative to state-dir"
    (g/assoc! :mem-fs (nexus/get :fs))
    (g/assoc! :state-dir test-state-dir)
    (sut/isaac-edn-file-exists (str "config/crew/" test-crew-id ".edn")
                               {:headers ["path" "value"]
                                :rows    [["model" marigold/helm-mark-iii]
                                          ["soul" "You are Cordelia."]]})
    (should= (marigold/crew-cfg test-crew-id :model test-model-id)
             (read-string (fs/slurp (nexus/get :fs) (str test-state-dir "/.isaac/config/crew/" test-crew-id ".edn")))))

  (it "writes bare isaac.edn under the config directory"
    (g/assoc! :mem-fs (nexus/get :fs))
    (g/assoc! :state-dir test-state-dir)
    (sut/isaac-file-exists-with-content "isaac.edn" "{:crew {}}")
    (should= "{:crew {}}"
             (fs/slurp (nexus/get :fs) (str test-state-dir "/.isaac/config/isaac.edn"))))

  (it "deletes config keys with #delete"
    (g/assoc! :mem-fs (nexus/get :fs))
    (g/assoc! :state-dir test-state-dir)
    (g/assoc! :server-config {:comms {(keyword marigold/longwave) {:token "shh" :name marigold/captain}}})
    (fs/mkdirs (nexus/get :fs) (str test-state-dir "/.isaac/config"))
    (fs/spit (nexus/get :fs) (str test-state-dir "/.isaac/config/isaac.edn")
             (pr-str {:comms {(keyword marigold/longwave) {:token "shh" :name marigold/captain}}}))
    (sut/configure {:headers ["key" "value"]
                    :rows    [[(str "comms." marigold/longwave ".token") "#delete"]]})
    (should= {:comms {(keyword marigold/longwave) {:name marigold/captain}}
              :key   "value"}
             (g/get :server-config))
    (should= {:comms {(keyword marigold/longwave) {:name marigold/captain}}
              :key   "value"}
             (read-string (fs/slurp (nexus/get :fs) (str test-state-dir "/.isaac/config/isaac.edn")))))

  (it "deletes isaac EDN file keys with #delete"
    (g/assoc! :mem-fs (nexus/get :fs))
    (g/assoc! :state-dir test-state-dir)
    (fs/mkdirs (nexus/get :fs) (str test-state-dir "/.isaac/config/crew"))
    (fs/spit (nexus/get :fs) (str test-state-dir "/.isaac/config/crew/" test-crew-id ".edn")
             (pr-str (assoc (marigold/crew-cfg test-crew-id :model test-model-id)
                            :tools {:allow [(keyword marigold/spyglass-tool)]})))
    (sut/isaac-edn-file-exists (str "config/crew/" test-crew-id ".edn")
                               {:headers ["path" "value"]
                                :rows    [["soul" "#delete"]
                                          ["model" marigold/helm-spark]]})
    (should= {:model updated-model-id
              :tools {:allow [(keyword marigold/spyglass-tool)]}}
             (read-string (fs/slurp (nexus/get :fs) (str test-state-dir "/.isaac/config/crew/" test-crew-id ".edn")))))

  (it "deletes EDN isaac file keys with #delete in write mode"
    (g/assoc! :mem-fs (nexus/get :fs))
    (g/assoc! :state-dir test-state-dir)
    (fs/mkdirs (nexus/get :fs) (str test-state-dir "/.isaac/delivery/pending"))
    (fs/spit (nexus/get :fs) (str test-state-dir "/.isaac/delivery/pending/7f3a.edn")
             (pr-str {"status" "pending"
                      "attempt" 1}))
    (sut/edn-isaac-file-contains "delivery/pending/7f3a.edn"
                                 {:headers ["path" "value"]
                                  :rows    [["status" "#delete"]
                                            ["attempt" "2"]]})
    (should= {"attempt" 2}
             (read-string (fs/slurp (nexus/get :fs) (str test-state-dir "/.isaac/delivery/pending/7f3a.edn")))))

)
