(ns isaac.session.key-spec
  (:require
    [isaac.session.key :as key]
    [speclj.core :refer :all]))

(describe "isaac.session.key"

  (describe "build-key"

    (it "builds a key from agent, channel, chatType, and conversation"
      (should= "agent:bot1:general:dm:conv123"
               (key/build-key {:agent "bot1" :channel "general" :chatType "dm" :conversation "conv123"})))

    (it "includes nil values as literal nil strings when fields are missing"
      (should= "agent::::"
               (key/build-key {:agent nil :channel nil :chatType nil :conversation nil}))))

  (describe "build-thread-key"

    (it "appends thread id to a parent key"
      (should= "agent:bot1:general:dm:conv123:thread:t456"
               (key/build-thread-key "agent:bot1:general:dm:conv123" "t456"))))

  (describe "parse-key"

    (it "parses a key string into its component parts"
      (let [parsed (key/parse-key "agent:bot1:general:dm:conv123")]
        (should= "bot1" (:agent parsed))
        (should= "general" (:channel parsed))
        (should= "dm" (:chatType parsed))
        (should= "conv123" (:conversation parsed))))

    (it "returns nil for a key with fewer than 5 parts"
      (should-be-nil (key/parse-key "agent:bot1:general")))

    (it "optionally parses a short 3-part legacy key"
      (should= {:agent        "main"
                :channel      "cli"
                :chatType     "direct"
                :conversation "main"}
               (key/parse-key "agent:main:main" {:allow-short? true})))

    (it "optionally includes :crew in parsed key data"
      (should= {:agent        "main"
                :crew         "main"
                :channel      "cli"
                :chatType     "direct"
                :conversation "main"}
               (key/parse-key "agent:main:main" {:allow-short? true :include-crew? true})))

    (it "round-trips through build-key and parse-key"
      (let [params {:agent "a" :channel "c" :chatType "t" :conversation "x"}
            key-str (key/build-key params)
            parsed  (key/parse-key key-str)]
        (should= params parsed)))))
