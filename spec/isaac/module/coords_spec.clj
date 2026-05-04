(ns isaac.module.coords-spec
  (:require
    [clojure.java.io :as io]
    [isaac.fs :as fs]
    [isaac.module.coords :as sut]
    [speclj.core :refer :all]))

(def test-root (str (System/getProperty "user.dir") "/target/module-coords-spec"))

(defn- delete-test-root! []
  (let [root (io/file test-root)]
    (when (.exists root)
      (doseq [file (reverse (file-seq root))]
        (.delete file)))))

(defn- path* [path]
  (str test-root "/" path))

(describe "module coords"

  (describe "valid-id?"

    (it "accepts keywords whose names are safe filesystem segments"
      (should (sut/valid-id? :module/pigeon))
      (should (sut/valid-id? :module/pigeon-v2.thing_1)))

    (it "rejects non-keywords and bad filesystem characters"
      (should-not (sut/valid-id? "pigeon"))
      (should-not (sut/valid-id? :module/pigeon?))))

  (describe "candidates"

    (it "returns the third-party and built-in paths in search order"
      (should= ["/state/.isaac/modules/pigeon" "/workspace/modules/pigeon"]
               (sut/candidates {:cwd "/workspace" :state-dir "/state/.isaac"} :module/pigeon)))

    (it "uses the keyword name as the filesystem segment"
      (should= ["/state/.isaac/modules/pigeon" "/workspace/modules/pigeon"]
               (sut/candidates {:cwd "/workspace" :state-dir "/state/.isaac"} :isaac.comm/pigeon)))

    (it "omits the third-party candidate when state-dir is nil"
      (should= ["/workspace/modules/pigeon"]
               (sut/candidates {:cwd "/workspace" :state-dir nil} :module/pigeon)))

    (it "fails fast for invalid ids with structured data"
      (let [error (try
                    (sut/candidates {:cwd "/workspace" :state-dir "/state/.isaac"} :module/pigeon?)
                    (catch clojure.lang.ExceptionInfo e
                      e))]
        (should= "invalid module id: :module/pigeon?" (.getMessage error))
        (should= {:type    :module/invalid-id
                  :id      :module/pigeon?
                  :segment "pigeon?"}
                 (select-keys (ex-data error) [:type :id :segment])))))

  (describe "resolve"

    (describe "memory fs"

      (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

      (it "returns the built-in module directory when it is the only match"
        (fs/mkdirs "/workspace/modules/pigeon")
        (should= "/workspace/modules/pigeon"
                 (sut/resolve {:cwd "/workspace" :state-dir "/state/.isaac"} :module/pigeon)))

      (it "prefers the third-party module over the built-in one"
        (fs/mkdirs "/workspace/modules/pigeon")
        (fs/mkdirs "/state/.isaac/modules/pigeon")
        (should= "/state/.isaac/modules/pigeon"
                 (sut/resolve {:cwd "/workspace" :state-dir "/state/.isaac"} :module/pigeon)))

      (it "returns nil when neither candidate exists"
        (should-be-nil
          (sut/resolve {:cwd "/workspace" :state-dir "/state/.isaac"} :module/pigeon)))

      (it "ignores matching files and keeps searching for a directory"
        (fs/spit "/state/.isaac/modules/pigeon" "not-a-dir")
        (fs/mkdirs "/workspace/modules/pigeon")
        (should= "/workspace/modules/pigeon"
                 (sut/resolve {:cwd "/workspace" :state-dir "/state/.isaac"} :module/pigeon))))

    (describe "real fs"

      (before (delete-test-root!))
      (around [it] (binding [fs/*fs* (fs/real-fs)] (it)))

      (it "resolves module directories on disk"
        (fs/mkdirs (path* "workspace/modules/pigeon"))
        (should= (path* "workspace/modules/pigeon")
                 (sut/resolve {:cwd (path* "workspace") :state-dir (path* "state/.isaac")}
                              :module/pigeon))))))
