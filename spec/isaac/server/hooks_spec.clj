(ns isaac.server.hooks-spec
  (:require
    [cheshire.core :as json]
    [isaac.config.loader :as config]
    [isaac.llm.api :as api]
    [isaac.logger :as log]
    [isaac.session.store :as store]
    [isaac.server.hooks :as sut]
    [isaac.system :as system]
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

  (around [it]
    (sut/reset-registry!)
    (sut/reconcile-config-hooks! nil (:hooks test-cfg))
    (it)
    (sut/reset-registry!))

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
                                                    {:model       "grover"
                                                     :provider    provider
                                                     :soul        "Workspace soul"
                                                     :context-window 32768})
                      isaac.server.hooks/dispatch-turn! (fn [_ _ _] nil)]
          (system/with-system {:session-store (store/create nil :memory)}
            (let [response (sut/handler (make-opts test-cfg "/tmp/hooks-home/.isaac")
                                        (post-request "/hooks/lettuce"
                                                      (json/generate-string {:count 3 :level 8})
                                                      {"authorization" "Bearer secret123"}))]
              (should= 202 (:status response))
              (should= "/tmp/hooks-home" @captured-home))))))

    (it "uses the hook model's provider when dispatching"
      (let [captured (atom nil)
            hook-cfg {:defaults {:crew "main" :model "gpt"}
                      :hooks    {:auth {:token "secret123"}
                                 "lettuce" {:crew        "main"
                                            :session-key "hook:lettuce"
                                            :model       "grok"
                                            :template    "Report: {{count}} items, freshness {{level}}/10."}}
                      :crew     {"main" {:soul "You are Isaac." :model "gpt"}}
                      :models   {"gpt"  {:model "gpt-5.4" :provider "openai-chatgpt" :context-window 32768}
                                 "grok" {:model "grok-4-1-fast" :provider "grok" :context-window 278528}}}]
        (sut/reset-registry!)
        (sut/reconcile-config-hooks! nil (:hooks hook-cfg))
        (with-redefs [isaac.server.hooks/dispatch-turn! (fn [_ _ opts]
                                                          (reset! captured opts)
                                                          nil)]
          (system/with-system {:session-store (store/create nil :memory)}
            (let [response (sut/handler (make-opts hook-cfg "/tmp/hooks-home/.isaac")
                                        (post-request "/hooks/lettuce"
                                                      (json/generate-string {:count 3 :level 8})
                                                      {"authorization" "Bearer secret123"}))]
              (should= 202 (:status response))
              (should= "grok-4-1-fast" (:model @captured))
              (should= "grok" (api/display-name (:provider @captured))))))))

    (it "creates new hook sessions with the crew quarters as cwd"
      (let [hook-cfg  {:defaults {:crew "main" :model "gpt"}
                       :hooks    {:auth {:token "secret123"}
                                  "lettuce" {:crew        "main"
                                             :session-key "hook:lettuce"
                                             :template    "Report: {{count}} items, freshness {{level}}/10."}}
                       :crew     {"main" {:soul "You are Isaac." :model "gpt"}}
                       :models   {"gpt" {:model "gpt-5.4" :provider "openai-chatgpt" :context-window 32768}}}
            mem-store (store/create nil :memory)]
        (sut/reset-registry!)
        (sut/reconcile-config-hooks! nil (:hooks hook-cfg))
        (with-redefs [isaac.server.hooks/dispatch-turn! (fn [_ _ _] nil)]
          (system/with-system {:session-store mem-store}
            (let [response (sut/handler (make-opts hook-cfg "/tmp/hooks-home/.isaac")
                                        (post-request "/hooks/lettuce"
                                                      (json/generate-string {:count 3 :level 8})
                                                      {"authorization" "Bearer secret123"}))
                  session  (store/get-session mem-store "hook:lettuce")]
              (should= 202 (:status response))
              (should= "/tmp/hooks-home/.isaac/crew/main" (:cwd session))
              (should= "main" (:crew session))
              (should= {:kind :webhook :name "lettuce"} (:origin session)))))))

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
        (sut/reset-registry!)
        (sut/reconcile-config-hooks! nil (:hooks hook-cfg))
        (with-redefs [isaac.server.hooks/dispatch-turn! (fn [_ _ _] nil)]
          (system/with-system {:session-store (store/create nil :memory)}
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
                (should= true (:has-model-override? entry)))))))))

  (describe "hook registry"

    (around [it]
      (sut/reset-registry!)
      (it)
      (sut/reset-registry!))

    (it "register-hook! adds a hook to the registry"
      (sut/register-hook! "ping" {:template "hello"} :module)
      (should-not-be-nil (sut/lookup-hook "ping")))

    (it "lookup-hook returns nil for unknown name"
      (should-be-nil (sut/lookup-hook "nope")))

    (it "deregister-hook! removes a registered hook"
      (sut/register-hook! "ping" {:template "hello"} :module)
      (sut/deregister-hook! "ping")
      (should-be-nil (sut/lookup-hook "ping")))

    (it "register-hook! logs :hook/registered"
      (log/capture-logs
        (sut/register-hook! "ping" {:template "hello"} :module)
        (should-not-be-nil (first (filter #(= :hook/registered (:event %)) @log/captured-logs)))))

    (it "deregister-hook! logs :hook/deregistered"
      (log/capture-logs
        (sut/register-hook! "ping" {:template "hello"} :module)
        (sut/deregister-hook! "ping")
        (should-not-be-nil (first (filter #(= :hook/deregistered (:event %)) @log/captured-logs)))))

    (it "throws on collision when module hook matches config hook name"
      (sut/register-hook! "ping" {:template "hello"} :config)
      (should-throw clojure.lang.ExceptionInfo
                    (sut/register-hook! "ping" (fn [_] nil) :module)))))
