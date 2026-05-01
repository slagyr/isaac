(ns isaac.server.app-spec
  (:require
     [c3kit.apron.refresh :as refresh]
     [isaac.comm.discord :as discord]
     [isaac.comm.discord.gateway :as discord-gateway]
     [isaac.config.change-source :as change-source]
     [isaac.plugin :as plugin]
     [isaac.fs :as fs]
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

  (it "builds plugins with the configured state dir"
    (let [captured (atom nil)]
      (with-redefs [httpkit/run-server   (fn [_ _] (fn [] nil))
                    httpkit/server-port  (fn [_] 7001)
                    httpkit/server-stop! (fn [_] nil)
                    plugin/build-all     (fn [ctx]
                                           (reset! captured ctx)
                                           [])]
        (sut/start! {:port      0
                     :home      "/tmp/service-home"
                     :state-dir "/tmp/service-home/.isaac"
                     :cfg       {}})
        (sut/stop!))
      (should= {:state-dir "/tmp/service-home/.isaac"
                :connect-ws! nil}
               @captured)))

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

  (it "creates and starts a config change source from the state dir's home"
    (let [created (atom nil)
          started (atom nil)]
      (with-redefs [change-source/watch-service-source (fn [home]
                                                         (reset! created home)
                                                         ::source)
                    change-source/start!               (fn [source]
                                                         (reset! started source)
                                                         source)
                    change-source/stop!                (fn [_] nil)
                    httpkit/run-server                 (fn [_ _] (fn [] nil))
                    httpkit/server-port                (fn [_] 7001)
                    httpkit/server-stop!               (fn [_] nil)]
        (sut/start! {:port 0 :state-dir "/tmp/isaac-home/.isaac"})
        (sut/stop!))
      (should= "/tmp/isaac-home" @created)
      (should= ::source @started)))

  (it "does not create a config change source when hot reload is disabled"
    (let [created (atom nil)
          started (atom nil)]
      (with-redefs [change-source/watch-service-source (fn [home]
                                                         (reset! created home)
                                                         ::source)
                    change-source/start!               (fn [source]
                                                         (reset! started source)
                                                         source)
                    change-source/stop!                (fn [_] nil)
                    httpkit/run-server                 (fn [_ _] (fn [] nil))
                    httpkit/server-port                (fn [_] 7001)
                    httpkit/server-stop!               (fn [_] nil)]
        (sut/start! {:port 0 :state-dir "/tmp/isaac" :cfg {:server {:hot-reload false}}})
        (sut/stop!))
      (should= nil @created)
      (should= nil @started)))

  (it "stops the config change source with the server"
    (let [stopped (atom nil)]
      (with-redefs [change-source/start!     identity
                    change-source/stop!      (fn [source]
                                               (reset! stopped source))
                    httpkit/run-server       (fn [_ _] (fn [] nil))
                    httpkit/server-port      (fn [_] 7001)
                    httpkit/server-stop!     (fn [_] nil)]
        (sut/start! {:port 0 :config-change-source ::source})
        (sut/stop!))
      (should= ::source @stopped)))

  (it "reloads the in-memory config when the config source publishes a change"
    (let [source (change-source/memory-source "/tmp/isaac-reload")]
      (binding [fs/*fs* (fs/mem-fs)]
        (fs/mkdirs "/tmp/isaac-reload/.isaac/config/crew")
        (fs/mkdirs "/tmp/isaac-reload/.isaac/config/models")
        (fs/mkdirs "/tmp/isaac-reload/.isaac/config/providers")
        (fs/spit "/tmp/isaac-reload/.isaac/config/crew/marvin.edn" "{:model :grover :soul \"old\"}")
        (fs/spit "/tmp/isaac-reload/.isaac/config/models/grover.edn" "{:model \"echo\" :provider \"anthropic\" :context-window 32768}")
        (fs/spit "/tmp/isaac-reload/.isaac/config/providers/anthropic.edn" "{:api \"anthropic\"}")
        (with-redefs [httpkit/run-server   (fn [_ _] (fn [] nil))
                      httpkit/server-port  (fn [_] 7001)
                      httpkit/server-stop! (fn [_] nil)]
          (sut/start! {:cfg                  {:crew {"marvin" {:model "grover" :soul "old"}}
                                              :models {"grover" {:model "echo" :provider "anthropic" :context-window 32768}}
                                              :providers {"anthropic" {:api "anthropic"}}}
                       :config-change-source source
                       :state-dir            "/tmp/isaac-reload/.isaac"
                       :port                 0})
          (fs/spit "/tmp/isaac-reload/.isaac/config/crew/marvin.edn" "{:model :grover :soul \"new\"}")
          (change-source/notify-path! source "/tmp/isaac-reload/.isaac/config/crew/marvin.edn")
          (helper/await-condition #(= "new" (get-in (sut/current-config) [:crew "marvin" :soul])))
          (should= "new" (get-in (sut/current-config) [:crew "marvin" :soul]))
          (sut/stop!)))))

  (it "preserves the previous config when reload fails validation"
    (let [source     (change-source/memory-source "/tmp/isaac-reload")
          orig-poll  change-source/poll!
          poll-count (atom 0)
          poll-ready (promise)]
      (binding [fs/*fs* (fs/mem-fs)]
        (fs/mkdirs "/tmp/isaac-reload/.isaac/config/models")
        (fs/mkdirs "/tmp/isaac-reload/.isaac/config/providers")
        (fs/spit "/tmp/isaac-reload/.isaac/config/models/grover.edn" "{:model \"echo\" :provider \"grover\" :context-window 32768}")
        (fs/spit "/tmp/isaac-reload/.isaac/config/providers/grover.edn" "{:api \"grover\"}")
        (with-redefs [httpkit/run-server   (fn [_ _] (fn [] nil))
                      httpkit/server-port  (fn [_] 7001)
                      httpkit/server-stop! (fn [_] nil)
                      change-source/poll!  (fn [s t]
                                             (when (= 2 (swap! poll-count inc))
                                               (deliver poll-ready true))
                                             (orig-poll s t))]
          (sut/start! {:cfg                  {:models {"grover" {:model "echo" :provider "grover" :context-window 32768}}
                                              :providers {"grover" {:api "grover"}}}
                       :config-change-source source
                       :state-dir            "/tmp/isaac-reload/.isaac"
                       :port                 0})
          (fs/spit "/tmp/isaac-reload/.isaac/config/models/grover.edn" "{:model \"\" :provider \"grover\" :context-window 32768}")
          (change-source/notify-path! source "/tmp/isaac-reload/.isaac/config/models/grover.edn")
          (deref poll-ready 1000 ::timeout)
          (should= "echo" (get-in (sut/current-config) [:models "grover" :model]))
          (sut/stop!)))))

  (it "connects Discord gateway on startup when token is present"
    (let [connected (atom nil)
          stopped   (atom nil)]
      (with-redefs [discord/connect!      (fn [opts] (reset! connected opts) {:client ::discord-client})
                    discord-gateway/stop! (fn [client] (reset! stopped client))]
        (sut/start! {:port               0
                     :state-dir          "/tmp/isaac"
                     :cfg                {:comms {:discord {:token "test-token"}}}
                     :start-http-server? false})
        (sut/stop!))
      (should= "/tmp/isaac" (:state-dir @connected))
      (should= ::discord-client @stopped)))

  (it "does not connect Discord gateway on startup when no token is configured"
    (let [connected (atom false)]
      (with-redefs [discord/connect! (fn [_] (reset! connected true) {:client nil})]
        (sut/start! {:port               0
                     :state-dir          "/tmp/isaac"
                     :cfg                {}
                     :start-http-server? false})
        (sut/stop!))
      (should= false @connected)))

  (it "connects Discord gateway when token is added via config hot-reload"
    (let [source    (change-source/memory-source "/tmp/isaac-discord")
          connected (atom nil)]
      (binding [fs/*fs* (fs/mem-fs)]
        (fs/mkdirs "/tmp/isaac-discord/.isaac/config")
        (fs/spit "/tmp/isaac-discord/.isaac/config/isaac.edn"
                 (pr-str {:comms {:discord {}}}))
        (with-redefs [discord/connect!      (fn [opts] (reset! connected opts) {:client ::discord-client})
                      discord-gateway/stop! (fn [_] nil)]
          (sut/start! {:cfg                  {:comms {:discord {}}}
                       :config-change-source source
                       :state-dir            "/tmp/isaac-discord/.isaac"
                       :port                 0
                       :start-http-server?   false})
          (fs/spit "/tmp/isaac-discord/.isaac/config/isaac.edn"
                   (pr-str {:comms {:discord {:token "new-token"}}}))
           (change-source/notify-path! source "/tmp/isaac-discord/.isaac/config/isaac.edn")
           (helper/await-condition #(some? @connected))
           (sut/stop!)))
      (should= "/tmp/isaac-discord/.isaac" (:state-dir @connected))))

  (it "disconnects Discord gateway when token is removed via config hot-reload"
    (let [source  (change-source/memory-source "/tmp/isaac-discord")
          stopped (atom nil)]
      (binding [fs/*fs* (fs/mem-fs)]
        (fs/mkdirs "/tmp/isaac-discord/.isaac/config")
        (fs/spit "/tmp/isaac-discord/.isaac/config/isaac.edn"
                 (pr-str {:comms {:discord {:token "old-token"}}}))
        (with-redefs [discord/connect!      (fn [_] {:client ::discord-client})
                      discord-gateway/stop! (fn [client] (reset! stopped client))]
          (sut/start! {:cfg                  {:comms {:discord {:token "old-token"}}}
                       :config-change-source source
                       :state-dir            "/tmp/isaac-discord/.isaac"
                       :port                 0
                       :start-http-server?   false})
          (fs/spit "/tmp/isaac-discord/.isaac/config/isaac.edn"
                   (pr-str {:comms {:discord {}}}))
          (change-source/notify-path! source "/tmp/isaac-discord/.isaac/config/isaac.edn")
          (helper/await-condition #(some? @stopped))
          (sut/stop!)))
      (should= ::discord-client @stopped)))

  (it "does not reconnect Discord gateway when token is unchanged on config hot-reload"
    (let [source         (change-source/memory-source "/tmp/isaac-discord")
          connect-count  (atom 0)]
      (binding [fs/*fs* (fs/mem-fs)]
        (fs/mkdirs "/tmp/isaac-discord/.isaac/config/crew")
        (fs/spit "/tmp/isaac-discord/.isaac/config/isaac.edn"
                 (pr-str {:comms {:discord {:token "stable-token"}}}))
        (fs/spit "/tmp/isaac-discord/.isaac/config/crew/main.edn"
                 (pr-str {:soul "old"}))
        (with-redefs [discord/connect!      (fn [_] (swap! connect-count inc) {:client ::discord-client})
                      discord-gateway/stop! (fn [_] nil)]
          (sut/start! {:cfg                  {:comms {:discord {:token "stable-token"}}}
                       :config-change-source source
                       :state-dir            "/tmp/isaac-discord/.isaac"
                       :port                 0
                       :start-http-server?   false})
          (fs/spit "/tmp/isaac-discord/.isaac/config/crew/main.edn"
                   (pr-str {:soul "new"}))
          (change-source/notify-path! source "/tmp/isaac-discord/.isaac/config/crew/main.edn")
          (helper/await-condition #(= "new" (get-in (sut/current-config) [:crew "main" :soul])))
          (sut/stop!)))
      (should= 1 @connect-count)))

  )
