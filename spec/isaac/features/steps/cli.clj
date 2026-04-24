(ns isaac.features.steps.cli
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defthen defwhen]]
    [isaac.features.steps.acp :as acp]
    [isaac.fs :as fs]
    [isaac.home :as home]
    [isaac.cli.chat.toad :as toad]
    [isaac.llm.grover :as grover]
    [isaac.main :as main]
    [isaac.util.shell :as shell]))

(defn- interpolate-args [args]
  (cond-> args
          (g/get :server-port) (str/replace "${server.port}" (str (g/get :server-port)))
          true                (str/replace "\\\"" "\"")))

(defwhen isaac-run "isaac is run with {args:string}"
  "Runs 'isaac <args>' in-process (not a subprocess). Parses argv with
   quoted-token handling, binds *in*/*out*/*err* to capture streams,
   applies any cmd-stubs, propagates mem-fs if set, and routes through
   main/*extra-opts* when state-dir / provider-configs / isaac-home are
   in scope. Populates :llm-request (from grover), :output (stdout),
   :stderr, :exit-code for downstream assertions."
  [args]
  (let [args             (interpolate-args args)
        argv             (if (str/blank? args)
                           []
                           (loop [s (str/trim args) tokens []]
                             (if (str/blank? s)
                               tokens
                               (cond
                                 (str/starts-with? s "'")
                                 (let [end (str/index-of s "'" 1)]
                                   (if end
                                     (recur (str/trim (subs s (inc end))) (conj tokens (subs s 1 end)))
                                     (conj tokens (subs s 1))))
                                 (str/starts-with? s "\"")
                                 (let [end (str/index-of s "\"" 1)]
                                   (if end
                                     (recur (str/trim (subs s (inc end))) (conj tokens (subs s 1 end)))
                                     (conj tokens (subs s 1))))
                                 :else
                                 (let [[tok rest-s] (str/split s #"\s+" 2)]
                                   (recur (or rest-s "") (conj tokens tok)))))))
        api-key-login?   (and (= "auth" (first argv))
                              (= "login" (second argv))
                              (some #(= "--api-key" %) argv))
        cmd-stub         (g/get :cmd-stub)
        run!             (fn []
                           (let [code (main/run argv)]
                             (g/assoc! :exit-code code)))
        run-with-stubs   (fn []
                           (if api-key-login?
                             (with-redefs [read-line (fn [] "sk-test-key")]
                               (run!))
                             (run!)))
        provider-configs (g/get :provider-configs)
        state-dir        (g/get :state-dir)
        isaac-home       (g/get :isaac-home)
        loopback?        (= "loopback" (get-in (g/get :server-config) [:acp :proxy-transport]))
        _                (when (and loopback? (nil? (g/get :acp-remote-connection-factory)))
                           (acp/ensure-loopback-proxy!))
        extra-opts       (cond-> (cond
                                   isaac-home {:home isaac-home}
                                   :else {})
                                  state-dir (assoc :state-dir state-dir)
                                  (and (not isaac-home) provider-configs) (assoc :provider-configs provider-configs)
                                  (g/get :acp-remote-connection-factory) (assoc :ws-connection-factory (g/get :acp-remote-connection-factory)))
        stdin-content    (g/get :stdin-content)
        run-final        (fn []
                            (let [run* #(binding [home/*user-home* (or (g/get :user-home) home/*user-home*)]
                                           (if (seq extra-opts)
                                             (binding [main/*extra-opts* extra-opts]
                                               (run-with-stubs))
                                             (run-with-stubs)))]
                              (if-let [mem-fs (g/get :mem-fs)]
                                (binding [fs/*fs* mem-fs]
                                  (run*))
                                (run*))))
        run-with-stdin   (fn []
                           (if stdin-content
                             (binding [*in* (java.io.BufferedReader. (java.io.StringReader. stdin-content))]
                               (run-final))
                             (run-final)))
        output-writer    (java.io.StringWriter.)
        error-writer     (java.io.StringWriter.)]
    (binding [*out* output-writer
              *err* error-writer]
      (if cmd-stub
        (with-redefs [shell/cmd-available? (fn [cmd] (get cmd-stub cmd false))
                       toad/spawn-toad!     (fn [& _] 0)]
          (run-with-stdin))
        (run-with-stdin)))
    (g/assoc! :llm-request (grover/last-request))
    (g/assoc! :output (str output-writer))
    (g/assoc! :stderr (str error-writer))))

(defgiven user-home-directory "the user home directory is {path:string}"
  "Deletes and recreates the given directory on the real filesystem,
   then binds it as *user-home* for the next 'isaac is run with'. Path
   may be absolute or relative (relative resolves under user.dir)."
  [path]
  (let [home (if (str/starts-with? path "/")
               path
               (str (System/getProperty "user.dir") "/" path))
        dir  (io/file home)]
    (when (.exists dir)
      (doseq [file (reverse (file-seq dir))]
        (.delete file)))
    (.mkdirs dir)
    (g/assoc! :user-home home)))

(defn- unescape-expected [expected]
  (-> expected
      (str/replace "\\\"" "\"")
      (str/replace "\\n" "\n")))

(defn- current-output []
  (if-let [writer (g/get :live-output-writer)]
    (str writer)
    (g/get :output)))

(defn- current-stderr []
  (if-let [writer (g/get :live-error-writer)]
    (str writer)
    (g/get :stderr)))

(defn- await-exit-code []
  (let [deadline (+ (System/currentTimeMillis) 1000)]
    (loop []
      (if-let [exit-code (g/get :exit-code)]
        exit-code
        (if (< (System/currentTimeMillis) deadline)
          (do
            (Thread/sleep 1)
            (recur))
          nil)))))

(defn- await-text [read-text pred]
  (let [deadline (+ (System/currentTimeMillis) 1000)]
    (loop []
      (let [text (or (read-text) "")]
        (if (pred text)
          text
          (if (< (System/currentTimeMillis) deadline)
            (do
              (Thread/sleep 1)
              (recur))
            text))))))

(defthen stdout-contains "the stdout contains {expected:string}"
  "Polls up to 1s for captured stdout to contain the given substring.
   Reads :live-output-writer if set (async proxy runs), else :output."
  [expected]
  (let [expected (unescape-expected expected)
        output   (await-text current-output #(str/includes? % expected))]
    (g/should (str/includes? output expected))))

(defthen reply-contains "the reply contains {expected:string}"
  "Comm-neutral: polls up to 1s for the user-visible reply to contain
   the substring. Same underlying source as 'the stdout contains' today;
   the name distinction is semantic — use 'reply' in comm-agnostic
   scenarios (bridge/session/drive) and 'stdout' in CLI scenarios."
  [expected]
  (let [expected (unescape-expected expected)
        output   (await-text current-output #(str/includes? % expected))]
    (g/should (str/includes? output expected))))

(defthen stdout-eventually-contains "the stdout eventually contains {expected:string}"
  [expected]
  (let [expected (unescape-expected expected)
        output   (await-text current-output #(str/includes? % expected))]
    (g/should (str/includes? output expected))))

(defthen reply-eventually-contains "the reply eventually contains {expected:string}"
  [expected]
  (let [expected (unescape-expected expected)
        output   (await-text current-output #(str/includes? % expected))]
    (g/should (str/includes? output expected))))

(defthen stderr-contains "the stderr contains {expected:string}"
  [expected]
  (let [expected (unescape-expected expected)
        stderr   (await-text current-stderr #(str/includes? % expected))]
    (g/should (str/includes? stderr expected))))

(defthen stderr-does-not-contain "the stderr does not contain {expected:string}"
  [expected]
  (let [stderr   (current-stderr)
        expected (unescape-expected expected)]
    (g/should-not (str/includes? (or stderr "") expected))))

(defthen stderr-matches "the stderr matches:"
  [table]
  (let [stderr   (or (current-stderr) "")
        patterns (map (fn [row]
                        (-> (if (and (< 1 (count row)) (= "\\" (first row)))
                              (str "\\| " (str/join "\\|" (rest row)))
                              (first row))
                            unescape-expected
                            str/trim))
                      (:rows table))]
    (doseq [pattern patterns]
      (g/should (re-find (re-pattern pattern) stderr)))))

(defn- lines-contain-in-order? [text patterns]
  (let [lines   (str/split-lines (or text ""))
        missing ::missing
        matched (reduce (fn [line-idx pattern]
                          (if (= missing line-idx)
                            missing
                            (or (first (keep-indexed (fn [idx line]
                                                       (when (and (> idx line-idx)
                                                                  (str/includes? line pattern))
                                                         idx))
                                                     lines))
                                missing)))
                        -1
                        patterns)]
    (not= missing matched)))

(defthen stdout-lines-contain-in-order "the stdout lines contain in order:"
  [table]
  (let [patterns (map #(-> (first %) unescape-expected str/trim) (:rows table))
        output   (await-text current-output #(lines-contain-in-order? % patterns))]
    (g/should (lines-contain-in-order? output patterns))))

(defthen reply-lines-contain-in-order "the reply lines contain in order:"
  [table]
  (let [patterns (map #(-> (first %) unescape-expected str/trim) (:rows table))
        output   (await-text current-output #(lines-contain-in-order? % patterns))]
    (g/should (lines-contain-in-order? output patterns))))

(defthen stdout-lines-match "the stdout lines match:"
  [table]
  (let [normalize (fn [lines] (mapv #(str/trim (or % "")) lines))
        expected  (normalize (map #(unescape-expected (get (zipmap (:headers table) %) "text")) (:rows table)))
        output    (await-text current-output #(= expected (normalize (str/split-lines (or % "")))))]
    (g/should= expected (normalize (str/split-lines (or output ""))))))

(defthen reply-lines-match "the reply lines match:"
  [table]
  (let [normalize (fn [lines] (mapv #(str/trim (or % "")) lines))
        expected  (normalize (map #(unescape-expected (get (zipmap (:headers table) %) "text")) (:rows table)))
        output    (await-text current-output #(= expected (normalize (str/split-lines (or % "")))))]
    (g/should= expected (normalize (str/split-lines (or output ""))))))

(defthen stdout-has-at-least-lines "the stdout has at least {int} lines"
  [n]
  (let [output (or (current-output) "")
        n      (if (string? n) (parse-long n) n)]
    (g/should (<= n (count (str/split-lines output))))))

(defthen reply-has-at-least-lines "the reply has at least {int} lines"
  [n]
  (let [output (or (current-output) "")
        n      (if (string? n) (parse-long n) n)]
    (g/should (<= n (count (str/split-lines output))))))

(defn- matches-patterns? [text patterns]
  (every? #(re-find (re-pattern %) text) patterns))

(defn- extract-patterns [table]
  (map (fn [row]
         (-> (if (and (< 1 (count row)) (= "\\" (first row)))
               (str "\\| " (str/join "\\|" (rest row)))
               (first row))
             unescape-expected
             str/trim))
       (:rows table)))

(defthen stdout-matches "the stdout matches:"
  "Each row's 'pattern' cell is compiled as a regex and searched across
   stdout. All rows must match somewhere (order not enforced). Since
   re-find succeeds on any match, multi-line shape isn't verified —
   pair with 'the stdout has at least N lines' when structure matters."
  [table]
  (let [output   (or (current-output) "")
        patterns (extract-patterns table)]
    (doseq [pattern patterns]
      (g/should (re-find (re-pattern pattern) output)))))

(defthen reply-matches "the reply matches:"
  "Comm-neutral regex match, same semantics as 'the stdout matches:'."
  [table]
  (let [output   (or (current-output) "")
        patterns (extract-patterns table)]
    (doseq [pattern patterns]
      (g/should (re-find (re-pattern pattern) output)))))

(defthen stdout-does-not-contain "the stdout does not contain {expected:string}"
  [expected]
  (let [output   (current-output)
        expected (unescape-expected expected)]
    (g/should-not (str/includes? (or output "") expected))))

(defthen reply-does-not-contain "the reply does not contain {expected:string}"
  [expected]
  (let [output   (current-output)
        expected (unescape-expected expected)]
    (g/should-not (str/includes? (or output "") expected))))

(defthen exit-code-is "the exit code is {int}"
  "Polls up to 1s for :exit-code to be set (background 'isaac is run'
   futures may not have finished yet)."
  [code]
  (let [code (if (string? code) (parse-long code) code)]
    (g/should= code (or (await-exit-code) (g/get :exit-code)))))

(defgiven command-available "the command {cmd:string} is available"
  "Stubs isaac.util.shell/cmd-available? to return true for this command
   for the next 'isaac is run with'. Does not actually install anything —
   purely a test-time override. Only one stub at a time (replaces prior)."
  [cmd]
  (g/assoc! :cmd-stub {cmd true}))

(defgiven command-not-available "the command {cmd:string} is not available"
  "Stubs isaac.util.shell/cmd-available? to return false for this command
   for the next 'isaac is run with'. Pairs with 'command is available'."
  [cmd]
  (g/assoc! :cmd-stub {cmd false}))

(defgiven stdin-is "stdin is:"
  "Buffers the heredoc content as stdin for the next 'isaac is run with'.
   Without this step, *in* is closed for the run."
  [doc-string]
  (g/assoc! :stdin-content (str/trim doc-string)))

(defgiven stdin-is-empty "stdin is empty"
  []
  (g/assoc! :stdin-content ""))

(defn- absolute-path [path]
  (if (str/starts-with? path "/")
    path
    (str (System/getProperty "user.dir") "/" path)))

(defn- delete-tree! [path]
  (doseq [child (or (fs/children path) [])]
    (delete-tree! (str path "/" child)))
  (when (fs/exists? path)
    (fs/delete path)))

(defgiven isaac-home-contains-config "isaac home {home:string} contains config:"
  "Writes the heredoc content as <home>/.isaac/config/isaac.edn. Differs
   from 'the isaac EDN file' steps, which write per-entity files under
   state-dir. This is for the monolithic root config in an isaac-home."
  [home doc-string]
  (let [abs-home   (absolute-path home)
        config-dir (str abs-home "/.isaac/config")
        config-file (str config-dir "/isaac.edn")
        mem-fs     (g/get :mem-fs)]
    (if mem-fs
      (binding [fs/*fs* mem-fs]
        (fs/mkdirs config-dir)
        (fs/spit config-file (str/trim doc-string)))
      (do (.mkdirs (io/file config-dir))
          (spit config-file (str/trim doc-string)))))
  (g/assoc! :isaac-home (absolute-path home)))

(defgiven isaac-home-has-no-config "isaac home {home:string} has no config file"
  [home]
  (g/assoc! :isaac-home (absolute-path home)))

(defgiven empty-isaac-home "an empty isaac home at {path:string}"
  "Deletes the path, creates <path>/.isaac, and binds both :isaac-home
   and :state-dir for the next 'isaac is run with'. Use when a scenario
   needs a bare home without any config."
  [path]
  (let [home      (absolute-path path)
        state-dir (str home "/.isaac")]
    (if-let [mem-fs (g/get :mem-fs)]
      (binding [fs/*fs* mem-fs]
        (delete-tree! home)
        (fs/mkdirs state-dir))
      (do
        (delete-tree! home)
        (.mkdirs (io/file state-dir))))
    (g/assoc! :isaac-home home)
    (g/assoc! :state-dir state-dir)))

(defthen isaac-file-exists "the isaac file {path:string} exists"
  "Checks for file existence under state-dir (or isaac-home as fallback).
   Path is state-dir-relative, e.g. 'config/crew/main.edn'."
  [path]
  (let [full-path (str (or (g/get :state-dir) (g/get :isaac-home)) "/" path)]
    (if-let [mem-fs (g/get :mem-fs)]
      (binding [fs/*fs* mem-fs]
        (g/should (fs/exists? full-path)))
      (g/should (.exists (io/file full-path))))))

(defthen isaac-file-contains "the isaac file {path:string} contains:"
  "Asserts an exact-match on file content (trimmed). Path is state-dir-
   relative. Pair with 'the isaac EDN file X exists with:' for the write
   side; 'contains:' is for read-side verification."
  [path content]
  (let [full-path (str (or (g/get :state-dir) (g/get :isaac-home)) "/" path)
        expected  (str/trim content)]
    (if-let [mem-fs (g/get :mem-fs)]
      (binding [fs/*fs* mem-fs]
        (g/should= expected (fs/slurp full-path)))
      (g/should= expected (slurp full-path)))))
