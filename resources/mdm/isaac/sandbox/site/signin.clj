(ns mdm.isaac.sandbox.site.signin
  (:require [airworthy.config :as config]
            [mdm.isaac.sandbox.util :as util]))

(defn google-signin []
  (list
    ;[:script {:src "https://accounts.google.com/gsi/client" :async "true"}]
    ;[:div#g_id_onload {:data-client_id   (-> config/env :google-oauth :client-id)
    ;                   :data-login_uri   (config/link "/signin/google-oauth")
    ;                   :data-auto_prompt "false"}]
    [:div.g_id_signin {:data-type           "standard"
                       :data-size           "large"
                       :data-theme          "outline"
                       :data-text           "sign_in_with"
                       :data-shape          "rectangular"
                       :data-logo_alignment "center"}
                       [:div.placeholder "Sign in with Google"]]))

(defn apple-signin []
  (list
    [:div#appleid-signin.apple-signin {:data-color "black" :data-border "true" :data-type "sign in"}
     [:div.placeholder "Sign in with Apple"]]
    [:script {:src "https://appleid.cdn-apple.com/appleauth/static/jsapi/appleid/1/en_US/appleid.auth.js" :async "true"}]
    [:script {:type "text/javascript"}
     "AppleID.auth.init({
           clientId: 'your.service.id.identifier',  // Replace with your Service ID (e.g., com.example.app)
           scope: 'name email',                    // Requested permissions (space-separated)
           redirectURI: 'https://yourdomain.com/callback',  // Your backend callback URL for auth response
           state: 'your-custom-state-string',      // Optional: CSRF protection (e.g., random string)
           usePopup: true                          // Optional: Use popup instead of redirect (true/false)
       });

       // Optional: Add click handler if needed (Apple handles clicks by default after init)
       document.getElementById('appleid-signin').addEventListener('click', function() {
           AppleID.auth.signIn();
       });"])
  )

(defn render []
  [:div#app-root
   (util/header-bare)
   [:main#signin
    [:div.floating-panel
     [:h2.text-align-center "Sign In"]
     [:div.oauth
      [:div.oauth-button [:div.placeholder "Sign in with Google"]]
      [:div.oauth-button [:div.placeholder "Sign in with Apple"]]
      (google-signin)
      (apple-signin)]
     [:form
      [:fieldset
       [:label "Email"]
       [:input {:type "email" :placeholder "Enter email"}]]
      [:fieldset
       [:label "Password"]
       [:input {:type "password" :placeholder "Enter password"}]]
      [:button.primary.margin-top-default "Sign In"]
      #_[:button.primary.margin-top-default [:span.spinner]]]
     [:div.alternatives
      [:p [:a "Forgot password?"]]
      [:p.margin-top-default [:a "Sign Up Instead"]]]]]
   (util/footer)])

