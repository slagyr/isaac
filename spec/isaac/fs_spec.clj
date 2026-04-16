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

(defn- test-path* [path]
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

  (it "exists? is false for missing paths and true for existing files"
    (should-not (fs/exists? "found.txt"))
    (fs/write-file "found.txt" "yep")
    (should (fs/exists? "found.txt")))

  (it "file? is false for missing paths and directories and true for existing files"
    (should-not (fs/file? "found.txt"))
    (fs/make-dirs "dir")
    (should-not (fs/file? "dir"))
    (fs/write-file "found.txt" "yep")
    (should (fs/file? "found.txt")))

  (it "dir? is false for missing paths and files and true for directories"
    (should-not (fs/dir? "dir"))
    (fs/write-file "found.txt" "yep")
    (should-not (fs/dir? "found.txt"))
    (fs/make-dirs "dir")
    (should (fs/dir? "dir")))

  (it "parent is nil for relative paths without a parent"
    (should-be-nil (fs/parent "found.txt")))

  (it "parent returns the lexical parent for relative directory paths ending with a slash"
    (should= "dir" (fs/parent "dir/subdir/")))

  (it "parent returns the lexical parent directory for nested paths"
    (should= "dir/subdir" (fs/parent "dir/subdir/found.txt")))

  (it "slurp returns nil for missing files"
    (should-be-nil (fs/slurp "missing.txt")))

  (it "slurp reads file contents"
    (fs/write-file "found.txt" "yep")
    (should= "yep" (fs/slurp "found.txt")))

  (it "slurp ignores the :encoding option"
    (fs/write-file "found.txt" "yep")
    (should= "yep" (fs/slurp "found.txt" :encoding :utf-8)))

  (it "spit writes file contents"
    (fs/spit "found.txt" "yep")
    (should= "yep" (fs/read-file "found.txt")))

  (it "spit ignores the :encoding option"
    (fs/spit "found.txt" "yep" :encoding "ISO-8859-1")
    (should= "yep" (fs/read-file "found.txt")))

  (it "spit appends when :append is true"
    (fs/spit "log.txt" "line1\n")
    (fs/spit "log.txt" "line2\n" :append true)
    (should= "line1\nline2\n" (fs/read-file "log.txt")))

  (it "lists files"
    (fs/write-file "dir/b.txt" "b")
    (fs/write-file "dir/a.txt" "a")
    (fs/write-file "other/c.txt" "c")
    (should= ["a.txt" "b.txt"] (fs/list-files "dir")))

  (it "children returns nil for missing paths"
    (should-be-nil (fs/children "missing")))

  (it "children returns nil for files"
    (fs/write-file "found.txt" "yep")
    (should-be-nil (fs/children "found.txt")))

  (it "children returns sorted child names for directories"
    (fs/write-file "dir/b.txt" "b")
    (fs/write-file "dir/a.txt" "a")
    (fs/write-file "other/c.txt" "c")
    (should= ["a.txt" "b.txt"] (fs/children "dir")))

  (it "children includes child directories"
    (fs/make-dirs "dir/subdir")
    (fs/write-file "dir/a.txt" "a")
    (should= ["a.txt" "subdir"] (fs/children "dir")))

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

  (it "exists? is false for missing paths and true for existing files"
    (should-not (fs/exists? (test-path* "found.txt")))
    (fs/write-file (test-path* "found.txt") "yep")
    (should (fs/exists? (test-path* "found.txt"))))

  (it "file? is false for missing paths and directories and true for existing files"
    (should-not (fs/file? (test-path* "found.txt")))
    (fs/make-dirs (test-path* "dir"))
    (should-not (fs/file? (test-path* "dir")))
    (fs/write-file (test-path* "found.txt") "yep")
    (should (fs/file? (test-path* "found.txt"))))

  (it "dir? is false for missing paths and files and true for directories"
    (should-not (fs/dir? (test-path* "dir")))
    (fs/write-file (test-path* "found.txt") "yep")
    (should-not (fs/dir? (test-path* "found.txt")))
    (.mkdirs (io/file (test-path* "dir")))
    (should (fs/dir? (test-path* "dir"))))

  (it "parent returns the lexical parent for single-segment paths under a test root"
    (should= test-path (fs/parent (test-path* "found.txt"))))

  (it "parent returns the lexical parent for directory paths ending with a slash"
    (should= (test-path* "dir")
             (fs/parent (str (test-path* "dir/subdir") "/"))))

  (it "parent returns the lexical parent directory for nested paths"
    (should= (test-path* "dir/subdir")
             (fs/parent (test-path* "dir/subdir/found.txt"))))

  (it "slurp returns nil for missing files"
    (should-be-nil (fs/slurp (test-path* "missing.txt"))))

  (it "slurp reads file contents"
    (fs/write-file (test-path* "found.txt") "yep")
    (should= "yep" (fs/slurp (test-path* "found.txt"))))

  (it "slurp honors the :encoding option"
    (spit (test-path* "latin1.txt") "caf\u00e9" :encoding "ISO-8859-1")
    (should= "caf\u00e9" (fs/slurp (test-path* "latin1.txt") :encoding "ISO-8859-1")))

  (it "spit writes file contents"
    (fs/spit (test-path* "found.txt") "yep")
    (should= "yep" (fs/read-file (test-path* "found.txt"))))

  (it "spit honors the :encoding option"
    (fs/spit (test-path* "latin1.txt") "caf\u00e9" :encoding "ISO-8859-1")
    (should= "caf\u00e9" (clojure.core/slurp (test-path* "latin1.txt") :encoding "ISO-8859-1")))

  (it "spit appends when :append is true"
    (fs/spit (test-path* "log.txt") "line1\n")
    (fs/spit (test-path* "log.txt") "line2\n" :append true)
    (should= "line1\nline2\n" (fs/read-file (test-path* "log.txt"))))

  (it "lists files"
    (fs/write-file (test-path* "dir/b.txt") "b")
    (fs/write-file (test-path* "dir/a.txt") "a")
    (fs/write-file (test-path* "other/c.txt") "c")
    (should= ["a.txt" "b.txt"] (fs/list-files (test-path* "dir"))))

  (it "children returns nil for missing paths"
    (should-be-nil (fs/children (test-path* "missing"))))

  (it "children returns nil for files"
    (fs/write-file (test-path* "found.txt") "yep")
    (should-be-nil (fs/children (test-path* "found.txt"))))

  (it "children returns sorted child names for directories"
    (fs/write-file (test-path* "dir/b.txt") "b")
    (fs/write-file (test-path* "dir/a.txt") "a")
    (fs/write-file (test-path* "other/c.txt") "c")
    (should= ["a.txt" "b.txt"] (fs/children (test-path* "dir"))))

  (it "children includes child directories"
    (.mkdirs (io/file (test-path* "dir/subdir")))
    (fs/write-file (test-path* "dir/a.txt") "a")
    (should= ["a.txt" "subdir"] (fs/children (test-path* "dir"))))

  (it "makes directories"
    (should= true (fs/make-dirs (test-path* "any/path/here"))))

  (it "deletes files"
    (fs/write-file (test-path* "gone.txt") "bye")
    (should (fs/file-exists? (test-path* "gone.txt")))
    (fs/delete-file (test-path* "gone.txt"))
    (should-not (fs/file-exists? (test-path* "gone.txt")))))
