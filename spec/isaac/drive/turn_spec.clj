(ns isaac.drive.turn-spec
  (:require
    [isaac.comm.null :as null-comm]
    [isaac.config.loader :as config]
    [isaac.drive.turn :as sut]
    [isaac.fs :as fs]
    [isaac.llm.api :as api]
    [isaac.llm.prompt.builder :as prompt]
    [isaac.llm.tool-loop :as tool-loop]
    [isaac.logger :as log]
    [isaac.marigold :as marigold]
    [isaac.session.store :as store]
    [isaac.spec-helper :as helper]
    [isaac.system :as system]
    [isaac.tool.registry :as tool-registry]
    [speclj.core :refer :all]))

(def test-dir marigold/home)

(deftype TestProvider [name cfg]
  api/Api
  (chat [_ _] {:message {:role "assistant" :content "ok"} :model "test-model" :usage {}})
  (chat-stream [_ _ _] {:message {:role "assistant" :content "ok"} :model "test-model" :usage {}})
  (followup-messages [_ request _ _ _] (:messages request))
  (config [_] cfg)
  (display-name [_] name)
  (format-tools [_ tools] (when (seq tools) (mapv api/wrapped-function-tool tools)))
  (build-prompt [_ {:keys [model tools]}]
    {:model    model
     :messages [{:role "user" :content "hi"}]
     :tools    tools}))

(deftype PromptProvider [name cfg]
  api/Api
  (chat [_ _] {:message {:role "assistant" :content "ok"} :model "test-model" :usage {}})
  (chat-stream [_ _ _] {:message {:role "assistant" :content "ok"} :model "test-model" :usage {}})
  (followup-messages [_ request _ _ _] (:messages request))
  (config [_] cfg)
  (display-name [_] name)
  (build-prompt [_ opts]
    (prompt/build opts)))

