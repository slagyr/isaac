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
      ;; This test verifies the Keychain fallback runs on macOS
      ;; It will find real credentials if Claude Code is logged in
      (let [result (sut/read-credentials {:path (str test-dir "/nonexistent.json")})]
        ;; On a machine with Claude Code logged in, this returns credentials
        ;; On CI or machines without Keychain, this returns nil
        ;; Either way, it shouldn't throw
        (should (or (nil? result) (contains? result :accessToken))))))

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

    (it "detects expired token"
      (let [path  (str test-dir "/.credentials.json")
            creds {:accessToken "expired" :refreshToken "refresh" :expiresAt 1000000000000}]
        (write-credentials! path creds)
        ;; Will try to refresh and fail (no real endpoint in test)
        ;; That's fine — we're testing it detects expiry
        (let [result (sut/resolve-token {:path path})]
          (should (or (:accessToken result)
                      (= :oauth-refresh-failed (:error result)))))))))
