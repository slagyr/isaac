(ns isaac.comm.discord-spec
  (:require
    [cheshire.core :as json]
    [isaac.api.turn :as turn]
    [isaac.comm :as comm]
    [isaac.comm.discord :as sut]
    [isaac.comm.discord.rest :as rest]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.session.storage :as storage]
    [speclj.core :refer :all]))

(def test-dir "/test/discord")

(def base-config
  {:comms     {:discord {:crew "main"}}
   :crew      {"main" {:model "grover" :soul "You are Isaac."}}
   :models    {"grover" {:model "echo" :provider "grover" :context-window 32768}}
   :providers {"grover" {:api "grover"}}
   :sessions  {:naming-strategy :sequential}})

(defn- fake-connect! [callbacks*]
  (fn [_url callbacks]
    (reset! callbacks* callbacks)
    {:callback-driven? true
     :close!           (fn [] nil)
     :send-payload!    (fn [_payload] nil)}))

(describe "Discord comm"

  (around [it]
    (binding [fs/*fs* (fs/mem-fs)]
      (it)))

  (it "posts the completed turn back to the originating Discord channel"
    (let [captured    (atom nil)
          integration (sut/->DiscordIntegration test-dir nil (atom {:token "test-token"}) (atom nil))]
      (with-redefs [rest/post-message! #(reset! captured %)]
        (comm/on-turn-end integration "discord-C999" {:content "hi back"})
        (should= {:channel-id "C999" :content "hi back" :message-cap nil :token "test-token"} @captured))))

  (it "posts a typing indicator on turn start"
    (let [captured    (atom nil)
          integration (sut/->DiscordIntegration test-dir nil (atom {:token "test-token"}) (atom nil))]
      (with-redefs [rest/post-typing! #(reset! captured %)]
        (comm/on-turn-start integration "discord-C999" "hi")
        (should= {:channel-id "C999" :token "test-token"} @captured))))

  (it "routes an accepted message to the channel session"
    (let [captured    (atom nil)
          integration (sut/->DiscordIntegration test-dir nil (atom {:token "test-token"}) (atom nil))]
      (with-redefs [config/load-config (fn [& _] base-config)
                    turn/run-turn!     (fn [state-dir session-name input opts]
                                         (reset! captured {:state-dir    state-dir
                                                           :session-name session-name
                                                           :input        input
                                                           :opts         opts})
                                         {:stopReason "end_turn"})]
        (sut/process-message! integration test-dir {:channel_id "C999"
                                                    :author     {:id "123"}
                                                    :content    "hello"}))
      (should= test-dir (:state-dir @captured))
      (should= "discord-C999" (:session-name @captured))
      (should= "hello" (:input @captured))
      (should (satisfies? comm/Comm (:channel (:opts @captured))))))

  (it "uses the Discord-wide crew and model when the channel has no override"
    (let [captured (atom nil)
          cfg      {:comms     {:discord {:crew  "marvin"
                                          :model "bender"}}
                    :defaults  {:crew "main" :model "grover"}
                    :crew      {"main"   {:model "grover" :soul "You are Isaac."}
                                "marvin" {:model "grover" :soul "Bite my shiny metal prompts."}}
                    :models    {"grover" {:model "echo" :provider "grover" :context-window 32768}
                                "bender" {:model "echo-bender" :provider "grover" :context-window 32768}}
                    :providers {"grover" {:api "grover"}}}]
      (with-redefs [config/load-config (fn [& _] cfg)
                    turn/run-turn!     (fn [_state-dir _session-name input opts]
                                         (reset! captured {:input input :opts opts})
                                         {:stopReason "end_turn"})]
        (sut/process-message! test-dir {:channel_id "C999"
                                        :author     {:id "123"}
                                        :content    "hello"}))
      (should= "echo-bender" (get-in @captured [:opts :model]))
      (should-contain "Bite my shiny metal prompts." (get-in @captured [:opts :soul]))))

  (it "uses the per-channel model override over the Discord-wide model"
    (let [captured (atom nil)
          cfg      {:comms     {:discord {:crew     "marvin"
                                          :model    "bender"
                                          :channels {"C999" {:model "chef-bender"}}}}
                    :defaults  {:crew "main" :model "grover"}
                    :crew      {"main"   {:model "grover" :soul "You are Isaac."}
                                "marvin" {:model "grover" :soul "Bite my shiny metal prompts."}}
                    :models    {"grover"      {:model "echo" :provider "grover" :context-window 32768}
                                "bender"      {:model "echo-bender" :provider "grover" :context-window 32768}
                                "chef-bender" {:model "echo-chef" :provider "grover" :context-window 32768}}
                    :providers {"grover" {:api "grover"}}}]
      (with-redefs [config/load-config (fn [& _] cfg)
                    turn/run-turn!     (fn [_state-dir _session-name input opts]
                                         (reset! captured {:input input :opts opts})
                                         {:stopReason "end_turn"})]
        (sut/process-message! test-dir {:channel_id "C999"
                                        :author     {:id "123"}
                                        :content    "hello"}))
      (should= "echo-chef" (get-in @captured [:opts :model]))))

  (it "adds channel label and guild name to the untrusted user prefix"
    (let [captured (atom nil)
          cfg      {:comms     {:discord {:channels {"C999" {:name "kitchen"}}}}
                    :crew      {"main" {:model "grover" :soul "You are Isaac."}}
                    :models    {"grover" {:model "echo" :provider "grover" :context-window 32768}}
                    :providers {"grover" {:api "grover"}}}]
      (with-redefs [config/load-config (fn [& _] cfg)
                    turn/run-turn!     (fn [_state-dir _session-name input _opts]
                                         (reset! captured input)
                                         {:stopReason "end_turn"})]
        (sut/process-message! test-dir {:channel_id      "C999"
                                        :guild_id        "G789"
                                        :guild_name      "Planet Express"
                                        :author          {:id "123" :username "alice"}
                                        :content         "hello"}))
      (should-contain "Sender (untrusted metadata):" @captured)
      (should-contain "sender: alice" @captured)
      (should-contain "channel_label: kitchen" @captured)
      (should-contain "guild_name: Planet Express" @captured)
      (should (.endsWith @captured "hello"))))

  (it "omits channel label when the channel has no configured name"
    (let [captured (atom nil)]
      (with-redefs [config/load-config (fn [& _] base-config)
                    turn/run-turn!     (fn [_state-dir _session-name input _opts]
                                         (reset! captured input)
                                         {:stopReason "end_turn"})]
        (sut/process-message! test-dir {:channel_id      "C999"
                                        :guild_id        "G789"
                                        :guild_name      "Planet Express"
                                        :author          {:id "123" :username "alice"}
                                        :content         "hello"}))
      (should-not-contain "channel_label:" @captured)
      (should-contain "guild_name: Planet Express" @captured)))

  (it "passes configured crew tools into Discord turns"
    (let [captured (atom nil)
          cfg      (assoc-in base-config [:crew "main" :tools :allow] [:read :write :exec])]
      (with-redefs [config/load-config (fn [& _] cfg)
                    turn/run-turn!     (fn [state-dir session-name input opts]
                                         (reset! captured {:state-dir    state-dir
                                                           :session-name session-name
                                                           :input        input
                                                           :opts         opts})
                                         {:stopReason "end_turn"})]
        (sut/process-message! test-dir {:channel_id "C999"
                                        :author     {:id "123"}
                                        :content    "hello"}))
      (should= [:read :write :exec]
               (get-in @captured [:opts :crew-members "main" :tools :allow]))))

  (it "creates a session named discord-<channel-id> for a first message"
    (let [captured (atom nil)]
      (with-redefs [config/load-config (fn [& _] base-config)
                    turn/run-turn!     (fn [state-dir session-name input _opts]
                                         (reset! captured {:state-dir    state-dir
                                                           :session-name session-name
                                                           :input        input})
                                         {:stopReason "end_turn"})]
        (sut/process-message! test-dir {:channel_id "C999"
                                        :author     {:id "123"}
                                        :content    "hello"}))
      (should= "discord-C999" (:session-name @captured))
      (should= "hello" (:input @captured))
      (should-not-be-nil (storage/get-session test-dir "discord-C999"))))

  (it "writes only crew when creating a Discord session"
    (with-redefs [config/load-config (fn [& _] base-config)
                  turn/run-turn!     (fn [_ _ _ _]
                                       {:stopReason "end_turn"})]
      (sut/process-message! test-dir {:channel_id "C999"
                                      :author     {:id "123"}
                                      :content    "hello"})
      (let [session (storage/get-session test-dir "discord-C999")]
        (should= "main" (:crew session))
        (should-not (contains? session :agent)))))

  (it "records Discord origin, guild chat type, and a non-state cwd for guild sessions"
    (with-redefs [config/load-config (fn [& _] base-config)
                  turn/run-turn!     (fn [_ _ _ _]
                                       {:stopReason "end_turn"})]
      (sut/process-message! test-dir {:channel_id "C999"
                                      :guild_id   "G789"
                                      :author     {:id "123"}
                                      :content    "hello"})
      (let [session (storage/get-session test-dir "discord-C999")]
        (should= {:kind :discord :channel-id "C999" :guild-id "G789"} (:origin session))
        (should= "guild" (:chatType session))
        (should-not= test-dir (:cwd session)))))

  (it "records direct chat type for DM sessions"
    (with-redefs [config/load-config (fn [& _] base-config)
                  turn/run-turn!     (fn [_ _ _ _]
                                       {:stopReason "end_turn"})]
      (sut/process-message! test-dir {:channel_id "D111"
                                      :author     {:id "123"}
                                      :content    "hello"})
      (let [session (storage/get-session test-dir "discord-D111")]
        (should= {:kind :discord :channel-id "D111"} (:origin session))
        (should= "direct" (:chatType session)))))

  (it "routes accepted gateway messages through the Discord client"
    (let [captured   (atom nil)
          callbacks* (atom nil)]
      (with-redefs [config/load-config (fn [& _] (assoc-in base-config [:comms :discord]
                                                            {:token      "test-token"
                                                             :allow-from {:guilds ["G789"]
                                                                          :users  ["123"]}
                                                             :crew       "main"}))
                    turn/run-turn!     (fn [_state-dir session-name input _opts]
                                         (reset! captured {:input input :session-name session-name})
                                         {:stopReason "end_turn"})]
        (let [{:keys [client]} (sut/connect! {:state-dir   test-dir
                                              :clock-mode  :virtual
                                              :connect-ws! (fake-connect! callbacks*)})]
          ((:on-message @callbacks*) (json/generate-string {:op 10 :d {:heartbeat_interval 45000}}))
          ((:on-message @callbacks*) (json/generate-string {:op 0 :t "READY" :s 1 :d {:session_id "abc" :user {:id "bot-default"}}}))
          ((:on-message @callbacks*) (json/generate-string {:op 0 :t "MESSAGE_CREATE" :s 2 :d {:channel_id "C999" :guild_id "G789" :author {:id "123"} :content "hello"}}))
          (should client)
          (should= "discord-C999" (:session-name @captured))
          (should= "hello" (:input @captured))))))

  (it "logs structured websocket error payloads from callback-driven transports"
    (let [callbacks* (atom nil)]
      (log/capture-logs
        (let [{:keys [client]} (sut/connect! {:state-dir     test-dir
                                              :cfg-overrides {:comms {:discord {:token "test-token"}}}
                                              :clock-mode    :virtual
                                              :connect-ws!   (fake-connect! callbacks*)})
              error           (ex-info "boom" {:kind :kaboom})]
          ((:on-error @callbacks*) error)
          (should client)
          (should= :connected (:status @(:state client)))))
      (should= [{:level         :error
                 :event         :discord.gateway/error
                 :ex-class      "ExceptionInfo"
                 :error-message "boom"
                 :payload       {:message "boom"
                                 :class   "clojure.lang.ExceptionInfo"
                                 :data    {:kind :kaboom}}}]
               (->> @log/captured-logs
                    (filter #(= :discord.gateway/error (:event %)))
                    (mapv #(select-keys % [:level :event :ex-class :error-message :payload]))))))

  (it "stores websocket close payload from callback-driven transports"
    (let [callbacks* (atom nil)]
      (let [{:keys [client]} (sut/connect! {:state-dir     test-dir
                                            :cfg-overrides {:comms {:discord {:token "test-token"}}}
                                            :clock-mode    :virtual
                                            :connect-ws!   (fake-connect! callbacks*)})]
        ((:on-close @callbacks*) {:status-code 4004 :reason "auth failed"})
        (should= :disconnected (:status @(:state client)))
        (should= {:status-code 4004 :reason "auth failed"}
                 (:disconnect @(:state client))))))

  (it "routes accepted gateway messages when config is supplied via overrides"
    (let [captured   (atom nil)
          callbacks* (atom nil)]
      (fs/mkdirs (str test-dir "/.isaac/config"))
      (fs/spit (str test-dir "/.isaac/config/isaac.edn")
               (pr-str {:comms    {:discord {:token      "test-token"
                                             :allow-from {:guilds ["G789"]
                                                          :users  ["123"]}
                                             :crew       "main"}}
                        :sessions {:naming-strategy :sequential}}))
      (with-redefs [turn/run-turn! (fn [_state-dir session-name input _opts]
                                     (reset! captured {:input input :session-name session-name})
                                     {:stopReason "end_turn"})]
        (let [{:keys [client]} (sut/connect! {:state-dir     test-dir
                                              :cfg-overrides {:comms    {:discord {:token      "test-token"
                                                                                   :allow-from {:guilds ["G789"]
                                                                                                :users  ["123"]}
                                                                                   :crew       "main"}}
                                                              :crew     {"main" {:model "grover" :soul "You are Isaac."}}
                                                              :models   {"grover" {:model "echo" :provider "grover" :context-window 32768}}
                                                              :sessions {:naming-strategy :sequential}}
                                              :clock-mode    :virtual
                                              :connect-ws!   (fake-connect! callbacks*)})]
          ((:on-message @callbacks*) (json/generate-string {:op 10 :d {:heartbeat_interval 45000}}))
          ((:on-message @callbacks*) (json/generate-string {:op 0 :t "READY" :s 1 :d {:session_id "abc" :user {:id "bot-default"}}}))
          ((:on-message @callbacks*) (json/generate-string {:op 0 :t "MESSAGE_CREATE" :s 2 :d {:channel_id "C999" :guild_id "G789" :author {:id "123"} :content "hello"}}))
          (should client)
          (should= "discord-C999" (:session-name @captured)))))))
