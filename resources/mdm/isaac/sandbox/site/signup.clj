(ns mdm.isaac.sandbox.site.signup
  (:require [mdm.isaac.sandbox.util :as util]))

(defn render []
  [:div#app-root
   (util/header-bare)
   [:main#signup
    [:div.floating-panel
     [:form
      [:h2.text-align-center "Signup"]
      [:fieldset
       [:label "Email"]
       [:input {:type "email" :placeholder "Enter email"}]]
      [:fieldset
       [:label "Password"]
       [:input {:type "password" :placeholder "Enter password"}]]
      [:fieldset
       [:label "Confirm Password"]
       [:input {:type "password" :placeholder "Re-Enter password"}]]
      [:button.primary.margin-top-default "Signup"]]
     [:div.alternatives
      [:p [:a "Login Instead"]]]]]
   (util/footer)])
