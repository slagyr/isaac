(ns isaac.fs-spec
  (:require
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(declare mem)

(describe "fs/MemFs"
  (with mem (fs/mem-fs))

  (it "reports file does not exist when empty"
    (should-not (fs/file-exists? @mem "foo/bar.txt")))

  (it "writes and reads a file"
    (fs/write-file @mem "a/b.txt" "hello")
    (should= "hello" (fs/read-file @mem "a/b.txt")))

  (it "reports file exists after writing"
    (fs/write-file @mem "a/b.txt" "hello")
    (should (fs/file-exists? @mem "a/b.txt")))

  (it "overwrites on repeated write"
    (fs/write-file @mem "f.txt" "v1")
    (fs/write-file @mem "f.txt" "v2")
    (should= "v2" (fs/read-file @mem "f.txt")))

  (it "appends content to a file"
    (fs/write-file @mem "log.txt" "line1\n")
    (fs/append-file @mem "log.txt" "line2\n")
    (should= "line1\nline2\n" (fs/read-file @mem "log.txt")))

  (it "appends to a file that did not previously exist"
    (fs/append-file @mem "new.txt" "first\n")
    (should= "first\n" (fs/read-file @mem "new.txt")))

  (it "lists files in a directory"
    (fs/write-file @mem "dir/a.txt" "a")
    (fs/write-file @mem "dir/b.txt" "b")
    (fs/write-file @mem "other/c.txt" "c")
    (should= ["a.txt" "b.txt"] (fs/list-files @mem "dir")))

  (it "returns nil for list-files on empty directory"
    (should-be-nil (fs/list-files @mem "empty")))

  (it "does not list nested files in list-files"
    (fs/write-file @mem "dir/a.txt" "a")
    (fs/write-file @mem "dir/sub/b.txt" "b")
    (should= ["a.txt"] (fs/list-files @mem "dir")))

  (it "make-dirs is a no-op"
    (should-be-nil (fs/make-dirs @mem "any/path/here")))

  (it "deletes a file from the store"
    (fs/write-file @mem "del.txt" "bye")
    (fs/delete-file @mem "del.txt")
    (should-not (fs/file-exists? @mem "del.txt")))

  (it "delete-file on nonexistent path is a no-op"
    (should-be-nil (fs/delete-file @mem "ghost.txt"))))

(describe "fs/*fs* dynamic var"
  (it "defaults to RealFs — reads and writes real files"
    (let [path "target/test-fs-real.txt"]
      (fs/write-file fs/*fs* path "real")
      (should= "real" (fs/read-file fs/*fs* path))
      (fs/write-file fs/*fs* path "")))

  (it "can be rebound to MemFs — no disk I/O"
    (binding [fs/*fs* (fs/mem-fs)]
      (fs/write-file fs/*fs* "x.txt" "mem")
      (should= "mem" (fs/read-file fs/*fs* "x.txt"))
      (should-not (fs/file-exists? (fs/->RealFs) "x.txt")))))

(describe "path-first fs wrappers"

  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (it "reads using the dynamically bound filesystem"
    (swap! (.-store fs/*fs*) assoc "a.txt" "hello")
    (should= "hello" (fs/read-file "a.txt")))

  (it "writes using the dynamically bound filesystem"
    (fs/write-file "a.txt" "hello")
    (should= "hello" (fs/read-file "a.txt")))

  (it "appends using the dynamically bound filesystem"
    (fs/write-file "log.txt" "line1\n")
    (fs/append-file "log.txt" "line2\n")
    (should= "line1\nline2\n" (fs/read-file "log.txt")))

  (it "checks existence using the dynamically bound filesystem"
    (should-not (fs/file-exists? "found.txt"))
    (fs/write-file "found.txt" "yep")
    (should (fs/file-exists? "found.txt"))))
