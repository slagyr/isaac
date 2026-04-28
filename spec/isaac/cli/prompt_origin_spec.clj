(ns isaac.cli.prompt-origin-spec
  (:require
    [isaac.cli.prompt :as sut]
    [isaac.drive.turn :as turn]
    [isaac.fs :as fs]
    [isaac.session.storage :as storage]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]
    [speclj.core :refer :all]))

(describe "CLI Prompt origin"

  (around [it]
    (binding [fs/*fs* (fs/mem-fs)]
      (it)))

  (it "creates prompt sessions with a cli origin"
    (let [captured (atom nil)]
      (with-redefs [storage/open-session         (fn [& _] nil)
                    storage/create-session!      (fn [_state-dir _session-key opts]
                                                   (reset! captured opts)
                                                   {:id "prompt-default"})
                    builtin/register-all!        (fn [& _] nil)
                    turn/run-turn!     (fn [& _] {:content "Hello"})
                    sut/ensure-local-config!     (fn [_] true)
                    sut/resolve-run-opts         (fn [_]
                                                   {:agent-id        "main"
                                                    :crew-members    {"main" {:model "grover" :soul "You are Isaac."}}
                                                    :models          {"grover" {:model "echo" :provider "grover" :context-window 32768}}
                                                    :state-dir       "/test/prompt"
                                                    :soul            "You are Isaac."
                                                    :model           "echo"
                                                    :provider        "grover"
                                                    :provider-config {}
                                                    :context-window  32768})]
        (with-out-str (should= 0 (sut/run {:message "Hi"}))))
      (should= {:kind :cli} (:origin @captured)))))
