(ns isaac.session.cli-spec
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.cli :as registry]
    [isaac.config.loader :as config]
    [isaac.bridge :as bridge]
    [isaac.session.cli :as sessions]
    [isaac.session.context :as session-ctx]
    [isaac.session.store :as store]
    [isaac.spec-helper :as helper]
    [speclj.core :refer :all]))

(defn- delete-dir! [path]
  (let [f (io/file path)]
    (when (.exists f)
      (doseq [file (reverse (file-seq f))]
        (.delete file)))))

(declare state-dir)

(describe "sessions/parse-iso-age-ms"
  (it "parses ISO timestamp and returns age in ms"
    (let [ts "2026-04-12T15:00:00"
          ms (sessions/age-ms ts)]
      (should-not-be-nil ms)
      (should (> ms 0))))

  (it "returns nil for nil input"
    (should-be-nil (sessions/age-ms nil)))

  (it "returns nil for invalid input"
    (should-be-nil (sessions/age-ms "not-a-date"))))

(describe "sessions/format-age"
  (it "formats seconds as 'Xs'"
    (should= "30s" (sessions/format-age 30000)))

  (it "formats minutes as 'Xm'"
    (should= "5m" (sessions/format-age (* 5 60 1000))))

  (it "formats hours as 'Xh'"
    (should= "2h" (sessions/format-age (* 2 60 60 1000))))

  (it "formats days as 'Xd'"
    (should= "3d" (sessions/format-age (* 3 24 60 60 1000)))))

(describe "sessions/format-context"
  (it "formats tokens and context window with percentage"
    (should= "5,000 / 32,768 (15%)" (sessions/format-context 5000 32768)))

  (it "handles zero tokens"
    (should= "0 / 32,768 (0%)" (sessions/format-context 0 32768)))

  (it "handles nil tokens as zero"
    (should= "0 / 32,768 (0%)" (sessions/format-context nil 32768))))

(describe "sessions/list-all"
  (with-all state-dir (str (System/getProperty "user.dir") "/target/test-state/sessions-spec"))

  (before-all
    (delete-dir! @state-dir)
    (helper/create-session! @state-dir "abc")
    (helper/update-session! @state-dir "abc"
                            {:total-tokens 5000 :updated-at "2026-04-12T15:00:00"})
    (helper/create-session! @state-dir "ghi" {:crew "ketch"})
    (helper/update-session! @state-dir "ghi"
                            {:total-tokens 12000 :updated-at "2026-04-11T10:00:00"}))

  (it "returns a map of crew-id to sessions list"
    (let [result (sessions/list-all @state-dir nil)]
      (should (contains? result "main"))
      (should (contains? result "ketch"))))

  (it "each crew entry is a non-empty seq of sessions"
    (let [result (sessions/list-all @state-dir nil)]
      (should (seq (get result "main")))
      (should (seq (get result "ketch")))))

  (it "filters to one crew when crew-filter is provided"
    (let [result (sessions/list-all @state-dir "ketch")]
      (should (contains? result "ketch"))
      (should-not (contains? result "main")))))

