(ns isaac.session.cli-spec
  (:require
    [cheshire.core :as json]
    [isaac.session.cli :as sut]
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
    (helper/create-session! "/test/sessions" "joe" {:crew "main"})
    (let [output (with-out-str (should= 0 (sut/run-fn {:home "/test" :_raw-args ["--json"]})))
          rows   (json/parse-string output true)
          joe    (some #(when (= "joe" (:name %)) %) rows)]
      (should= "joe" (:name joe))
      (should= "main" (:crew joe))))

  (it "renders show output as JSON"
    (helper/create-session! "/test/sessions" "joe" {:crew "main"})
    (helper/update-session! "/test/sessions" "joe" {:crew "alice"})
    (let [output (with-out-str (should= 0 (sut/run-fn {:home "/test" :_raw-args ["show" "joe" "--json"]})))
          row    (json/parse-string output true)]
      (should= "joe" (:name row))
      (should= "alice" (:crew row)))))
