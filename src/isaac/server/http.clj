(ns isaac.server.http
  (:require [isaac.server.routes :as routes]))

(defn create-handler []
  routes/handler)
