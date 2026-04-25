(ns isaac.features.steps.cli
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defthen defwhen helper!]]
    [isaac.features.steps.acp :as acp]
    [isaac.fs :as fs]
    [isaac.home :as home]
    [isaac.cli.chat.toad :as toad]
    [isaac.llm.grover :as grover]
    [isaac.main :as main]
    [isaac.util.shell :as shell]))

(helper! isaac.features.steps.cli)

(defn- interpolate-args [args]
  (cond-> args
          (g/get :server-port) (str/replace "${server.port}" (str (g/get :server-port)))
          true                (str/replace "\\\"" "\"")))

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

(defn- absolute-path [path]
  (if (str/starts-with? path "/")
    path
    (str (System/getProperty "user.dir") "/" path)))

(defn- delete-tree! [path]
  (doseq [child (or (fs/children path) [])]
    (delete-tree! (str path "/" child)))
  (when (fs/exists? path)
    (fs/delete path)))

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

(defn- extract-patterns [table]
  (map (fn [row]
         (-> (if (and (< 1 (count row)) (= "\\" (first row)))
               (str "\\| " (str/join "\\|" (rest row)))
               (first row))
             unescape-expected
             str/trim))
       (:rows table)))

;; region ----- Step bodies -----

(defn isaac-run [args]
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

(defn user-home-directory [path]
  (let [home (if (str/starts-with? path "/")
               path
               (str (System/getProperty "user.dir") "/" path))
        dir  (io/file home)]
    (when (.exists dir)
      (doseq [file (reverse (file-seq dir))]
        (.delete file)))
    (.mkdirs dir)
    (g/assoc! :user-home home)))

(defn stdout-contains [expected]
  (let [expected (unescape-expected expected)
        output   (await-text current-output #(str/includes? % expected))]
    (g/should (str/includes? output expected))))

(defn reply-contains [expected]
  (let [expected (unescape-expected expected)
        output   (await-text current-output #(str/includes? % expected))]
    (g/should (str/includes? output expected))))

(defn stdout-eventually-contains [expected]
  (let [expected (unescape-expected expected)
        output   (await-text current-output #(str/includes? % expected))]
    (g/should (str/includes? output expected))))

(defn stderr-contains [expected]
  (let [expected (unescape-expected expected)
        stderr   (await-text current-stderr #(str/includes? % expected))]
    (g/should (str/includes? stderr expected))))

(defn stderr-does-not-contain [expected]
  (let [stderr   (current-stderr)
        expected (unescape-expected expected)]
    (g/should-not (str/includes? (or stderr "") expected))))

(defn stderr-matches [table]
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

(defn stdout-lines-contain-in-order [table]
  (let [patterns (map #(-> (first %) unescape-expected str/trim) (:rows table))
        output   (await-text current-output #(lines-contain-in-order? % patterns))]
    (g/should (lines-contain-in-order? output patterns))))

(defn stdout-lines-match [table]
  (let [normalize (fn [lines] (mapv #(str/trim (or % "")) lines))
        expected  (normalize (map #(unescape-expected (get (zipmap (:headers table) %) "text")) (:rows table)))
        output    (await-text current-output #(= expected (normalize (str/split-lines (or % "")))))]
    (g/should= expected (normalize (str/split-lines (or output ""))))))

(defn stdout-has-at-least-lines [n]
  (let [output (or (current-output) "")
        n      (if (string? n) (parse-long n) n)]
    (g/should (<= n (count (str/split-lines output))))))

(defn stdout-matches [table]
  (let [output   (or (current-output) "")
        patterns (extract-patterns table)]
    (doseq [pattern patterns]
      (g/should (re-find (re-pattern pattern) output)))))

(defn reply-matches [table]
  (let [output   (or (current-output) "")
        patterns (extract-patterns table)]
    (doseq [pattern patterns]
      (g/should (re-find (re-pattern pattern) output)))))

(defn stdout-does-not-contain [expected]
  (let [output   (current-output)
        expected (unescape-expected expected)]
    (g/should-not (str/includes? (or output "") expected))))

(defn reply-does-not-contain [expected]
  (let [output   (current-output)
        expected (unescape-expected expected)]
    (g/should-not (str/includes? (or output "") expected))))

(defn exit-code-is [code]
  (let [code (if (string? code) (parse-long code) code)]
    (g/should= code (or (await-exit-code) (g/get :exit-code)))))

(defn command-available [cmd]
  (g/assoc! :cmd-stub {cmd true}))

(defn command-not-available [cmd]
  (g/assoc! :cmd-stub {cmd false}))

(defn stdin-is [doc-string]
  (g/assoc! :stdin-content (str/trim doc-string)))

(defn stdin-is-empty []
  (g/assoc! :stdin-content ""))

(defn isaac-home-contains-config [home doc-string]
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

(defn isaac-home-has-no-config [home]
  (g/assoc! :isaac-home (absolute-path home)))

(defn empty-isaac-home [path]
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

(defn isaac-file-contains [path content]
  (let [full-path (str (or (g/get :state-dir) (g/get :isaac-home)) "/" path)
        expected  (str/trim content)]
    (if-let [mem-fs (g/get :mem-fs)]
      (binding [fs/*fs* mem-fs]
        (g/should= expected (fs/slurp full-path)))
      (g/should= expected (slurp full-path)))))

;; endregion ^^^^^ Step bodies ^^^^^

;; region ----- Routing -----

(defwhen "isaac is run with {args:string}" cli/isaac-run
  "Runs 'isaac <args>' in-process (not a subprocess). Parses argv with
   quoted-token handling, binds *in*/*out*/*err* to capture streams,
   applies any cmd-stubs, propagates mem-fs if set, and routes through
   main/*extra-opts* when state-dir / provider-configs / isaac-home are
   in scope. Populates :llm-request (from grover), :output (stdout),
   :stderr, :exit-code for downstream assertions.")

(defgiven "the user home directory is {path:string}" cli/user-home-directory
  "Deletes and recreates the given directory on the real filesystem,
   then binds it as *user-home* for the next 'isaac is run with'. Path
   may be absolute or relative (relative resolves under user.dir).")

(defthen "the stdout contains {expected:string}" cli/stdout-contains
  "Polls up to 1s for captured stdout to contain the given substring.
   Reads :live-output-writer if set (async proxy runs), else :output.")

(defthen "the reply contains {expected:string}" cli/reply-contains
  "Comm-neutral: polls up to 1s for the user-visible reply to contain
   the substring. Same underlying source as 'the stdout contains' today;
   the name distinction is semantic — use 'reply' in comm-agnostic
   scenarios (bridge/session/drive) and 'stdout' in CLI scenarios.")

(defthen "the stdout eventually contains {expected:string}" cli/stdout-eventually-contains)

(defthen "the stderr contains {expected:string}" cli/stderr-contains)

(defthen "the stderr does not contain {expected:string}" cli/stderr-does-not-contain)

(defthen "the stderr matches:" cli/stderr-matches)

(defthen "the stdout lines contain in order:" cli/stdout-lines-contain-in-order)

(defthen "the stdout lines match:" cli/stdout-lines-match)

(defthen "the stdout has at least {int} lines" cli/stdout-has-at-least-lines)

(defthen "the stdout matches:" cli/stdout-matches
  "Each row's 'pattern' cell is compiled as a regex and searched across
   stdout. All rows must match somewhere (order not enforced). Since
   re-find succeeds on any match, multi-line shape isn't verified —
   pair with 'the stdout has at least N lines' when structure matters.")

(defthen "the reply matches:" cli/reply-matches
  "Comm-neutral regex match, same semantics as 'the stdout matches:'.")

(defthen "the stdout does not contain {expected:string}" cli/stdout-does-not-contain)

(defthen "the reply does not contain {expected:string}" cli/reply-does-not-contain)

(defthen "the exit code is {int}" cli/exit-code-is
  "Polls up to 1s for :exit-code to be set (background 'isaac is run'
   futures may not have finished yet).")

(defgiven "the command {cmd:string} is available" cli/command-available
  "Stubs isaac.util.shell/cmd-available? to return true for this command
   for the next 'isaac is run with'. Does not actually install anything —
   purely a test-time override. Only one stub at a time (replaces prior).")

(defgiven "the command {cmd:string} is not available" cli/command-not-available
  "Stubs isaac.util.shell/cmd-available? to return false for this command
   for the next 'isaac is run with'. Pairs with 'command is available'.")

(defgiven "stdin is:" cli/stdin-is
  "Buffers the heredoc content as stdin for the next 'isaac is run with'.
   Without this step, *in* is closed for the run.")

(defgiven "stdin is empty" cli/stdin-is-empty)

(defgiven "isaac home {home:string} contains config:" cli/isaac-home-contains-config
  "Writes the heredoc content as <home>/.isaac/config/isaac.edn. Differs
   from 'the isaac EDN file' steps, which write per-entity files under
   state-dir. This is for the monolithic root config in an isaac-home.")

(defgiven "isaac home {home:string} has no config file" cli/isaac-home-has-no-config)

(defgiven "an empty isaac home at {path:string}" cli/empty-isaac-home
  "Deletes the path, creates <path>/.isaac, and binds both :isaac-home
   and :state-dir for the next 'isaac is run with'. Use when a scenario
   needs a bare home without any config.")

(defthen "the isaac file {path:string} contains:" cli/isaac-file-contains
  "Asserts an exact-match on file content (trimmed). Path is state-dir-
   relative. Pair with 'the isaac EDN file X exists with:' for the write
   side; 'contains:' is for read-side verification.")

;; endregion ^^^^^ Routing ^^^^^
