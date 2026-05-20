(ns isaac.service.macos-spec
  (:require
    [clojure.string :as str]
    [isaac.fs :as fs]
    [isaac.home :as home]
    [isaac.service.macos :as sut]
    [isaac.util.shell :as shell]
    [speclj.core :refer :all]))

(defn- stub-sh [calls-atom]
  (fn [& args]
    (swap! calls-atom conj (vec args))
    {:exit 0 :out "" :err ""}))

(describe "service.macos"

  (around [it]
    (binding [fs/*fs*          (fs/mem-fs)
              home/*user-home* "/test/home"]
      (it)))

  (describe "plist-content"

    (it "substitutes all placeholders"
      (let [plist (sut/plist-content {:bb-bin  "/opt/homebrew/bin/bb"
                                      :bb-edn  "/projects/isaac"
                                      :home    "/test/home"
                                      :log-dir "/test/home/Library/Logs/isaac"})]
        (should (str/includes? plist "/opt/homebrew/bin/bb"))
        (should (str/includes? plist "/projects/isaac/bb.edn"))
        (should (str/includes? plist "com.slagyr.isaac"))
        (should (str/includes? plist "/test/home/Library/Logs/isaac/server.log"))))

    (it "generates valid XML plist structure"
      (let [plist (sut/plist-content {:bb-bin  "/usr/local/bin/bb"
                                      :bb-edn  "/repo"
                                      :home    "/home/user"
                                      :log-dir "/home/user/Library/Logs/isaac"})]
        (should (str/starts-with? plist "<?xml"))
        (should (str/includes? plist "<plist")))))

  (describe "install!"

    (it "writes the plist file"
      (let [calls (atom [])]
        (binding [shell/*sh* (stub-sh calls)]
          (sut/install! {:bb-bin "/opt/homebrew/bin/bb" :bb-edn "/projects/isaac"})
          (should (fs/exists? "/test/home/Library/LaunchAgents/com.slagyr.isaac.plist")))))

    (it "creates the log directory"
      (let [calls (atom [])]
        (binding [shell/*sh* (stub-sh calls)]
          (sut/install! {:bb-bin "/opt/homebrew/bin/bb" :bb-edn "/projects/isaac"})
          (should (fs/exists? "/test/home/Library/Logs/isaac")))))

    (it "calls launchctl bootstrap with the plist path"
      (let [calls (atom [])]
        (binding [shell/*sh* (stub-sh calls)]
          (sut/install! {:bb-bin "/opt/homebrew/bin/bb" :bb-edn "/projects/isaac"})
          (should (some #(and (= "launchctl" (first %))
                              (= "bootstrap" (second %))
                              (str/includes? (last %) "com.slagyr.isaac.plist"))
                        @calls))))))

  (describe "start!"

    (it "calls launchctl bootstrap with the plist path"
      (let [calls (atom [])]
        (binding [shell/*sh* (stub-sh calls)]
          (sut/start! {})
          (should (some #(and (= "launchctl" (first %))
                              (= "bootstrap" (second %))
                              (str/includes? (last %) "com.slagyr.isaac.plist"))
                        @calls))))))

  (describe "logs!"

    (it "returns file content when follow? is false"
      (let [calls (atom [])]
        (binding [shell/*sh* (stub-sh calls)]
          (fs/mkdirs "/test/home/Library/Logs/isaac")
          (fs/spit "/test/home/Library/Logs/isaac/server.log" "log line")
          (let [result (sut/logs! {:follow? false})]
            (should= "log line" (:content result))))))

    (it "calls tail -f when follow? is true"
      (let [calls (atom [])]
        (binding [shell/*sh* (stub-sh calls)]
          (fs/mkdirs "/test/home/Library/Logs/isaac")
          (fs/spit "/test/home/Library/Logs/isaac/server.log" "log line")
          (sut/logs! {:follow? true})
          (should (some #(and (= "tail" (first %)) (= "-f" (second %))) @calls))))))

  (describe "uninstall!"

    (it "removes the plist file"
      (let [calls (atom [])]
        (binding [shell/*sh* (stub-sh calls)]
          (fs/mkdirs "/test/home/Library/LaunchAgents")
          (fs/spit "/test/home/Library/LaunchAgents/com.slagyr.isaac.plist" "test")
          (sut/uninstall! {})
          (should-not (fs/exists? "/test/home/Library/LaunchAgents/com.slagyr.isaac.plist")))))

    (it "calls launchctl bootout"
      (let [calls (atom [])]
        (binding [shell/*sh* (stub-sh calls)]
          (fs/mkdirs "/test/home/Library/LaunchAgents")
          (fs/spit "/test/home/Library/LaunchAgents/com.slagyr.isaac.plist" "test")
          (sut/uninstall! {})
          (should (some #(and (= "launchctl" (first %)) (= "bootout" (second %))) @calls))))))

  (describe "parse-status"

    (it "extracts state, pid, and last exit code from launchctl print output"
      (let [output "{\n\tstate = running\n\tpid = 51234\n\tlast exit code = 0\n}"
            result (sut/parse-status output)]
        (should= "running" (:state result))
        (should= "51234" (:pid result))
        (should= "0" (:last-exit result))))

    (it "returns nil for missing fields"
      (let [result (sut/parse-status "{ state = waiting }")]
        (should= "waiting" (:state result))
        (should-be-nil (:pid result))
        (should-be-nil (:last-exit result))))))
