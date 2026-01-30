(ns mdm.isaac.sandbox.components.menu
  (:require [mdm.isaac.sandbox.util :as util]))

(defn render []
  [:div#app-root
   [:header
    [:nav
     [:ul
      [:li.mobile-menu-toggle [:span.fa-solid.fa-bars]]
      [:li.logo [:a {:href "/"} [:span]]]
      [:li.user-menu-toggle [:span.fa-solid.fa-bars]]]]]
   [:div.user-menu-backdrop
    [:div.user-menu
     [:ul
      [:li.menu-header
       [:div.avatar [:img {:src "/images/icons/logo.png"}]]
       [:span "leonardo.dapenguin@airworthy.co"]]
      [:li [:a {:href "#"} "My Memories"]]
      [:li [:a {:href "#"} "Sign Out"]]]]]
   [:div.flash-root
    [:div.flash-message.success
     [:div.container
      [:p [:span "✕"]
       [:span.flash-message-text "This is a success flash"]]]]]
   [:main [:div.floating-panel "Some content"]]
   (util/footer)])

