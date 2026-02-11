(ns mdm.isaac.tui.ws-spec
  (:require [speclj.core :refer :all]
            [clojure.edn :as edn]
            [mdm.isaac.tui.ws :as ws]))

(describe "WebSocket Client"

  (describe "message formatting"
    (it "formats goals/list request"
      (let [msg     (ws/format-request {:action :goals/list})
            parsed  (edn/read-string msg)]
        (should= :goals/list (:kind parsed))
        (should= {} (:params parsed))
        (should-not-be-nil (:request-id parsed))))

    (it "formats goals/add request with content"
      (let [msg    (ws/format-request {:action :goals/add :content "Learn macros"})
            parsed (edn/read-string msg)]
        (should= :goals/add (:kind parsed))
        (should= {:content "Learn macros"} (:params parsed))
        (should-not-be-nil (:request-id parsed))))

    (it "formats thoughts/recent request"
      (let [msg    (ws/format-request {:action :thoughts/recent})
            parsed (edn/read-string msg)]
        (should= :thoughts/recent (:kind parsed))
        (should= {:limit 10} (:params parsed))
        (should-not-be-nil (:request-id parsed))))

    (it "formats thoughts/search request"
      (let [msg    (ws/format-request {:action :thoughts/search :query "Clojure"})
            parsed (edn/read-string msg)]
        (should= :thoughts/search (:kind parsed))
        (should= {:query "Clojure" :limit 10} (:params parsed))
        (should-not-be-nil (:request-id parsed))))

    (it "formats shares/unread request"
      (let [msg    (ws/format-request {:action :shares/unread})
            parsed (edn/read-string msg)]
        (should= :shares/unread (:kind parsed))
        (should= {} (:params parsed))
        (should-not-be-nil (:request-id parsed))))

    (it "formats shares/ack request"
      (let [msg    (ws/format-request {:action :shares/ack :id 123})
            parsed (edn/read-string msg)]
        (should= :shares/ack (:kind parsed))
        (should= {:id 123} (:params parsed))
        (should-not-be-nil (:request-id parsed))))

    (it "formats chat/send request with text"
      (let [msg    (ws/format-request {:action :chat/send :text "Hello Isaac"})
            parsed (edn/read-string msg)]
        (should= :chat/send (:kind parsed))
        (should= {:text "Hello Isaac"} (:params parsed))
        (should-not-be-nil (:request-id parsed)))))

  (describe "response parsing"
    (it "parses successful goals/list response with c3kit envelope"
      (let [response "{:response-id 1 :payload {:status :ok :payload [{:id 1 :content \"Learn\" :status \"active\"}]}}"
            parsed (ws/parse-response :goals/list response)]
        (should= :ws-message (:type parsed))
        (should= :goals/list (:action parsed))
        (should= [{:id 1 :content "Learn" :status "active"}] (:payload parsed))))

    (it "parses successful thoughts/recent response with c3kit envelope"
      (let [response "{:response-id 2 :payload {:status :ok :payload [{:id 1 :content \"A thought\" :type \"thought\"}]}}"
            parsed (ws/parse-response :thoughts/recent response)]
        (should= :ws-message (:type parsed))
        (should= :thoughts/recent (:action parsed))
        (should= [{:id 1 :content "A thought" :type "thought"}] (:payload parsed))))

    (it "parses failed response with c3kit envelope"
      (let [response "{:response-id 3 :payload {:status :fail :message \"Error\"}}"
            parsed (ws/parse-response :goals/list response)]
        (should= :ws-error (:type parsed))
        (should= "Error" (:message parsed))))

    (it "handles malformed response"
      (let [response "not valid edn {"
            parsed (ws/parse-response :goals/list response)]
        (should= :ws-error (:type parsed)))))

  (describe "request tracking"
    (it "generates unique request ids"
      (let [id1 (ws/next-request-id!)
            id2 (ws/next-request-id!)]
        (should-not= id1 id2)))))
