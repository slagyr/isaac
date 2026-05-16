(ns isaac.hooks-spec
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.configurator :as configurator]
    [isaac.hooks :as sut]
    [isaac.logger :as log]
    [isaac.marigold :as marigold]
    [isaac.session.store :as store]
    [isaac.system :as system]
    [speclj.core :refer :all]))

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
  {:hooks {:auth                 {:token "secret123"}
           marigold/lettuce-hook {:crew        "main"
                                  :session-key (str "hook:" marigold/lettuce-hook)
                                  :template    "Report: {{count}} items, freshness {{level}}/10."}}
   :crew   {"main" {:soul "You are Isaac."}}
   :models {"grover" {:model "echo" :provider marigold/grover-api :context-window 32768}}})

(describe "Webhook handler"

  (defn- startup-hooks! [slice]
    (configurator/on-startup! (sut/make nil) slice))

  #_{:clj-kondo/ignore [:invalid-arity]}
  (around [it]
    (sut/reset-registry!)
    (startup-hooks! (:hooks test-cfg))
    (it)
    (sut/reset-registry!))

  (describe "HooksModule lifecycle"

    (it "registers config hooks on startup"
      (sut/reset-registry!)
      (startup-hooks! {marigold/lettuce-hook {:template "A"}})
      (should= "A" (get-in (sut/lookup-hook marigold/lettuce-hook) [:entry :template])))

    (it "updates hook content when the slice changes"
      (sut/reset-registry!)
      (let [module (sut/make nil)]
        (configurator/on-startup! module {marigold/lettuce-hook {:template "A"}})
        (configurator/on-config-change! module
                                        {marigold/lettuce-hook {:template "A"}}
                                        {marigold/lettuce-hook {:template "B"}})
        (should= "B" (get-in (sut/lookup-hook marigold/lettuce-hook) [:entry :template]))))

    (it "deregisters removed hooks when the slice changes"
      (sut/reset-registry!)
      (let [module (sut/make nil)]
        (configurator/on-startup! module {marigold/lettuce-hook {:template "A"}})
        (configurator/on-config-change! module
                                        {marigold/lettuce-hook {:template "A"}}
                                        {})
        (should-be-nil (sut/lookup-hook marigold/lettuce-hook))))

    (it "does nothing when the slice is unchanged"
      (sut/reset-registry!)
      (let [module  (sut/make nil)
            before  (atom nil)
            payload {marigold/lettuce-hook {:template "A"}}]
        (configurator/on-startup! module payload)
        (reset! before (sut/lookup-hook marigold/lettuce-hook))
        (configurator/on-config-change! module payload payload)
        (should= @before (sut/lookup-hook marigold/lettuce-hook)))))

  (describe "render-template"
    (it "substitutes present vars"
      (let [result (#'sut/render-template "Hello {{name}}, you have {{count}} items." {:name "Zane" :count 3})]
        (should= "Hello Zane, you have 3 items." result)))

    (it "renders (missing) for absent vars"
      (let [result (#'sut/render-template "Hello {{name}}, you have {{count}} items." {:name "Zane"})]
        (should= "Hello Zane, you have (missing) items." result))))

  (describe "auth"
    (it "returns 401 when no token is provided"
      (system/with-system {:state-dir "/test"}
        (config/set-snapshot! test-cfg)
        (let [resp (sut/handler (post-request (str "/hooks/" marigold/lettuce-hook) "{}" {}))]
          (should= 401 (:status resp)))))

    (it "returns 401 when wrong token is provided"
      (system/with-system {:state-dir "/test"}
        (config/set-snapshot! test-cfg)
        (let [resp (sut/handler (post-request (str "/hooks/" marigold/lettuce-hook) "{}" {"authorization" "Bearer wrong"}))]
          (should= 401 (:status resp)))))

    (it "returns 401 for unknown paths when token is missing"
      (system/with-system {:state-dir "/test"}
        (config/set-snapshot! test-cfg)
        (let [resp (sut/handler (post-request "/hooks/unknown" "{}" {}))]
          (should= 401 (:status resp))))))

  (describe "method check"
    (it "returns 405 for GET requests"
      (system/with-system {:state-dir "/test"}
        (config/set-snapshot! test-cfg)
        (let [resp (sut/handler (get-request (str "/hooks/" marigold/lettuce-hook) {"authorization" "Bearer secret123"}))]
          (should= 405 (:status resp))))))

  (describe "path lookup"
    (it "returns 404 for unknown hook name"
      (system/with-system {:state-dir "/test"}
        (config/set-snapshot! test-cfg)
        (let [resp (sut/handler (post-request "/hooks/unknown" "{}" {"authorization" "Bearer secret123"}))]
          (should= 404 (:status resp))))))

  (describe "content-type check"
    (it "returns 415 for non-JSON content-type"
      (system/with-system {:state-dir "/test"}
        (config/set-snapshot! test-cfg)
        (let [resp (sut/handler {:request-method :post
                                 :uri            (str "/hooks/" marigold/lettuce-hook)
                                 :headers        {"authorization"  "Bearer secret123"
                                                  "content-type"   "text/plain"}
                                 :body           "not json"})]
          (should= 415 (:status resp))))))

  (describe "body parse"
    (it "returns 400 for malformed JSON"
      (system/with-system {:state-dir "/test"}
        (config/set-snapshot! test-cfg)
        (let [resp (sut/handler (post-request (str "/hooks/" marigold/lettuce-hook) "not-json" {"authorization" "Bearer secret123"}))]
          (should= 400 (:status resp))))))

  (describe "state dir"

    (it "does not depend on isaac.comm.acp"
      (let [ns-form (->> (slurp "src/isaac/hooks.clj")
                         str/split-lines
                         (take 20)
                         (str/join "\n"))]
        (should-not-contain "isaac.comm.acp" ns-form)))

    (it "resolves crew context from the state dir's parent home"
      (let [captured-home (atom nil)]
        (with-redefs [isaac.hooks/dispatch-turn! (fn [_ _ opts]
                                                   (reset! captured-home (:home opts))
                                                   nil)]
          (system/with-system {:state-dir     "/tmp/hooks-home/.isaac"
                               :session-store (store/create nil :memory)}
            (config/set-snapshot! test-cfg)
            (let [response (sut/handler (post-request (str "/hooks/" marigold/lettuce-hook)
                                                      (json/generate-string {:count 3 :level 8})
                                                      {"authorization" "Bearer secret123"}))]
              (should= 202 (:status response))
              (should= "/tmp/hooks-home" @captured-home))))))

    (it "uses the hook model's provider when dispatching"
      (let [captured (atom nil)
            hook-cfg {:defaults {:crew "main" :model "spark"}
                      :hooks    {:auth                       {:token "secret123"}
                                 marigold/lettuce-hook       {:crew        "main"
                                                              :session-key (str "hook:" marigold/lettuce-hook)
                                                              :model       marigold/starcore
                                                              :template    "Report: {{count}} items, freshness {{level}}/10."}}
                      :crew     {"main" {:soul "You are Isaac." :model "spark"}}
                      :models   {"spark"           {:model "helm-spark-1.0"  :provider marigold/quantum-anvil :context-window 32768}
                                 marigold/starcore {:model "starcore-7-fast" :provider marigold/starcore     :context-window 278528}}}]
        (sut/reset-registry!)
        (startup-hooks! (:hooks hook-cfg))
        (with-redefs [isaac.hooks/dispatch-turn! (fn [_ _ opts]
                                                    (reset! captured opts)
                                                    nil)]
          (system/with-system {:state-dir     "/tmp/hooks-home/.isaac"
                               :session-store (store/create nil :memory)}
            (config/set-snapshot! hook-cfg)
            (let [response (sut/handler (post-request (str "/hooks/" marigold/lettuce-hook)
                                                      (json/generate-string {:count 3 :level 8})
                                                      {"authorization" "Bearer secret123"}))]
              (should= 202 (:status response))
              (should= marigold/starcore (:model-override @captured)))))))

    (it "passes the crew quarters cwd and webhook origin into dispatch"
      (let [hook-cfg  {:defaults {:crew "main" :model "spark"}
                       :hooks    {:auth                 {:token "secret123"}
                                  marigold/lettuce-hook {:crew        "main"
                                                         :session-key (str "hook:" marigold/lettuce-hook)
                                                         :template    "Report: {{count}} items, freshness {{level}}/10."}}
                       :crew     {"main" {:soul "You are Isaac." :model "spark"}}
                       :models   {"spark" {:model "helm-spark-1.0" :provider marigold/quantum-anvil :context-window 32768}}}
            captured  (atom nil)
            mem-store (store/create nil :memory)]
        (sut/reset-registry!)
        (startup-hooks! (:hooks hook-cfg))
        (with-redefs [isaac.hooks/dispatch-turn! (fn [_ _ opts]
                                                   (reset! captured opts)
                                                   nil)]
          (system/with-system {:state-dir     "/tmp/hooks-home/.isaac"
                               :session-store mem-store}
            (config/set-snapshot! hook-cfg)
            (let [response (sut/handler (post-request (str "/hooks/" marigold/lettuce-hook)
                                                       (json/generate-string {:count 3 :level 8})
                                                       {"authorization" "Bearer secret123"}))]
              (should= 202 (:status response))
              (should= "/tmp/hooks-home/.isaac/crew/main" (:cwd @captured))
              (should= "main" (:crew-override @captured))
              (should= {:kind :webhook :name marigold/lettuce-hook} (:origin @captured)))))))

    (it "logs hook dispatch planning details"
      (let [hook-cfg {:defaults {:crew "main" :model "spark"}
                      :hooks    {:auth                 {:token "secret123"}
                                 marigold/lettuce-hook {:crew        "main"
                                                        :session-key (str "hook:" marigold/lettuce-hook)
                                                        :model       marigold/starcore
                                                        :template    "Report: {{count}} items, freshness {{level}}/10."}}
                      :crew     {"main" {:soul "You are Isaac." :model "spark"}}
                      :models   {"spark"           {:model "helm-spark-1.0"  :provider marigold/quantum-anvil :context-window 32768}
                                 marigold/starcore {:model "starcore-7-fast" :provider marigold/starcore     :context-window 278528}}}]
        (sut/reset-registry!)
        (startup-hooks! (:hooks hook-cfg))
        (with-redefs [isaac.hooks/dispatch-turn! (fn [_ _ _] nil)]
          (system/with-system {:state-dir     "/tmp/hooks-home/.isaac"
                               :session-store (store/create nil :memory)}
            (config/set-snapshot! hook-cfg)
            (log/capture-logs
              (let [response (sut/handler (post-request (str "/hooks/" marigold/lettuce-hook)
                                                        (json/generate-string {:count 3 :level 8})
                                                        {"authorization" "Bearer secret123"}))
                    entry    (first (filter #(= :hook/dispatch-planned (:event %)) @log/captured-logs))]
                 (should= 202 (:status response))
                 (should-not-be-nil entry)
                 (should= marigold/lettuce-hook (:hook entry))
                 (should= (str "hook:" marigold/lettuce-hook) (:session entry))
                 (should= "main" (:crew entry))
                 (should= false (:existing-session? entry))
                 (should= true (:has-model-override? entry)))))))))

  (describe "hook registry"

    #_{:clj-kondo/ignore [:invalid-arity]}
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
