(ns isaac.cli.acp-spec
  (:require
    [clojure.string :as str]
    [isaac.acp.rpc :as rpc]
    [isaac.cli.acp :as sut]
    [speclj.core :refer :all]))

(def base-opts
  {:state-dir "target/test-acp"
   :agents    {"main" {:name "main" :soul "You are Isaac." :model "grover"}}
   :models    {"grover" {:alias "grover" :model "echo" :provider "grover" :contextWindow 32768}}})

(defn- run-with-stdin [content opts]
  (binding [*in* (java.io.BufferedReader. (java.io.StringReader. content))]
    (let [result (atom nil)]
      {:output (with-out-str (reset! result (sut/run opts)))
       :exit   @result})))

(describe "ACP CLI"

  (it "returns 0 when stdin is empty"
    (should= 0 (:exit (run-with-stdin "" base-opts))))

  (it "writes JSON response to stdout for each request"
    (with-redefs [rpc/handle-line (fn [_ _] {:jsonrpc "2.0" :id 1 :result {:ok true}})]
      (let [{:keys [output exit]} (run-with-stdin "{}\n" base-opts)]
        (should= 0 exit)
        (should (str/includes? output "\"id\":1")))))

  (it "processes multiple requests in sequence"
    (let [call-count (atom 0)]
      (with-redefs [rpc/handle-line (fn [_ _]
                                      (swap! call-count inc)
                                      {:jsonrpc "2.0" :id @call-count :result {}})]
        (run-with-stdin "{}\n{}\n" base-opts)
        (should= 2 @call-count))))

  (it "skips nil responses (notifications with no response)"
    (with-redefs [rpc/handle-line (fn [_ _] nil)]
      (let [{:keys [output exit]} (run-with-stdin "{}\n" base-opts)]
        (should= 0 exit)
        (should= "" (str/trim output)))))

  (it "writes notification messages before the response in an envelope"
    (let [notif {:jsonrpc "2.0" :method "progress" :params {}}
          resp  {:jsonrpc "2.0" :id 1 :result {}}]
      (with-redefs [rpc/handle-line (fn [_ _] {:response resp :notifications [notif]})]
        (let [{:keys [output]} (run-with-stdin "{}\n" base-opts)]
          (should (str/includes? output "progress"))
          (should (str/includes? output "\"id\":1")))))))
