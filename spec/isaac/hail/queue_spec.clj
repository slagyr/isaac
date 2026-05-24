(ns isaac.hail.queue-spec
  (:require
    [isaac.fs :as fs]
    [isaac.hail.queue :as sut]
    [isaac.nexus :as nexus]
    [isaac.tool.memory :as memory]
    [speclj.core :refer :all]))

(describe "hail.queue"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (nexus/-with-nested-nexus {:state-dir "/test/isaac" :fs (fs/mem-fs)}
      (example)))

  (it "writes a hail record under hail/pending"
    (binding [memory/*now* (java.time.Instant/parse "2026-05-23T12:00:00Z")]
      (let [record (sut/send! {:frequency {:band "bean-pickup"}
                               :payload   {:n 1}
                               :from      :cli})]
        (should= {:id        "hail-1"
                  :frequency {:band "bean-pickup"}
                  :payload   {:n 1}
                  :from      :cli
                  :sent-at   "2026-05-23T12:00:00Z"}
                 record)
        (should= record
                 (sut/read-pending "hail-1")))))

  (it "mints sequential hail ids"
    (should= "hail-1" (:id (sut/send! {:frequency {:band "bean-pickup"} :from :cli})))
    (should= "hail-2" (:id (sut/send! {:frequency {:band "bean-pickup"} :from :cli}))))

  (it "stores the pending file at hail/pending/<id>.edn"
    (sut/send! {:frequency {:band "bean-pickup"} :from :cli})
    (should (fs/exists? (nexus/get :fs) "/test/isaac/hail/pending/hail-1.edn"))))
