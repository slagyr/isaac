(ns isaac.server.hooks-spec
  (:require
    [cheshire.core :as json]
    [isaac.config.loader :as config]
    [isaac.llm.api :as api]
    [isaac.logger :as log]
    [isaac.session.store :as store]
    [isaac.server.hooks :as sut]
    [speclj.core :refer :all]))

(defn- make-opts [cfg state-dir]
  {:cfg cfg :state-dir state-dir})

(defn- post-request [path body headers]
  {:request-method :post
   :uri            path
   :headers        (merge {"content-type" "application/json"} headers)
   :body           body})

(defn- get-request [path headers]
  {:request-method :get
   :uri            path
   :headers        headers})

(def ^:private test-cfg
  {:hooks {:auth {:token "secret123"}
           "lettuce" {:crew        "main"
                      :session-key "hook:lettuce"
                      :template    "Report: {{count}} items, freshness {{level}}/10."}}
   :crew  {"main" {:soul "You are Isaac."}}
   :models {"grover" {:model "echo" :provider "grover" :context-window 32768}}})

(describe "Webhook handler"

  (describe "render-template"
    (it "substitutes present vars"
      (let [result (#'sut/render-template "Hello {{name}}, you have {{count}} items." {:name "Zane" :count 3})]
        (should= "Hello Zane, you have 3 items." result)))

    (it "renders (missing) for absent vars"
      (let [result (#'sut/render-template "Hello {{name}}, you have {{count}} items." {:name "Zane"})]
        (should= "Hello Zane, you have (missing) items." result))))

  (describe "auth"
    (it "returns 401 when no token is provided"
      (let [resp (sut/handler (make-opts test-cfg "/test") (post-request "/hooks/lettuce" "{}" {}))]
        (should= 401 (:status resp))))

    (it "returns 401 when wrong token is provided"
      (let [resp (sut/handler (make-opts test-cfg "/test") (post-request "/hooks/lettuce" "{}" {"authorization" "Bearer wrong"}))]
        (should= 401 (:status resp))))

    (it "returns 401 for unknown paths when token is missing"
      (let [resp (sut/handler (make-opts test-cfg "/test") (post-request "/hooks/unknown" "{}" {}))]
        (should= 401 (:status resp)))))

  (describe "method check"
    (it "returns 405 for GET requests"
      (let [resp (sut/handler (make-opts test-cfg "/test") (get-request "/hooks/lettuce" {"authorization" "Bearer secret123"}))]
        (should= 405 (:status resp)))))

  (describe "path lookup"
    (it "returns 404 for unknown hook name"
      (let [resp (sut/handler (make-opts test-cfg "/test") (post-request "/hooks/unknown" "{}" {"authorization" "Bearer secret123"}))]
        (should= 404 (:status resp)))))

  (describe "content-type check"
    (it "returns 415 for non-JSON content-type"
      (let [resp (sut/handler (make-opts test-cfg "/test")
                              {:request-method :post
                               :uri            "/hooks/lettuce"
                               :headers        {"authorization"  "Bearer secret123"
                                                "content-type"   "text/plain"}
                               :body           "not json"})]
        (should= 415 (:status resp)))))

  (describe "body parse"
    (it "returns 400 for malformed JSON"
      (let [resp (sut/handler (make-opts test-cfg "/test") (post-request "/hooks/lettuce" "not-json" {"authorization" "Bearer secret123"}))]
        (should= 400 (:status resp)))))

  (describe "state dir"
    (it "resolves crew context from the state dir's parent home"
      (let [captured-home (atom nil)
            provider      (reify api/Api
                            (chat [_ _] nil)
                            (chat-stream [_ _ _] nil)
                            (followup-messages [_ request _ _ _] (:messages request))
                            (config [_] {})
                            (display-name [_] "test-provider")
                            (build-prompt [_ _] nil))]
        (with-redefs [config/resolve-crew-context (fn [_ _ opts]
                                                    (reset! captured-home (:home opts))
                                                    {:model "grover"
                                                     :provider provider
                                                     :soul "Workspace soul"
                                                     :context-window 32768})
                      store/get-session (fn [_ _] nil)
                      store/open-session! (fn [& _] nil)
                      isaac.server.hooks/dispatch-turn! (fn [_ _ _] nil)]
           (let [response (sut/handler (make-opts test-cfg "/tmp/hooks-home/.isaac")
                                       (post-request "/hooks/lettuce"
                                                     (json/generate-string {:count 3 :level 8})
                                                     {"authorization" "Bearer secret123"}))]
             (should= 202 (:status response))
             (should= "/tmp/hooks-home" @captured-home))))))

    (it "uses the hook model's provider when dispatching"
      (let [captured  (atom nil)
            hook-cfg  {:defaults {:crew "main" :model "gpt"}
                       :hooks    {:auth {:token "secret123"}
                                  "lettuce" {:crew        "main"
                                              :session-key "hook:lettuce"
                                              :model       "grok"
                                              :template    "Report: {{count}} items, freshness {{level}}/10."}}
                       :crew     {"main" {:soul "You are Isaac." :model "gpt"}}
                       :models   {"gpt"  {:model "gpt-5.4" :provider "openai-chatgpt" :context-window 32768}
                                  "grok" {:model "grok-4-1-fast" :provider "grok" :context-window 278528}}}]
        (with-redefs [store/get-session             (fn [_ _] nil)
                      store/open-session!           (fn [& _] nil)
                      isaac.server.hooks/dispatch-turn! (fn [_ _ opts]
                                                          (reset! captured opts)
                                                          nil)]
          (let [response (sut/handler (make-opts hook-cfg "/tmp/hooks-home/.isaac")
                                      (post-request "/hooks/lettuce"
                                                    (json/generate-string {:count 3 :level 8})
                                                    {"authorization" "Bearer secret123"}))]
            (should= 202 (:status response))
            (should= "grok-4-1-fast" (:model @captured))
            (should= "grok" (api/display-name (:provider @captured)))))))

    (it "logs hook dispatch planning details"
      (let [hook-cfg {:defaults {:crew "main" :model "gpt"}
                      :hooks    {:auth {:token "secret123"}
                                 "lettuce" {:crew        "main"
                                             :session-key "hook:lettuce"
                                             :model       "grok"
                                             :template    "Report: {{count}} items, freshness {{level}}/10."}}
                      :crew     {"main" {:soul "You are Isaac." :model "gpt"}}
                      :models   {"gpt"  {:model "gpt-5.4" :provider "openai-chatgpt" :context-window 32768}
                                 "grok" {:model "grok-4-1-fast" :provider "grok" :context-window 278528}}}]
        (with-redefs [store/get-session             (fn [_ _] nil)
                      store/open-session!           (fn [& _] nil)
                      isaac.server.hooks/dispatch-turn! (fn [_ _ _] nil)]
          (log/capture-logs
            (let [response (sut/handler (make-opts hook-cfg "/tmp/hooks-home/.isaac")
                                        (post-request "/hooks/lettuce"
                                                      (json/generate-string {:count 3 :level 8})
                                                      {"authorization" "Bearer secret123"}))
                  entry    (first (filter #(= :hook/dispatch-planned (:event %)) @log/captured-logs))]
              (should= 202 (:status response))
              (should-not-be-nil entry)
              (should= "lettuce" (:hook entry))
              (should= "hook:lettuce" (:session entry))
              (should= "main" (:crew entry))
              (should= "grok-4-1-fast" (:model entry))
              (should= "grok" (:provider entry))
              (should= false (:existing-session? entry))
              (should= true (:has-model-override? entry))))))))
