(ns mdm.isaac.server.layouts
  (:require [c3kit.apron.legend :as legend]
            [c3kit.apron.utilc :as utilc]
            [c3kit.wire.api :as api]
            [c3kit.wire.assets :refer [add-fingerprint]]
            [c3kit.wire.flash :as flash]
            [c3kit.wire.jwt :as jwt]
            [clojure.string :as str]
            [hiccup.element :as elem]
            [hiccup.page :as page]
            [mdm.isaac.config :as config]
            [ring.util.response :as response]))

(def default-title "Isaac")
(def default-description "Isaac")
(def default-image "/images/isaac.jpg")
(defn title [options] (or (:title options) default-title))

(defn social-meta [options]
  (list
    [:meta {:property "og:url" :content (or (:og/url options) (str config/host "/"))}]
    [:meta {:property "og:title" :content (or (:og/title options) (title options))}]
    [:meta {:property "og:description" :content (or (:og/description options) default-description)}]
    [:meta {:property "og:image" :content (or (:og/image options) default-image)}]
    [:meta {:name "twitter:card" :content "summary"}]
    [:meta {:name "twitter:site" :content "@slagyr"}]))

(defn google-onload-options [options]
  {:data-client_id   (get-in config/active [:google-oauth :client-id])
   :data-login_uri   (config/link "/signin/google-oauth")
   :data-context     "signin"
   :data-ux_mode     "redirect"
   ;:data-auto_prompt (if (:prompt-google-signin? options) "true" "false")
   :data-auto_prompt "true"
   :data-auto_select "false"})

(defn default
  ([body] (default body {}))
  ([body options]
   (-> (response/response
         (page/html5
           [:head
            [:title (title options)]
            [:meta {:charset "utf-8"}]
            [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0, minimum-scale=1.0"}]
            (social-meta options)
            [:link {:rel "shortcut icon" :href "/images/favicon.ico" :type "image/x-icon"}]
            [:link {:rel "icon" :href "/images/favicon.ico" :type "image/x-icon"}]
            ;[:script {:src "https://kit.fontawesome.com/982c21555a.js" :crossorigin "anonymous"}]
            ;[:script {:src "https://accounts.google.com/gsi/client" :async "true" :defer "true"}]
            ;[:script {:src "https://appleid.cdn-apple.com/appleauth/static/jsapi/appleid/1/en_US/appleid.auth.js"}]
            (if config/development?
              (list
                (page/include-js "/cljs/goog/base.js")
                (page/include-js "/cljs/isaac_dev.js"))
              (page/include-js (add-fingerprint "/cljs/isaac.js")))
            (:head options)                                 ;; MDM - must go after js so we can include js-fns, and before css, so we can override styles as needed
            (page/include-css (add-fingerprint (or (:css options) "/css/isaac.css")))]
           [:body [:div#g_id_onload (google-onload-options options)] body]))
       (response/content-type "text/html")
       (response/charset "UTF-8"))))

(defn static [& content]
  (default
    [:div#app-root
     [:header
      [:nav
       [:ul
        [:li.logo [:a {:href "/"} [:span]]]]]]
     content
     [:footer
      [:div.container
       [:div.row.no-margin
        [:div.column "© 2026 Micah Martin"]
        [:div.column
         [:ul
          [:li
           [:a {:href "mailto:micahmartin@gmail.com"} "Contact Us"]]
          [:li
           [:a {:href "/terms"} "Terms"]]]]]]]]))

(defn not-found []
  (-> (static
        [:main#not-found
         [:div.floating-panel
          [:h1.uppercase "Not Found"]
          [:h4.margin-top-default "We couldn't find what you were looking for."]]])
      (response/status 404)))

(defn client-init
  ([] (client-init {}))
  ([data]
   (let [payload (pr-str (utilc/->transit data))]
     (str "<script type=\"text/javascript\">\n//<![CDATA[\n"
          "isaac.main.main(" (str/replace payload "</script>" "<\\/script>") ");"
          "\n//]]>\n</script>"))))

(def rich-client-placeholder "Your page is loading...")

(defn rich-client [payload options]
  (default (list [:div#app-root rich-client-placeholder]
                 (client-init payload))
           (assoc options
             :head (elem/javascript-tag (str "goog.require('isaac.main');")))))

(defn build-rich-client-payload [request]
  {:user   (some-> request :user deref legend/present!)
   :flash  (flash/messages request)
   :config {
            :anti-forgery-token (jwt/client-id request)
            :ws-csrf-token      (jwt/client-id request)
            :api-version        (api/version)
            :environment        config/environment
            :google-client-id   (-> config/active :google-oauth :client-id)
            :isaac-root     (-> config/active :cleancoders-auth :url-root)
            :host               config/host
            :apple-client-id    (-> config/active :apple-auth :client-id)
            }})

(defn web-rich-client
  "Load the default web page and let the client side take over."
  ([request] (web-rich-client request {}))
  ([request options] (rich-client (build-rich-client-payload request) options)))