(describe "turn usage"

  (marigold/with-manifest)

  (describe "normalize-usage"
    (it "normalizes provider usage aliases into transcript-friendly keys"
      (should= {:input-tokens     100
                :output-tokens    50
                :total-tokens     150
                :cache-read       7
                :cache-write      3
                :reasoning-tokens 11}
               (sut/normalize-usage {:response {:usage {:input_tokens           100
                                                       :output_tokens          50
                                                       :cache_creation_input_tokens 3
                                                       :input_tokens_details   {:cached_tokens 7}
                                                       :output_tokens_details  {:reasoning_tokens 11}}}})))

    (it "prefers accumulated token counts over the last raw provider usage block"
      (should= {:input-tokens  12
                :output-tokens 8
                :total-tokens  20
                :cache-read    2
                :cache-write   1}
               (sut/normalize-usage {:token-counts {:input-tokens  12
                                                    :output-tokens 8
                                                    :cache-read    2
                                                    :cache-write   1}
                                     :response     {:usage {:input_tokens                 3
                                                            :output_tokens                4
                                                            :cache_creation_input_tokens 88
                                                            :input_tokens_details         {:cached_tokens 99}}}}))))

  (describe "process-response!"
    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example]
      (binding [fs/*fs* (fs/mem-fs)]
        (system/with-system {:state-dir test-dir}
          (example))))

    (it "stores a normalized usage map even when the provider omits :usage"
      (helper/create-session! test-dir "usage-test")
      (sut/process-response! "usage-test"
                             {:content  "Hello from Marigold"
                              :response {:prompt_eval_count 20
                                         :eval_count        5}}
                             {:model "groves-13b" :provider marigold/flicker-labs})
       (let [assistant (-> (helper/get-transcript test-dir "usage-test")
                           last
                           :message)]
         (should= {:input-tokens  20
                   :output-tokens 5
                   :total-tokens  25
                   :cache-read    0
                   :cache-write   0}
                 (:usage assistant)))))

  (describe "build-turn-ctx"
    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example]
      (binding [fs/*fs* (fs/mem-fs)]
        (system/with-system {:state-dir test-dir}
          (example))))

    (it "does not resolve effort when the override model disallows it"
      (helper/create-session! test-dir "override-model")
      (helper/update-session! test-dir "override-model" {:crew "main"})
      (try
        (config/set-snapshot! {:defaults {:crew "main" :model "spark"}
                               :crew     {"main" {:model "spark" :soul "You are Isaac."}}
                               :models   {"spark"           {:model "helm-spark-1.0"  :provider marigold/helm-systems}
                                          marigold/starcore {:model "starcore-7-fast" :provider marigold/starcore :allows-effort false}}})
        (let [provider ((requiring-resolve 'isaac.drive.dispatch/make-provider) marigold/starcore {})]
          (with-redefs [sut/augment-provider (fn [provider _session-key _context-window _model-cfg-overrides]
                                               provider)]
            (let [ctx (#'sut/build-turn-ctx "override-model"
                                            {:comm           :test-comm
                                             :context-window 278528
                                             :model          "starcore-7-fast"
                                             :model-cfg      {:model "starcore-7-fast"
                                                              :provider marigold/starcore
                                                              :allows-effort false}
                                             :provider       provider
                                             :soul           "You are Isaac."})]
              (should= nil (:effort ctx)))))
         (finally
           (config/set-snapshot! nil))))

    (it "uses the explicit crew from opts instead of the stored session crew"
      (helper/create-session! test-dir "override-crew")
      (helper/update-session! test-dir "override-crew" {:crew "main"})
      (try
        (config/set-snapshot! {:defaults {:crew "main" :model "spark"}
                               :crew     {"main"  {:model "spark" :soul "You are Isaac."}
                                          "pinky" {:model "smart" :soul "You are Pinky."}}
                               :models   {"spark" {:model "helm-spark-mini" :provider marigold/quantum-anvil :context-window 32768}
                                          "smart" {:model "helm-spark-1.0"  :provider marigold/quantum-anvil :context-window 128000}}})
        (let [provider (->TestProvider marigold/quantum-anvil {:api marigold/anvil-api})]
          (with-redefs [sut/augment-provider (fn [provider _session-key _context-window _model-cfg-overrides]
                                               provider)]
            (let [ctx (#'sut/build-turn-ctx "override-crew"
                                            {:comm           :test-comm
                                             :crew           "pinky"
                                             :context-window 128000
                                             :model          "helm-spark-1.0"
                                             :provider       provider
                                             :soul           "You are Pinky."})]
              (should= "pinky" (:crew ctx)))))
        (finally
          (config/set-snapshot! nil)))))

  (describe "context-mode"
    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example]
      (binding [fs/*fs* (fs/mem-fs)]
        (system/with-system {:state-dir test-dir}
          (example))))

    (it "replays prior transcript entries by default"
      (helper/create-session! test-dir "full-history" {:crew "main"})
      (helper/append-message! test-dir "full-history" {:role "user" :content "What are we doing tonight?"})
      (helper/append-message! test-dir "full-history" {:role "assistant" :content "The same thing we do every night."})
      (let [provider (->PromptProvider marigold/starcore {:api marigold/sky-api})
            captured (atom nil)]
        (config/set-snapshot! {:defaults {:crew "main" :model "test"}
                               :crew     {"main" {:model "test" :soul "You are Brain."}}
                               :models   {"test" {:model "test-model" :provider marigold/starcore :context-window 1000}}})
        (with-redefs [tool-loop/run (fn [_ _ request _ _]
                                      (reset! captured request)
                                      {:message {:role "assistant" :content "Try to take over the world."}
                                       :model   "test-model"
                                       :usage   {}
                                       :tool-calls []})]
          (sut/run-turn! "full-history" "Are the blueprints ready?"
                         {:comm           null-comm/channel
                          :crew           "main"
                          :context-window 1000
                          :model          "test-model"
                          :provider       provider
                          :soul           "You are Brain."}))
        (should= [{:role "system" :content "You are Brain."}
                  {:role "user" :content "What are we doing tonight?"}
                  {:role "assistant" :content "The same thing we do every night."}
                  {:role "user" :content "Are the blueprints ready?"}]
                 (:messages @captured))))

    (it "replays only the current user message when context-mode is reset"
      (helper/create-session! test-dir "reset-history" {:crew "pinky"})
      (helper/append-message! test-dir "reset-history" {:role "user" :content "Are you pondering what I'm pondering?"})
      (helper/append-message! test-dir "reset-history" {:role "assistant" :content "I think so, Brain."})
      (let [provider (->PromptProvider marigold/starcore {:api marigold/sky-api})
            captured (atom nil)]
        (config/set-snapshot! {:defaults {:crew "main" :model "test"}
                               :crew     {"main"  {:model "test" :soul "You are Isaac."}
                                          "pinky" {:model "test" :soul "You are Pinky." :context-mode :reset}}
                               :models   {"test" {:model "test-model" :provider marigold/starcore :context-window 1000}}})
        (with-redefs [tool-loop/run (fn [_ _ request _ _]
                                      (reset! captured request)
                                      {:message {:role "assistant" :content "Logged. Narf!"}
                                       :model   "test-model"
                                       :usage   {}
                                       :tool-calls []})]
          (sut/run-turn! "reset-history" "Brain escaped the cage."
                         {:comm           null-comm/channel
                          :crew           "pinky"
                          :context-window 1000
                          :model          "test-model"
                          :provider       provider
                          :soul           "You are Pinky."}))
        (should= [{:role "system" :content "You are Pinky."}
                  {:role "user" :content "Brain escaped the cage."}]
                 (:messages @captured))))

    )

  (describe "logging"
    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example]
      (binding [fs/*fs* (fs/mem-fs)]
        (system/with-system {:state-dir test-dir}
          (example))))

    (it "logs the resolved turn context"
      (helper/create-session! test-dir "context-log")
      (helper/update-session! test-dir "context-log" {:crew "main" :cwd "/tmp/workspace"})
      (try
        (config/set-snapshot! {:defaults {:crew "main" :model "spark"}
                               :crew     {"main" {:model "spark" :soul "You are Isaac." :tools {:allow [:spyglass :sextant]}}}
                               :models   {"spark" {:model "helm-spark-1.0" :provider marigold/quantum-anvil :context-window 32768}}})
        (let [provider (->TestProvider marigold/quantum-anvil {:api marigold/anvil-api})]
          (with-redefs [sut/augment-provider (fn [provider _session-key _context-window _model-cfg-overrides]
                                               provider)]
            (log/capture-logs
              (#'sut/build-turn-ctx "context-log"
                                    {:comm           :test-comm
                                     :context-window 32768
                                     :model          "helm-spark-1.0"
                                     :provider       provider
                                     :soul           "You are Isaac."})
              (let [entry (first (filter #(= :turn/context-resolved (:event %)) @log/captured-logs))]
                (should-not-be-nil entry)
                (should= "context-log" (:session entry))
                (should= "main" (:crew entry))
                (should= "helm-spark-1.0" (:model entry))
                (should= marigold/quantum-anvil (:provider entry))
                (should= 32768 (:context-window entry))
                (should= #{"main"} (set (:crew-keys entry)))
                (should= #{:model :soul :tools} (set (:crew-cfg-keys entry)))
                (should= [:spyglass :sextant] (:crew-tools entry))
                (should= ["sextant" "spyglass"] (sort (:allowed-tools entry)))
                (should= "/tmp/workspace" (:cwd entry))))))
        (finally
          (config/set-snapshot! nil))))

    (it "logs selected tools, built request, and response summary"
      (helper/create-session! test-dir "log-turn")
      (helper/update-session! test-dir "log-turn" {:crew "main"})
      (let [provider (->TestProvider marigold/starcore {:api marigold/sky-api})
            result   {:message {:role "assistant" :content "ok"}
                      :model   "test-model"
                      :usage   {}
                      :tool-calls []}]
        (config/set-snapshot! {:defaults {:crew "main" :model "test"}
                               :crew     {"main" {:model "test" :soul "You are Isaac." :tools {:allow [:logbook-entry]}}}
                               :models   {"test" {:model "test-model" :provider marigold/starcore :context-window 32768}}})
        (tool-registry/clear!)
        (tool-registry/register! {:name        "logbook-entry"
                                  :description "Append to the ship's log"
                                  :parameters  {:type "object"}
                                  :handler     (fn [_] {:result "ok"})})
        (with-redefs [sut/append-message!   (fn [& _] nil)
                      sut/process-response! (fn [_ result _] result)
                      store/get-transcript  (fn [& _] [])
                      tool-loop/run         (fn [& _] result)]
          (log/capture-logs
            (sut/run-turn! "log-turn" "hi"
                           {:comm           null-comm/channel
                            :context-window 32768
                            :model          "test-model"
                            :provider       provider
                            :soul           "You are Isaac."})
            (let [tools-entry    (first (filter #(= :turn/tools-selected (:event %)) @log/captured-logs))
                  request-entry  (first (filter #(= :turn/request-built (:event %)) @log/captured-logs))
                  response-entry (first (filter #(= :turn/model-response-summary (:event %)) @log/captured-logs))]
              (should-not-be-nil tools-entry)
              (should= "log-turn" (:session tools-entry))
              (should= marigold/starcore (:provider tools-entry))
              (should= 1 (:selected-tools-count tools-entry))
              (should= ["logbook-entry"] (:selected-tools tools-entry))
              (should-not-be-nil request-entry)
              (should= "log-turn" (:session request-entry))
              (should= "test-model" (:model request-entry))
              (should= 1 (:tools-count request-entry))
              (should= ["logbook-entry"] (:tool-names request-entry))
              (should-not-be-nil response-entry)
              (should= "log-turn" (:session response-entry))
              (should= 2 (:assistant-content-chars response-entry))
              (should= 0 (:tool-calls-count response-entry))
              (should= 0 (:executed-tools-count response-entry)))))
        (config/set-snapshot! nil)))))
