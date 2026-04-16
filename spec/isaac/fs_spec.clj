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

(describe "memory fs"

  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (it "exists? is false for missing paths and true for existing files"
    (should-not (fs/exists? "found.txt"))
    (fs/spit "found.txt" "yep")
    (should (fs/exists? "found.txt")))

  (it "file? is false for missing paths and directories and true for existing files"
    (should-not (fs/file? "found.txt"))
    (fs/mkdirs "dir")
    (should-not (fs/file? "dir"))
    (fs/spit "found.txt" "yep")
    (should (fs/file? "found.txt")))

  (it "dir? is false for missing paths and files and true for directories"
    (should-not (fs/dir? "dir"))
    (fs/spit "found.txt" "yep")
    (should-not (fs/dir? "found.txt"))
    (fs/mkdirs "dir")
    (should (fs/dir? "dir")))

  (it "parent is nil for relative paths without a parent"
    (should-be-nil (fs/parent "found.txt")))

  (it "parent returns the lexical parent for relative directory paths ending with a slash"
    (should= "dir" (fs/parent "dir/subdir/")))

  (it "parent returns the lexical parent directory for nested paths"
    (should= "dir/subdir" (fs/parent "dir/subdir/found.txt")))

  (it "slurp returns nil for missing files"
    (should-be-nil (fs/slurp "missing.txt")))

  (it "slurp ignores the :encoding option"
    (fs/spit "found.txt" "yep")
    (should= "yep" (fs/slurp "found.txt" :encoding :utf-8)))

  (it "spit ignores the :encoding option"
    (fs/spit "found.txt" "yep" :encoding "ISO-8859-1")
    (should= "yep" (fs/slurp "found.txt")))

  (it "spit appends when :append is true"
    (fs/spit "log.txt" "line1\n")
    (fs/spit "log.txt" "line2\n" :append true)
    (should= "line1\nline2\n" (fs/slurp "log.txt")))

  (it "children returns nil for missing paths"
    (should-be-nil (fs/children "missing")))

  (it "children returns nil for files"
    (fs/spit "found.txt" "yep")
    (should-be-nil (fs/children "found.txt")))

  (it "children returns sorted child names for directories"
    (fs/spit "dir/b.txt" "b")
    (fs/spit "dir/a.txt" "a")
    (fs/spit "other/c.txt" "c")
    (should= ["a.txt" "b.txt"] (fs/children "dir")))

  (it "children includes child directories"
    (fs/mkdirs "dir/subdir")
    (fs/spit "dir/a.txt" "a")
    (should= ["a.txt" "subdir"] (fs/children "dir")))

  (it "mkdirs creates directories"
    (should-be-nil (fs/mkdirs "any/path/here"))
    (should (fs/dir? "any/path/here")))

  (it "delete removes files"
    (fs/spit "gone.txt" "bye")
    (should (fs/exists? "gone.txt"))
    (fs/delete "gone.txt")
    (should-not (fs/exists? "gone.txt"))))

(describe "real fs"

  (before (delete-test-path!))
  (before (io/make-parents (test-path* "keep")))
  (around [it] (binding [fs/*fs* (fs/->RealFs)] (it)))

  (it "exists? is false for missing paths and true for existing files"
    (should-not (fs/exists? (test-path* "found.txt")))
    (fs/spit (test-path* "found.txt") "yep")
    (should (fs/exists? (test-path* "found.txt"))))

  (it "file? is false for missing paths and directories and true for existing files"
    (should-not (fs/file? (test-path* "found.txt")))
    (fs/mkdirs (test-path* "dir"))
    (should-not (fs/file? (test-path* "dir")))
    (fs/spit (test-path* "found.txt") "yep")
    (should (fs/file? (test-path* "found.txt"))))

  (it "dir? is false for missing paths and files and true for directories"
    (should-not (fs/dir? (test-path* "dir")))
    (fs/spit (test-path* "found.txt") "yep")
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

  (it "slurp honors the :encoding option"
    (spit (test-path* "latin1.txt") "caf\u00e9" :encoding "ISO-8859-1")
    (should= "caf\u00e9" (fs/slurp (test-path* "latin1.txt") :encoding "ISO-8859-1")))

  (it "spit honors the :encoding option"
    (fs/spit (test-path* "latin1.txt") "caf\u00e9" :encoding "ISO-8859-1")
    (should= "caf\u00e9" (clojure.core/slurp (test-path* "latin1.txt") :encoding "ISO-8859-1")))

  (it "spit appends when :append is true"
    (fs/spit (test-path* "log.txt") "line1\n")
    (fs/spit (test-path* "log.txt") "line2\n" :append true)
    (should= "line1\nline2\n" (fs/slurp (test-path* "log.txt"))))


  (it "children returns nil for missing paths"
    (should-be-nil (fs/children (test-path* "missing"))))

  (it "children returns nil for files"
    (fs/spit (test-path* "found.txt") "yep")
    (should-be-nil (fs/children (test-path* "found.txt"))))

  (it "children returns sorted child names for directories"
    (fs/spit (test-path* "dir/b.txt") "b")
    (fs/spit (test-path* "dir/a.txt") "a")
    (fs/spit (test-path* "other/c.txt") "c")
    (should= ["a.txt" "b.txt"] (fs/children (test-path* "dir"))))

  (it "children includes child directories"
    (.mkdirs (io/file (test-path* "dir/subdir")))
    (fs/spit (test-path* "dir/a.txt") "a")
    (should= ["a.txt" "subdir"] (fs/children (test-path* "dir"))))

  (it "mkdirs creates directories"
    (should= true (fs/mkdirs (test-path* "any/path/here/file.txt")))
    (should (fs/dir? (test-path* "any/path/here"))))

  (it "delete removes files"
    (fs/spit (test-path* "gone.txt") "bye")
    (should (fs/exists? (test-path* "gone.txt")))
    (fs/delete (test-path* "gone.txt"))
    (should-not (fs/exists? (test-path* "gone.txt")))))
