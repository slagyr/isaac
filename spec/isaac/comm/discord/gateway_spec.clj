(ns isaac.comm.discord.gateway-spec
  (:require
    [cheshire.core :as json]
    [isaac.comm.discord.gateway :as sut]
    [isaac.logger :as log]
    [speclj.core :refer :all]))

(defn- fake-connect! [sent callbacks*]
  (fn [_url callbacks]
    (reset! callbacks* callbacks)
    {:close! (fn [] nil)
     :send!  (fn [payload] (swap! sent conj payload))}))

(describe "Discord gateway"

  (before
    (log/set-output! :memory)
    (log/clear-entries!))

  (it "sends IDENTIFY after receiving HELLO"
    (let [sent       (atom [])
          callbacks* (atom nil)
          client     (sut/connect! {:token       "test-token"
                                    :clock-mode  :virtual
                                    :connect-ws! (fake-connect! sent callbacks*)})]
      ((:on-message @callbacks*) (json/generate-string {:op 10 :d {:heartbeat_interval 45000}}))
      (should= 2 (:op (first @sent)))
      (should= "test-token" (get-in (first @sent) [:d :token]))
      (should= 33280 (get-in (first @sent) [:d :intents]))))

  (it "sends HEARTBEAT when virtual time advances past the interval"
    (let [sent       (atom [])
          callbacks* (atom nil)
          client     (sut/connect! {:token       "test-token"
                                    :clock-mode  :virtual
                                    :connect-ws! (fake-connect! sent callbacks*)})]
      ((:on-message @callbacks*) (json/generate-string {:op 10 :d {:heartbeat_interval 45000}}))
      (sut/advance-time! client 45000)
      (should= 1 (:op (second @sent)))
      (should= nil (get-in (second @sent) [:d]))))

  (it "marks the client connected after READY"
    (let [sent       (atom [])
          callbacks* (atom nil)
          client     (sut/connect! {:token       "test-token"
                                    :clock-mode  :virtual
                                    :connect-ws! (fake-connect! sent callbacks*)})]
      ((:on-message @callbacks*) (json/generate-string {:op 10 :d {:heartbeat_interval 45000}}))
      ((:on-message @callbacks*) (json/generate-string {:op 0 :t "READY" :s 42 :d {:session_id "abc"}}))
      (should (sut/connected? client))
      (should= 42 (sut/current-sequence client))))

  (it "heartbeats with the latest sequence after dispatch events"
    (let [sent       (atom [])
          callbacks* (atom nil)
          client     (sut/connect! {:token       "test-token"
                                    :clock-mode  :virtual
                                    :connect-ws! (fake-connect! sent callbacks*)})]
      ((:on-message @callbacks*) (json/generate-string {:op 10 :d {:heartbeat_interval 45000}}))
      ((:on-message @callbacks*) (json/generate-string {:op 0 :t "READY" :s 7 :d {:session_id "abc"}}))
      (sut/advance-time! client 45000)
      (should= 1 (:op (nth @sent 1)))
      (should= 7 (get-in (nth @sent 1) [:d]))))

  (it "logs malformed frames as errors"
    (let [sent       (atom [])
          callbacks* (atom nil)
          _client    (sut/connect! {:token       "test-token"
                                    :clock-mode  :virtual
                                    :connect-ws! (fake-connect! sent callbacks*)})]
      ((:on-message @callbacks*) "not-json")
      (should= :discord.gateway/invalid-frame (:event (last (log/get-entries))))))

  (it "reconnects and re-identifies after a normal close"
    (let [sent*      (atom [])
          callbacks* (atom [])
          connect!   (fn [_url callbacks]
                       (swap! callbacks* conj callbacks)
                       {:close! (fn [] nil)
                        :send!  (fn [payload] (swap! sent* conj payload))})
          client     (sut/connect! {:token       "test-token"
                                    :clock-mode  :virtual
                                    :connect-ws! connect!})]
      ((:on-close (first @callbacks*)) {:status 1000 :reason "bye"})
      (should (sut/running? client))
      (should= 2 (count @callbacks*))
      (should= 2 (:op (last @sent*)))
      (should= :discord.gateway/identify (:event (last (log/get-entries))))))

  (it "reconnects and sends RESUME for a resumable close code"
    (let [sent*      (atom [])
          callbacks* (atom [])
          connect!   (fn [_url callbacks]
                       (swap! callbacks* conj callbacks)
                       {:close! (fn [] nil)
                        :send!  (fn [payload] (swap! sent* conj payload))})
          client     (sut/connect! {:token       "test-token"
                                    :clock-mode  :virtual
                                    :connect-ws! connect!})]
      ((:on-message (first @callbacks*)) (json/generate-string {:op 10 :d {:heartbeat_interval 45000}}))
      ((:on-message (first @callbacks*)) (json/generate-string {:op 0 :t "READY" :s 7 :d {:session_id "abc" :user {:id "bot-default"}}}))
      ((:on-close (first @callbacks*)) {:status 4000 :reason "bye"})
      (should= 2 (count @callbacks*))
      (should= 6 (:op (last @sent*)))
      (should= "test-token" (get-in (last @sent*) [:d :token]))
      (should= "abc" (get-in (last @sent*) [:d :session_id]))
      (should= 7 (get-in (last @sent*) [:d :seq]))))

  (it "reconnects and sends IDENTIFY for a non-resumable close code"
    (let [sent*      (atom [])
          callbacks* (atom [])
          connect!   (fn [_url callbacks]
                       (swap! callbacks* conj callbacks)
                       {:close! (fn [] nil)
                        :send!  (fn [payload] (swap! sent* conj payload))})
          client     (sut/connect! {:token       "test-token"
                                    :clock-mode  :virtual
                                    :connect-ws! connect!})]
      ((:on-message (first @callbacks*)) (json/generate-string {:op 10 :d {:heartbeat_interval 45000}}))
      ((:on-message (first @callbacks*)) (json/generate-string {:op 0 :t "READY" :s 7 :d {:session_id "abc" :user {:id "bot-default"}}}))
      ((:on-close (first @callbacks*)) {:status 4009 :reason "session timeout"})
      (should= 2 (count @callbacks*))
      (should= 2 (:op (last @sent*)))
      (should= "test-token" (get-in (last @sent*) [:d :token]))))

  (it "logs fatal close codes without reconnecting"
    (let [sent*      (atom [])
          callbacks* (atom [])
          connect!   (fn [_url callbacks]
                       (swap! callbacks* conj callbacks)
                       {:close! (fn [] nil)
                        :send!  (fn [payload] (swap! sent* conj payload))})
          _client    (sut/connect! {:token       "test-token"
                                    :clock-mode  :virtual
                                    :connect-ws! connect!})]
      ((:on-close (first @callbacks*)) {:status 4004 :reason "bad token"})
      (should= 1 (count @callbacks*))
      (should= :discord.gateway/fatal-close (:event (last (log/get-entries))))))

  (describe "message intake"

    (it "accepts MESSAGE_CREATE from an allowed user and guild"
      (let [sent       (atom [])
            callbacks* (atom nil)
            client     (sut/connect! {:token            "test-token"
                                      :clock-mode       :virtual
                                      :allow-from-users ["123456"]
                                      :allow-from-guilds ["789012"]
                                      :connect-ws!      (fake-connect! sent callbacks*)})]
        ((:on-message @callbacks*) (json/generate-string {:op 10 :d {:heartbeat_interval 45000}}))
        ((:on-message @callbacks*) (json/generate-string {:op 0 :t "READY" :s 1 :d {:session_id "abc" :user {:id "bot-default"}}}))
        ((:on-message @callbacks*) (json/generate-string {:op 0 :t "MESSAGE_CREATE" :s 2 :d {:channel_id "999001" :guild_id "789012" :author {:id "123456"} :content "hello"}}))
        (should= [{:channel_id "999001" :guild_id "789012" :author {:id "123456"} :content "hello"}] (sut/accepted-messages client))))

    (it "ignores MESSAGE_CREATE from a user not on the allow list"
      (let [sent       (atom [])
            callbacks* (atom nil)
            client     (sut/connect! {:token            "test-token"
                                      :clock-mode       :virtual
                                      :allow-from-users ["123456"]
                                      :allow-from-guilds ["789012"]
                                      :connect-ws!      (fake-connect! sent callbacks*)})]
        ((:on-message @callbacks*) (json/generate-string {:op 10 :d {:heartbeat_interval 45000}}))
        ((:on-message @callbacks*) (json/generate-string {:op 0 :t "READY" :s 1 :d {:session_id "abc" :user {:id "bot-default"}}}))
        ((:on-message @callbacks*) (json/generate-string {:op 0 :t "MESSAGE_CREATE" :s 2 :d {:channel_id "999001" :guild_id "789012" :author {:id "999999"} :content "hello"}}))
        (should= [] (sut/accepted-messages client))))

    (it "ignores MESSAGE_CREATE from a guild not on the allow list"
      (let [sent       (atom [])
            callbacks* (atom nil)
            client     (sut/connect! {:token            "test-token"
                                      :clock-mode       :virtual
                                      :allow-from-users ["123456"]
                                      :allow-from-guilds ["789012"]
                                      :connect-ws!      (fake-connect! sent callbacks*)})]
        ((:on-message @callbacks*) (json/generate-string {:op 10 :d {:heartbeat_interval 45000}}))
        ((:on-message @callbacks*) (json/generate-string {:op 0 :t "READY" :s 1 :d {:session_id "abc" :user {:id "bot-default"}}}))
        ((:on-message @callbacks*) (json/generate-string {:op 0 :t "MESSAGE_CREATE" :s 2 :d {:channel_id "999001" :guild_id "888888" :author {:id "123456"} :content "hello"}}))
        (should= [] (sut/accepted-messages client))))

    (it "ignores the bot's own MESSAGE_CREATE events"
      (let [sent       (atom [])
            callbacks* (atom nil)
            client     (sut/connect! {:token            "test-token"
                                      :clock-mode       :virtual
                                      :allow-from-users ["555"]
                                      :allow-from-guilds ["789012"]
                                      :connect-ws!      (fake-connect! sent callbacks*)})]
        ((:on-message @callbacks*) (json/generate-string {:op 10 :d {:heartbeat_interval 45000}}))
        ((:on-message @callbacks*) (json/generate-string {:op 0 :t "READY" :s 1 :d {:session_id "abc" :user {:id "555"}}}))
        ((:on-message @callbacks*) (json/generate-string {:op 0 :t "MESSAGE_CREATE" :s 2 :d {:channel_id "999001" :guild_id "789012" :author {:id "555"} :content "echo"}}))
        (should= [] (sut/accepted-messages client))))))
