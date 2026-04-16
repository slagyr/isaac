(ns isaac.fs-spec
  (:require
    [clojure.java.io :as io]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(def test-path "target/test-fs-real")

(defn- delete-test-path! []
  (let [root (io/file test-path)]
    (when (.exists root)
      (doseq [file (reverse (file-seq root))]
        (.delete file)))))

(defn- rm atest-path* [path]
  (str test-path "/" path))

(describe "fs/*fs* dynamic var"
  (it "defaults to a filesystem that writes to disk"
    (let [path (str test-path "/default.txt")]
      (fs/write-file path "real")
      (should= "real" (fs/read-file fs/*fs* path))))

  (it "can be rebound to MemFs — no disk I/O"
    (binding [fs/*fs* (fs/mem-fs)]
      (fs/write-file fs/*fs* "x.txt" "mem")
      (should= "mem" (fs/read-file fs/*fs* "x.txt"))
      (should-not (fs/file-exists? (fs/->RealFs) "x.txt")))))

(describe "memory fs"

  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (it "reads"
    (swap! (.-store fs/*fs*) assoc "a.txt" "hello")
    (should= "hello" (fs/read-file "a.txt")))

  (it "writes"
    (fs/write-file "a.txt" "hello")
    (should= "hello" (fs/read-file "a.txt")))

  (it "appends"
    (fs/write-file "log.txt" "line1\n")
    (fs/append-file "log.txt" "line2\n")
    (should= "line1\nline2\n" (fs/read-file "log.txt")))

  (it "checks existence"
    (should-not (fs/file-exists? "found.txt"))
    (fs/write-file "found.txt" "yep")
    (should (fs/file-exists? "found.txt")))

  (it "lists files"
    (fs/write-file "dir/b.txt" "b")
    (fs/write-file "dir/a.txt" "a")
    (fs/write-file "other/c.txt" "c")
    (should= ["a.txt" "b.txt"] (fs/list-files "dir")))

  (it "makes directories"
    (should-be-nil (fs/make-dirs "any/path/here")))

  (it "deletes files"
    (fs/write-file "gone.txt" "bye")
    (should (fs/file-exists? "gone.txt"))
    (fs/delete-file "gone.txt")
    (should-not (fs/file-exists? "gone.txt"))))

(describe "real fs"

  (before (delete-test-path!))
  (before (io/make-parents (test-path* "keep")))
  (around [it] (binding [fs/*fs* (fs/->RealFs)] (it)))

  (it "reads"
    (spit (test-path* "a.txt") "hello")
    (should= "hello" (fs/read-file (test-path* "a.txt"))))

  (it "writes"
    (fs/write-file (test-path* "a.txt") "hello")
    (should= "hello" (fs/read-file (test-path* "a.txt"))))

  (it "appends"
    (fs/write-file (test-path* "log.txt") "line1\n")
    (fs/append-file (test-path* "log.txt") "line2\n")
    (should= "line1\nline2\n" (fs/read-file (test-path* "log.txt"))))

  (it "checks existence"
    (should-not (fs/file-exists? (test-path* "found.txt")))
    (fs/write-file (test-path* "found.txt") "yep")
    (should (fs/file-exists? (test-path* "found.txt"))))

  (it "lists files"
    (fs/write-file (test-path* "dir/b.txt") "b")
    (fs/write-file (test-path* "dir/a.txt") "a")
    (fs/write-file (test-path* "other/c.txt") "c")
    (should= ["a.txt" "b.txt"] (fs/list-files (test-path* "dir"))))

  (it "makes directories"
    (should= true (fs/make-dirs (test-path* "any/path/here"))))

  (it "deletes files"
    (fs/write-file (test-path* "gone.txt") "bye")
    (should (fs/file-exists? (test-path* "gone.txt")))
    (fs/delete-file (test-path* "gone.txt"))
    (should-not (fs/file-exists? (test-path* "gone.txt")))))
