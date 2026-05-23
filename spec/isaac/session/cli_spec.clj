(ns isaac.session.cli-spec
  (:require
    [cheshire.core :as json]
    [isaac.session.cli :as sut]
    [isaac.session.store :as store]
    [isaac.spec-helper :as helper]
    [isaac.system :as system]
    [speclj.core :refer :all]))

(describe "session cli"

  #_{:clj-kondo/ignore [:invalid-arity :unresolved-symbol]}
  (around [it]
    (system/with-nested-system {:state-dir "/test/sessions"}
      (helper/with-memory-store
        (it))))

  (it "renders list output as JSON with sorted tags"
    (helper/create-session! "/test/sessions" "joe" {:crew "main" :tags #{:role/worker :project/chess}})
    (let [output (with-out-str (should= 0 (sut/run-fn {:home "/test" :_raw-args ["--json"]})))
          rows   (json/parse-string output true)
          joe    (some #(when (= "joe" (:name %)) %) rows)]
      (should= "joe" (:name joe))
      (should= "main" (:crew joe))
      (should= ["project/chess" "role/worker"] (:tags joe))))

  (it "renders show output as JSON"
    (helper/create-session! "/test/sessions" "joe" {:crew "main" :tags #{:project/x}})
    (helper/update-session! "/test/sessions" "joe" {:crew "alice" :tags #{:project/x}})
    (let [output (with-out-str (should= 0 (sut/run-fn {:home "/test" :_raw-args ["show" "joe" "--json"]})))
          row    (json/parse-string output true)]
      (should= "joe" (:name row))
      (should= "alice" (:crew row))
      (should= ["project/x"] (:tags row))))

  (it "filters sessions by tag"
    (helper/create-session! "/test/sessions" "joe" {:crew "main" :tags #{:project/chess}})
    (helper/create-session! "/test/sessions" "sue" {:crew "main" :tags #{:project/poker}})
    (let [output (with-out-str (should= 0 (sut/run-fn {:home "/test" :_raw-args ["--tag" "project/chess"]})))]
      (should-contain "joe" output)
      (should-not-contain "sue" output)))

  (it "filters idle sessions with not-in-flight"
    (helper/create-session! "/test/sessions" "joe" {:crew "main" :tags #{:project/chess}})
    (helper/create-session! "/test/sessions" "sue" {:crew "main" :tags #{:project/chess}})
    (store/mark-in-flight! (store/registered-store) "joe")
    (let [output (with-out-str (should= 0 (sut/run-fn {:home "/test" :_raw-args ["--tag" "project/chess" "--not-in-flight"]})))]
      (should-contain "sue" output)
      (should-not-contain "joe" output))))
