(ns isaac.comm.delivery.queue-spec
  (:require
    [isaac.comm.delivery.queue :as sut]
    [isaac.fs :as fs]
    [isaac.home :as home]
    [speclj.core :refer :all]))

(describe "comm.delivery.queue"

  (around [it]
    (binding [fs/*fs*          (fs/mem-fs)
              home/*state-dir* "/test/isaac"]
      (it)))

  (it "stores a queued delivery under comm/delivery/pending"
    (sut/enqueue! {:id      "7f3a"
                   :comm    :discord
                   :target  "C999"
                   :content "Hello"})
    (should= {:id      "7f3a"
              :comm    :discord
              :target  "C999"
              :content "Hello"
              :attempts 0}
             (select-keys (sut/read-pending "7f3a") [:id :comm :target :content :attempts])))

  (it "stores the pending file at comm/delivery/pending/<id>.edn"
    (sut/enqueue! {:id "7f3a" :comm :discord :target "C999" :content "Hi"})
    (should (fs/exists? "/test/isaac/comm/delivery/pending/7f3a.edn")))

  (it "moves a pending delivery to comm/delivery/failed"
    (sut/enqueue! {:id      "7f3a"
                   :comm    :discord
                   :target  "C999"
                   :content "Hello"})
    (sut/move-to-failed! "7f3a" {:attempts 5})
    (should-be-nil (sut/read-pending "7f3a"))
    (should= 5 (:attempts (sut/read-failed "7f3a")))))
