(ns isaac.comm.discord-spec
  (:require
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [isaac.comm :as comm]
    [isaac.comm.discord :as sut]
    [isaac.comm.discord.rest :as rest]
    [isaac.config.loader :as config]
    [isaac.drive.turn :as turn]
    [isaac.fs :as fs]
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
    (let [captured (atom nil)
          channel  (sut/channel {:channel-id "C999" :token "test-token"})]
      (with-redefs [rest/post-message! #(reset! captured %)]
        (comm/on-turn-end channel "primary" {:content "hi back"})
        (should= {:channel-id "C999" :content "hi back" :token "test-token"} @captured))))

  (it "routes an accepted message to the mapped session"
    (storage/create-session! test-dir "primary" {:crew "main" :agent "main" :cwd test-dir})
    (fs/spit (str test-dir "/comm/discord/routing.edn") (pr-str {"C999" {"123" "primary"}}))
    (let [captured (atom nil)]
      (with-redefs [config/load-config          (fn [& _] base-config)
                    turn/process-user-input! (fn [state-dir session-name input opts]
                                               (reset! captured {:state-dir    state-dir
                                                                 :session-name session-name
                                                                 :input        input
                                                                 :opts         opts})
                                               {:stopReason "end_turn"})]
        (sut/process-message! test-dir {:channel_id "C999"
                                        :author     {:id "123"}
                                        :content    "hello"}))
      (should= test-dir (:state-dir @captured))
      (should= "primary" (:session-name @captured))
      (should= "hello" (:input @captured))
      (should (satisfies? comm/Comm (:channel (:opts @captured))))))

  (it "creates a session and persists a route for a new channel-user pair"
    (let [captured (atom nil)]
      (with-redefs [config/load-config          (fn [& _] base-config)
                    turn/process-user-input! (fn [state-dir session-name input _opts]
                                               (reset! captured {:state-dir state-dir
                                                                 :session-name session-name
                                                                 :input input})
                                               {:stopReason "end_turn"})]
        (sut/process-message! test-dir {:channel_id "C999"
                                        :author     {:id "123"}
                                        :content    "hello"}))
      (should= "session-1" (:session-name @captured))
      (should= "hello" (:input @captured))
      (should-not-be-nil (storage/get-session test-dir "session-1"))
      (should= {"C999" {"123" "session-1"}}
               (edn/read-string (fs/slurp (str test-dir "/comm/discord/routing.edn"))))))

  (it "routes accepted gateway messages through the Discord client"
    (let [captured   (atom nil)
          callbacks* (atom nil)]
      (with-redefs [config/load-config          (fn [& _] (assoc-in base-config [:comms :discord]
                                                                    {:token      "test-token"
                                                                     :allow-from {:guilds ["G789"]
                                                                                  :users  ["123"]}
                                                                     :crew       "main"}))
                    turn/process-user-input! (fn [_state-dir session-name input _opts]
                                               (reset! captured {:input input :session-name session-name})
                                               {:stopReason "end_turn"})]
        (let [{:keys [client]} (sut/connect! {:state-dir   test-dir
                                             :clock-mode  :virtual
                                             :connect-ws! (fake-connect! callbacks*)})]
          ((:on-message @callbacks*) (json/generate-string {:op 10 :d {:heartbeat_interval 45000}}))
          ((:on-message @callbacks*) (json/generate-string {:op 0 :t "READY" :s 1 :d {:session_id "abc" :user {:id "bot-default"}}}))
          ((:on-message @callbacks*) (json/generate-string {:op 0 :t "MESSAGE_CREATE" :s 2 :d {:channel_id "C999" :guild_id "G789" :author {:id "123"} :content "hello"}}))
          (should client)
          (should= {:input "hello" :session-name "session-1"} @captured)
          (should= {"C999" {"123" "session-1"}}
                   (edn/read-string (fs/slurp (str test-dir "/comm/discord/routing.edn"))))))))

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
      (with-redefs [turn/process-user-input! (fn [_state-dir session-name input _opts]
                                               (reset! captured {:input input :session-name session-name})
                                               {:stopReason "end_turn"})]
        (let [{:keys [client]} (sut/connect! {:state-dir     test-dir
                                             :cfg-overrides {:comms {:discord {:token      "test-token"
                                                                               :allow-from {:guilds ["G789"]
                                                                                            :users  ["123"]}
                                                                               :crew       "main"}}
                                                             :crew  {"main" {:model "grover" :soul "You are Isaac."}}
                                                             :models {"grover" {:model "echo" :provider "grover" :context-window 32768}}
                                                             :sessions {:naming-strategy :sequential}}
                                             :clock-mode    :virtual
                                             :connect-ws!   (fake-connect! callbacks*)})]
          ((:on-message @callbacks*) (json/generate-string {:op 10 :d {:heartbeat_interval 45000}}))
          ((:on-message @callbacks*) (json/generate-string {:op 0 :t "READY" :s 1 :d {:session_id "abc" :user {:id "bot-default"}}}))
          ((:on-message @callbacks*) (json/generate-string {:op 0 :t "MESSAGE_CREATE" :s 2 :d {:channel_id "C999" :guild_id "G789" :author {:id "123"} :content "hello"}}))
          (should client)
          (should= {:input "hello" :session-name "session-1"} @captured))))))
