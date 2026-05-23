(ns isaac.crew.cli-spec
  (:require
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [isaac.config.loader :as config]
    [isaac.crew.cli :as sut]
    [speclj.core :refer :all]))

(def crew-cfg
  {:crew   {"main" {:model "grover"}
            "joe"  {:model "grover" :soul "You are Joe."}}
   :models {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}})

(def crew-opts {:state-dir "/test/crew" :home "/test/crew-home"})

(describe "crew cli"

  (it "renders list output as JSON with sorted tags"
    (with-redefs [config/load-config (fn [_] crew-cfg)]
      (let [output (with-out-str (should= 0 (sut/run (assoc crew-opts :json true))))
            rows   (json/parse-string output true)
            joe    (some #(when (= "joe" (:name %)) %) rows)]
        (should= "echo" (:model joe))
        (should= "grover" (:provider joe)))))

  (it "renders list output as EDN with tags as a set"
    (with-redefs [config/load-config (fn [_] crew-cfg)]
      (let [output (with-out-str (should= 0 (sut/run (assoc crew-opts :edn true))))
            rows   (edn/read-string output)
            joe    (some #(when (= "joe" (:name %)) %) rows)]
        (should= "echo" (:model joe))
        (should= "grover" (:provider joe)))))

  (it "renders show output as JSON"
    (with-redefs [config/load-config (fn [_] crew-cfg)]
      (let [output (with-out-str (should= 0 (sut/run-fn (assoc crew-opts :_raw-args ["show" "joe" "--json"]))))
            row    (json/parse-string output true)]
        (should= "joe" (:name row))
        (should= "echo" (:model row))
        (should= "grover" (:provider row))))))
