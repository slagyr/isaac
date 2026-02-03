(ns mdm.isaac.tui.auth-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.tui.auth :as sut]
            [clojure.java.io :as io]))

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
        (should-be-nil (sut/load-token))))))
