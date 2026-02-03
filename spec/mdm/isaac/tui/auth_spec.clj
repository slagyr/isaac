(ns mdm.isaac.tui.auth-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.tui.auth :as sut]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [java.util Base64]))

(describe "TUI Auth"

  (describe "token persistence"

    (with-stubs)
    (with test-dir (io/file (System/getProperty "java.io.tmpdir") (str "isaac-test-" (System/currentTimeMillis))))

    (before
      (.mkdirs @test-dir))

    (after
      (doseq [f (reverse (file-seq @test-dir))]
        (.delete f)))

    (it "returns nil when no token file exists"
      (with-redefs [sut/isaac-dir (constantly (str @test-dir))]
        (should-be-nil (sut/load-token))))

    (it "saves and loads token"
      (with-redefs [sut/isaac-dir (constantly (str @test-dir))]
        (sut/save-token! "my-jwt-token")
        (should= "my-jwt-token" (sut/load-token))))

    (it "creates isaac directory if it doesn't exist"
      (let [new-dir (io/file @test-dir "subdir")]
        (with-redefs [sut/isaac-dir (constantly (str new-dir))]
          (sut/save-token! "test-token")
          (should (.exists new-dir)))))

    (it "deletes token file"
      (with-redefs [sut/isaac-dir (constantly (str @test-dir))]
        (sut/save-token! "my-jwt-token")
        (sut/delete-token!)
        (should-be-nil (sut/load-token)))))

  (describe "token expiration"

    (with make-jwt (fn [payload]
                     (let [header (-> {:alg "HS256" :typ "JWT"}
                                      json/write-str
                                      (.getBytes "UTF-8")
                                      (->> (.encodeToString (Base64/getUrlEncoder))))
                           body (-> payload
                                    json/write-str
                                    (.getBytes "UTF-8")
                                    (->> (.encodeToString (Base64/getUrlEncoder))))
                           sig "fake-signature"]
                       (str header "." body "." sig))))

    (it "returns false for expired token"
      (let [expired-token (@make-jwt {:exp (- (quot (System/currentTimeMillis) 1000) 3600)})]
        (should= false (sut/token-valid? expired-token))))

    (it "returns true for valid token"
      (let [valid-token (@make-jwt {:exp (+ (quot (System/currentTimeMillis) 1000) 3600)})]
        (should= true (sut/token-valid? valid-token))))

    (it "returns false for malformed token"
      (should= false (sut/token-valid? "not-a-jwt")))

    (it "returns false for nil token"
      (should= false (sut/token-valid? nil)))

    (it "extracts client-id from token"
      (let [token (@make-jwt {:client-id "abc-123" :exp (+ (quot (System/currentTimeMillis) 1000) 3600)})]
        (should= "abc-123" (sut/client-id token))))

    (it "returns nil for token without client-id"
      (let [token (@make-jwt {:exp (+ (quot (System/currentTimeMillis) 1000) 3600)})]
        (should-be-nil (sut/client-id token))))))
