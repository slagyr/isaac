(ns isaac.comm.discord.rest-spec
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [isaac.comm.discord.rest :as sut]
    [isaac.logger :as log]
    [speclj.core :refer :all]))

(describe "Discord REST"

  (before
    (log/set-output! :memory)
    (log/clear-entries!))

  (it "posts a channel message with Bot authorization"
    (let [captured (atom nil)]
      (with-redefs [http/post (fn [url opts]
                                (reset! captured {:url url :opts opts})
                                {:status 200 :body (json/generate-string {:id "msg-1"})})]
        (sut/post-message! {:channel-id "C999" :content "hi back" :token "test-token"})
        (should= (str sut/api-base "/channels/C999/messages") (:url @captured))
        (should= "Bot test-token" (get-in @captured [:opts :headers "Authorization"]))
        (should= {:content "hi back"}
                 (json/parse-string (get-in @captured [:opts :body]) true)))))

  (it "logs non-retryable HTTP errors with the channel id and status"
    (with-redefs [http/post (fn [_ _]
                              {:status 403 :body "forbidden"})]
      (sut/post-message! {:channel-id "C999" :content "hi back" :token "test-token"})
      (should= {:event :discord.reply/http-error :channelId "C999" :status 403}
               (select-keys (last (log/get-entries)) [:event :channelId :status])))))
