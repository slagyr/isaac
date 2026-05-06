(ns comm.discord.reply-spec
  (:require [speclj.core :refer :all]
            [gherclj.core :as g]
            [gherclj.lifecycle :as lifecycle]
            [isaac.features.steps.discord :as discord]))

(describe "Discord reply via REST API"

  (before-all (lifecycle/run-before-feature-hooks!))
  (before (g/reset!) (lifecycle/run-before-scenario-hooks!))
  (after (lifecycle/run-after-scenario-hooks!))
  (after-all (lifecycle/run-after-feature-hooks!))

  (it "crew text response is posted back to the originating Discord channel"
    ;; given default Grover setup in "/test/discord-reply"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given the following model responses are queued:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then an outbound HTTP request to "https://discord.com/api/v10/channels/C999/messages" matches:
    (pending "not yet implemented"))

  (it "a non-retryable Discord REST error is logged"
    ;; given default Grover setup in "/test/discord-reply"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; given the following model responses are queued:
    ;; and the URL "https://discord.com/api/v10/channels/C999/messages" responds with:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then the log has entries matching:
    (pending "not yet implemented")))
