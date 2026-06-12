(ns isaac.foundation.fs-steps
  "Foundation-grade filesystem fixture/assertion steps: write a file, assert
   a file exists / doesn't / has content. Path expansion handles ~, <uid>,
   absolute, and root-relative forms. Depends only on foundation namespaces
   (fs, root, nexus, util.shell); moves to the foundation repo at cut time."
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defthen helper!]]
    [isaac.fs :as fs]
    [isaac.root :as root]
    [isaac.nexus :as nexus]
    [isaac.util.shell :as shell]))

(helper! isaac.foundation.fs-steps)

(defn- uid-placeholder []
  (str/trim (:out (shell/sh! "id" "-u"))))

(defn- expand-path [path]
  (cond
    (= "~" path)                  (root/user-home)
    (str/starts-with? path "~/")  (str (root/user-home) (subs path 1))
    (str/starts-with? path "<uid>") (str/replace path "<uid>" (uid-placeholder))
    (str/starts-with? path "/")   path
    :else                         (str (or (g/get :root) (System/getProperty "user.dir")) "/" path)))

(defn- check-file-exists [path]
  (let [expanded (expand-path path)
        fs*      (or (g/get :mem-fs) (nexus/get :fs) (fs/real-fs))]
    (fs/exists? fs* expanded)))

(defn file-with-content [path content]
  (let [expanded (expand-path path)]
    (if-let [mem-fs (g/get :mem-fs)]
      (do
        (fs/mkdirs mem-fs (fs/parent expanded))
        (fs/spit   mem-fs expanded (str/trim content)))
      (do
        (io/make-parents expanded)
        (spit expanded (str/trim content))))))

(defn file-exists [path]
  (g/should (check-file-exists path)))

(defn file-does-not-exist [path]
  (g/should-not (check-file-exists path)))

;; region ----- Routing -----

(defgiven "the file {path:string} contains:" isaac.foundation.fs-steps/file-with-content
  "Writes the heredoc content to the given path (tilde/<uid>-expanded, or
   root-relative) in mem-fs or the real fs.")

(defthen "the file {path:string} exists" isaac.foundation.fs-steps/file-exists
  "Asserts the file at the given path (tilde-expanded) exists in mem-fs or real fs.")

(defthen "the file {path:string} does not exist" isaac.foundation.fs-steps/file-does-not-exist
  "Asserts the file at the given path (tilde-expanded) does not exist.")

;; endregion ^^^^^ Routing ^^^^^
