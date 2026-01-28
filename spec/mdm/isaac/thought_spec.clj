(ns mdm.isaac.thought-spec
  (:require [mdm.isaac.spec-helper :refer [with-config]]
            [mdm.isaac.thought :as sut]
            [speclj.core :refer :all]))

(def isaac-test {:impl :postgres :dbtype "postgresql" :dbname "isaac_test" :host "localhost" :port 5432})

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
      (let [base-embedding (vec (repeat 768 0.0))
            similar-embedding (assoc base-embedding 0 1.0)
            less-similar-embedding (assoc base-embedding 0 0.5 1 0.5)
            dissimilar-embedding (vec (repeat 768 1.0))
            _t1 (sut/save {:kind :thought :content "dissimilar" :embedding dissimilar-embedding})
            t2 (sut/save {:kind :thought :content "similar" :embedding similar-embedding})
            t3 (sut/save {:kind :thought :content "less-similar" :embedding less-similar-embedding})
            results (sut/find-similar similar-embedding 2)]
        (should= 2 (count results))
        (should= (:id t2) (:id (first results)))
        (should= (:id t3) (:id (second results)))))
    ])

(describe "Thought"

  (context "memory"

    (with-config {:db {:impl :memory}})

    (specs)

    )

  (context "sql"
    (tags :slow)

    (with-config {:db isaac-test})
    (before-all (sut/pg-drop-database "isaac_test")
                (sut/pg-create-database "isaac_test")
                (sut/pg-init isaac-test))

    (specs)

    )




  )
