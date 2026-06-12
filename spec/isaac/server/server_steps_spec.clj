(ns isaac.server.server-steps-spec
  (:require
    [gherclj.core :as g]
    [isaac.fs :as fs]
    [isaac.marigold :as marigold]
    [isaac.server.app :as app]
    [isaac.nexus :as nexus]
    [isaac.server.server-steps :as sut]
    [speclj.core :refer :all]))

(def test-root "/target/test-state")

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
      (g/assoc! :root virtual-home)
      (fs/mkdirs (nexus/get :fs) (str virtual-home "/config"))
      (fs/spit (nexus/get :fs) (str virtual-home "/config/isaac.edn") (pr-str cfg))
      (with-redefs [app/start! (fn [opts]
                                 (reset! started opts)
                                 {:port 7788 :host "0.0.0.0"})
                     app/stop!  (fn [] nil)]
        (sut/server-running))
      (should= 7788 (get-in (:cfg @started) [:server :port]))
      (should= virtual-home (:root @started))))

  (it "can skip binding a real port for reload-only scenarios"
    (let [started      (atom nil)
          virtual-home "/target/test-state"
          cfg          {:server {:port 7788}}]
      (g/assoc! :mem-fs (nexus/get :fs))
      (g/assoc! :root virtual-home)
      (g/assoc! :bind-server-port? false)
      (fs/mkdirs (nexus/get :fs) (str virtual-home "/config"))
      (fs/spit (nexus/get :fs) (str virtual-home "/config/isaac.edn") (pr-str cfg))
      (with-redefs [app/start! (fn [opts]
                                 (reset! started opts)
                                 {:port 0 :host "0.0.0.0"})
                     app/stop!  (fn [] nil)]
        (sut/server-running))
      (should= 0 (:port @started))
      (should= virtual-home (:root @started))))

  (it "uses an isolated default home when no root or isaac-home is set"
    (let [started (atom nil)]
      (with-redefs [app/start! (fn [opts]
                                 (reset! started opts)
                                 {:port 7788 :host "0.0.0.0"})
                    app/stop!  (fn [] nil)]
        (sut/server-running))
      (should= (str (System/getProperty "user.dir") "/target/test-state/server-default-home")
               (:root @started))
      (should= (str (System/getProperty "user.dir") "/target/test-state/server-default-home")
               (g/get :root))))

  (it "deletes config keys with #delete"
    (g/assoc! :mem-fs (nexus/get :fs))
    (g/assoc! :root test-root)
    (g/assoc! :server-config {:comms {(keyword marigold/longwave) {:token "shh" :name marigold/captain}}})
    (fs/mkdirs (nexus/get :fs) (str test-root "/config"))
    (fs/spit (nexus/get :fs) (str test-root "/config/isaac.edn")
             (pr-str {:comms {(keyword marigold/longwave) {:token "shh" :name marigold/captain}}}))
    (sut/configure {:headers ["key" "value"]
                    :rows    [[(str "comms." marigold/longwave ".token") "#delete"]]})
    (should= {:comms {(keyword marigold/longwave) {:name marigold/captain}}
              :key   "value"}
             (g/get :server-config))
    (should= {:comms {(keyword marigold/longwave) {:name marigold/captain}}
              :key   "value"}
             (read-string (fs/slurp (nexus/get :fs) (str test-root "/config/isaac.edn")))))

)
