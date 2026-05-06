(ns comm.discord.typing-spec
  (:require [speclj.core :refer :all]
            [gherclj.core :as g]
            [gherclj.lifecycle :as lifecycle]
            [isaac.features.steps.discord :as discord]))

(describe "Discord typing indicator"

  (before-all (lifecycle/run-before-feature-hooks!))
  (before (g/reset!) (lifecycle/run-before-scenario-hooks!))
  (after (lifecycle/run-after-scenario-hooks!))
  (after-all (lifecycle/run-after-feature-hooks!))

  (it "turn start posts a typing indicator to the Discord channel"
    ;; given default Grover setup in "/test/discord-typing"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then an outbound HTTP request to "https://discord.com/api/v10/channels/C999/typing" matches:
    (pending "not yet implemented")))
