(ns mdm.isaac.thought-spec
  (:require [mdm.isaac.spec-helper :refer [with-config]]
            [mdm.isaac.thought :as sut]
            [speclj.core :refer :all]))

(def isaac-test {:impl :postgres :dbtype "postgresql" :dbname "isaac_test" :host "localhost" :port 5432})

;; TODO (isaac-oc4) - MDM: `(take 768 (repeatedly rand))` and `(vec (repeat 768 ...))` are repeated many times. First, 768 should be replaced with (embedding.dimensions).  Second, they should be extracted into reusable functions.

(defn specs []
  [
   (it "can save"
     (let [thought (sut/save {:kind :thought :content "cheeseburger" :embedding (take 768 (repeatedly rand))})]
       (should= :thought (:kind thought))
       (should= "cheeseburger" (:content thought))
       (should= 768 (count (:embedding thought)))
       (should-not-be-nil (:id thought))))

   (it "can update"
     (let [thought (sut/save {:kind :thought :content "cheeseburger" :embedding (take 768 (repeatedly rand))})
           updated (sut/save (assoc thought :content "hotdog"))]
       ;(prn "updated: " updated)
       (should= "hotdog" (:content updated))
       (should= (:id thought) (:id updated))))

   (it "find-similar returns top N thoughts by cosine similarity"
     (let [base-embedding         (vec (repeat 768 0.0))
           similar-embedding      (assoc base-embedding 0 1.0)
           less-similar-embedding (assoc base-embedding 0 0.5 1 0.5)
           dissimilar-embedding   (vec (repeat 768 1.0))
           _t1                    (sut/save {:kind :thought :content "dissimilar" :embedding dissimilar-embedding})
           t2                     (sut/save {:kind :thought :content "similar" :embedding similar-embedding})
           t3                     (sut/save {:kind :thought :content "less-similar" :embedding less-similar-embedding})
           results                (sut/find-similar similar-embedding 2)]
       (should= 2 (count results))
       (should= (:id t2) (:id (first results)))
       (should= (:id t3) (:id (second results)))))

   (it "saves and retrieves thought type"
     (let [thought (sut/save {:kind :thought :type :goal :content "Learn Clojure" :embedding (vec (repeat 768 0.1))})]
       (should= :goal (:type thought))))

   (it "defaults type to :thought when not specified"
     (let [thought (sut/save {:kind :thought :content "Random thought" :embedding (vec (repeat 768 0.1))})]
       (should= :thought (:type thought))))

   (it "find-by-type returns thoughts of specified type"
     (let [_goal    (sut/save {:kind :thought :type :goal :content "Be awesome" :embedding (vec (repeat 768 0.1))})
           _insight (sut/save {:kind :thought :type :insight :content "I learned something" :embedding (vec (repeat 768 0.2))})
           _goal2   (sut/save {:kind :thought :type :goal :content "Learn more" :embedding (vec (repeat 768 0.3))})
           goals    (sut/find-by-type :goal)]
       (should= 2 (count goals))
       (should (every? #(= :goal (:type %)) goals))))
   ])

(describe "Thought"

  (context "memory"

    (with-config {:db {:impl :memory}})
    (before (sut/memory-clear!))

    (specs)

    )

  (context "sql"
    (tags :slow)

    (with-config {:db isaac-test})
    (before-all (sut/pg-drop-database "isaac_test")
                (sut/pg-create-database "isaac_test")
                (sut/pg-init isaac-test))
    (before (sut/pg-clear!))

    (specs)

    )

  )
