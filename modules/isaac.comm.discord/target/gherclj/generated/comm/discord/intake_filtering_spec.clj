(ns comm.discord.intake-filtering-spec
  (:require [speclj.core :refer :all]
            [gherclj.core :as g]
            [gherclj.lifecycle :as lifecycle]
            [isaac.features.steps.discord :as discord]))

(describe "Discord intake filtering"

  (before-all (lifecycle/run-before-feature-hooks!))
  (before (g/reset!) (lifecycle/run-before-scenario-hooks!))
  (after (lifecycle/run-after-scenario-hooks!))
  (after-all (lifecycle/run-after-feature-hooks!))

  (it "guild post accepted when its guild is allowlisted"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; when Discord sends MESSAGE_CREATE:
    ;; then the Discord client accepted a message with:
    (pending "not yet implemented"))

  (it "guild post dropped when its guild is not allowlisted"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; when Discord sends MESSAGE_CREATE:
    ;; then the Discord client accepted no messages
    ;; and the log has entries matching:
    (pending "not yet implemented"))

  (it "DM accepted when its author is allowlisted"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; when Discord sends MESSAGE_CREATE:
    ;; then the Discord client accepted a message with:
    (pending "not yet implemented"))

  (it "DM dropped when its author is not allowlisted"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; when Discord sends MESSAGE_CREATE:
    ;; then the Discord client accepted no messages
    ;; and the log has entries matching:
    (pending "not yet implemented"))

  (it "bot's own message is dropped even when allowlists match"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given config:
    ;; given the Discord client is ready as bot "555"
    ;; when Discord sends MESSAGE_CREATE:
    ;; then the Discord client accepted no messages
    ;; and the log has entries matching:
    (pending "not yet implemented")))
