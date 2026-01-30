(ns mdm.isaac.client.ws-spec
  (:require [speclj.core :refer :all]
            [clojure.edn :as edn]
            [mdm.isaac.client.ws :as ws]))

(describe "WebSocket Client"

  (describe "message formatting"
    (it "formats goals/list request"
      (let [msg (ws/format-request {:action :goals/list})]
        (should= {:kind :goals/list :params {}} (edn/read-string msg))))

    (it "formats goals/add request with content"
      (let [msg (ws/format-request {:action :goals/add :content "Learn macros"})]
        (should= {:kind :goals/add :params {:content "Learn macros"}}
                 (edn/read-string msg))))

    (it "formats thoughts/recent request"
      (let [msg (ws/format-request {:action :thoughts/recent})]
        (should= {:kind :thoughts/recent :params {:limit 10}}
                 (edn/read-string msg))))

    (it "formats thoughts/search request"
      (let [msg (ws/format-request {:action :thoughts/search :query "Clojure"})]
        (should= {:kind :thoughts/search :params {:query "Clojure" :limit 10}}
                 (edn/read-string msg))))

    (it "formats shares/unread request"
      (let [msg (ws/format-request {:action :shares/unread})]
        (should= {:kind :shares/unread :params {}} (edn/read-string msg))))

    (it "formats shares/ack request"
      (let [msg (ws/format-request {:action :shares/ack :id 123})]
        (should= {:kind :shares/ack :params {:id 123}} (edn/read-string msg)))))

  (describe "response parsing"
    (it "parses successful goals/list response"
      (let [response "{:status :ok :payload [{:id 1 :content \"Learn\" :status :active}]}"
            parsed (ws/parse-response :goals/list response)]
        (should= :ws-message (:type parsed))
        (should= :goals/list (:action parsed))
        (should= [{:id 1 :content "Learn" :status :active}] (:payload parsed))))

    (it "parses successful thoughts/recent response"
      (let [response "{:status :ok :payload [{:id 1 :content \"A thought\" :type :thought}]}"
            parsed (ws/parse-response :thoughts/recent response)]
        (should= :ws-message (:type parsed))
        (should= :thoughts/recent (:action parsed))
        (should= [{:id 1 :content "A thought" :type :thought}] (:payload parsed))))

    (it "parses failed response"
      (let [response "{:status :fail :message \"Error\"}"
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
