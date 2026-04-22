(ns isaac.delivery.queue-spec
  (:require
    [isaac.delivery.queue :as sut]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(describe "delivery queue"

  (around [it]
    (binding [fs/*fs* (fs/mem-fs)]
      (it)))

  (it "stores a queued delivery under pending"
    (sut/enqueue! "/test/isaac" {:id      "7f3a"
                                  :comm    :discord
                                  :target  "C999"
                                  :content "Hello"})
    (should= {:id      "7f3a"
              :comm    :discord
              :target  "C999"
              :content "Hello"
              :attempts 0}
             (select-keys (sut/read-pending "/test/isaac" "7f3a") [:id :comm :target :content :attempts])))

  (it "moves a pending delivery to failed"
    (sut/enqueue! "/test/isaac" {:id      "7f3a"
                                  :comm    :discord
                                  :target  "C999"
                                  :content "Hello"})
    (sut/move-to-failed! "/test/isaac" "7f3a" {:attempts 5})
    (should-be-nil (sut/read-pending "/test/isaac" "7f3a"))
    (should= 5 (:attempts (sut/read-failed "/test/isaac" "7f3a")))))
