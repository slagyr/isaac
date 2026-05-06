(ns comm.discord.splitting-spec
  (:require [speclj.core :refer :all]
            [gherclj.core :as g]
            [gherclj.lifecycle :as lifecycle]
            [isaac.features.steps.discord :as discord]))

(describe "Discord long-message splitting"

  (before-all (lifecycle/run-before-feature-hooks!))
  (before (g/reset!) (lifecycle/run-before-scenario-hooks!))
  (after (lifecycle/run-after-scenario-hooks!))
  (after-all (lifecycle/run-after-feature-hooks!))

  (it "a response longer than the message cap is split at newline boundaries"
    ;; given default Grover setup in "/test/discord-splitting"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given config:
    ;; and the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then an outbound HTTP request to "https://discord.com/api/v10/channels/C999/messages" matches:
    ;; and an outbound HTTP request to "https://discord.com/api/v10/channels/C999/messages" matches:
    ;; and an outbound HTTP request to "https://discord.com/api/v10/channels/C999/messages" matches:
    (pending "not yet implemented"))

  (it "a single line longer than the cap is hard-split at the cap boundary"
    ;; given default Grover setup in "/test/discord-splitting"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given config:
    ;; and the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then an outbound HTTP request to "https://discord.com/api/v10/channels/C999/messages" matches:
    ;; and an outbound HTTP request to "https://discord.com/api/v10/channels/C999/messages" matches:
    (pending "not yet implemented")))
