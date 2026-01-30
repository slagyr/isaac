(ns mdm.isaac.sandbox.components.modal
  (:require [mdm.isaac.sandbox.util :as util]))

(defn render []
  [:div#app-root
   (util/header-bare)
   [:div.flash-root
    [:div.flash-message.success
     [:div.container
      [:p [:span "✕"]
       [:span.flash-message-text "This is a success flash"]]]]]
   [:main
    [:div.floating-panel "Some content."]]
   [:div#-modal.modal-background.width-600 {:tab-index "0"}
    [:div#-modal-content.modal
     [:div.modal-header
      [:span#-modal-desktop-close.fas.fa-chevron-left ]
      [:span#-modal-mobile-close.fas.fa-times ]]
     [:div.container.vertical-scroll
      [:div#-default-modal
       [:h2 "Default Modal"]
       [:h3 "This should not happen.  How did this happen?"]
       [:pre "state"]]]]]
   (util/footer)])
