(ns mdm.isaac.sandbox.site.marketing
  (:require [mdm.isaac.sandbox.util :as util]))

(defn render []
  [:div#app-root
   (util/header-unauthenticated)
   [:main#marketing
    [:section.home.hero
     [:div.call-to-action
      [:img {:src "/images/logos/type-black.png"}]
      [:h4.subtitle "AI-Assisted Memory"]
      [:a.button.primary {:href "/signup"}
       [:h3 "Sign Up"]]
      [:p "Create an account. It's free for now.  Gain super powered memory today."]]]]
   (util/footer)]
  )
