(ns isaac.auth.oauth-spec
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [isaac.auth.oauth :as sut]
    [speclj.core :refer :all]))

(def test-dir "target/test-oauth")

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(defn- write-credentials! [path creds]
  (io/make-parents path)
  (spit path (json/generate-string {:claudeAiOauth creds})))

(describe "OAuth"

  (before-all (clean-dir! test-dir))
  (after (clean-dir! test-dir))

  (describe "read-credentials"

    (it "reads from credentials file"
      (let [path  (str test-dir "/.credentials.json")
            creds {:accessToken "test-token" :refreshToken "refresh" :expiresAt 9999999999999}]
        (write-credentials! path creds)
        (let [result (sut/read-credentials {:path path})]
          (should= "test-token" (:accessToken result)))))

    (it "returns nil when no file and keychain skipped"
      (should-be-nil (sut/read-credentials {:path          (str test-dir "/nonexistent.json")
                                            :skip-keychain true})))

    (it "falls back to macOS Keychain when no file"
      (let [result (sut/read-credentials {:path (str test-dir "/nonexistent.json")})]
        (should (or (nil? result) (contains? result :accessToken)))))

    (it "returns all credential fields"
      (let [path  (str test-dir "/.credentials.json")
            creds {:accessToken "tok" :refreshToken "ref" :expiresAt 123}]
        (write-credentials! path creds)
        (let [result (sut/read-credentials {:path path})]
          (should= "tok" (:accessToken result))
          (should= "ref" (:refreshToken result))
          (should= 123 (:expiresAt result)))))

    (it "returns nil when file has no claudeAiOauth key"
      (let [path (str test-dir "/empty-creds.json")]
        (io/make-parents path)
        (spit path (json/generate-string {:otherKey "value"}))
        (should-be-nil (sut/read-credentials {:path path :skip-keychain true}))))

    (it "returns nil when claudeAiOauth is null"
      (let [path (str test-dir "/null-creds.json")]
        (io/make-parents path)
        (spit path (json/generate-string {:claudeAiOauth nil}))
        (should-be-nil (sut/read-credentials {:path path :skip-keychain true})))))

  (describe "resolve-token"

    (it "resolves a valid non-expired token"
      (let [path  (str test-dir "/.credentials.json")
            creds {:accessToken "valid-token" :refreshToken "refresh" :expiresAt 9999999999999}]
        (write-credentials! path creds)
        (let [result (sut/resolve-token {:path path})]
          (should= "valid-token" (:accessToken result)))))

    (it "returns error when no credentials found"
      (let [result (sut/resolve-token {:path (str test-dir "/nonexistent.json")
                                       :skip-keychain true})]
        (should= :no-oauth-credentials (:error result))))

    (it "detects expired token and attempts refresh"
      (let [path  (str test-dir "/.credentials.json")
            creds {:accessToken "expired" :refreshToken "refresh" :expiresAt 1000000000000}]
        (write-credentials! path creds)
        (let [result (sut/resolve-token {:path path})]
          (should (or (:accessToken result)
                      (= :oauth-refresh-failed (:error result)))))))

    (it "returns error message with no-oauth-credentials"
      (let [result (sut/resolve-token {:path (str test-dir "/nope.json")
                                       :skip-keychain true})]
        (should= :no-oauth-credentials (:error result))
        (should (string? (:message result)))))

    (it "passes opts through to read-credentials"
      (let [path  (str test-dir "/opts-test.json")
            creds {:accessToken "via-opts" :refreshToken "ref" :expiresAt 9999999999999}]
        (write-credentials! path creds)
        (let [result (sut/resolve-token {:path path :skip-keychain true})]
          (should= "via-opts" (:accessToken result)))))))
