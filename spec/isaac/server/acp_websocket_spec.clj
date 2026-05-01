(ns isaac.server.acp-websocket-spec
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.fs :as fs]
    [isaac.acp.jsonrpc :as jrpc]
    [isaac.logger :as log]
    [isaac.session.storage :as storage]
    [isaac.server.acp-websocket :as sut]
    [org.httpkit.server :as httpkit]
    [speclj.core :refer :all]))

(describe "ACP WebSocket endpoint"

  (describe "auth-error-response"

    (it "returns nil when token auth is not enabled"
      (should= nil (sut/auth-error-response {:cfg {}} {:headers {}})))

    (it "returns 401 when the bearer token is missing"
      (let [response (sut/auth-error-response {:cfg {:gateway {:auth {:mode "token" :token "secret123"}}}}
                                              {:headers {}})]
        (should= 401 (:status response))
        (should= "authentication failed" (:body response))))

    (it "returns nil when the bearer token matches"
      (should= nil (sut/auth-error-response {:cfg {:gateway {:auth {:mode "token" :token "secret123"}}}}
                                            {:headers {"authorization" "Bearer secret123"}}))))

  (describe "send-dispatch-result!"

    (it "sends a response as one JSON line"
      (let [sent (atom [])]
        (sut/send-dispatch-result! #(swap! sent conj %) (jrpc/result 1 {:ok true}))
        (should= (jrpc/result 1 {:ok true})
                 (json/parse-string (first @sent) true))))

    (it "sends notifications before the response when given an envelope"
      (let [sent   (atom [])
            notif  (jrpc/notification "session/update" {:sessionId "agent:main:acp:direct:user1"})
            result {:notifications [notif]
                    :response      (jrpc/result 2 {:stopReason "end_turn"})}]
        (sut/send-dispatch-result! #(swap! sent conj %) result)
        (should= notif (json/parse-string (first @sent) true))
        (should= 2 (:id (json/parse-string (second @sent) true)))))

    )

  (describe "dispatch-line"

    (it "reuses the most recent session for session/new when resume=true is set"
      (binding [fs/*fs* (fs/mem-fs)]
        (let [state-dir (str "/test/acp-ws-resume-" (random-uuid))
              older     "older"
              recent    "recent"
              _         (storage/create-session! state-dir older)
              _         (storage/create-session! state-dir recent)
              _         (storage/update-session! state-dir older {:updated-at "2026-04-10T10:00:00"})
              _         (storage/update-session! state-dir recent {:updated-at "2026-04-12T15:00:00"})
              result    (sut/dispatch-line {:cfg          {}
                                            :state-dir    state-dir
                                            :query-params {"resume" "true"}}
                                           {:headers {} :uri "/acp"}
                                           (str/trim-newline (jrpc/request-line 2 "session/new" {})))]
          (should= recent (get-in result [:result :sessionId]))
          (should= 2 (count (storage/list-sessions state-dir "main"))))))

    (it "reuses the most recent session when state-dir and crew are provided by handler inputs"
      (binding [fs/*fs* (fs/mem-fs)]
        (let [state-dir (str "/test/acp-home-" (random-uuid) "/.isaac")
              older     "older"
              recent    "recent"
              _         (storage/create-session! state-dir older {:crew "marvin"})
              _         (storage/create-session! state-dir recent {:crew "marvin"})
              _         (storage/update-session! state-dir older {:updated-at "2026-04-10T10:00:00"})
              _         (storage/update-session! state-dir recent {:updated-at "2026-04-12T15:00:00"})
              result    (sut/dispatch-line {:cfg          {}
                                            :state-dir    state-dir
                                            :query-params {"resume" "true"
                                                           "crew"   "marvin"}}
                                           {:headers {} :uri "/acp"}
                                           (str/trim-newline (jrpc/request-line 2 "session/new" {})))]
          (should= recent (get-in result [:result :sessionId]))
          (should= 2 (count (storage/list-sessions state-dir "marvin"))))))

    (it "attaches a requested session and replays its transcript on session/new"
      (binding [fs/*fs* (fs/mem-fs)]
        (let [state-dir     (str "/test/acp-home-" (random-uuid) "/.isaac")
              session-key   "tidy-comet"
              notifications (atom [])
              _             (storage/create-session! state-dir session-key {:crew "marvin"})
              _             (storage/append-message! state-dir session-key {:role "user" :content "Howdy."})
              _             (storage/append-message! state-dir session-key {:role "assistant" :content "Howdy."})
              result        (sut/dispatch-line {:cfg          {}
                                                :state-dir    state-dir
                                                :output-writer #(swap! notifications conj (json/parse-string % true))
                                                :query-params {"session" "tidy-comet"
                                                               "crew"    "marvin"}}
                                               {:headers {} :uri "/acp"}
                                               (str/trim-newline (jrpc/request-line 2 "session/new" {})))]
          (should= session-key (get-in result [:result :sessionId]))
          (should= ["user_message_chunk" "agent_message_chunk"]
                   (mapv #(get-in % [:params :update :sessionUpdate]) @notifications))
          (should= ["Howdy." "Howdy."]
                   (mapv #(get-in % [:params :update :content :text]) @notifications)))))

    )

  (describe "handler"

    (it "returns an authentication error before websocket upgrade"
      (let [response (sut/handler {:cfg {:gateway {:auth {:mode "token" :token "secret123"}}}}
                                  {:websocket? true :headers {}})]
        (should= 401 (:status response))
        (should= "authentication failed" (:body response))))

    (it "upgrades authenticated websocket requests"
      (let [captured (atom nil)]
        (with-redefs [httpkit/as-channel (fn [request opts]
                                           (reset! captured [request opts])
                                           {:body :channel})]
          (let [response (sut/handler {:cfg {:gateway {:auth {:mode "token" :token "secret123"}}}}
                                      {:websocket? true
                                       :headers    {"authorization" "Bearer secret123"}})]
            (should= :channel (:body response))
            (should-not-be-nil @captured)
            (should (fn? (:on-receive (second @captured))))))))

    (it "logs connection lifecycle events"
      (let [captured (atom nil)]
        (with-redefs [httpkit/as-channel (fn [_request opts]
                                           (reset! captured opts)
                                           :ok)]
          (log/capture-logs
            (sut/handler {:cfg {}}
                         {:websocket? true
                          :uri        "/acp"
                          :headers    {"x-forwarded-for" "127.0.0.1"}})
            ((:on-open @captured) :channel)
            ((:on-close @captured) :channel 1000 "bye")
            (should= [:acp-ws/connection-opened :acp-ws/connection-closed]
                     (mapv :event @log/captured-logs))))))

    (it "logs initialize dispatch"
      (let [captured (atom nil)]
        (with-redefs [httpkit/as-channel           (fn [_request opts]
                                                     (reset! captured opts)
                                                     :ok)
                      httpkit/send!                (fn [_channel _line] nil)
                      isaac.acp.server/dispatch-line (fn [_opts _line]
                                                       (jrpc/result 1 {:ok true}))]
          (log/capture-logs
            (sut/handler {:cfg {}}
                         {:websocket? true
                          :uri        "/acp"
                          :headers    {}})
            ((:on-receive @captured) :channel (str/trim-newline (jrpc/request-line 1 "initialize" {})))
            (should= [:acp-ws/initialize]
                     (mapv :event @log/captured-logs))))))

    (it "logs session/new with returned session id"
      (let [captured (atom nil)]
        (with-redefs [httpkit/as-channel           (fn [_request opts]
                                                     (reset! captured opts)
                                                     :ok)
                      httpkit/send!                (fn [_channel _line] nil)
                      isaac.acp.server/dispatch-line (fn [_opts _line]
                                                       (jrpc/result 2 {:sessionId "agent:main:acp:direct:user1"}))]
          (log/capture-logs
            (sut/handler {:cfg {}}
                         {:websocket? true
                          :uri        "/acp"
                          :headers    {}})
            ((:on-receive @captured) :channel (str/trim-newline (jrpc/request-line 2 "session/new" {})))
            (should= [{:event :acp-ws/session-new :sessionId "agent:main:acp:direct:user1"}]
                     (mapv #(select-keys % [:event :sessionId]) @log/captured-logs))))))

    (it "applies query params as websocket handler overrides"
      (with-redefs [httpkit/as-channel           (fn [_request opts]
                                                    ((:on-receive opts) :channel (str/trim-newline (jrpc/request-line 1 "initialize" {})))
                                                    :ok)
                    httpkit/send!                (fn [_channel _line] nil)
                    isaac.acp.server/dispatch-line (fn [opts _line]
                                                     (should= "ketch" (:crew-id opts))
                                                     (should= "grover2" (:model-override opts))
                                                     (jrpc/result 1 {:ok true}))]
        (sut/handler {:cfg {}}
                     {:websocket?  true
                      :uri         "/acp"
                      :query-string "crew=ketch&model=grover2&resume=true"})))

    (it "reads cfg-fn on every frame to pick up hot-reloaded config"
      (let [cfg*     (atom {:v 1})
            captured (atom [])
            channel* (atom nil)
            frame    (str/trim-newline (jrpc/request-line 1 "initialize" {}))]
        (with-redefs [httpkit/as-channel (fn [_request opts]
                                           (reset! channel* opts)
                                           :ok)
                      httpkit/send!      (fn [_ch _line] nil)
                      isaac.acp.server/dispatch-line (fn [opts _line]
                                                       (swap! captured conj (:cfg opts))
                                                       (jrpc/result 1 {:ok true}))]
          (sut/handler {:cfg-fn (fn [] @cfg*)}
                       {:websocket? true :uri "/acp" :headers {}})
          ((:on-receive @channel*) :channel frame)
          (reset! cfg* {:v 2})
          ((:on-receive @channel*) :channel frame)
          (should= {:v 1} (first @captured))
          (should= {:v 2} (second @captured)))))

    (it "flushes tool notifications before the final response completes"
      (let [captured (atom nil)
            sent     (atom [])
            release* (promise)]
        (with-redefs [httpkit/as-channel           (fn [_request opts]
                                                     (reset! captured opts)
                                                     :ok)
                      httpkit/send!                (fn [_channel line]
                                                     (swap! sent conj line))
                      isaac.server.acp-websocket/dispatch-line
                      (fn [opts _request _line]
                        ((:output-writer opts) (str/trim-newline (jrpc/notification-line "session/update" {:tool "exec"})))
                        @release*
                        (jrpc/result 2 {:stopReason "end_turn"}))]
          (sut/handler {:cfg {}}
                       {:websocket? true
                        :uri        "/acp"
                        :headers    {}})
          (future ((:on-receive @captured) :channel (str/trim-newline (jrpc/request-line 2 "session/prompt" {}))))
          (let [deadline (+ (System/currentTimeMillis) 1000)]
            (while (and (< (count @sent) 1) (< (System/currentTimeMillis) deadline))
              (Thread/sleep 1)))
          (should= 1 (count @sent))
          (should= "session/update" (:method (json/parse-string (first @sent) true)))
           (deliver release* :ok)
           (let [deadline (+ (System/currentTimeMillis) 1000)]
             (while (and (< (count @sent) 2) (< (System/currentTimeMillis) deadline))
               (Thread/sleep 1)))
           (should= 2 (count @sent)))))

    )

  )
