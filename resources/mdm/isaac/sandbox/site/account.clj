(ns mdm.isaac.sandbox.site.account
  (:require [mdm.isaac.sandbox.util :as util]))

(defn render []
  [:div#app-root
   (util/header-authenticated)
   [:main#memory
    [:div
     [:div.account-section
      [:h2.account-title "Account"]
      [:h4.user-name
       [:label "Name:"]
        " Jimmy Bob"]
      [:h4.user-name
       [:label "Email:"]
       " Jimmy@bob.com"]
      [:div.button-group.left
       [:button.primary.hover
        [:span.fa-solid.fa-download]
        " Download My Data"]
       [:button.error.hover
        [:span.fa-solid.fa-trash]
        " Delete Account and All My Data"]]]]]
   (util/footer)])
