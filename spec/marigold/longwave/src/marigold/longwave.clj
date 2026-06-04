(ns marigold.longwave
  "Fixture consumer module: contributes a route entry to
   :marigold.bridge/signal-route. The handler symbol below is the
   value the per-entry factory installs in the nexus — its body is a
   stand-in (no HTTP dispatch in these scenarios)."
  (:require
    [isaac.module :as module]))

(defn create-module
  []
  (module/module))

(defn ping-handler
  [_request]
  {:status 200 :body "pong"})
