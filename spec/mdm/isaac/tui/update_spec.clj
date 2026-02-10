(ns mdm.isaac.tui.update-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.tui.core :as core]
            [mdm.isaac.tui.update :as update]))

(describe "Update Function"

  (describe "keyboard input"
    (it "types 'q' as a regular character"
      (let [state (core/init-state)
            msg   {:type :key-press :key "q"}
            [new-state cmd] (update/update-fn state msg)]
        (should= "q" (:input new-state))
        (should-be-nil cmd)))

    (it "returns quit command on ctrl+c"
      (let [state (core/init-state)
            msg   {:type :key-press :key "ctrl+c"}
            [_ cmd] (update/update-fn state msg)]
        (should= :quit cmd)))

    (it "cycles panel on tab"
      (let [state (core/init-state)
            msg   {:type :key-press :key :tab}
            [new-state _] (update/update-fn state msg)]
        (should= :thoughts (:active-panel new-state))))

    (it "appends character to input on regular key (string)"
      (let [state (core/init-state)
            msg   {:type :key-press :key "a"}
            [new-state _] (update/update-fn state msg)]
        (should= "a" (:input new-state))))

    (it "appends character to input on regular key (char)"
      (let [state (core/init-state)
            msg   {:type :key-press :key \a}
            [new-state _] (update/update-fn state msg)]
        (should= "a" (:input new-state))))

    (it "handles backspace"
      (let [state (-> (core/init-state)
                      (core/set-input "hello"))
            msg   {:type :key-press :key :backspace}
            [new-state _] (update/update-fn state msg)]
        (should= "hell" (:input new-state))))

    (it "clears input and returns send command on enter with input"
      (let [state (-> (core/init-state)
                      (core/set-input "/goals"))
            msg   {:type :key-press :key :enter}
            [new-state cmd] (update/update-fn state msg)]
        (should= "" (:input new-state))
        (should= :send (:type cmd))
        (should= "/goals" (:text cmd))))

    (it "does nothing on enter with empty input"
      (let [state (core/init-state)
            msg   {:type :key-press :key :enter}
            [new-state cmd] (update/update-fn state msg)]
        (should= "" (:input new-state))
        (should-be-nil cmd)))

    (it "appends pasted text to input"
      (let [state (core/init-state)
            msg   {:type :paste :text "hello world"}
            [new-state _] (update/update-fn state msg)]
        (should= "hello world" (:input new-state))))

    (it "appends pasted text to existing input"
      (let [state (-> (core/init-state)
                      (core/set-input "prefix: "))
            msg   {:type :paste :text "pasted content"}
            [new-state _] (update/update-fn state msg)]
        (should= "prefix: pasted content" (:input new-state)))))

  (describe "command parsing"
    (it "parses /goals command"
      (let [cmd (update/parse-command "/goals")]
        (should= :goals/list (:action cmd))))

    (it "parses /add command with content"
      (let [cmd (update/parse-command "/add Learn macros")]
        (should= :goals/add (:action cmd))
        (should= "Learn macros" (:content cmd))))

    (it "parses /thoughts command"
      (let [cmd (update/parse-command "/thoughts")]
        (should= :thoughts/recent (:action cmd))))

    (it "parses /shares command"
      (let [cmd (update/parse-command "/shares")]
        (should= :shares/unread (:action cmd))))

    (it "parses /search command"
      (let [cmd (update/parse-command "/search Clojure")]
        (should= :thoughts/search (:action cmd))
        (should= "Clojure" (:query cmd))))

    (it "treats non-command input as chat"
      (let [cmd (update/parse-command "Hello Isaac")]
        (should= :chat (:action cmd))
        (should= "Hello Isaac" (:text cmd)))))

  (describe "WebSocket messages"
    (it "updates goals on goals/list response"
      (let [state (core/init-state)
            goals [{:id 1 :content "Learn" :status :active}]
            msg   {:type :ws-message :action :goals/list :payload goals}
            [new-state _] (update/update-fn state msg)]
        (should= goals (:goals new-state))))

    (it "updates thoughts on thoughts/recent response"
      (let [state (core/init-state)
            thoughts [{:id 1 :content "A thought" :type :thought}]
            msg   {:type :ws-message :action :thoughts/recent :payload thoughts}
            [new-state _] (update/update-fn state msg)]
        (should= thoughts (:thoughts new-state))))

    (it "updates shares on shares/unread response"
      (let [state (core/init-state)
            shares [{:id 1 :content "Share" :type :share}]
            msg   {:type :ws-message :action :shares/unread :payload shares}
            [new-state _] (update/update-fn state msg)]
        (should= shares (:shares new-state))))

    (it "updates connection status on connect"
      (let [state (core/init-state)
            msg   {:type :ws-connect}
            [new-state _] (update/update-fn state msg)]
        (should= :connected (:connection-status new-state))))

    (it "updates connection status on disconnect without auto-reconnect"
      (let [state (-> (core/init-state)
                      (core/set-connection-status :connected))
            msg   {:type :ws-disconnect}
            [new-state cmd] (update/update-fn state msg)]
        (should= :disconnected (:connection-status new-state))
        (should-be-nil cmd)))

    (it "triggers reconnect on disconnect with auto-reconnect when under max attempts"
      (let [state (-> (core/init-state)
                      (core/set-connection-status :connected))
            msg   {:type :ws-disconnect :auto-reconnect true}
            [new-state cmd] (update/update-fn state msg)]
        (should= :reconnecting (:connection-status new-state))
        (should= 1 (:reconnect-attempts new-state))
        (should= :reconnect (:type cmd))
        (should= 1000 (:delay-ms cmd))))

    (it "calculates exponential backoff delay for reconnect"
      (let [state (-> (core/init-state)
                      (core/set-connection-status :connected)
                      (assoc :reconnect-attempts 3))
            msg   {:type :ws-disconnect :auto-reconnect true}
            [new-state cmd] (update/update-fn state msg)]
        (should= :reconnecting (:connection-status new-state))
        (should= 4 (:reconnect-attempts new-state))
        ;; Delay is calculated BEFORE incrementing, so attempt 3 = 8s delay
        (should= 8000 (:delay-ms cmd))))

    (it "stops reconnecting after max attempts"
      (let [state (-> (core/init-state)
                      (core/set-connection-status :connected)
                      (assoc :reconnect-attempts 5))
            msg   {:type :ws-disconnect :auto-reconnect true}
            [new-state cmd] (update/update-fn state msg)]
        (should= :disconnected (:connection-status new-state))
        (should= 6 (:reconnect-attempts new-state))
        (should-be-nil cmd)))

    (it "adds new goal to goals list on goals/add response"
      (let [existing-goal {:id 1 :content "Existing" :status :active}
            state (-> (core/init-state)
                      (core/set-goals [existing-goal]))
            new-goal {:id 2 :content "New goal" :status :pending}
            msg {:type :ws-message :action :goals/add :payload new-goal}
            [new-state _] (update/update-fn state msg)]
        (should= 2 (count (:goals new-state)))
        (should= new-goal (second (:goals new-state)))))

    (it "adds user message on chat/send-user response"
      (let [state (core/init-state)
            msg {:type :ws-message :action :chat/send-user :payload {:content "Hello"}}
            [new-state _] (update/update-fn state msg)]
        (should= 1 (count (:messages new-state)))
        (should= {:role :user :content "Hello"} (first (:messages new-state)))))

    (it "adds Isaac response on chat/send response"
      (let [state (-> (core/init-state)
                      (core/add-message {:role :user :content "Hello"}))
            msg {:type :ws-message :action :chat/send :payload {:response "Hi there!"}}
            [new-state _] (update/update-fn state msg)]
        (should= 2 (count (:messages new-state)))
        (should= {:role :isaac :content "Hi there!"} (second (:messages new-state)))))

    (it "sets error on ws-error message"
      (let [state (core/init-state)
            msg {:type :ws-error :message "Connection failed"}
            [new-state _] (update/update-fn state msg)]
        (should= "Connection failed" (:error new-state))))

    (it "clears previous error on successful ws-message"
      (let [state (-> (core/init-state)
                      (core/set-error "Previous error"))
            msg {:type :ws-message :action :goals/list :payload []}
            [new-state _] (update/update-fn state msg)]
        (should-be-nil (:error new-state))))))
