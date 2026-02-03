(ns mdm.isaac.tui.update-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.tui.core :as core]
            [mdm.isaac.tui.update :as update]))

(describe "Update Function"

  (describe "keyboard input"
    (it "returns quit command on 'q' key"
      (let [state (core/init-state)
            msg   {:type :key-press :key "q"}
            [_ cmd] (update/update-fn state msg)]
        (should= :quit cmd)))

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
        (should-be-nil cmd))))

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

    (it "updates connection status on disconnect"
      (let [state (-> (core/init-state)
                      (core/set-connection-status :connected))
            msg   {:type :ws-disconnect}
            [new-state _] (update/update-fn state msg)]
        (should= :disconnected (:connection-status new-state))))))
