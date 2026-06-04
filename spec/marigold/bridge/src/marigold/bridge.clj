(ns marigold.bridge
  "Fixture module for manifest-only berth processing tests. Declares
   the :marigold.bridge/signal-route berth; consumers contribute route
   entries that get installed in the nexus via signal/register-route!."
  (:require
    [isaac.module :as module]))

(defn create-module
  "Module-level :factory — no on-startup/on-shutdown needed for the
   manifest-only berth processing scenario."
  []
  (module/module))
