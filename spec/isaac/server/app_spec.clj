(ns isaac.server.app-spec
  (:require
    [c3kit.apron.refresh :as refresh]
    [isaac.cron.scheduler :as scheduler]
    [isaac.delivery.worker :as worker]
    [isaac.logger :as log]
    [isaac.server.app :as sut]
    [isaac.spec-helper :as helper]
    [org.httpkit.server :as httpkit]
    [speclj.core :refer :all]))

(describe "Server app"

  (helper/with-captured-logs)

  (after (sut/stop!))

  (it "starts the server and returns a port"
    (let [result (sut/start! {:port 0})]
      (should (pos-int? (:port result)))))

  (it "returns the bound host in the result"
    (let [result (sut/start! {:port 0 :host "127.0.0.1"})]
      (should= "127.0.0.1" (:host result))))

  (it "defaults to 0.0.0.0 when no host given"
    (let [result (sut/start! {:port 0})]
      (should= "0.0.0.0" (:host result))))

  (it "reports running? as true after start"
    (sut/start! {:port 0})
    (should (sut/running?)))

  (it "reports running? as false before start"
    (should-not (sut/running?)))

  (it "stops the server"
    (sut/start! {:port 0})
    (sut/stop!)
    (should-not (sut/running?)))

  (it "initializes code refresh and wraps handler in dev mode"
    (let [init-args   (atom nil)
          wrapped-sym (atom nil)
          captured    (atom nil)]
      (with-redefs [refresh/init                        (fn [services ns-prefix excludes]
                                                          (reset! init-args [services ns-prefix excludes]))
                    refresh/refresh-handler             (fn [root-sym]
                                                          (reset! wrapped-sym root-sym)
                                                          (fn [_request] {:status 200}))
                    httpkit/run-server                  (fn [handler _opts]
                                                          (reset! captured handler)
                                                          (fn [] nil))
                    httpkit/server-port                 (fn [_] 7777)
                    httpkit/server-stop!                (fn [_] nil)]
        (sut/start! {:port 0 :dev true})
        (sut/stop!))
      (should= "isaac" (second @init-args))
      (should= [] (nth @init-args 2))
      (should= 'isaac.server.http/root-handler @wrapped-sym)
      (should-not-be-nil @captured)))

  (it "logs dev mode enabled when started in dev mode"
    (with-redefs [refresh/init            (fn [_ _ _] nil)
                  refresh/refresh-handler (fn [_] (fn [_request] {:status 200}))
                  httpkit/run-server      (fn [_ _] (fn [] nil))
                  httpkit/server-port     (fn [_] 7001)
                  httpkit/server-stop!    (fn [_] nil)]
      (sut/start! {:port 0 :host "127.0.0.1" :dev true})
      (sut/stop!))
    (let [entry (first (filter #(= :server/dev-mode-enabled (:event %)) @log/captured-logs))]
      (should-not-be-nil entry)
      (should= "127.0.0.1" (:host entry))
      (should= 7001 (:port entry))))

  (it "starts the cron scheduler when cron jobs are configured"
    (let [started (atom nil)]
      (with-redefs [httpkit/run-server   (fn [_ _] (fn [] nil))
                    httpkit/server-port  (fn [_] 7001)
                    httpkit/server-stop! (fn [_] nil)
                    scheduler/start!     (fn [opts]
                                           (reset! started opts)
                                           ::scheduler)
                    scheduler/stop!      (fn [_] nil)]
        (sut/start! {:port      0
                     :state-dir "/tmp/isaac"
                     :cfg       {:cron {"health-check" {:expr "0 9 * * *"}}}})
        (sut/stop!))
      (should= {:cfg       {:cron {"health-check" {:expr "0 9 * * *"}}}
                :state-dir "/tmp/isaac"}
               @started)))

  (it "stops the cron scheduler with the server"
    (let [stopped (atom nil)]
      (with-redefs [httpkit/run-server   (fn [_ _] (fn [] nil))
                    httpkit/server-port  (fn [_] 7001)
                    httpkit/server-stop! (fn [_] nil)
                    scheduler/start!     (fn [_] ::scheduler)
                    scheduler/stop!      (fn [scheduler]
                                           (reset! stopped scheduler))]
        (sut/start! {:port      0
                     :state-dir "/tmp/isaac"
                     :cfg       {:cron {"health-check" {:expr "0 9 * * *"}}}})
        (sut/stop!))
      (should= ::scheduler @stopped)))

  (it "starts the delivery worker when the server has a state dir"
    (let [started (atom nil)]
      (with-redefs [httpkit/run-server   (fn [_ _] (fn [] nil))
                    httpkit/server-port  (fn [_] 7001)
                    httpkit/server-stop! (fn [_] nil)
                    worker/start!        (fn [opts]
                                           (reset! started opts)
                                           ::worker)]
        (sut/start! {:port      0
                     :state-dir "/tmp/isaac"
                     :cfg       {}})
        (sut/stop!))
      (should= {:state-dir "/tmp/isaac"} @started)))

  (it "stops the delivery worker with the server"
    (let [stopped (atom nil)]
      (with-redefs [httpkit/run-server   (fn [_ _] (fn [] nil))
                    httpkit/server-port  (fn [_] 7001)
                    httpkit/server-stop! (fn [_] nil)
                    worker/start!        (fn [_] ::worker)
                    worker/stop!         (fn [worker]
                                           (reset! stopped worker))]
        (sut/start! {:port      0
                     :state-dir "/tmp/isaac"
                     :cfg       {}})
        (sut/stop!))
      (should= ::worker @stopped)))

  )
