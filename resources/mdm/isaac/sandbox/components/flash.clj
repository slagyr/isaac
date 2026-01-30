(ns mdm.isaac.sandbox.components.flash
  (:require [mdm.isaac.sandbox.util :as util]))

(defn render []
  [:div#app-root
   (util/header-bare)
   [:div.flash-root
    [:div.flash-message.success
     [:div.container
      [:p [:span "✕"]
       [:span.flash-message-text "This is a success flash"]]]]
    [:div.flash-message.warn
     [:div.container
      [:p [:span "✕"]
       [:span.flash-message-text "This is a warn flash"]]]]
    [:div.flash-message.error
     [:div.container
      [:p [:span "✕"]
       [:span.flash-message-text "This is an error flash"]]]]]
   [:main
    [:div {:style "padding-top: 200px; padding-bottom: 100px;"}
    "Some content."]]
   (util/footer)])
