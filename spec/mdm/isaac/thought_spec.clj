(ns mdm.isaac.thought-spec
  (:require [mdm.isaac.spec-helper :refer [with-config]]
            [mdm.isaac.thought :as sut]
            [speclj.core :refer :all]))

(def isaac-test {:dbtype "postgresql" :dbname "isaac_test" :host "localhost" :port 5432})

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
   ])

(describe "Thought"

  (context "memory"

    (with-config {:db {:impl :memory}})

    (specs)

    )

  (context "sql"

    (before-all (sut/pg-drop-database "isaac_test")
                (sut/pg-create-database "isaac_test")
                (sut/pg-init isaac-test))
    (with-config {:db {:impl :postgres}})
    (redefs-around [sut/pg-isaac isaac-test])

    (specs)

    )




  )
