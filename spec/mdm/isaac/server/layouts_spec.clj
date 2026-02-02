(ns mdm.isaac.server.layouts-spec
  (:require [c3kit.wire.api :as api]
            [c3kit.wire.flash :as flash]
            [mdm.isaac.config :as config]
            [mdm.isaac.robots :as robots]
            [mdm.isaac.server.layouts :as sut]
            [speclj.core :refer :all]
            [speclj.stub :as stub]))

(describe "layouts"
  (with-stubs)
  (robots/with-kinds :user)

  (it "web-rich-client content"
    (let [response (sut/web-rich-client {})]
      (should= 200 (:status response))
      (should-contain "<div id=\"app-root\">Your page is loading...</div>" (:body response))))

  (it "rich client handler includes flash messages"
    (with-redefs [sut/rich-client (stub :layout/rich-client)]
      (sut/web-rich-client (flash/warn {:flash {:foo :bar}} "Hello"))
      (should-have-invoked :layout/rich-client)
      (should= "Hello" (-> :layout/rich-client stub/last-invocation-of first :flash first :text))))

  (it "static"
    (let [response (sut/static "hello")
          body     (:body response)]
      (should= 200 (:status response))
      (should-contain "app-root" body)
      (should-contain "<header" body)
      (should-contain "hello" body)
      (should-contain "<footer" body)
      (should-contain "© 2026 Micah Martin" body)))

  (it "not-found"
    (let [response (sut/not-found)]
      (should= 404 (:status response))
      (should-contain "not-found" (:body response))
      (should-contain "Not Found" (:body response))))

  (context "rich client payload"

    (it "rich client payload config"
      (let [{:keys [airworthy-root environment host api-version ws-csrf-token anti-forgery-token]}
            (->> {:jwt/payload {:client-id "abc123"}}
                 sut/build-rich-client-payload
                 :config)]
        (should= "abc123" anti-forgery-token)
        (should= "abc123" ws-csrf-token)
        (should= (api/version) api-version)
        (should= "development" environment)
        (should= (-> config/active :cleancoders-auth :url-root) airworthy-root)
        (should= config/host host)))

    (it "apple client-id"
      (with-redefs [config/active {:apple-auth {:client-id "apple-client-id"}}]
        (let [payload (sut/build-rich-client-payload {})]
          (should= "apple-client-id" (-> payload :config :apple-client-id)))))

    (it "google client-id"
      (with-redefs [config/active {:google-oauth {:client-id "google-client-id"}}]
        (let [payload (sut/build-rich-client-payload {})]
          (should= "google-client-id" (-> payload :config :google-client-id)))))
    )

  (context "google config"

    (it "does not auto-prompt"
      (let [options (sut/google-onload-options nil)]
        (should= (get-in config/active [:google-oauth :client-id]) (:data-client_id options))
        (should= (str config/host "/signin/google-oauth") (:data-login_uri options))
        (should= "signin" (:data-context options))
        (should= "redirect" (:data-ux_mode options))
        (should= "true" (:data-auto_prompt options))
        (should= "false" (:data-auto_select options))))

    #_(it "auto-prompts"
        (let [options (sut/google-onload-options {:prompt-google-signin? true})]
          (should= "true" (:data-auto_prompt options))))

    )
  )
