(ns mdm.isaac.sandbox.site.example-page)

(defn render []
  [:div#content
   [:header
    [:div.container.inliner.space-between
     [:a {:href "/"} [:img.logo {:src "/images/logos/cc-emblem.png"}]]]]
   [:main
    [:section
     [:div.container.width-750
      [:h2 "Example Sandbox pages"]
      [:ul#-interactive.interactive.small-margin-bottom
       [:li#pail [:span.name "pail"]]
       [:li#shovel [:span.name "shovel"]]
       [:li#castle-mold [:span.name "castle mold"]]
       [:li#page-bot [:span.name "page boat"]]
       [:li#monster.trucks [:span.name "monster trucks"]]]]]]])
