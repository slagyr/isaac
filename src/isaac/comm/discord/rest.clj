(ns isaac.comm.discord.rest
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.logger :as log]))

(def api-base "https://discord.com/api/v10")

(defn- truncate-content [content]
  (let [content (str content)]
    (if (<= (count content) 2000)
      content
      (str (subs content 0 1997) "..."))))

(defn- preview-body [body]
  (let [body (str body)]
    (subs body 0 (min 200 (count body)))))

(defn post-message!
  [{:keys [channel-id content token]}]
  (let [url      (str api-base "/channels/" channel-id "/messages")
        payload  {:content (truncate-content content)}
        response (http/post url {:body    (json/generate-string payload)
                                 :headers {"Authorization" (str "Bot " token)
                                           "Content-Type"  "application/json"}
                                 :throw   false})]
    (if (>= (:status response 0) 400)
      (do
        (log/error :discord.reply/http-error
                   :bodyPreview (preview-body (:body response))
                   :channelId channel-id
                   :status (:status response))
        response)
      response)))
