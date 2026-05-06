(ns comm.discord.idle-spec
  (:require [speclj.core :refer :all]
            [gherclj.core :as g]
            [gherclj.lifecycle :as lifecycle]
            [isaac.features.steps.discord :as discord]))

(describe "Discord Gateway idle handling"

  (before-all (lifecycle/run-before-feature-hooks!))
  (before (g/reset!) (lifecycle/run-before-scenario-hooks!))
  (after (lifecycle/run-after-scenario-hooks!))
  (after-all (lifecycle/run-after-feature-hooks!))

  (it "idle gap between IDENTIFY and READY does not synthesize a close"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; when the Discord client connects
    ;; when Discord sends HELLO:
    ;; when Discord stays silent for 250 milliseconds
    ;; then the Discord client is connected
    ;; and the log has no entries matching:
    (pending "not yet implemented"))

  (it "idle gap after READY does not synthesize a close"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; when Discord stays silent for 1000 milliseconds
    ;; then the Discord client is connected
    ;; and the log has no entries matching:
    (pending "not yet implemented"))

  (it "a real close with a status code still propagates the code"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; when Discord closes the connection with code 4014 reason "Disallowed intents"
    ;; then the log has entries matching:
    (pending "not yet implemented")))
