(ns isaac.fs-spec
  (:require
    [clojure.java.io :as io]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(def test-path (str (System/getProperty "user.dir") "/target/test-fs-real"))

(defn- delete-test-path! []
  (let [root (io/file test-path)]
    (when (.exists root)
      (doseq [file (reverse (file-seq root))]
        (.delete file)))))

(defn- test-path* [path]
  (str test-path "/" path))

(describe "path validation"

  (it "spit rejects relative paths"
    (should-throw IllegalArgumentException "Relative path not allowed: foo.txt"
      (fs/spit "foo.txt" "content")))

  (it "slurp rejects relative paths"
    (should-throw IllegalArgumentException "Relative path not allowed: foo.txt"
      (fs/slurp "foo.txt")))

  (it "exists? rejects relative paths"
    (should-throw IllegalArgumentException "Relative path not allowed: foo.txt"
      (fs/exists? "foo.txt")))

  (it "mkdirs rejects relative paths"
    (should-throw IllegalArgumentException "Relative path not allowed: foo.txt"
      (fs/mkdirs "foo.txt")))

  (it "delete rejects relative paths"
    (should-throw IllegalArgumentException "Relative path not allowed: foo.txt"
      (fs/delete "foo.txt")))

  (it "children rejects relative paths"
    (should-throw IllegalArgumentException "Relative path not allowed: dir"
      (fs/children "dir")))

  (it "file? rejects relative paths"
    (should-throw IllegalArgumentException "Relative path not allowed: foo.txt"
      (fs/file? "foo.txt")))

  (it "dir? rejects relative paths"
    (should-throw IllegalArgumentException "Relative path not allowed: dir"
      (fs/dir? "dir")))

  (it "rejects tilde paths"
    (should-throw IllegalArgumentException "Relative path not allowed: ~/documents/file.txt"
      (fs/spit "~/documents/file.txt" "content")))

  (it "allows absolute paths"
    (binding [fs/*fs* (fs/mem-fs)]
      (fs/spit "/tmp/test.txt" "ok")
      (should= "ok" (fs/slurp "/tmp/test.txt")))))

(describe "memory fs"

  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (it "exists? is false for missing paths and true for existing files"
    (should-not (fs/exists? "/mem/found.txt"))
    (fs/spit "/mem/found.txt" "yep")
    (should (fs/exists? "/mem/found.txt")))

  (it "file? is false for missing paths and directories and true for existing files"
    (should-not (fs/file? "/mem/found.txt"))
    (fs/mkdirs "/mem/dir")
    (should-not (fs/file? "/mem/dir"))
    (fs/spit "/mem/found.txt" "yep")
    (should (fs/file? "/mem/found.txt")))

  (it "dir? is false for missing paths and files and true for directories"
    (should-not (fs/dir? "/mem/dir"))
    (fs/spit "/mem/found.txt" "yep")
    (should-not (fs/dir? "/mem/found.txt"))
    (fs/mkdirs "/mem/dir")
    (should (fs/dir? "/mem/dir")))

  (it "parent returns nil for root"
    (should-be-nil (fs/parent "/")))

  (it "parent returns the lexical parent for directory paths ending with a slash"
    (should= "/mem/dir" (fs/parent "/mem/dir/subdir/")))

  (it "parent returns the lexical parent directory for nested paths"
    (should= "/mem/dir/subdir" (fs/parent "/mem/dir/subdir/found.txt")))

  (it "slurp returns nil for missing files"
    (should-be-nil (fs/slurp "/mem/missing.txt")))

  (it "slurp ignores the :encoding option"
    (fs/spit "/mem/found.txt" "yep")
    (should= "yep" (fs/slurp "/mem/found.txt" :encoding :utf-8)))

  (it "spit ignores the :encoding option"
    (fs/spit "/mem/found.txt" "yep" :encoding "ISO-8859-1")
    (should= "yep" (fs/slurp "/mem/found.txt")))

  (it "spit appends when :append is true"
    (fs/spit "/mem/log.txt" "line1\n")
    (fs/spit "/mem/log.txt" "line2\n" :append true)
    (should= "line1\nline2\n" (fs/slurp "/mem/log.txt")))

  (it "children returns nil for missing paths"
    (should-be-nil (fs/children "/mem/missing")))

  (it "children returns nil for files"
    (fs/spit "/mem/found.txt" "yep")
    (should-be-nil (fs/children "/mem/found.txt")))

  (it "children returns sorted child names for directories"
    (fs/spit "/mem/dir/b.txt" "b")
    (fs/spit "/mem/dir/a.txt" "a")
    (fs/spit "/mem/other/c.txt" "c")
    (should= ["a.txt" "b.txt"] (fs/children "/mem/dir")))

  (it "children includes child directories"
    (fs/mkdirs "/mem/dir/subdir")
    (fs/spit "/mem/dir/a.txt" "a")
    (should= ["a.txt" "subdir"] (fs/children "/mem/dir")))

  (it "mkdirs creates directories"
    (should-be-nil (fs/mkdirs "/mem/any/path/here"))
    (should (fs/dir? "/mem/any/path/here")))

  (it "cache-token advances on writes"
    (let [before (fs/cache-token)]
      (fs/spit "/mem/log.txt" "line1")
      (should (< before (fs/cache-token)))
      (let [after-write (fs/cache-token)]
        (fs/delete "/mem/log.txt")
        (should (< after-write (fs/cache-token))))))

  (it "delete removes files"
    (fs/spit "/mem/gone.txt" "bye")
    (should (fs/exists? "/mem/gone.txt"))
    (fs/delete "/mem/gone.txt")
    (should-not (fs/exists? "/mem/gone.txt"))))

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
    (fs/mkdirs (test-path* "dir"))
    (fs/mkdirs (test-path* "other"))
    (fs/spit (test-path* "dir/b.txt") "b")
    (fs/spit (test-path* "dir/a.txt") "a")
    (fs/spit (test-path* "other/c.txt") "c")
    (should= ["a.txt" "b.txt"] (fs/children (test-path* "dir"))))

  (it "children includes child directories"
    (fs/mkdirs (test-path* "dir/subdir"))
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
