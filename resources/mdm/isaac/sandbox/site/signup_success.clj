(ns mdm.isaac.sandbox.site.signup-success
  (:require [mdm.isaac.sandbox.util :as util]))

(defn render []
  [:div#app-root
   (util/header-bare)
   [:main#signup-success
    [:div.floating-panel
     [:h2 "Thanks for signing up!"]
     [:p.margin-top-default "Check your email to confirm your account."]
     [:div.alternatives
      [:a.-home.button.primary {:href "/"} "Go Home"]]]]
   (util/footer)])
