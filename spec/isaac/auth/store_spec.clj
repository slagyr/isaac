(ns isaac.auth.store-spec
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [isaac.auth.store :as sut]
    [speclj.core :refer :all]))

(describe "Auth Store"

  (with-stubs)
  (with auth-dir "target/test-auth")
  (before (let [d (io/file @auth-dir)]
            (when (.exists d)
              (doseq [f (.listFiles d)] (.delete f))
              (.delete d))))

  (describe "save-tokens!"

    (it "creates auth.json with provider tokens"
      (sut/save-tokens! @auth-dir "openai" {:access_token  "at-123"
                                             :refresh_token "rt-456"
                                             :expires_in    3600})
      (let [saved (json/parse-string (slurp (str @auth-dir "/auth.json")) true)]
        (should= "oauth" (get-in saved [:openai :type]))
        (should= "at-123" (get-in saved [:openai :access]))
        (should-be-nil (get-in saved [:openai :id-token]))
        (should= "rt-456" (get-in saved [:openai :refresh]))))

    (it "preserves existing provider tokens"
      (sut/save-tokens! @auth-dir "anthropic" {:access_token  "ant-111"
                                                :refresh_token "ant-222"
                                                :expires_in    7200})
      (sut/save-tokens! @auth-dir "openai" {:access_token  "oai-333"
                                             :refresh_token "oai-444"
                                             :expires_in    3600})
      (let [saved (json/parse-string (slurp (str @auth-dir "/auth.json")) true)]
        (should= "ant-111" (get-in saved [:anthropic :access]))
        (should= "oai-333" (get-in saved [:openai :access]))))

    (it "sets expires timestamp from expires_in"
      (let [before (System/currentTimeMillis)]
        (sut/save-tokens! @auth-dir "openai" {:access_token  "at-x"
                                               :refresh_token "rt-x"
                                               :expires_in    3600})
        (let [saved   (json/parse-string (slurp (str @auth-dir "/auth.json")) true)
              expires (get-in saved [:openai :expires])]
          (should (>= expires (+ before 3500000)))
          (should (<= expires (+ before 3700000)))))))

  (describe "save-api-key!"

    (it "creates auth.json with provider api key credentials"
      (sut/save-api-key! @auth-dir "anthropic" "sk-ant-123")
      (let [saved (json/parse-string (slurp (str @auth-dir "/auth.json")) true)]
        (should= "api-key" (get-in saved [:anthropic :type]))
        (should= "sk-ant-123" (get-in saved [:anthropic :apiKey]))))

    (it "preserves existing oauth credentials"
      (sut/save-tokens! @auth-dir "openai" {:access_token  "at-123"
                                             :refresh_token "rt-456"
                                             :expires_in    3600})
      (sut/save-api-key! @auth-dir "anthropic" "sk-ant-999")
      (let [saved (json/parse-string (slurp (str @auth-dir "/auth.json")) true)]
        (should= "oauth" (get-in saved [:openai :type]))
        (should= "at-123" (get-in saved [:openai :access]))
        (should= "api-key" (get-in saved [:anthropic :type]))
        (should= "sk-ant-999" (get-in saved [:anthropic :apiKey])))))

  (describe "load-tokens"

    (it "returns nil when auth.json does not exist"
      (should-be-nil (sut/load-tokens @auth-dir "openai")))

    (it "returns nil when provider not in auth.json"
      (sut/save-tokens! @auth-dir "anthropic" {:access_token  "ant-111"
                                                :refresh_token "ant-222"
                                                :expires_in    7200})
      (should-be-nil (sut/load-tokens @auth-dir "openai")))

    (it "returns token map for stored provider"
      (sut/save-tokens! @auth-dir "openai" {:access_token  "at-abc"
                                             :id_token      "id-ghi"
                                             :refresh_token "rt-def"
                                             :expires_in    3600})
      (let [tokens (sut/load-tokens @auth-dir "openai")]
        (should= "oauth" (:type tokens))
        (should= "at-abc" (:access tokens))
        (should= "id-ghi" (:id-token tokens))
        (should= "rt-def" (:refresh tokens)))))

  (describe "token-expired?"

    (it "returns true when expires is in the past"
      (should (sut/token-expired? {:expires (- (System/currentTimeMillis) 1000)})))

    (it "returns false when expires is in the future"
      (should-not (sut/token-expired? {:expires (+ (System/currentTimeMillis) 60000)})))

    (it "returns true when no expires field"
      (should (sut/token-expired? {})))))
