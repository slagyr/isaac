(ns comm.discord.intake-spec
  (:require [speclj.core :refer :all]
            [gherclj.core :as g]
            [gherclj.lifecycle :as lifecycle]
            [isaac.features.steps.discord :as discord]))

(describe "Discord message intake"

  (before-all (lifecycle/run-before-feature-hooks!))
  (before (g/reset!) (lifecycle/run-before-scenario-hooks!))
  (after (lifecycle/run-after-scenario-hooks!))
  (after-all (lifecycle/run-after-feature-hooks!))

  (it "accept MESSAGE_CREATE from an allowed user and guild"
    (discord/discord-faked)
    (discord/discord-client-ready-as-bot "bot-default")
    (discord/discord-sends-message-create {:headers ["channel_id" "999001"], :rows [["guild_id" "789012"] ["author.id" "123456"] ["content" "hello"]]})
    (discord/discord-client-accepted-message {:headers ["content" "hello"], :rows [["author.id" "123456"]]}))

  (it "ignore MESSAGE_CREATE from a guild not on the allow list"
    (discord/discord-faked)
    (discord/discord-client-ready-as-bot "bot-default")
    (discord/discord-sends-message-create {:headers ["channel_id" "999001"], :rows [["guild_id" "888888"] ["author.id" "123456"] ["content" "hi"]]})
    (discord/discord-client-accepted-no-messages))

  (it "ignore MESSAGE_CREATE from the bot itself even if on allow list"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given config:
    ;; given the Discord client is ready as bot "555"
    ;; when Discord sends MESSAGE_CREATE:
    ;; then the Discord client accepted no messages
    (pending "not yet implemented")))