(describe "sessions/run"
  (with-all state-dir (str (System/getProperty "user.dir") "/target/test-state/sessions-run-spec"))

  (before-all
    (delete-dir! @state-dir)
    (helper/create-session! @state-dir "abc")
    (helper/update-session! @state-dir "abc"
                            {:total-tokens 5000 :updated-at "2026-04-12T15:00:00"}))

  (it "outputs crew header"
    (let [output (with-out-str (sessions/run {:state-dir @state-dir}))]
      (should (str/includes? output "crew: main"))))

  (it "outputs session key"
    (let [output (with-out-str (sessions/run {:state-dir @state-dir}))]
      (should (str/includes? output "abc"))))

  (it "returns exit code 0"
    (let [result (atom nil)]
      (with-out-str (reset! result (sessions/run {:state-dir @state-dir})))
      (should= 0 @result)))

  (it "returns exit code 0 when no sessions exist"
    (let [empty-dir (str (System/getProperty "user.dir") "/target/test-state/sessions-empty")
          _         (delete-dir! empty-dir)
          result    (atom nil)]
      (with-out-str (reset! result (sessions/run {:state-dir empty-dir})))
      (should= 0 @result)))

  (it "prints 'no sessions' when no sessions exist"
    (let [empty-dir (str (System/getProperty "user.dir") "/target/test-state/sessions-empty2")
          _         (delete-dir! empty-dir)
          output    (with-out-str (sessions/run {:state-dir empty-dir}))]
      (should (str/includes? output "no sessions"))))

  (it "returns exit code 1 for unknown crew"
    (let [result     (atom nil)
          err-writer (java.io.StringWriter.)]
      (binding [*err* (java.io.PrintWriter. err-writer)]
        (with-out-str
          (reset! result (sessions/run {:state-dir @state-dir :crew "nonexistent"}))))
      (should= 1 @result)))

  (it "prints error to stderr for unknown crew"
    (let [err-writer (java.io.StringWriter.)]
      (binding [*err* (java.io.PrintWriter. err-writer)]
        (with-out-str
          (sessions/run {:state-dir @state-dir :crew "nonexistent"})))
      (let [stderr (str err-writer)]
        (should (str/includes? stderr "unknown crew"))
        (should (str/includes? stderr "nonexistent")))))

  (it "shows age (not dash) for sessions with updated-at timestamps"
    (let [output (with-out-str (sessions/run {:state-dir @state-dir}))]
      (should-not (re-find #"abc\s+-\s" output))))

  (it "uses context-window from injected model config"
    (let [models {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 8192}}
          agents {"main" {:name "main" :soul "You are Isaac." :model "grover"}}
          output (with-out-str (sessions/run {:state-dir @state-dir
                                              :agents    agents
                                              :models    models}))]
      (should (str/includes? output "8,192")))))

(describe "sessions/run-show"

  (it "prints usage and returns 1 when session id is blank"
    (let [result (atom nil)
          output (with-out-str (reset! result (#'sessions/run-show {:home "/tmp/home"} "")))]
      (should= 1 @result)
      (should (str/includes? output "Usage: isaac sessions show <session-id>"))))

  (it "prints not found and returns 1 when the session is missing"
    (with-redefs [config/load-config     (fn [_] {:stateDir "/tmp/state"})
                   config/normalize-config identity
                   store/get-session       (fn [session-store session-id]
                                             (should-not-be-nil session-store)
                                             (should= "ghost" session-id)
                                             nil)]
      (let [result (atom nil)
            output (with-out-str (reset! result (#'sessions/run-show {:home "/tmp/home"} "ghost")))]
        (should= 1 @result)
        (should (str/includes? output "session not found: ghost")))))

  (it "prints formatted status and returns 0 for an existing session"
    (let [captured-context (atom nil)
          captured-status  (atom nil)
          loaded-cfg       {:stateDir "/tmp/state"
                            :crew     {"main" {:model "grover"}}
                            :models   {"grover" {:context-window 8192}}}]
      (with-redefs [config/load-config     (fn [_] loaded-cfg)
                    config/normalize-config identity
                    store/get-session       (fn [session-store session-id]
                                              (should-not-be-nil session-store)
                                              (should= "abc" session-id)
                                              {:cwd "/tmp/project"})
                    session-ctx/resolve-turn-context
                    (fn [context crew-id]
                      (reset! captured-context [context crew-id])
                      {:model "grover" :window 8192})
                    bridge/status-data
                    (fn [state-dir session-id context]
                      (reset! captured-status [state-dir session-id context])
                      {:id session-id :ok true})
                    bridge/format-status   (fn [status]
                                             (should= {:id "abc" :ok true} status)
                                             "formatted status")]
        (let [result (atom nil)
              output (with-out-str (reset! result (#'sessions/run-show {:home "/tmp/home"} "abc")))]
          (should= 0 @result)
          (should= [{:crew-members {"main" {:model "grover"}}
                      :models       {"grover" {:context-window 8192}}
                      :cwd          "/tmp/project"
                      :home         "/tmp/state"}
                     "main"]
                    @captured-context)
          (should= ["/tmp/state" "abc" {:model "grover" :window 8192 :crew "main"}]
                    @captured-status)
          (should (str/includes? output "formatted status")))))))

(describe "sessions/run-fn"

  (it "dispatches show to run-show with the requested session id"
    (let [captured (atom nil)]
      (with-redefs [isaac.session.cli/run-show (fn [opts session-id]
                                                 (reset! captured [opts session-id])
                                                 0)]
        (should= 0 (sessions/run-fn {:_raw-args ["show" "abc"] :home "/tmp/home"}))
        (should= [{:_raw-args ["show" "abc"] :home "/tmp/home"} "abc"] @captured))))

  (it "dispatches delete to run-delete with the requested session id"
    (let [captured (atom nil)]
      (with-redefs [isaac.session.cli/run-delete (fn [opts session-id]
                                                   (reset! captured [opts session-id])
                                                   0)]
        (should= 0 (sessions/run-fn {:_raw-args ["delete" "abc"] :home "/tmp/home"}))
        (should= [{:_raw-args ["delete" "abc"] :home "/tmp/home"} "abc"] @captured))))

  (it "prints command help and returns 0 when --help is requested"
    (with-redefs [isaac.session.cli/parse-option-map (fn [_] {:options {:help true} :errors []})
                  registry/get-command (fn [_] {:name "sessions"})
                  registry/command-help (fn [_] "sessions help")]
      (let [output (with-out-str (should= 0 (sessions/run-fn {:_raw-args ["--help"]})))]
        (should (str/includes? output "sessions help")))))

  (it "prints parse errors and returns 1"
    (with-redefs [isaac.session.cli/parse-option-map (fn [_] {:options {} :errors ["bad arg"]})]
      (let [output (with-out-str (should= 1 (sessions/run-fn {:_raw-args ["--bogus"]})))]
        (should (str/includes? output "bad arg")))))

  (it "delegates to run with parsed options merged into opts"
    (let [captured (atom nil)]
      (with-redefs [isaac.session.cli/parse-option-map (fn [_] {:options {:crew "main"} :errors []})
                    isaac.session.cli/run (fn [opts]
                                            (reset! captured opts)
                                            0)]
        (should= 0 (sessions/run-fn {:_raw-args ["--crew" "main"] :home "/tmp/home"}))
        (should= {:home "/tmp/home" :crew "main"} @captured)))))
