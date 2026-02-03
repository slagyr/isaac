(ns mdm.isaac.thought-spec
  (:require [c3kit.bucket.api :as db]
            [c3kit.bucket.spec-helperc :as helper]
            [mdm.isaac.thought.schema :as schema.thought]
            [mdm.isaac.thought :as sut]
            [speclj.core :refer :all]))

(def embedding-length 7)
(defn random-embedding [] (take embedding-length (repeatedly rand)))
(defn repeated-embedding [v] (vec (repeat embedding-length v)))

(describe "Thought"

  (helper/with-schemas [schema.thought/thought])

  (it "can save"
    (let [thought (db/tx {:kind :thought :content "cheeseburger" :embedding (random-embedding)})]
      (should= :thought (:kind thought))
      (should= "cheeseburger" (:content thought))
      (should= embedding-length (count (:embedding thought)))
      (should-not-be-nil (:id thought))))

  (it "can update"
    (let [thought (db/tx {:kind :thought :content "cheeseburger" :embedding (random-embedding)})
          updated (db/tx (assoc thought :content "hotdog"))]
      ;(prn "updated: " updated)
      (should= "hotdog" (:content updated))
      (should= (:id thought) (:id updated))))

  (it "find-similar returns top N thoughts by cosine similarity"
    (let [base-embedding         (repeated-embedding 0.0)
          similar-embedding      (assoc base-embedding 0 1.0)
          less-similar-embedding (assoc base-embedding 0 0.5 1 0.5)
          dissimilar-embedding   (repeated-embedding 1.0)
          _t1                    (db/tx {:kind :thought :content "dissimilar" :embedding dissimilar-embedding})
          t2                     (db/tx {:kind :thought :content "similar" :embedding similar-embedding})
          t3                     (db/tx {:kind :thought :content "less-similar" :embedding less-similar-embedding})
          results                (sut/find-similar similar-embedding 2)]
      (should= 2 (count results))
      (should= (:id t2) (:id (first results)))
      (should= (:id t3) (:id (second results)))))

  (it "saves and retrieves thought type"
    (let [thought (db/tx {:kind :thought :type :goal :content "Learn Clojure" :embedding (repeated-embedding 0.1)})]
      (should= :goal (:type thought))))

  (it "find-by-type returns thoughts of specified type"
    (let [_goal    (db/tx {:kind :thought :type :goal :content "Be awesome" :embedding (repeated-embedding 0.1)})
          _insight (db/tx {:kind :thought :type :insight :content "I learned something" :embedding (repeated-embedding 0.2)})
          _goal2   (db/tx {:kind :thought :type :goal :content "Learn more" :embedding (repeated-embedding 0.3)})
          goals    (sut/find-by-type :goal)]
      (should= 2 (count goals))
      (should (every? #(= :goal (:type %)) goals))))

  )
