(ns mdm.isaac.secret.aws-spec
  (:require [mdm.isaac.aws :as aws]
            [mdm.isaac.secret.aws]  ;; Load to register multimethod
            [mdm.isaac.secret.core :as sut]
            [mdm.isaac.spec-helper :refer [with-config]]
            [speclj.core :refer :all]))

(describe "secret.aws"

  (with-config {:secret-source {:impl   :aws
                                :region "us-west-2"
                                :prefix "isaac/"}})

  (context "get-secret :aws"

    (it "calls aws/get-secret with prefixed name and region"
      (let [calls (atom [])]
        (with-redefs [aws/get-secret (fn [name region]
                                       (swap! calls conj {:name name :region region})
                                       "secret-value")]
          (let [result (sut/get-secret "API_KEY")]
            (should= "secret-value" result)
            (should= 1 (count @calls))
            (should= "isaac/API_KEY" (-> @calls first :name))
            (should= "us-west-2" (-> @calls first :region))))))

    (it "uses configured prefix"
      (let [calls (atom [])]
        (with-redefs [aws/get-secret (fn [name _]
                                       (swap! calls conj name)
                                       nil)]
          (sut/get-secret "MY_SECRET")
          (should= "isaac/MY_SECRET" (first @calls)))))

    (it "uses configured region"
      (let [calls (atom [])]
        (with-redefs [aws/get-secret (fn [_ region]
                                       (swap! calls conj region)
                                       nil)]
          (sut/get-secret "MY_SECRET")
          (should= "us-west-2" (first @calls)))))))
