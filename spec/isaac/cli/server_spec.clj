(ns isaac.cli.server-spec
  (:require
    [isaac.cli.registry :as registry]
    [isaac.cli.server :as sut]
    [isaac.config.resolution :as config]
    [isaac.logger :as log]
    [isaac.server.app :as app]
    [isaac.spec-helper :as helper]
    [speclj.core :refer :all]))

(describe "Server command"

  (helper/with-captured-logs)

  (describe "command registration"

    (it "registers the server command"
      (should-not-be-nil (registry/get-command "server")))

    (it "registers gateway as an alias for server via main resolve-alias"
      (require 'isaac.main)
      (let [resolve (ns-resolve 'isaac.main 'resolve-alias)]
        (should= ["server" "--port" "3000"] (resolve ["gateway" "--port" "3000"])))))

  (describe "run"

    (it "starts the server on the given port"
      (let [started (atom nil)]
        (with-redefs [app/start!         (fn [opts] (reset! started opts) {:port (:port opts) :host (:host opts)})
                      sut/block!         (fn [] nil)
                      config/load-config (fn [& _] {})]
          (with-out-str (sut/run {:port "4000"})))
        (should= 4000 (:port @started))))

    (it "loads port from config when no port flag given"
      (let [started (atom nil)]
        (with-redefs [app/start!         (fn [opts] (reset! started opts) {:port (:port opts) :host (:host opts)})
                      sut/block!         (fn [] nil)
                      config/load-config (fn [& _] {:gateway {:port 8888}})]
          (with-out-str (sut/run {})))
        (should= 8888 (:port @started))))

    (it "defaults to port 6674 when no port flag and no config"
      (let [started (atom nil)]
        (with-redefs [app/start!         (fn [opts] (reset! started opts) {:port 6674 :host (:host opts)})
                      sut/block!         (fn [] nil)
                      config/load-config (fn [& _] {})]
          (with-out-str (sut/run {})))
        (should= 6674 (:port @started))))

    (it "CLI --port overrides config port"
      (let [started (atom nil)]
        (with-redefs [app/start!         (fn [opts] (reset! started opts) {:port (:port opts) :host (:host opts)})
                      sut/block!         (fn [] nil)
                      config/load-config (fn [& _] {:gateway {:port 8888}})]
          (with-out-str (sut/run {:port "4000"})))
        (should= 4000 (:port @started))))

    (it "loads host from config when no host flag given"
      (let [started (atom nil)]
        (with-redefs [app/start!         (fn [opts] (reset! started opts) {:port (:port opts) :host (:host opts)})
                      sut/block!         (fn [] nil)
                      config/load-config (fn [& _] {:server {:host "127.0.0.1"}})]
          (with-out-str (sut/run {})))
        (should= "127.0.0.1" (:host @started))))

    (it "prints the host and port on startup"
      (with-redefs [app/start!         (fn [_] {:port 5000 :host "0.0.0.0"})
                    sut/block!         (fn [] nil)
                    config/load-config (fn [& _] {})]
        (let [output (with-out-str (sut/run {:port "5000"}))]
          (should (re-find #"5000" output)))))

    (it "logs server/started with host and port"
      (with-redefs [app/start!         (fn [_] {:port 7000 :host "0.0.0.0"})
                    sut/block!         (fn [] nil)
                    config/load-config (fn [& _] {})]
        (with-out-str (sut/run {:port "7000"})))
      (let [started (first (filter #(= :server/started (:event %)) @log/captured-logs))]
        (should-not-be-nil started)
        (should= 7000 (:port started))
        (should= "0.0.0.0" (:host started))))

    (it "enables dev mode from config"
      (let [started (atom nil)]
        (with-redefs [app/start!         (fn [opts] (reset! started opts) {:port 6674 :host "0.0.0.0"})
                      sut/block!         (fn [] nil)
                      config/load-config (fn [& _] {:dev true})]
          (with-out-str (sut/run {})))
        (should= true (:dev @started))))

    (it "CLI --dev overrides config dev false"
      (let [started (atom nil)]
        (with-redefs [app/start!         (fn [opts] (reset! started opts) {:port 6674 :host "0.0.0.0"})
                      sut/block!         (fn [] nil)
                      config/load-config (fn [& _] {:dev false})]
          (with-out-str (sut/run {:dev true})))
        (should= true (:dev @started))))
    )

  )
