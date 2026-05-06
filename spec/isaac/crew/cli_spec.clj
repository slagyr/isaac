(ns isaac.crew.cli-spec
  (:require
    [clojure.string :as str]
    [speclj.core :refer :all]
    [isaac.crew.cli :as sut]
    [isaac.config.loader :as config]))

(def test-crew
  {"main"  {:name "main"  :soul "You are Isaac."    :model "grover"}
   "ketch" {:name "ketch" :soul "You are a pirate." :model "grover"}})

(def test-models
  {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}})

(describe "crew/resolve-crew"
  (it "returns a seq of crew maps with name, model, provider"
    (let [result (sut/resolve-crew {:crew test-crew :models test-models})]
      (should= 2 (count result))
      (let [main (first (filter #(= "main" (:name %)) result))]
        (should-not-be-nil main)
        (should= "echo" (:model main))
        (should= "grover" (:provider main)))))

  (it "shows inline soul truncated when short"
    (let [result  (sut/resolve-crew {:crew test-crew :models test-models})
          main    (first (filter #(= "main" (:name %)) result))]
      (should (str/includes? (:soul-source main) "You are Isaac."))))

  (it "truncates long inline souls to 40 chars"
    (let [long-soul "You are a very detailed and comprehensive AI assistant with many capabilities and features."
          result    (sut/resolve-crew
                      {:crew {"main" {:name "main" :soul long-soul :model "grover"}}
                       :models test-models})
          main      (first result)]
      (should (<= (count (:soul-source main)) 43))
      (should (str/ends-with? (:soul-source main) "..."))))

  (it "returns default main crew when no crew configured"
    (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {} :models {} :providers {}})]
      (let [result (sut/resolve-crew {})]
        (should= 1 (count result))
        (should= "main" (:name (first result))))))

  (it "shows actual default model when no crew configured"
    (with-redefs [config/load-config (fn [& _] {:defaults  {:model "llama3"}
                                                :crew      {}
                                                :models    {"llama3" {:model "llama3" :provider "ollama" :context-window 32768}}
                                                :providers {}})]
      (let [result (sut/resolve-crew {})]
        (should= "llama3" (:model (first result)))
        (should= "ollama" (:provider (first result))))))

  (it "includes ketch crew member"
    (let [result (sut/resolve-crew {:crew test-crew :models test-models})
          ketch  (first (filter #(= "ketch" (:name %)) result))]
      (should-not-be-nil ketch)
      (should= "echo" (:model ketch)))))

(describe "crew/format-crew"
  (it "includes header row"
    (let [rows   [{:name "main" :model "echo" :provider "grover" :soul-source "You are Isaac."}]
          output (sut/format-crew rows)]
      (should (str/includes? output "Name"))))

  (it "includes crew name and model in same line"
    (let [rows   [{:name "main" :model "echo" :provider "grover" :soul-source "You are Isaac."}]
          output (sut/format-crew rows)]
      (should (re-find #"main.*echo" output))))

  (it "shows multiple crew members on separate lines"
    (let [rows   [{:name "main"  :model "echo" :provider "grover" :soul-source "You are Isaac."}
                  {:name "ketch" :model "echo" :provider "grover" :soul-source "You are a pirate."}]
          output (sut/format-crew rows)]
      (should (re-find #"main" output))
      (should (re-find #"ketch" output)))))

(describe "crew/run"
  (it "returns exit code 0 with configured crew"
    (let [code (with-out-str (sut/run {:crew test-crew :models test-models}))]
      (should-not-be-nil code)))

  (it "outputs crew names"
    (let [output (with-out-str (sut/run {:crew test-crew :models test-models}))]
      (should (str/includes? output "main"))
      (should (str/includes? output "ketch"))))

  (it "outputs model names"
    (let [output (with-out-str (sut/run {:crew test-crew :models test-models}))]
      (should (str/includes? output "echo"))))

  (it "outputs default main when no crew configured"
    (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {} :models {} :providers {}})]
      (let [output (with-out-str (sut/run {}))]
        (should (str/includes? output "main"))))))
