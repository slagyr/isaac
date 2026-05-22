(ns isaac.session.naming-spec
  (:require
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.session.naming :as sut]
    [speclj.core :refer :all]))

(describe "Session naming"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (binding [fs/*fs* (fs/mem-fs)]
      (example)))

  (it "generates sequential names and persists the counter"
    (let [state-dir "/test/naming"
          first    (sut/generate :sequential {:state-dir state-dir :store {}})
          second   (sut/generate :sequential {:state-dir state-dir :store {"session-1" {:id "session-1"}}})]
      (should= "session-1" first)
      (should= "session-2" second)
      (should= "2" (str/trim (fs/slurp "/test/naming/sessions/.counter")))))

  (it "reads the configured naming strategy"
    (with-redefs [config/load-config (fn [& _] {:sessions {:naming-strategy :sequential}})]
      (should= :sequential (sut/strategy "/test/naming"))))

  (it "defaults the naming strategy to adjective-noun"
    (with-redefs [config/load-config (fn [& _] {})]
      (should= :adjective-noun (sut/strategy "/test/naming"))))

  (it "reads the configured naming strategy from an explicit fs"
    (let [mem (fs/mem-fs)]
      (with-redefs [config/load-config (fn [opts]
                                                      (should= mem (:fs opts))
                                                      {:sessions {:naming-strategy :sequential}})]
        (should= :sequential (sut/strategy "/test/naming" mem)))))

  (it "generates adjective-noun names"
    (with-redefs [clojure.core/rand-nth (fn [coll] (first coll))]
      (should= "Calm Otter" (sut/generate :adjective-noun {:state-dir "/test/naming" :store {}})))))
