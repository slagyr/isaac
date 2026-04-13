(ns isaac.features.steps.cli
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.main :as main]
    [isaac.util.shell :as shell]
    [isaac.session.storage :as storage]))

(defwhen isaac-run "isaac is run with {args:string}"
  [args]
  (let [argv           (if (str/blank? args)
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
        api-key-login? (and (= "auth" (first argv))
                            (= "login" (second argv))
                            (some #(= "--api-key" %) argv))
        cmd-stub       (g/get :cmd-stub)
        run!           (fn []
                         (let [code (main/run argv)]
                           (g/assoc! :exit-code code)))
        run-with-stubs (fn []
                         (if api-key-login?
                           (with-redefs [read-line (fn [] "sk-test-key")]
                             (run!))
                           (run!)))
         agents         (g/get :agents)
          models         (g/get :models)
          provider-configs (g/get :provider-configs)
          state-dir      (g/get :state-dir)
          isaac-home     (g/get :isaac-home)
         extra-opts     (cond-> (cond
                                  isaac-home {:home isaac-home}
                                  (and agents models) {:agents agents :models models}
                                  :else {})
                          state-dir (assoc :state-dir state-dir)
                          (and (not isaac-home) provider-configs) (assoc :provider-configs provider-configs)
                          (g/get :acp-remote-connection-factory) (assoc :ws-connection-factory (g/get :acp-remote-connection-factory)))
         stdin-content  (g/get :stdin-content)
         run-final      (fn []
                          (if (seq extra-opts)
                            (binding [main/*extra-opts* extra-opts]
                              (run-with-stubs))
                            (run-with-stubs)))
        run-with-stdin (fn []
                         (if stdin-content
                           (binding [*in* (java.io.BufferedReader. (java.io.StringReader. stdin-content))]
                             (run-final))
                           (run-final)))
        output-writer  (java.io.StringWriter.)
        error-writer   (java.io.StringWriter.)]
    (binding [*out* output-writer
              *err* error-writer]
      (if cmd-stub
        (with-redefs [shell/cmd-available? (fn [cmd] (get cmd-stub cmd false))]
          (run-with-stdin))
        (run-with-stdin)))
    (g/assoc! :output (str output-writer))
    (g/assoc! :stderr (str error-writer))))

(defn- unescape-expected [expected]
  (-> expected
      (str/replace "\\\"" "\"")
      (str/replace "\\n" "\n")))

(defthen output-contains "the output contains {expected:string}"
  [expected]
  (let [output   (g/get :output)
        expected (unescape-expected expected)]
    (g/should (str/includes? output expected))))

(defthen stderr-contains "the stderr contains {expected:string}"
  [expected]
  (let [stderr   (g/get :stderr)
        expected (unescape-expected expected)]
    (g/should (str/includes? stderr expected))))

(defthen output-lines-contain-in-order "the output lines contain in order:"
  [table]
  (let [lines    (str/split-lines (or (g/get :output) ""))
        patterns (map #(-> (first %) unescape-expected str/trim) (:rows table))
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
  (let [output   (or (g/get :output) "")
        patterns (map #(str/trim (first %)) (:rows table))]
    (doseq [pattern patterns]
      (g/should (re-find (re-pattern (java.util.regex.Pattern/quote pattern)) output)))))

(defthen output-does-not-contain "the output does not contain {expected:string}"
  [expected]
  (let [output   (g/get :output)
        expected (unescape-expected expected)]
    (g/should-not (str/includes? (or output "") expected))))

(defthen exit-code-is "the exit code is {int}"
  [code]
  (let [code (if (string? code) (parse-long code) code)]
    (g/should= code (g/get :exit-code))))

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
