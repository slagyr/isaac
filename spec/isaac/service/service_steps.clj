(ns isaac.service.service-steps
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.data.xml :as xml]
    [gherclj.core :as g :refer [defgiven defthen helper!]]
    [isaac.fs :as fs]
    [isaac.home :as home]
    [isaac.system :as system]
    [isaac.util.shell :as shell]))

(helper! isaac.service.service-steps)

(defn- uid-placeholder []
  (str/trim (:out (shell/sh! "id" "-u"))))

(defn- expand-path [path]
  (cond
    (= "~" path)                  (home/user-home)
    (str/starts-with? path "~/")  (str (home/user-home) (subs path 1))
    (str/starts-with? path "<uid>") (str/replace path "<uid>" (uid-placeholder))
    :else                          path))


(defn- check-file-exists [path]
  (let [expanded (expand-path path)
        fs*      (or (g/get :mem-fs) (system/get :fs) (fs/real-fs))]
    (fs/exists? fs* expanded)))

;; region ----- Plist parsing -----

(declare plist-dict)

(defn- elem-nodes [content]
  (filter map? content))

(defn- plist-value [node]
  (case (:tag node)
    :string  (first (:content node))
    :integer (parse-long (first (:content node)))
    :true    true
    :false   false
    :array   (mapv plist-value (elem-nodes (:content node)))
    :dict    (plist-dict (elem-nodes (:content node)))
    nil))

(defn plist-dict [content]
  (loop [remaining (elem-nodes content) m {}]
    (if (< (count remaining) 2)
      m
      (let [k (first (:content (first remaining)))
            v (plist-value (second remaining))]
        (recur (drop 2 remaining) (assoc m k v))))))

(defn- parse-plist [xml-str]
  (let [clean (str/replace xml-str #"<!DOCTYPE[^>]*>" "")
        root  (xml/parse (java.io.ByteArrayInputStream. (.getBytes clean)))
        dict  (first (elem-nodes (:content root)))]
    (plist-dict (:content dict))))

(defn- plist-get [pmap path]
  (if-let [[_ key idx-str] (re-matches #"(.+)\[(\d+)\]" path)]
    (nth (get pmap key) (parse-long idx-str) nil)
    (get pmap path)))

;; endregion

(defn- sh-fn []
  (fn [& args]
    (let [argv (vec args)]
      (g/update! :sh-calls #(conj (or % []) argv))
      (when (= "launchctl" (first argv))
        (g/update! :launchctl-calls #(conj (or % []) argv)))
      (cond
        (and (= "launchctl" (first argv)) (= "print" (second argv)))
        {:exit 0 :out (or (g/get :launchctl-print-output) "") :err ""}

        (= "which" (first argv))
        (if-let [bin (g/get :which-results)]
          (let [cmd (second argv)]
            (if-let [path (get bin cmd)]
              {:exit 0 :out (str path "\n") :err ""}
              {:exit 1 :out "" :err ""}))
          {:exit 1 :out "" :err ""})

        (= "id" (first argv))
        {:exit 0 :out "501\n" :err ""}

        :else
        {:exit 0 :out "" :err ""}))))

;; region ----- Step bodies -----

(defn launchctl-stubbed []
  (g/assoc! :launchctl-calls [])
  (g/assoc! :sh-calls [])
  (g/assoc! :sh-fn (sh-fn)))

(defn launchctl-print-returns [doc-string]
  (g/assoc! :launchctl-print-output (str/trim doc-string))
  (when-not (g/get :sh-fn)
    (g/assoc! :sh-fn (sh-fn))))

(defn launchctl-was-called-with [expected]
  (let [calls   (or (g/get :launchctl-calls) [])
        norm    (fn [s] (-> s
                            (str/replace "<uid>" "501")
                            (str/replace "~" (home/user-home))
                            str/trim))
        pattern (norm expected)]
    (g/should (some (fn [call]
                      (let [call-str (str/join " " call)]
                        (str/includes? call-str pattern)))
                    calls))))

(defn operating-system-is [os]
  (g/assoc! :os-name os))

(defn bb-resolves-to [cmd path]
  (g/assoc! :which-results {cmd path})
  (when-not (g/get :sh-fn)
    (g/assoc! :sh-fn (sh-fn))))

(defn bb-not-on-path [_cmd]
  (g/assoc! :which-results {})
  (when-not (g/get :sh-fn)
    (g/assoc! :sh-fn (sh-fn))))

(defn file-with-content [path content]
  (let [expanded (expand-path path)]
    (if-let [mem-fs (g/get :mem-fs)]
      (do
        (fs/mkdirs mem-fs (fs/parent expanded))
        (fs/spit   mem-fs expanded (str/trim content)))
      (do
        (clojure.java.io/make-parents expanded)
        (spit expanded (str/trim content))))))

(defn file-exists [path]
  (g/should (check-file-exists path)))

(defn file-does-not-exist [path]
  (g/should-not (check-file-exists path)))

(defn sh-was-called-with [expected]
  (let [calls   (or (g/get :sh-calls) [])
        pattern (str/trim expected)]
    (g/should (some (fn [call]
                      (str/includes? (str/join " " call) pattern))
                    calls))))

(defn plist-contains [table]
  (let [plist-path (expand-path "~/Library/LaunchAgents/com.slagyr.isaac.plist")
        content    (if-let [mem-fs (g/get :mem-fs)]
                     (fs/slurp mem-fs plist-path)
                     (slurp plist-path))
        pmap       (parse-plist content)]
    (doseq [row (:rows table)]
      (let [[path expected] row
            actual          (plist-get pmap path)]
        (g/should= expected (str actual))))))

;; endregion

;; region ----- Routing -----

(defgiven "launchctl is stubbed" isaac.service.service-steps/launchctl-stubbed
  "Installs a shell/*sh* stub that captures launchctl invocations in :launchctl-calls.")

(defgiven "launchctl print returns:" isaac.service.service-steps/launchctl-print-returns
  "Configures canned output for 'launchctl print ...' calls.")

(defthen "launchctl was called with {expected:string}" isaac.service.service-steps/launchctl-was-called-with
  "Asserts at least one captured launchctl invocation contains the expected arguments.")

(defgiven "the operating system is {os:string}" isaac.service.service-steps/operating-system-is
  "Stubs shell/os-name to return the given OS string for the next isaac run.")

(defgiven "{cmd:string} resolves to {path:string}" isaac.service.service-steps/bb-resolves-to
  "Stubs 'which <cmd>' to return the given path.")

(defgiven "{cmd:string} is not on PATH" isaac.service.service-steps/bb-not-on-path
  "Stubs 'which <cmd>' to fail (not found).")

(defgiven "the file {path:string} contains:" isaac.service.service-steps/file-with-content
  "Writes heredoc content to the given path (tilde-expanded) in mem-fs or real fs.")

(defthen "the file {path:string} exists" isaac.service.service-steps/file-exists
  "Asserts the file at the given path (tilde-expanded) exists in mem-fs or real fs.")

(defthen "the file {path:string} does not exist" isaac.service.service-steps/file-does-not-exist
  "Asserts the file at the given path (tilde-expanded) does not exist.")

(defthen "the plist contains:" isaac.service.service-steps/plist-contains
  "Parses the installed plist and asserts path|value table rows match.")

(defthen "sh was called with {expected:string}" isaac.service.service-steps/sh-was-called-with
  "Asserts at least one captured shell invocation (any command) contains the expected arguments.")

;; endregion
