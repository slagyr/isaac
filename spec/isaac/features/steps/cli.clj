(ns isaac.features.steps.cli
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defthen defwhen]]
    [isaac.features.steps.acp :as acp]
    [isaac.cli.chat.toad :as toad]
    [isaac.main :as main]
    [isaac.util.shell :as shell]))

(defn- interpolate-args [args]
  (cond-> args
          (g/get :server-port) (str/replace "${server.port}" (str (g/get :server-port)))))

(defwhen isaac-run "isaac is run with {args:string}"
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
        agents           (g/get :agents)
        models           (g/get :models)
        provider-configs (g/get :provider-configs)
        state-dir        (g/get :state-dir)
        isaac-home       (g/get :isaac-home)
        loopback?        (= "loopback" (get-in (g/get :server-config) [:acp :proxy-transport]))
        _                (when (and loopback? (nil? (g/get :acp-remote-connection-factory)))
                           (acp/ensure-loopback-proxy!))
        extra-opts       (cond-> (cond
                                   isaac-home {:home isaac-home}
                                   (and agents models) {:agents agents :models models}
                                   :else {})
                                 state-dir (assoc :state-dir state-dir)
                                 (and (not isaac-home) provider-configs) (assoc :provider-configs provider-configs)
                                 (g/get :acp-remote-connection-factory) (assoc :ws-connection-factory (g/get :acp-remote-connection-factory)))
        stdin-content    (g/get :stdin-content)
        run-final        (fn []
                           (if (seq extra-opts)
                             (binding [main/*extra-opts* extra-opts]
                               (run-with-stubs))
                             (run-with-stubs)))
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
    (g/assoc! :output (str output-writer))
    (g/assoc! :stderr (str error-writer))))

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
            (Thread/sleep 10)
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
              (Thread/sleep 10)
              (recur))
            text))))))

(defthen output-contains "the output contains {expected:string}"
  [expected]
  (let [expected (unescape-expected expected)
        output   (await-text current-output #(str/includes? % expected))]
    (g/should (str/includes? output expected))))

(defthen stderr-contains "the stderr contains {expected:string}"
  [expected]
  (let [expected (unescape-expected expected)
        stderr   (await-text current-stderr #(str/includes? % expected))]
    (g/should (str/includes? stderr expected))))

(defthen output-lines-contain-in-order "the output lines contain in order:"
  [table]
  (let [patterns (map #(-> (first %) unescape-expected str/trim) (:rows table))
        output   (await-text current-output
                             (fn [text]
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
                                 (not= missing matched))))
        lines    (str/split-lines (or output ""))
        missing  ::missing
        matched  (reduce (fn [line-idx pattern]
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
    (g/should (not= missing matched))))

(defthen output-matches "the output matches:"
  [table]
  (let [output   (or (current-output) "")
        patterns (map #(str/trim (first %)) (:rows table))]
    (doseq [pattern patterns]
      (g/should (re-find (re-pattern pattern) output)))))

(defthen output-does-not-contain "the output does not contain {expected:string}"
  [expected]
  (let [output   (current-output)
        expected (unescape-expected expected)]
    (g/should-not (str/includes? (or output "") expected))))

(defthen exit-code-is "the exit code is {int}"
  [code]
  (let [code (if (string? code) (parse-long code) code)]
    (g/should= code (or (await-exit-code) (g/get :exit-code)))))

(defgiven command-available "the command {cmd:string} is available"
  [cmd]
  (g/assoc! :cmd-stub {cmd true}))

(defgiven command-not-available "the command {cmd:string} is not available"
  [cmd]
  (g/assoc! :cmd-stub {cmd false}))

(defgiven stdin-is "stdin is:"
  [doc-string]
  (g/assoc! :stdin-content (str/trim doc-string)))

(defgiven stdin-is-empty "stdin is empty"
  []
  (g/assoc! :stdin-content ""))

(defgiven isaac-home-contains-config "isaac home {home:string} contains config:"
  [home doc-string]
  (let [config-dir (str home "/.isaac")]
    (.mkdirs (io/file config-dir))
    (spit (str config-dir "/isaac.json") (str/trim doc-string)))
  (g/assoc! :isaac-home home))

(defgiven isaac-home-has-no-config "isaac home {home:string} has no config file"
  [home]
  (g/assoc! :isaac-home home))
