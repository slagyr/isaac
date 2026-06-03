(ns isaac.main-spec
  (:require
    [isaac.cli :as registry]
    [isaac.config.api :as config]
    [isaac.fs :as fs]
    [isaac.root :as root]
    [isaac.module.loader :as module-loader]
    [isaac.main :as sut]
    [isaac.session.store :as store]
    [isaac.nexus :as nexus]
    [speclj.core :refer :all]))

(defn make-greet-command []
  {:name        "greet"
   :usage       "greet"
   :option-spec []
   :run-fn      (fn [_] 0)})

(describe "Main CLI"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (binding [*out* (java.io.StringWriter.)]
      (example)))

  (describe "run"

    (it "prints usage and returns 0 when no args"
      (should= 0 (sut/run [])))

    (it "prints usage and returns 0 for blank command"
      (should= 0 (sut/run [""])))

    (it "prints usage and returns 0 for help command"
      (should= 0 (sut/run ["help"])))

    (it "returns 1 for unknown command"
      (should= 1 (sut/run ["nonexistent-command-xyz"])))

    (it "dispatches to a registered command"
      (let [received (atom nil)]
        (registry/register! {:name   "test-dispatch"
                             :desc   "Test"
                             :usage  "test-dispatch"
                             :option-spec []
                             :run-fn (fn [opts] (reset! received opts) 0)})
        (should= 0 (sut/run ["test-dispatch" "--agent" "bot"]))
        (should= ["--agent" "bot"] (:_raw-args @received))))

    (it "injects root resolved from the XDG pointer file"
      (let [received (atom nil)
            mem      (fs/mem-fs)]
        (registry/register! {:name   "pointer-dispatch"
                             :desc   "Test"
                             :usage  "pointer-dispatch"
                             :option-spec []
                             :run-fn (fn [opts] (reset! received opts) 0)})
        (binding [root/*user-home*  "/tmp/user"
                  sut/*extra-opts*  {:fs mem}]
          (fs/mkdirs mem "/tmp/user/.config")
          (fs/spit   mem "/tmp/user/.config/isaac.edn" "{:root \"/tmp/pointer\"}")
          (should= 0 (sut/run ["pointer-dispatch"])))
        (should= "/tmp/pointer" (:root @received))))

    (it "lets the top-level --root flag override the pointer file"
      (let [received (atom nil)
            mem      (fs/mem-fs)]
        (registry/register! {:name   "root-flag-dispatch"
                             :desc   "Test"
                             :usage  "root-flag-dispatch"
                             :option-spec []
                             :run-fn (fn [opts] (reset! received opts) 0)})
        (binding [root/*user-home* "/tmp/user"
                  sut/*extra-opts* {:fs mem}]
          (fs/mkdirs mem "/tmp/user/.config")
          (fs/spit   mem "/tmp/user/.config/isaac.edn" "{:root \"/tmp/pointer\"}")
          (should= 0 (sut/run ["--root" "/tmp/flag" "root-flag-dispatch"])))
        (should= "/tmp/flag" (:root @received))
        (should= [] (:_raw-args @received))))

    (it "returns exit code from command run-fn"
      (registry/register! {:name   "fail-cmd"
                           :desc   "Fails"
                           :usage  "fail-cmd"
                           :option-spec []
                           :run-fn (fn [_] 42)})
      (should= 42 (sut/run ["fail-cmd"])))

    (it "returns 0 when run-fn returns nil"
      (registry/register! {:name   "nil-cmd"
                           :desc   "Returns nil"
                           :usage  "nil-cmd"
                           :option-spec []
                           :run-fn (fn [_] nil)})
      (should= 0 (sut/run ["nil-cmd"])))

    (it "shows help for a known command via 'help <cmd>'"
      (registry/register! {:name    "documented"
                           :desc    "A documented command"
                           :usage   "documented [options]"
                           :option-spec [["-v" "--verbose" "Be loud"]]
                           :run-fn  identity})
      (should= 0 (sut/run ["help" "documented"])))

    (it "returns 1 for 'help <unknown>'"
      (should= 1 (sut/run ["help" "no-such-command-xyz"])))

    (it "shows help when --help flag is passed to a command"
      (let [received (atom nil)]
        (registry/register! {:name        "help-flag-test"
                             :desc        "Has help"
                             :usage       "help-flag-test"
                             :option-spec []
                             :run-fn      (fn [opts]
                                            (reset! received opts)
                                            0)})
        (should= 0 (sut/run ["help-flag-test" "--help"]))
        (should= ["--help"] (:_raw-args @received))))

    (it "prints usage and returns 0 for top-level --help"
      (should= 0 (sut/run ["--help"])))

    (it "prints usage and returns 0 for top-level -h"
      (should= 0 (sut/run ["-h"])))

    (it "documents global options in top-level usage output"
      (let [output (with-out-str (should= 0 (sut/run ["--help"])))]
        (should-contain "Usage: isaac [options] <command> [args]" output)
        (should-contain "Global Options:" output)
        (should-contain "--root <dir>" output)
        (should-contain "~/.config/isaac.edn" output)
        (should-contain "--help, -h" output)
        (should-contain "Commands:" output))))

  (describe "alias resolution"

    (it "resolves 'models auth' to 'auth'"
      (let [received (atom nil)]
        (registry/register! {:name   "auth"
                             :desc   "Auth"
                             :usage  "auth"
                             :option-spec []
                             :run-fn (fn [opts] (reset! received opts) 0)})
        (should= 0 (sut/run ["models" "auth"]))
        (should-not-be-nil @received)))

    (it "does not resolve non-alias prefixes"
      (should= 1 (sut/run ["models" "something-else"]))))

  (describe "dispatch payload"

    (it "includes _raw-args"
      (let [received (atom nil)]
        (registry/register! {:name   "raw-test"
                             :desc   "Test"
                             :usage  "raw-test"
                             :option-spec []
                             :run-fn (fn [opts] (reset! received opts) 0)})
        (sut/run ["raw-test" "--agent" "x" "extra"])
        (should= ["--agent" "x" "extra"] (:_raw-args @received))))

    (it "includes bound extra opts"
      (let [received (atom nil)]
        (registry/register! {:name        "extra-test"
                             :desc        "Test"
                             :usage       "extra-test"
                             :option-spec []
                             :run-fn      (fn [opts] (reset! received opts) 0)})
        (binding [sut/*extra-opts* {:root (str (System/getProperty "user.dir") "/target/test-state")}]
          (sut/run ["extra-test"]))
        (should= (str (System/getProperty "user.dir") "/target/test-state") (:root @received)))))

  (describe "substitute-env"

    (it "expands ${VAR} strings using config/env"
      (with-redefs [config/env (fn [v] (when (= v "MY_ROOT") "/resolved/path"))]
        (should= {:local/root "/resolved/path"}
                 (@#'sut/substitute-env {:local/root "${MY_ROOT}"}))))

    (it "leaves unknown variables unexpanded"
      (with-redefs [config/env (constantly nil)]
        (should= "${UNKNOWN}" (@#'sut/substitute-env "${UNKNOWN}"))))

    (it "recurses into nested maps and vectors"
      (with-redefs [config/env (fn [v] (when (= v "X") "y"))]
        (should= {:a {:b "y"} :c ["y" 1]}
                 (@#'sut/substitute-env {:a {:b "${X}"} :c ["${X}" 1]})))))

  (describe "register-module-cli-commands!"

    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example]
      (nexus/-with-nested-nexus {:fs (fs/mem-fs)}
        (registry/clear-module-commands!)
        (example)
        (registry/clear-module-commands!)))

    (it "clears stale module commands before re-discovery"
      (registry/register-module-command! {:name "stale-cmd" :desc "old" :usage "stale-cmd"
                                          :option-spec [] :run-fn (fn [_] 0)})
      (should-not-be-nil (registry/get-command "stale-cmd"))
      (binding [sut/*extra-opts* {:root (str (System/getProperty "user.dir") "/target/test-state")}]
        (with-out-str (sut/run ["--help"])))
      (should-be-nil (registry/get-command "stale-cmd")))

    (it "reads module cli config from an explicit fs"
      (let [mem         (fs/mem-fs)
            config-path "/tmp/home/.isaac/config/isaac.edn"]
        (fs/mkdirs mem "/tmp/home/.isaac/config")
        (fs/spit mem config-path "{:modules {:hello {}}}")
        (with-redefs [module-loader/discover! (fn [config context]
                                                (should= {:modules {:hello {}}} config)
                                                (should= {:cwd (System/getProperty "user.dir")} context)
                                                {:index {:hello {:manifest {:cli {:greet {:factory 'isaac.main-spec/make-greet-command
                                                                                          :description "Greets"}}}}}})]
          (@#'sut/register-module-cli-commands! "/tmp/home/.isaac" mem))
        (should-not-be-nil (registry/get-command "greet"))
        (should= "Greets" (:desc (registry/get-command "greet")))))

    (it "installs the active fs into runtime init"
      (let [mem       (fs/mem-fs)
            init-opts (atom nil)]
        (registry/register! {:name        "fs-init"
                             :desc        "Test"
                             :usage       "fs-init"
                             :option-spec []
                             :run-fn      (fn [_] 0)})
        (with-redefs [nexus/init!       (fn
                                           ([] (reset! init-opts {}))
                                           ([opts] (reset! init-opts opts)))
                      nexus/register!   (fn [& _])
                      store/register!    (fn [& _])
                      root/resolve-root  (fn [& _] "/tmp/home")]
          (binding [sut/*extra-opts* {:fs mem}]
            (should= 0 (sut/run ["fs-init"]))))
        (should= mem (:fs @init-opts))))))
