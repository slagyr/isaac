(ns comm.discord.routing-spec
  (:require [speclj.core :refer :all]
            [gherclj.core :as g]
            [gherclj.lifecycle :as lifecycle]
            [isaac.features.steps.discord :as discord]))

(describe "Discord session routing"

  (before-all (lifecycle/run-before-feature-hooks!))
  (before (g/reset!) (lifecycle/run-before-scenario-hooks!))
  (after (lifecycle/run-after-scenario-hooks!))
  (after-all (lifecycle/run-after-feature-hooks!))

  (it "first message in a channel creates a session named discord-<channel-id>"
    ;; given default Grover setup in "/test/discord-routing"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then session "discord-C999" has transcript matching:
    (pending "not yet implemented"))

  (it "second author in the same channel routes to the same session"
    ;; given default Grover setup in "/test/discord-routing"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given the following sessions exist:
    ;; and the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then session "discord-C999" has transcript matching:
    (pending "not yet implemented"))

  (it "per-channel session override routes to the configured session"
    ;; given default Grover setup in "/test/discord-routing"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given the following sessions exist:
    ;; and config:
    ;; and the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then session "kitchen" has transcript matching:
    (pending "not yet implemented"))

  (it "Discord-wide crew and model apply when the channel has no override"
    ;; given default Grover setup in "/test/discord-routing"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given config:
    ;; and the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then the system prompt contains "Bite my shiny metal prompts."
    ;; and session "discord-C999" has transcript matching:
    (pending "not yet implemented"))

  (it "per-channel model override wins over the Discord-wide model"
    ;; given default Grover setup in "/test/discord-routing"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given config:
    ;; and the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then session "discord-C999" has transcript matching:
    (pending "not yet implemented")))
