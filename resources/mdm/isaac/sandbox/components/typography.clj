(ns mdm.isaac.sandbox.components.typography
  (:require [mdm.isaac.sandbox.util :as util]))

(defn render []
  [:div#app-root
   (util/header-authenticated)
   [:main
    [:div.container
     [:h1 "Header 1"]
     [:h2 "Header 2"]
     [:h3 "Header 3"]
     [:h4 "Header 4"]
     [:h5 "Header 5"]
     [:h6 "Header 6"]
     [:h6 [:span "Header 6 Span"]]
     [:label "Label"]
     [:div "This is normal text without any modifiers."]
     [:p "This is a paragraph (p tag)."]
     [:span.small-caps "Span with small-caps class"]
     [:div [:small "Small"]]
     [:div [:a "Anchor tag (a tag)"]]
     [:div [:b "Bold (b tag)"]]
     [:div [:strong "Strong"]]
     ]
    ]
   (util/footer)])


