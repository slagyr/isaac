(ns comm.discord.reconnect-spec
  (:require [speclj.core :refer :all]
            [gherclj.core :as g]
            [gherclj.lifecycle :as lifecycle]
            [isaac.features.steps.discord :as discord]))

(describe "Discord Gateway reconnect"

  (before-all (lifecycle/run-before-feature-hooks!))
  (before (g/reset!) (lifecycle/run-before-scenario-hooks!))
  (after (lifecycle/run-after-scenario-hooks!))
  (after-all (lifecycle/run-after-feature-hooks!))

  (it "resumable disconnect triggers RESUME with session_id and last sequence"
    (discord/discord-faked)
    (discord/discord-client-ready-as-bot "bot-default")
    (discord/discord-closes-connection 4000)
    (discord/discord-sends-resume {:headers ["token" "test-token"], :rows [["session_id" "fake-session"] ["seq" "1"]]}))

  (it "fatal disconnect (invalid token) is logged and does not reconnect"
    ;; given the Discord Gateway is faked in-memory
    ;; and config:
    ;; given the Discord client is ready as bot "bot-default"
    ;; when Discord closes the connection with code 4004
    ;; then the log has entries matching:
    (pending "not yet implemented")))
