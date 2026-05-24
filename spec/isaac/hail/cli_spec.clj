(ns isaac.hail.cli-spec
  (:require
    [cheshire.core :as json]
    [isaac.fs :as fs]
    [isaac.hail.cli :as sut]
    [isaac.nexus :as nexus]
    [isaac.tool.memory :as memory]
    [speclj.core :refer :all]))

(describe "hail cli"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (nexus/-with-nested-nexus {:state-dir "/test/isaac" :fs (fs/mem-fs)}
      (example)))

  (it "prints the hail id by default"
    (let [output (with-out-str
                   (should= 0 (sut/run-fn {:_raw-args ["send" "--band" "bean-pickup" "--payload" "{:n 1}"]})))]
      (should= "hail-1\n" output)
      (should= "{:n 1}"
               (pr-str (:payload (sut/read-pending "hail-1"))))))

  (it "prints the full hail record as JSON"
    (binding [memory/*now* (java.time.Instant/parse "2026-05-23T12:00:00Z")]
      (let [output (with-out-str
                     (should= 0 (sut/run-fn {:_raw-args ["send" "--band" "bean-pickup" "--payload" "{:n 1}" "--json"]})))
            value  (json/parse-string output true)]
        (should= "hail-1" (:id value))
        (should= "bean-pickup" (get-in value [:frequency :band]))
        (should= 1 (get-in value [:payload :n]))
        (should= "cli" (:from value))
        (should= "2026-05-23T12:00:00Z" (:sent-at value)))))

  (it "reads a whole hail record from stdin"
    (let [output (with-in-str "{:frequency {:band \"bean-pickup\"} :payload {:n 1}}"
                   (with-out-str
                     (should= 0 (sut/run-fn {:_raw-args ["send" "-"]}))))]
      (should= "hail-1\n" output)
      (should= {:band "bean-pickup"}
               (:frequency (sut/read-pending "hail-1"))))))
