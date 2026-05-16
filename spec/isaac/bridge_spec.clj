(ns isaac.bridge-spec
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.logger :as log]
    [isaac.bridge.cancellation :as bridge-cancel]
    [isaac.bridge.core :as bridge]
    [isaac.bridge.status :as bridge-status]
    [isaac.drive.turn :as single-turn]
    [isaac.marigold :as marigold]
    [isaac.spec-helper :as helper]
    [isaac.system :as system]
    [isaac.tool.registry :as tool-registry]
    [speclj.core :refer :all]))

(defn- delete-dir! [path]
  (let [f (io/file path)]
    (when (.exists f)
      (doseq [file (reverse (file-seq f))]
        (.delete file)))))

(def state-dir (atom nil))

(describe "bridge"
  (context "status-data"
    (around [it] (helper/with-memory-store (it)))
    (before
      (reset! state-dir (str (System/getProperty "user.dir") "/target/test-state/bridge-spec-" (random-uuid)))
      (delete-dir! @state-dir)
      (helper/create-session! @state-dir "testuser"))

    (it "includes crew, model, provider from context"
      (let [ctx {:crew "main" :agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge-status/status-data @state-dir "testuser" ctx)]
        (should= "main" (:crew data))
        (should= "echo" (:model data))
        (should= "grover" (:provider data))))

    (it "includes context-window from context"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge-status/status-data @state-dir "testuser" ctx)]
        (should= 32768 (:context-window data))))

    (it "includes session-key"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge-status/status-data @state-dir "testuser" ctx)]
        (should= "testuser" (:session-key data))))

    (it "includes session-file from storage"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge-status/status-data @state-dir "testuser" ctx)]
        (should-not-be-nil (:session-file data))
        (should (re-matches #"[a-z0-9-]+\.jsonl" (:session-file data)))))

    (it "counts zero turns on a fresh session"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge-status/status-data @state-dir "testuser" ctx)]
        (should= 0 (:turns data))))

    (it "counts turns from transcript messages"
      (helper/append-message! @state-dir "testuser" {:role "user" :content "hello"})
      (helper/append-message! @state-dir "testuser" {:role "assistant" :content "hi"})
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge-status/status-data @state-dir "testuser" ctx)]
        (should= 2 (:turns data))))

    (it "includes compaction count from storage"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge-status/status-data @state-dir "testuser" ctx)]
        (should= 0 (:compactions data))))

    (it "includes tokens from storage"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge-status/status-data @state-dir "testuser" ctx)]
        (should (number? (:tokens data)))))

    (it "computes context-pct as percentage of tokens over context-window"
      (helper/update-tokens! @state-dir "testuser" {:input-tokens 3277 :output-tokens 0})
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge-status/status-data @state-dir "testuser" ctx)]
        (should (> (:context-pct data) 0))))

    (it "includes cwd"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge-status/status-data @state-dir "testuser" ctx)]
        (should-not-be-nil (:cwd data))))

    (it "prefers the session cwd over the process working directory"
      (helper/update-session! @state-dir "testuser" {:cwd "/tmp/isaac-cwd-fixture"})
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge-status/status-data @state-dir "testuser" ctx)]
        (should= "/tmp/isaac-cwd-fixture" (:cwd data))))

    (it "includes tool-count from registry"
      (system/with-system {:state-dir @state-dir}
        (tool-registry/clear!)
        (tool-registry/register! {:name "bash" :description "Run bash" :handler identity})
        (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
              data (bridge-status/status-data "testuser" ctx)]
          (should= 1 (:tool-count data)))))
    )

  (context "format-status"
    (it "formats status map as a markdown table"
      (let [data {:boot-files "Micah's AI assistant management tools. This project uses toolbox to manage agent components."
                  :crew "main" :agent "main" :model "echo" :provider "grover" :context-window 32768
                  :session-key "testuser" :session-file "abc12345.jsonl"
                  :soul "You are Isaac." :turns 2 :compactions 2 :tokens 5000 :context-pct 15 :tool-count 1
                  :cwd "/tmp/test"}
            output (bridge-status/format-status data)]
        (should (re-find #"```text" output))
        (should (re-find #"Session Status" output))
        (should (re-find #"Crew\s+main" output))
        (should (re-find #"─+" output))
        (should (re-find #"Model\s+echo \(grover\)" output))
        (should (re-find #"Compactions\s+2" output))
        (should (re-find #"Context\s+5,000 / 32,768 \(15%\)" output))
        (should (re-find #"Soul\s+\"Micah's AI assistant management tools\. This project uses ...\"" output))
        (should (re-find #"```$" output))
        (should-not (re-find #"You are Isaac\." output))))
    )

  (context "slash-command?"
    (it "returns true for /status"
      (should (bridge/slash-command? "/status")))

    (it "returns true for any slash-prefixed input"
      (should (bridge/slash-command? "/help")))

    (it "returns false for normal input"
      (should-not (bridge/slash-command? "hello world")))

    (it "returns false for blank input"
      (should-not (bridge/slash-command? "")))

    (it "returns false for nil"
      (should-not (bridge/slash-command? nil)))
    )

  (context "dispatch"
    (around [it] (helper/with-memory-store (it)))
    (before
      (reset! state-dir (str (System/getProperty "user.dir") "/target/test-state/bridge-dispatch-spec-" (random-uuid)))
      (delete-dir! @state-dir)
      (helper/create-session! @state-dir "testuser"))

    (it "returns command type for /status"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            result (bridge/dispatch @state-dir "testuser" "/status" ctx nil)]
        (should= :command (:type result))
        (should= :status (:command result))
        (should-not-be-nil (:data result))))

    (it "includes status data in command result"
      (let [ctx {:crew "main" :agent "main" :model "echo" :provider "grover" :context-window 32768}
            result (bridge/dispatch @state-dir "testuser" "/status" ctx nil)]
        (should= "main" (get-in result [:data :crew]))
        (should= "echo" (get-in result [:data :model]))))

    (it "returns unknown command error for unrecognized slash commands"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            result (bridge/dispatch @state-dir "testuser" "/unknown" ctx nil)]
        (should= :command (:type result))
        (should= :unknown (:command result))))

    (it "delegates to turn-fn for non-slash input"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            called (atom nil)
            turn-fn (fn [input opts] (reset! called {:input input :opts opts}) {:content "hi"})
            result (bridge/dispatch @state-dir "testuser" "hello" ctx turn-fn)]
        (should= :turn (:type result))
        (should= "hello" (:input @called))))
    )

  (context "dispatch - /model command"
    (around [it] (helper/with-memory-store (it)))
    (before
      (reset! state-dir (str (System/getProperty "user.dir") "/target/test-state/bridge-model-spec-" (random-uuid)))
      (delete-dir! @state-dir)
      (helper/create-session! @state-dir "model-test"))

    (it "shows the current model when no argument is given"
      (let [ctx {:model "echo" :provider "grover" :context-window 32768
                 :models {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}}
            result (bridge/dispatch @state-dir "model-test" "/model" ctx nil)]
        (should= :command (:type result))
        (should= :model (:command result))
        (should= "grover (grover/echo) is the current model" (:message result))))

    (it "switches model and returns confirmation message"
      (let [alt-model "starcore-7-fast"
            ctx       {:model "echo" :provider "grover" :context-window 32768
                       :models {"grover"          {:alias "grover"          :model "echo"      :provider "grover"          :context-window 32768}
                                marigold/starcore {:alias marigold/starcore :model alt-model :provider marigold/starcore :context-window 32768}}}
            result    (bridge/dispatch @state-dir "model-test" (str "/model " marigold/starcore) ctx nil)]
        (should= :command (:type result))
        (should= :model (:command result))
        (should= (str "switched model to " marigold/starcore " (" marigold/starcore "/" alt-model ")") (:message result))))

    (it "persists the alias in the session :model field, leaves :provider unset"
      (let [ctx {:model "echo" :provider "grover" :context-window 32768
                 :models {"grover"          {:alias "grover"          :model "echo"             :provider "grover"          :context-window 32768}
                          marigold/starcore {:alias marigold/starcore :model "starcore-7-fast" :provider marigold/starcore :context-window 32768}}}]
        (bridge/dispatch @state-dir "model-test" (str "/model " marigold/starcore) ctx nil)
        (let [session (helper/get-session @state-dir "model-test")]
          (should= marigold/starcore (:model session))
          (should-be-nil (:provider session)))))

    (it "returns an error for an unknown model alias"
      (let [ctx {:model "echo" :provider "grover" :context-window 32768
                 :models {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}}
            result (bridge/dispatch @state-dir "model-test" "/model nonexistent" ctx nil)]
        (should= :command (:type result))
        (should= :unknown (:command result))
        (should= "unknown model: nonexistent" (:message result))))
    )

  (context "dispatch - /crew command"
    (around [it] (helper/with-memory-store (it)))
    (before
      (reset! state-dir (str (System/getProperty "user.dir") "/target/test-state/bridge-crew-spec-" (random-uuid)))
      (delete-dir! @state-dir)
      (helper/create-session! @state-dir "crew-test"))

    (it "shows the current crew when no argument is given"
      (let [ctx {:crew "main" :crew-members {"main" {} "ketch" {}}}
            result (bridge/dispatch @state-dir "crew-test" "/crew" ctx nil)]
        (should= :command (:type result))
        (should= :crew (:command result))
        (should= "main is the current crew member" (:message result))))

    (it "switches crew and returns confirmation message"
      (let [ctx {:crew "main" :crew-members {"main" {} "ketch" {}}}
            result (bridge/dispatch @state-dir "crew-test" "/crew ketch" ctx nil)]
        (should= :command (:type result))
        (should= :crew (:command result))
        (should= "switched crew to ketch" (:message result))))

    (it "logs crew changes when switching to a known crew"
      (let [ctx {:crew "main" :crew-members {"main" {} "ketch" {}}}]
        (log/capture-logs
          (bridge/dispatch @state-dir "crew-test" "/crew ketch" ctx nil)
          (let [entry (last @log/captured-logs)]
            (should= :session/crew-changed (:event entry))
            (should= "crew-test" (:session entry))
            (should= "main" (:from entry))
            (should= "ketch" (:to entry))))))

    (it "persists the switched crew in the session"
      (let [ctx {:crew "main" :crew-members {"main" {} "ketch" {}}}]
        (bridge/dispatch @state-dir "crew-test" "/crew ketch" ctx nil)
        (let [session (helper/get-session @state-dir "crew-test")]
          (should= "ketch" (:crew session))
          (should-not (contains? session :agent))
          (should= nil (:model session))
          (should= nil (:provider session)))))

    (it "returns an error for an unknown crew name"
      (let [ctx {:crew "main" :crew-members {"main" {} "ketch" {}}}
            result (bridge/dispatch @state-dir "crew-test" "/crew nonexistent" ctx nil)]
        (should= :command (:type result))
        (should= :unknown (:command result))
        (should= "unknown crew: nonexistent" (:message result))))
    )

  (context "cancellation"
    (it "cancels an active turn and runs cancel hooks"
      (let [called? (atom false)
            turn (bridge-cancel/begin-turn! "cancel-test")]
        (bridge-cancel/on-cancel! "cancel-test" #(reset! called? true))
        (bridge-cancel/cancel! "cancel-test")
        (should @called?)
        (should (bridge-cancel/cancelled? "cancel-test"))
        (bridge-cancel/end-turn! "cancel-test" turn)))

    (it "applies a pending cancel to the next turn"
      (bridge-cancel/cancel! "cancel-later")
      (let [turn (bridge-cancel/begin-turn! "cancel-later")]
        (should (bridge-cancel/cancelled? "cancel-later"))
        (bridge-cancel/end-turn! "cancel-later" turn)
        (should-not (bridge-cancel/cancelled? "cancel-later"))))
    )

  (context "dispatch!"
    (around [it]
      (helper/with-memory-store
        (let [dir (str (System/getProperty "user.dir") "/target/test-state/bridge-dispatch-bang-" (random-uuid))]
          (delete-dir! dir)
          (system/with-system {:state-dir dir}
            (it)))))

    (it "uses the stored session crew when no override is provided"
      (let [captured (atom nil)
            cfg      {:defaults  {:crew "main" :model "fast"}
                      :crew      {"main"  {:soul "Main soul"  :model "fast"}
                                  "pinky" {:soul "Pinky soul" :model "smart"}}
                      :models    {"fast"  {:model "anvil-x-mini" :provider marigold/quantum-anvil :context-window 16000}
                                  "smart" {:model "anvil-x"      :provider marigold/quantum-anvil :context-window 128000}}
                      :providers {marigold/quantum-anvil {:api marigold/anvil-api}}}]
        (config/set-snapshot! cfg)
        (helper/create-session! (system/get :state-dir) "pinky-session" {:crew "pinky"})
        (with-redefs [single-turn/run-turn! (fn [_ _ opts]
                                              (reset! captured opts)
                                              {:message {:role "assistant" :content "ok"}})]
          (bridge/dispatch! {:session-key "pinky-session" :input "hello"}))
        (should= "anvil-x" (:model @captured))
        (should= "Pinky soul" (:soul @captured))))

    (it "lets an explicit crew override win over the stored session crew"
      (let [captured (atom nil)
            cfg      {:defaults  {:crew "main" :model "fast"}
                      :crew      {"main"  {:soul "Main soul"  :model "fast"}
                                  "pinky" {:soul "Pinky soul" :model "smart"}}
                      :models    {"fast"  {:model "anvil-x-mini" :provider marigold/quantum-anvil :context-window 16000}
                                  "smart" {:model "anvil-x"      :provider marigold/quantum-anvil :context-window 128000}}
                      :providers {marigold/quantum-anvil {:api marigold/anvil-api}}}]
        (config/set-snapshot! cfg)
        (helper/create-session! (system/get :state-dir) "pinky-session" {:crew "pinky"})
        (with-redefs [single-turn/run-turn! (fn [_ _ opts]
                                              (reset! captured opts)
                                              {:message {:role "assistant" :content "ok"}})]
          (bridge/dispatch! {:session-key "pinky-session" :input "hello" :crew-override "main"}))
        (should= "anvil-x-mini" (:model @captured))
        (should= "Main soul" (:soul @captured)))))

  (context "session cwd cascade"
    (it "explicit override beats crew and channel default"
      (should= "/explicit/path"
               (bridge/resolve-session-cwd "/explicit/path" {:cwd "/crew/path"} "/channel/default")))

    (it "crew cwd beats channel default when no explicit override"
      (should= "/crew/path"
               (bridge/resolve-session-cwd nil {:cwd "/crew/path"} "/channel/default")))

    (it "channel default is used when neither explicit nor crew cwd is set"
      (should= "/channel/default"
               (bridge/resolve-session-cwd nil {} "/channel/default"))))
  )
