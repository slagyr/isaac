(ns isaac.drive.dispatch-spec
  (:require
    [isaac.drive.dispatch :as sut]
    [isaac.module.loader :as module-loader]
    [isaac.llm.api.responses :as responses]
    [isaac.llm.api :as api]
    [isaac.llm.providers :as providers]
    [speclj.core :refer :all]))

(describe "dispatch"

  (after (api/unregister! :test-api))

  (it "activates a module when a provider api is missing from the registry"
    (api/unregister! :test-api)
    (module-loader/clear-activations!)
    (let [p (sut/make-provider "test-provider"
                               {:api          "test-api"
                                :module-index {:isaac.module.provider-test {:manifest {:llm/api {:test-api {:factory 'isaac.module.provider-test/make}}}}}})]
      (should= "test-provider" (api/display-name p))
      (should-not-be-nil (api/factory-for :test-api))))

  (context "unknown provider"

    (it "emits :unknown-provider error for an unrecognized provider name"
      (let [p   (sut/make-provider "totally-bogus" {})
            res (api/chat p {})]
        (should= :unknown-provider (:error res))
        (should (clojure.string/includes? (:message res) "unknown provider \"totally-bogus\""))
        (should (clojure.string/includes? (:message res) "configured:"))
        (should (clojure.string/includes? (:message res) "known templates:"))))

    (it "includes a did-you-mean suggestion when the name is close to a known provider"
      (let [p   (sut/make-provider "ollam" {})
            res (api/chat p {})]
        (should= :unknown-provider (:error res))
        (should (clojure.string/includes? (:message res) "did you mean \"ollama\""))))

    (it "omits did-you-mean when no close match exists"
      (let [p   (sut/make-provider "zzzzzzz" {})
            res (api/chat p {})]
        (should= :unknown-provider (:error res))
        (should-not (clojure.string/includes? (:message res) "did you mean"))))

    (it "lists known providers from the manifest"
      (let [p         (sut/make-provider "totally-bogus" {})
            res       (api/chat p {})
            known-set (providers/known-providers)]
        (doseq [provider-name known-set]
          (should (clojure.string/includes? (:message res) provider-name))))))

  (context "normalize-provider defaults"

    (it "merges oauth defaults for real chatgpt provider"
      (let [captured     (atom nil)
            provider-cfg (providers/lookup {:providers {:chatgpt {}}} nil "chatgpt")]
        (with-redefs [responses/chat (fn [_req opts]
                                            (reset! captured (:provider-config opts))
                                            {:message {:role "assistant" :content "ok"} :model "m" :usage {} :_headers {}})]
          (sut/dispatch-chat (sut/make-provider "chatgpt" provider-cfg)
                             {:model "gpt-5.4" :messages [{:role "user" :content "hi"}]}))
        (should= "oauth-device" (:auth @captured))
        (should= "https://chatgpt.com/backend-api/codex" (:base-url @captured))))

    (it "allows user config to override defaults for chatgpt"
      (let [captured     (atom nil)
            provider-cfg (providers/lookup {:providers {:chatgpt {:name "custom-name"}}} nil "chatgpt")]
        (with-redefs [responses/chat (fn [_req opts]
                                            (reset! captured (:provider-config opts))
                                            {:message {:role "assistant" :content "ok"} :model "m" :usage {} :_headers {}})]
          (sut/dispatch-chat (sut/make-provider "chatgpt" provider-cfg)
                             {:model "gpt-5.4" :messages [{:role "user" :content "hi"}]}))
        (should= "custom-name" (:name @captured))))))
