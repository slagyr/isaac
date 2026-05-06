(ns comm.discord.lifecycle-spec
  (:require [speclj.core :refer :all]
            [gherclj.core :as g]
            [gherclj.lifecycle :as lifecycle]
            [isaac.features.steps.discord :as discord]))

(describe "Discord client lifecycle"

  (before-all (lifecycle/run-before-feature-hooks!))
  (before (g/reset!) (lifecycle/run-before-scenario-hooks!))
  (after (lifecycle/run-after-scenario-hooks!))
  (after-all (lifecycle/run-after-feature-hooks!))

  (it "Discord client starts on isaac server startup when config is present"
    ;; given default Grover setup
    ;; and config:
    ;; given the Discord Gateway is faked in-memory
    ;; given config:
    ;; and the Isaac server is started
    ;; then the Discord client is connected
    (pending "not yet implemented"))

  (it "Discord client starts when config is added mid-run"
    ;; given default Grover setup
    ;; and config:
    ;; given the Discord Gateway is faked in-memory
    ;; given the Isaac server is started
    ;; when the isaac EDN file "config/isaac.edn" exists with:
    ;; then the log has entries matching:
    ;; then the Discord client is connected
    (pending "not yet implemented"))

  (it "Discord client stops when its config is removed mid-run"
    ;; given default Grover setup
    ;; and config:
    ;; given the Discord Gateway is faked in-memory
    ;; given config:
    ;; and the Isaac server is started
    ;; then the Discord client is connected
    ;; when the isaac EDN file "config/isaac.edn" exists with:
    ;; then the log has entries matching:
    ;; then the Discord client is disconnected
    (pending "not yet implemented"))

  (it "allow-from updates take effect without restart"
    ;; given default Grover setup
    ;; and config:
    ;; given the Discord Gateway is faked in-memory
    ;; given config:
    ;; and the Isaac server is started
    ;; given the Discord client is ready as bot "bot-default"
    ;; when the isaac EDN file "config/isaac.edn" exists with:
    ;; then the log has entries matching:
    ;; when Discord sends MESSAGE_CREATE:
    ;; then the Discord client accepted a message with:
    ;; and the log has no entries matching:
    (pending "not yet implemented")))
