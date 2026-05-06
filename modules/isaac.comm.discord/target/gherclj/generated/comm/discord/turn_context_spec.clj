(ns comm.discord.turn-context-spec
  (:require [speclj.core :refer :all]
            [gherclj.core :as g]
            [gherclj.lifecycle :as lifecycle]
            [isaac.features.steps.discord :as discord]))

(describe "Discord per-turn context"

  (before-all (lifecycle/run-before-feature-hooks!))
  (before (g/reset!) (lifecycle/run-before-scenario-hooks!))
  (after (lifecycle/run-after-scenario-hooks!))
  (after-all (lifecycle/run-after-feature-hooks!))

  (it "trusted system block is appended to the system prompt"
    ;; given default Grover setup in "/test/discord-context"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then the system prompt contains "isaac.inbound_meta.v1"
    ;; and the system prompt contains "channel_id"
    ;; and the system prompt contains "sender_id"
    ;; and the system prompt contains "trusted metadata"
    (pending "not yet implemented"))

  (it "untrusted user-message prefix is prepended to the user message"
    ;; given default Grover setup in "/test/discord-context"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then session "discord-C999" has transcript matching:
    (pending "not yet implemented"))

  (it "configured channel label and guild name are included in the untrusted prefix"
    ;; given default Grover setup in "/test/discord-context"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given config:
    ;; and the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then session "discord-C999" has transcript matching:
    (pending "not yet implemented"))

  (it "channel label is omitted when the channel has no configured name"
    ;; given default Grover setup in "/test/discord-context"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then session "discord-C999" has transcript matching:
    (pending "not yet implemented"))

  (it "was_mentioned is true when bot id is in the mentions array"
    ;; given default Grover setup in "/test/discord-context"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then the system prompt contains "was_mentioned"
    ;; and the system prompt contains ":true"
    (pending "not yet implemented"))

  (it "was_mentioned is false when bot id is not in the mentions array"
    ;; given default Grover setup in "/test/discord-context"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then the system prompt contains "was_mentioned"
    ;; and the system prompt contains ":false"
    (pending "not yet implemented")))
