(ns isaac.cli.serve-spec
  (:require
    [isaac.cli.registry :as registry]
    [isaac.cli.serve :as sut]
    [isaac.logger :as log]
    [isaac.server.app :as app]
    [speclj.core :refer :all]))

(describe "Serve command"

  (describe "command registration"

    (it "registers the serve command"
      (should-not-be-nil (registry/get-command "serve")))

    (it "registers gateway as an alias via main resolve-alias"
      (require 'isaac.main)
      (let [resolve (ns-resolve 'isaac.main 'resolve-alias)]
        (should= ["serve" "--port" "3000"] (resolve ["gateway" "--port" "3000"])))))

  (describe "run"

    (it "starts the server on the given port"
      (let [started (atom nil)]
        (with-redefs [app/start! (fn [opts] (reset! started opts) {:port (:port opts)})
                      sut/block! (fn [] nil)]
          (with-out-str (sut/run {:port "4000"})))
        (should= 4000 (:port @started))))

    (it "defaults to port 3000"
      (let [started (atom nil)]
        (with-redefs [app/start! (fn [opts] (reset! started opts) {:port 3000})
                      sut/block! (fn [] nil)]
          (with-out-str (sut/run {})))
        (should= 3000 (:port @started))))

    (it "prints the host and port on startup"
      (with-redefs [app/start! (fn [_] {:port 5000})
                    sut/block! (fn [] nil)]
        (let [output (with-out-str (sut/run {:port "5000"}))]
          (should (re-find #"5000" output)))))

    (it "logs server/started with host and port"
      (let [logged (atom [])]
        (with-redefs [app/start!  (fn [_] {:port 7000})
                      sut/block!  (fn [] nil)
                      log/log*    (fn [level data _ _] (swap! logged conj {:level level :data data}))]
          (with-out-str (sut/run {:port "7000"})))
        (let [started (first (filter #(= :server/started (get-in % [:data :event])) @logged))]
          (should-not-be-nil started)
          (should= 7000 (get-in started [:data :port])))))

    )

  )
