(ns isaac.drive.dispatch-spec
  (:require
    [isaac.drive.dispatch :as sut]
    [isaac.module.loader :as module-loader]
    [isaac.llm.openai-compat :as openai-compat]
    [isaac.provider :as provider]
    [speclj.core :refer :all]))

(describe "dispatch"

  (after (provider/unregister! "test-api"))

  (it "activates a module when a provider api is missing from the registry"
    (provider/unregister! "test-api")
    (module-loader/clear-activations!)
    (let [p (sut/make-provider "test-provider"
                               {:api          "test-api"
                                :module-index {:isaac.module.provider-test {:manifest {:entry   'isaac.module.provider-test
                                                                                      :extends {:provider {:test-api {}}}}}}})]
      (should= "test-provider" (provider/display-name p))
      (should-not-be-nil (provider/factory-for "test-api"))))

  (context "normalize-provider defaults"

    (it "merges oauth defaults for real openai-codex provider"
      (let [captured (atom nil)]
        (with-redefs [openai-compat/chat (fn [_req opts]
                                           (reset! captured (:provider-config opts))
                                           {:message {:role "assistant" :content "ok"} :model "m" :usage {} :_headers {}})]
          (sut/dispatch-chat (sut/make-provider "openai-codex" {:auth "oauth-device"})
                             {:model "codex-mini" :messages [{:role "user" :content "hi"}]}))
        (should= "openai-chatgpt" (:name @captured))
        (should= "oauth-device" (:auth @captured))))

    (it "merges oauth defaults for real openai-chatgpt provider"
      (let [captured (atom nil)]
        (with-redefs [openai-compat/chat (fn [_req opts]
                                           (reset! captured (:provider-config opts))
                                           {:message {:role "assistant" :content "ok"} :model "m" :usage {} :_headers {}})]
          (sut/dispatch-chat (sut/make-provider "openai-chatgpt" {})
                             {:model "gpt-5.4" :messages [{:role "user" :content "hi"}]}))
        (should= "openai-chatgpt" (:name @captured))
        (should= "oauth-device" (:auth @captured))))

    (it "allows user config to override defaults for openai-codex"
      (let [captured (atom nil)]
        (with-redefs [openai-compat/chat (fn [_req opts]
                                           (reset! captured (:provider-config opts))
                                           {:message {:role "assistant" :content "ok"} :model "m" :usage {} :_headers {}})]
          (sut/dispatch-chat (sut/make-provider "openai-codex" {:auth "oauth-device" :name "custom-name"})
                             {:model "codex-mini" :messages [{:role "user" :content "hi"}]}))
        (should= "custom-name" (:name @captured))))))
