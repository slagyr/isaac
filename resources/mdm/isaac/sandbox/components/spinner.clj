(ns mdm.isaac.sandbox.components.spinner
  (:require [mdm.isaac.sandbox.util :as util]))

(defn render []

  [:div#app-root
   (util/header-bare)
   [:main "Some Content"]
   [:div {:class "site-spinner"}]
   (util/footer)]

  )
