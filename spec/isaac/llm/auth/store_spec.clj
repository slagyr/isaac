(ns isaac.llm.auth.store-spec
  (:require
    [cheshire.core :as json]
    [isaac.llm.auth.store :as sut]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(def ^:dynamic *fs* nil)

(describe "Auth Store"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example] (binding [*fs* (fs/mem-fs)] (example)))

  (describe "save-tokens!"

    (it "creates auth.json with provider tokens"
      (sut/save-tokens! "/auth" "openai" {:access_token  "at-123"
                                           :refresh_token "rt-456"
                                           :expires_in    3600} *fs*)
      (let [saved (json/parse-string (fs/slurp *fs* "/auth/auth.json") true)]
        (should= "oauth" (get-in saved [:openai :type]))
        (should= "at-123" (get-in saved [:openai :access]))
        (should-be-nil (get-in saved [:openai :id-token]))
        (should= "rt-456" (get-in saved [:openai :refresh]))))

    (it "preserves existing provider tokens"
      (sut/save-tokens! "/auth" "anthropic" {:access_token  "ant-111"
                                              :refresh_token "ant-222"
                                              :expires_in    7200} *fs*)
      (sut/save-tokens! "/auth" "openai" {:access_token  "oai-333"
                                           :refresh_token "oai-444"
                                           :expires_in    3600} *fs*)
      (let [saved (json/parse-string (fs/slurp *fs* "/auth/auth.json") true)]
        (should= "ant-111" (get-in saved [:anthropic :access]))
        (should= "oai-333" (get-in saved [:openai :access]))))

    (it "sets expires timestamp from expires_in"
      (let [before (System/currentTimeMillis)]
        (sut/save-tokens! "/auth" "openai" {:access_token  "at-x"
                                             :refresh_token "rt-x"
                                             :expires_in    3600} *fs*)
        (let [saved   (json/parse-string (fs/slurp *fs* "/auth/auth.json") true)
              expires (get-in saved [:openai :expires])]
          (should (>= expires (+ before 3500000)))
          (should (<= expires (+ before 3700000)))))))

  (describe "save-api-key!"

    (it "creates auth.json with provider api key credentials"
      (sut/save-api-key! "/auth" "anthropic" "sk-ant-123" *fs*)
      (let [saved (json/parse-string (fs/slurp *fs* "/auth/auth.json") true)]
        (should= "api-key" (get-in saved [:anthropic :type]))
        (should= "sk-ant-123" (get-in saved [:anthropic :apiKey]))))

    (it "preserves existing oauth credentials"
      (sut/save-tokens! "/auth" "openai" {:access_token  "at-123"
                                           :refresh_token "rt-456"
                                           :expires_in    3600} *fs*)
      (sut/save-api-key! "/auth" "anthropic" "sk-ant-999" *fs*)
      (let [saved (json/parse-string (fs/slurp *fs* "/auth/auth.json") true)]
        (should= "oauth" (get-in saved [:openai :type]))
        (should= "at-123" (get-in saved [:openai :access]))
        (should= "api-key" (get-in saved [:anthropic :type]))
        (should= "sk-ant-999" (get-in saved [:anthropic :apiKey])))))

  (describe "load-tokens"

    (it "returns nil when auth.json does not exist"
      (should-be-nil (sut/load-tokens "/auth" "openai" *fs*)))

    (it "returns nil when provider not in auth.json"
      (sut/save-tokens! "/auth" "anthropic" {:access_token  "ant-111"
                                              :refresh_token "ant-222"
                                              :expires_in    7200} *fs*)
      (should-be-nil (sut/load-tokens "/auth" "openai" *fs*)))

    (it "returns token map for stored provider"
      (sut/save-tokens! "/auth" "openai" {:access_token  "at-abc"
                                            :id_token      "id-ghi"
                                            :refresh_token "rt-def"
                                            :expires_in    3600} *fs*)
      (let [tokens (sut/load-tokens "/auth" "openai" *fs*)]
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
