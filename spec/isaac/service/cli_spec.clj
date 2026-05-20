(ns isaac.service.cli-spec
  (:require
    [clojure.string :as str]
    [isaac.fs :as fs]
    [isaac.home :as home]
    [isaac.main :as main]
    [isaac.util.shell :as shell]
    [speclj.core :refer :all]))

(defn- run [args]
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        code (binding [*out* out
                       *err* err]
               (main/run (str/split args #"\s+" -1)))]
    {:exit code :out (str out) :err (str err)}))

(describe "service.cli"

  (around [it]
    (binding [fs/*fs*          (fs/mem-fs)
              home/*user-home* "/test/home"
              shell/*sh*       (fn [& _]
                                 {:exit 0 :out "" :err ""})]
      (it)))

  (describe "on macOS"

    (around [it]
      (binding [shell/*os-name* "Mac OS X"]
        (it)))

    (it "install succeeds when bb is found"
      (binding [shell/*sh* (fn [& args]
                             (if (= ["which" "bb"] (take 2 (vec args)))
                               {:exit 0 :out "/opt/homebrew/bin/bb\n" :err ""}
                               {:exit 0 :out "" :err ""}))]
        (let [result (run "service install")]
          (should= 0 (:exit result))
          (should (str/includes? (:out result) "Resolved bb:")))))

    (it "install fails when bb is not found"
      (binding [shell/*sh* (fn [& _] {:exit 1 :out "" :err ""})]
        (let [result (run "service install")]
          (should= 1 (:exit result))
          (should (str/includes? (:err result) "could not locate bb")))))

    (it "install succeeds with --bb-bin override"
      (binding [shell/*sh* (fn [& _] {:exit 0 :out "" :err ""})]
        (let [result (run "service install --bb-bin /usr/local/bin/bb")]
          (should= 0 (:exit result))
          (should (str/includes? (:out result) "/usr/local/bin/bb")))))

    (it "uninstall prints confirmation"
      (let [result (run "service uninstall")]
        (should= 0 (:exit result))
        (should (str/includes? (:out result) "uninstalled"))))

    (it "status reports not installed when plist absent"
      (let [result (run "service status")]
        (should= 1 (:exit result))
        (should (str/includes? (:out result) "not installed"))))

    (it "status reports running when launchctl print shows running"
      (fs/mkdirs "/test/home/Library/LaunchAgents")
      (fs/spit "/test/home/Library/LaunchAgents/com.slagyr.isaac.plist" "test")
      (binding [shell/*sh* (fn [& args]
                             (if (= "print" (second (vec args)))
                               {:exit 0 :out "{ state = running\n\tpid = 99\n\tlast exit code = 0 }" :err ""}
                               {:exit 0 :out "" :err ""}))]
        (let [result (run "service status")]
          (should= 0 (:exit result))
          (should (str/includes? (:out result) "running")))))

    (it "restart calls launchctl kickstart -k"
      (let [calls (atom [])]
        (binding [shell/*sh* (fn [& args]
                               (swap! calls conj (vec args))
                               {:exit 0 :out "" :err ""})]
          (run "service restart")
          (should (some #(and (= "launchctl" (first %)) (= "kickstart" (second %)) (some #{"-k"} %)) @calls))))))

  (describe "on unsupported OS"

    (around [it]
      (binding [shell/*os-name* "Linux"]
        (it)))

    (it "install prints not supported message"
      (let [result (run "service install")]
        (should= 1 (:exit result))
        (should (str/includes? (:err result) "not yet supported on Linux"))))

    (it "status prints not supported message"
      (let [result (run "service status")]
        (should= 1 (:exit result))
        (should (str/includes? (:err result) "not yet supported on Linux"))))))
