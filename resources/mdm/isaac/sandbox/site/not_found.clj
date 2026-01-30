(ns mdm.isaac.sandbox.site.not-found
  (:require [mdm.isaac.sandbox.util :as util]))

(defn render []
  [:div#app-root
   (util/header-bare)
   [:main#not-found
    [:div.floating-panel
     [:h1.uppercase "Not Found"]
     [:h4.margin-top-default "We couldn't find what you were looking for."]]]
   (util/footer)])


