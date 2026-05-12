(ns isaac.drive.dispatch-spec
  (:require
    [isaac.drive.dispatch :as sut]
    [isaac.module.loader :as module-loader]
    [isaac.llm.api.openai-responses :as openai-responses]
    [isaac.llm.api :as api]
    [speclj.core :refer :all]))

(describe "dispatch"

  (after (api/unregister! :test-api))

  (it "activates a module when a provider api is missing from the registry"
    (api/unregister! :test-api)
    (module-loader/clear-activations!)
    (let [p (sut/make-provider "test-provider"
                               {:api          "test-api"
                                :module-index {:isaac.module.provider-test {:manifest {:extends {:llm/api {:test-api {:isaac/factory 'isaac.module.provider-test/make}}}}}}})]
      (should= "test-provider" (api/display-name p))
      (should-not-be-nil (api/factory-for :test-api))))

  (context "normalize-provider defaults"

    (it "merges oauth defaults for real openai-codex provider"
      (let [captured (atom nil)]
        (with-redefs [openai-responses/chat (fn [_req opts]
                                           (reset! captured (:provider-config opts))
                                           {:message {:role "assistant" :content "ok"} :model "m" :usage {} :_headers {}})]
          (sut/dispatch-chat (sut/make-provider "openai-codex" {:auth "oauth-device"})
                             {:model "codex-mini" :messages [{:role "user" :content "hi"}]}))
        (should= "openai-chatgpt" (:name @captured))
        (should= "oauth-device" (:auth @captured))))

    (it "merges oauth defaults for real openai-chatgpt provider"
      (let [captured (atom nil)]
        (with-redefs [openai-responses/chat (fn [_req opts]
                                           (reset! captured (:provider-config opts))
                                           {:message {:role "assistant" :content "ok"} :model "m" :usage {} :_headers {}})]
          (sut/dispatch-chat (sut/make-provider "openai-chatgpt" {})
                             {:model "gpt-5.4" :messages [{:role "user" :content "hi"}]}))
        (should= "openai-chatgpt" (:name @captured))
        (should= "oauth-device" (:auth @captured))))

    (it "allows user config to override defaults for openai-codex"
      (let [captured (atom nil)]
        (with-redefs [openai-responses/chat (fn [_req opts]
                                           (reset! captured (:provider-config opts))
                                           {:message {:role "assistant" :content "ok"} :model "m" :usage {} :_headers {}})]
          (sut/dispatch-chat (sut/make-provider "openai-codex" {:auth "oauth-device" :name "custom-name"})
                             {:model "codex-mini" :messages [{:role "user" :content "hi"}]}))
        (should= "custom-name" (:name @captured))))))
