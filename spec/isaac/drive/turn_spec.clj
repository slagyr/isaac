(ns isaac.drive.turn-spec
  (:require
    [isaac.config.loader :as config]
    [isaac.drive.turn :as sut]
    [isaac.fs :as fs]
    [isaac.spec-helper :as helper]
    [isaac.system :as system]
    [speclj.core :refer :all]))

(def test-dir "/test/turn")

(describe "turn usage"

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
                             {:content  "Hello from Ollama"
                              :response {:prompt_eval_count 20
                                         :eval_count        5}}
                             {:model "qwen3-coder" :provider "ollama"})
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
      (config/set-snapshot! {:defaults {:crew "main" :model "gpt"}
                             :crew     {"main" {:model "gpt" :soul "You are Isaac."}}
                             :models   {"gpt"  {:model "gpt-5.4" :provider "openai-chatgpt"}
                                         "grok" {:model "grok-4-1-fast" :provider "grok" :allows-effort false}}})
      (let [provider ((requiring-resolve 'isaac.drive.dispatch/make-provider) "grok" {})]
        (with-redefs [sut/augment-provider (fn [provider _session-key _context-window _model-cfg-overrides]
                                             provider)]
          (let [ctx (#'sut/build-turn-ctx "override-model"
                                          {:comm           :test-comm
                                           :context-window 278528
                                           :model          "grok-4-1-fast"
                                           :model-cfg      {:model "grok-4-1-fast"
                                                            :provider "grok"
                                                            :allows-effort false}
                                           :provider       provider
                                           :soul           "You are Isaac."})]
            (should= nil (:effort ctx))))))))
