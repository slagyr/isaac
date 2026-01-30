(ns mdm.isaac.sandbox.util
  (:require [speclj.core :refer :all]))

(defn header-bare []
  [:header
   [:nav
    [:ul
     [:li.logo [:a {:href "/"} [:span]]]]]])

(defn header-authenticated []
  [:header
   [:nav
    [:ul
     [:li.-mobile-menu-toggle.mobile-menu-toggle [:span.fa-solid.fa-bars]]
     [:li.logo [:a {:href "/"} [:span]]]
     [:li.-user-menu-toggle.user-menu-toggle [:span.fa-solid.fa-bars]]]]])

(defn header-unauthenticated []
  [:header
   [:nav
    [:ul
     [:li.logo [:a {:href "/"} [:span]]]
     [:li
      [:ul.inline-nav
       [:li
        [:a {:href "#"} "Sign Up"]
        [:span]]
       [:li
        [:a {:href "#"} "Log In"]
        [:span]]]]]]])

(defn footer []
  [:footer
   [:div.container
    [:div.row.no-margin
     [:div.column "© 2025 Airworthy, LLC"]
     [:div.column
      [:ul
       [:li
        [:a {:href "mailto:contact@airworthy.co"} "Contact Us"]]
       [:li
        [:a {:href "/terms"} "Terms"]]]]]]])
