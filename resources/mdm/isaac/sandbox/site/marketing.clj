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
      [:p "Create an account. It's free for now.  Gain super powered memory today."]]
     [:div.social
      [:a.facebook {:href "https://www.facebook.com/Airworthy.co"}]
      [:a.twitter {:href "https://twitter.com/airworthyco"}]]]
    [:section.tutorial
     [:div.row.no-margin
      [:div.container
       [:h1 "Why Use Airworthy?"]]]
     [:div.container
      [:div.row
       [:div.column
        [:img {:src "/images/elements/tutorial-2.jpg"}]
        [:h4 "Digital Memory"]
        [:p "Record your activities throughout the day. With a single button push, you can tell your phone what you are doing, or what happened in the past."]]
       [:div.column
        [:img {:src "/images/elements/tutorial-1.jpg"}]
        [:h4 "Your memory at a Glance"]
        [:p "All your recorded events are at your finger tips. View them in your browser, or on your phone."]]
       [:div.column
        [:img {:src "/images/elements/tutorial-3b.jpg"}]
        [:h4 "Magical Insights"]
        [:p "Ask Airworthy anything about yourself."
         " This is where the super powers come in. If the answer is in your history, Airworthy will find it."]]]]]
    [:section.timeline
     [:div.row.no-margin
      [:div.container [:h1 "Example Insights"]]]
     [:div.row.no-margin
      [:div.container
       [:ol
        [:li
         [:div.row
          [:div.column
           [:img.round {:src "/images/elements/automagic.png"}]
           [:div.trail]]
          [:div.column [:h3 "Diet"]
           [:ul
            [:li [:p "- What did I eat for breakfast last Monday?"]]
            [:li [:p "- How many calories have I consumed today?"]]
            [:li [:p "- When was the last time I ate gluten?"]]]]]]
        [:li
         [:div.row
          [:div.column
           [:img {:src "/images/elements/automagic.png"}]
           [:div.trail]]
          [:div.column [:h3 "Health"]
           [:ul
            [:li [:p "- Have I taken my medication today?"]]
            [:li [:p "- Is my blood pressure trending down this week?"]]
            [:li [:p "- What happened today that could be making me sick?"]]]]]]
        [:li
         [:div.row
          [:div.column
           [:img.round {:src "/images/elements/automagic.png"}]
           [:div.trail]]
          [:div.column [:h3 "Fitness"]
           [:ul
            [:li [:p "- How many calories did I burn during my workout this morning?"]]
            [:li [:p "- What weight did I bench last week?"]]
            [:li [:p "- What's the total distance of my runs this month?"]]]]]]
        [:li
         [:div.row
          [:div.column
           [:img.round {:src "/images/elements/automagic.png"}]
           [:div.trail]]
          [:div.column [:h3 "Work"]
           [:ul
            [:li [:p "- How many meetings did I attend this week?"]]
            [:li [:p "- List all the people I met at the conference."]]
            [:li [:p "- How many vacation days have I used this year?"]]]]]]
        [:li
         [:div.row
          [:div.column
           [:img.round {:src "/images/elements/automagic.png"}]]
          [:div.column [:h3 "Miscellaneous"]
           [:ul
            [:li [:p "- Where do I spend most of my time?"]]
            [:li [:p "- What books did I read this year?"]]
            [:li [:p "- When was the last date without my significant other?"]]]]]]]]]]]
   (util/footer)]
  )
