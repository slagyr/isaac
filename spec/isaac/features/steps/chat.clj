(ns isaac.features.steps.chat
  (:require
    [clojure.string :as str]
    [gherclj.core :as g :refer [defwhen defthen]]
    [isaac.cli.chat :as chat]))

(defwhen chat-started "chat is started with {args:string}"
  [args]
  (let [opts (if (str/blank? args)
               {}
               (loop [remaining (str/split args #"\s+")
                      result    {}]
                 (if (empty? remaining)
                   result
                   (let [[flag & rest-args] remaining]
                     (case flag
                       "--agent"   (recur (rest rest-args) (assoc result :agent (first rest-args)))
                       "--model"   (recur (rest rest-args) (assoc result :model (first rest-args)))
                       "--resume"  (recur rest-args (assoc result :resume true))
                       "--session" (recur (rest rest-args) (assoc result :session (first rest-args)))
                       (recur rest-args result))))))
        output (with-out-str
                 (g/assoc! :chat-ctx
                           (chat/prepare opts {:sdir    (g/get :state-dir)
                                               :models  (g/get :models)
                                               :agents  (g/get :agents)})))]
    (g/assoc! :chat-output output)))

(defthen active-agent "the active agent is {expected:string}"
  [expected]
  (g/should= expected (:agent (g/get :chat-ctx))))

(defthen active-model "the active model is {expected:string}"
  [expected]
  (g/should= expected (:model (g/get :chat-ctx))))

(defthen active-provider "the active provider is {expected:string}"
  [expected]
  (g/should= expected (:provider (g/get :chat-ctx))))

(defthen active-soul-contains "the active soul contains {expected:string}"
  [expected]
  (g/should (str/includes? (:soul (g/get :chat-ctx)) expected)))

(defthen active-session "the active session is {expected:string}"
  [expected]
  (g/should= expected (:session-key (g/get :chat-ctx))))

(defthen context-window-is "the context window is {int}"
  [n]
  (let [n (if (string? n) (parse-long n) n)]
    (g/should= n (:context-window (g/get :chat-ctx)))))
