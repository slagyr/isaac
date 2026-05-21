(ns isaac.bridge.prompt-cli-origin-spec
  (:require
    [isaac.bridge.core :as bridge]
    [isaac.bridge.prompt-cli :as sut]
    [isaac.comm :as comm]
    [isaac.drive.turn :as single-turn]
    [isaac.fs :as fs]
    [isaac.spec-helper :as helper]
    [speclj.core :refer :all]))

(def ^:private base-opts
  {:state-dir "/test/prompt"
   :agents    {"main" {:name "main" :soul "You are Isaac." :model "grover"}}
   :models    {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}})

(describe "CLI Prompt origin"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [it] (helper/with-memory-store (binding [fs/*fs* (fs/mem-fs)] (it))))

  (it "creates prompt sessions with a cli origin"
    (with-redefs [single-turn/run-turn! (fn [charge]
                                          (comm/on-text-chunk (:comm charge) (:session-key charge) "Hello")
                                          {})]
      (with-out-str
        (should= 0 (sut/run (assoc base-opts :message "Hi"))))
      (let [session (helper/get-session "/test/prompt" "prompt-default")]
        (should= {:kind :cli} (:origin session)))))

  (it "charge carries the cli origin"
    (let [captured (atom nil)]
      (with-redefs [bridge/dispatch! (fn [charge]
                                       (reset! captured charge)
                                       {:content "Hello"})]
        (with-out-str
          (should= 0 (sut/run (assoc base-opts :message "Hi")))))
      (should= {:kind :cli} (:origin @captured)))))
