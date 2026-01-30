(ns mdm.isaac.sandbox.documentation.how-to-use)

(defn render []
  [:div#content
   [:header
    [:nav {:style "max-width: 100%;"}
     [:ul
      [:li.-mobile-menu-toggle.mobile-menu-toggle
       [:span.fa-solid.fa-bars {:aria-hidden "true"}]]
      [:li.logo
       [:a {:href "/"} [:span]]]
      [:li.-user-menu-toggle.user-menu-toggle
       [:span.fa-solid.fa-bars {:aria-hidden "true"}]]]]]

   [:main
    [:section
     [:div.container.width-750
      [:div.flex-row.docs

       [:div.flex-column
         [:style "
           a:hover .name {
             color: #3ec2cf;
             cursor: pointer;
           }
         "]
        [:div.card.docs
         [:div.inliner.gap-minus-1 {:style "border-bottom: 1px solid #8a8181; padding: 1rem;"}
          [:img {:src "/images/logos/type-black.png"
                 :style "width: 125px;"}]
          [:h4 "DOCS"]]

         [:ul#-interactive.interactive.small-margin-bottom {:style "padding: 1rem;"}
          [:li [:h6 "General"]]
          [:li [:a {:href "#what" :style "text-decoration:none; color:#333;"} [:span.name "What is Airworthy"]]]
          [:li.margin-bottom-plus-1 [:a {:href "#how" :style "text-decoration:none; color:#333;"} [:span.name "Example Uses"]]]

          [:li [:h6 "IOS"]]
           [:li [:a {:href "#shortcuts" :style "text-decoration:none; color:#333;"} [:span.name "Shortcuts"]]]
          [:li [:a {:href "#iphone16" :style "text-decoration:none; color:#333;"} [:span.name "For iPhone 16+"]]]
          [:li [:a {:href "#siri" :style "text-decoration:none; color:#333;"} [:span.name "Siri"]]]
          [:li.margin-bottom-plus-1 [:a {:href "#compound" :style "text-decoration:none; color:#333;"} [:span.name "Compound Memories"]]]

          [:li [:h6 "Web"]]
          [:li [:a {:href "#memories" :style "text-decoration:none; color:#333;"} [:span.name "Memories"]]]
          [:li [:a {:href "#insights" :style "text-decoration:none; color:#333;"} [:span.name "Insights"]]]]]]


       [:div.flex-column.docs-column {:style "align-items: flex-start;"}


        [:div.margin-bottom-plus-3
         [:h1.margin-bottom-plus-3 "Introduction"]

         [:h2 {:id "what"} "What is Airworthy?"]
         [:div.margin-bottom-plus-1 {:style "display: flex; flex-direction: column; gap: 1rem;"}
          [:p "Airworthy is a journaling and insights app that helps you capture thoughts, memories, and everyday data
               across devices. Each entry, called a memory, can be made through quick voice notes or written messages."]

          [:p "It's built for both reflection and practical use, making it useful for documenting habits, tracking health data,
               or noting progress on personal or work goals."]

          [:p "Over time, Airworthy organizes and analyzes your memories to highlight patterns in your habits,
               focus, and routines. You can record through the web or mobile app, or use Siri and the iPhone Action
               Button for hands-free input. Every memory is stored securely in your account and can be revisted and edited at any time."]]

         [:h2 {:id "something-else"} "What can it be used for?"]
         [:div.margin-bottom-plus-1 {:style "display: flex; flex-direction: column; gap: 1rem;"}
          [:p "Airworthy can function as a journal, tracker, or workspace for structured and unstructured data. Examples include:"]

          [:ol {:style "list-style-type: disc; padding-left: 1.5rem; margin-top: 0.5rem;"}

           [:li [:p "Writing daily reflections or creative notes"]]
           [:li [:p "Logging work sessions, meetings, or study time"]]
           [:li [:p "Tracking workouts, personal records, or physical progress"]]
           [:li [:p "Monitoring mood, fasting, or sleep patterns"]]
           [:li [:p "Recording meals, medications, or calories"]]
           [:li [:p "Asking AI-powered insights like 'What did I work on most this month?' or 'When am I most consistent?'"]]]]]


        [:div.margin-bottom-plus-3
         [:h1.margin-bottom-0 "IOS"]

         [:h2.margin-bottom-0 {:id "shortcuts"} "Shortcuts"]
         [:div.margin-bottom-plus-1 {:style "display: flex; flex-direction: column; gap: 1rem;"}
          [:h4 "1: Open the Shortcuts app."]
          [:p "Tap the search bar."]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/10.jpeg" :style "border: 1px solid black;"}]

          [:h4 "2: Find Airworthy."]
          [:p "Search and select Airworthy."]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/11.jpeg" :style "border: 1px solid black;"}]


          [:h4 "3: Find Airworthy."]
          [:p "Hold down on one of the commands."]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/12.jpeg" :style "border: 1px solid black;"}]


          [:h4 "4: Add to homescreen."]
          [:p "Select 'Add to homescreen'."]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/13.jpeg" :style "border: 1px solid black;"}]

          [:h4 "5: Test out your command."]
          [:p "Now, you will be able to quickly add memories to Airworthy."]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/15.jpeg" :style "border: 1px solid black;"}]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/16.jpeg" :style "border: 1px solid black;"}]]


         [:h2.margin-bottom-0 {:id "iphone16"} "iPhone 16+"]
         [:div.margin-bottom-plus-1 {:style "display: flex; flex-direction: column; gap: 1rem;"}

          [:h4 "1: Open the Shortcuts app."]
          [:p "Tap the “+” in the top-right corner to create a new shortcut."]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/1.png" :style "border: 1px solid black;"}]

          [:h4 "2: Add the “Dictate Text” action."]
          [:p "In the search bar at the bottom, type “Dictate Text” and select it. This will let you speak your memory or insight aloud."]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/4.png" :style "border: 1px solid black;"}]

          [:h4 "3: Add the Airworthy action."]
          [:p "Search for “Airworthy” and select “AirWorthy All In One.”"]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/5.png" :style "border: 1px solid black;"}]

          [:h4 "4: Connect the actions."]
          [:p "Make sure the “Dictate Text” action flows into “AirWorthy All In One” so your dictated text is passed into the app."]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/6.png" :style "border: 1px solid black;"}]

          [:h4 "5: Name your shortcut."]
          [:p "Tap the name field at the top and call it something like “Airworthy All In One.”"]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/7.png" :style "border: 1px solid black;"}]

          [:h4 "6: Add it to the Action Button."]
          [:p "Open Settings → Action Button, select Shortcut, then choose your new shortcut from the list."]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/8.png" :style "border: 1px solid black;"}]

          [:h4 "7: Test your shortcut."]
          [:p "Press your iPhon's Action Button and speak your input. Airworthy will open and automatically create a memory or answer your insight."]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/9.png" :style "border: 1px solid black;"}]]

         [:h2 {:id "siri"} "Siri"]
         [:div.margin-bottom-plus-1 {:style "display: flex; flex-direction: column; gap: 1rem;"}
          [:p "You can activate Airworthy through Siri commands for hands-free logging."]
          [:p "For example, say 'Hey Siri, record a memory in Airworthy' to create a new memory automatically."]]

         [:h2 {:id "compound"} "Compound Memories"]
         [:div.margin-bottom-plus-1 {:style "display: flex; flex-direction: column; gap: 1rem;"}
          [:p "Compound memories allow you to create multiple memories in one go."]
          [:p "When recounting events, just speak naturally:"]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/21.jpeg" :style "border: 1px solid black;"}]

          [:p "Airworthy will seperate the memories for you."]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/22.jpeg" :style "border: 1px solid black;"}]


          ]]



        [:div.margin-bottom-plus-3
         [:h1.margin-bottom-0 "Web App"]

         [:h2 {:id "memories"} "Memories"]
         [:div.margin-bottom-plus-1 {:style "display: flex; flex-direction: column; gap: 1rem;"}
          [:p "You can create, edit, and delete memories directly from the Airworthy web dashboard."]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/18.png" :style "border: 1px solid black;"}]
          [:p "Each memory shows timestamps, tags, and any linked insights. You can also edit or delete from this view."]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/20.png" :style "border: 1px solid black;" }]]

         [:h2 {:id "insights"} "Insights"]
         [:div.margin-bottom-plus-1 {:style "display: flex; flex-direction: column; gap: 1rem;"}
          [:p "Insights analyze your stored memories to identify patterns, recurring themes, or productivity streaks."]
          [:img.margin-bottom-plus-1 {:src "/images/tutorials/19.png" :style "border: 1px solid black;"}]


          ]]]]]]]])
