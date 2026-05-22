(ns isaac.logs.cli-spec
  (:require
    [isaac.cli :as registry]
    [isaac.fs :as fs]
    [isaac.log-viewer :as viewer]
    [isaac.logger :as log]
    [isaac.logs.cli :as sut]
    [speclj.core :refer :all]))

(describe "logs cli"

  (describe "resolve-path"

    (it "returns nil for nil input"
      (should= nil (#'sut/resolve-path nil "/tmp/state")))

    (it "keeps absolute paths"
      (should= "/tmp/isaac.log" (#'sut/resolve-path "/tmp/isaac.log" "/tmp/state")))

    (it "resolves relative paths under state-dir"
      (should= "/tmp/state/isaac.log" (#'sut/resolve-path "isaac.log" "/tmp/state")))

    (it "returns the relative path when state-dir is unavailable"
      (should= "isaac.log" (#'sut/resolve-path "isaac.log" nil))))

  (describe "config-log-path"

    (it "reads the configured log output path"
      (let [mem (fs/mem-fs)]
        (fs/mkdirs- mem "/tmp/home/.isaac/config")
        (fs/spit-   mem "/tmp/home/.isaac/config/isaac.edn" "{:log {:output \"logs/custom.log\"}}")
        (should= "logs/custom.log" (#'sut/config-log-path "/tmp/home" mem))))

    (it "returns nil when the config file is missing"
      (should= nil (#'sut/config-log-path "/tmp/home" (fs/mem-fs))))

    (it "returns nil when the config file is invalid"
      (let [mem (fs/mem-fs)]
        (fs/mkdirs- mem "/tmp/home/.isaac/config")
        (fs/spit-   mem "/tmp/home/.isaac/config/isaac.edn" "{:log")
        (should= nil (#'sut/config-log-path "/tmp/home" mem)))))

  (describe "run"

    (it "prefers the explicit file path and forwards viewer options"
      (let [captured (atom nil)]
        (with-redefs [viewer/tail! (fn [path opts] (reset! captured [path opts]))]
          (sut/run {:file      "logs/custom.log"
                    :state-dir "/tmp/state"
                    :follow    true
                    :limit     5
                    :zebra     true
                    :plain     true
                    :no-color  true})
          (should= ["/tmp/state/logs/custom.log"
                    {:color? false :zebra? true :follow? true :plain? true :limit 5}]
                   @captured))))

    (it "falls back to the configured log path when no explicit file is given"
      (let [captured (atom nil)]
        (with-redefs [sut/config-log-path (fn [_ _]"logs/from-config.log")
                      viewer/tail!        (fn [path opts] (reset! captured [path opts]))]
          (sut/run {:home "/tmp/home" :state-dir "/tmp/state" :limit 20})
          (should= ["/tmp/state/logs/from-config.log"
                    {:color? true :zebra? false :follow? false :plain? false :limit 20}]
                   @captured))))

    (it "falls back to the logger default when no config path exists"
      (let [captured (atom nil)]
        (with-redefs [sut/config-log-path (fn [_ _]nil)
                      log/log-file        (fn [] "/tmp/default.log")
                      viewer/tail!        (fn [path opts] (reset! captured [path opts]))]
          (sut/run {:home "/tmp/home" :state-dir "/tmp/state" :limit 20})
          (should= ["/tmp/default.log"
                    {:color? true :zebra? false :follow? false :plain? false :limit 20}]
                   @captured)))))

  (describe "run-fn"

    (it "prints help and returns 0 for --help"
      (with-redefs [registry/get-command  (fn [_] {:name "logs"})
                    registry/command-help (fn [_] "logs help")]
        (let [output (with-out-str (should= 0 (sut/run-fn {:_raw-args ["--help"]})))]
          (should (.contains output "logs help")))))

    (it "prints parse errors and returns 1"
      (let [output (with-out-str (should= 1 (sut/run-fn {:_raw-args ["--limit" "bogus"]})))]
        (should (.contains output "For input string: \"bogus\""))))

    (it "delegates to run with merged parsed options"
      (let [captured (atom nil)]
        (with-redefs [sut/run (fn [opts] (reset! captured opts) 0)]
          (should= 0 (sut/run-fn {:_raw-args ["--file" "logs/app.log" "--zebra"]
                                  :home      "/tmp/home"
                                  :state-dir "/tmp/state"}))
          (should= {:home "/tmp/home"
                    :state-dir "/tmp/state"
                    :file "logs/app.log"
                    :limit 20
                    :zebra true}
                   @captured))))))
