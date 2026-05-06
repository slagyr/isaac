(ns comm.discord.gateway-spec
  (:require [speclj.core :refer :all]
            [gherclj.core :as g]
            [gherclj.lifecycle :as lifecycle]
            [isaac.features.steps.discord :as discord]))

(describe "Discord Gateway connection"

  (before-all (lifecycle/run-before-feature-hooks!))
  (before (g/reset!) (lifecycle/run-before-scenario-hooks!))
  (after (lifecycle/run-after-scenario-hooks!))
  (after-all (lifecycle/run-after-feature-hooks!))

  (it "client sends IDENTIFY after receiving HELLO"
    (discord/discord-faked)
    (discord/discord-connects)
    (discord/discord-sends-hello {:headers ["heartbeat_interval" "45000"], :rows []})
    (discord/discord-sends-identify {:headers ["token" "test-token"], :rows [["intents" "37377"]]}))

  (it "client sends HEARTBEAT at the interval from HELLO"
    (discord/discord-faked)
    (discord/discord-connects)
    (discord/discord-sends-hello {:headers ["heartbeat_interval" "45000"], :rows []})
    (discord/test-clock-advances 45000)
    (discord/discord-sends-heartbeat))

  (it "client is connected after receiving READY"
    (discord/discord-faked)
    (discord/discord-connects)
    (discord/discord-sends-hello {:headers ["heartbeat_interval" "45000"], :rows []})
    (discord/discord-sends-ready {:headers ["session_id" "fake-session"], :rows []})
    (discord/discord-client-connected)))
