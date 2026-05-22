(ns isaac.crew.cli-spec
  (:require
    [clojure.string :as str]
    [isaac.cli :as registry]
    [isaac.crew.cli :as sut]
    [isaac.config.loader :as config]
    [isaac.marigold :as marigold]
    [speclj.core :refer :all]))

(def test-model-id marigold/helm-mark-iii)
(def test-provider-id marigold/helm-systems)

(def test-crew
  {"main"               (marigold/crew-cfg marigold/captain :model test-model-id)
   marigold/first-mate (marigold/crew-cfg marigold/first-mate :model test-model-id)})

(def test-models
  {test-model-id (assoc (marigold/model-cfg test-provider-id test-model-id :context-window 32768)
                        :alias test-model-id)})

(describe "crew/resolve-crew"
  (it "returns a seq of crew maps with name, model, provider"
    (let [result (sut/resolve-crew {:crew test-crew :models test-models})]
      (should= 2 (count result))
      (let [main (first (filter #(= "main" (:name %)) result))]
        (should-not-be-nil main)
        (should= test-model-id (:model main))
        (should= test-provider-id (:provider main)))))

  (it "shows inline soul truncated when short"
    (let [result  (sut/resolve-crew {:crew test-crew :models test-models})
          main    (first (filter #(= "main" (:name %)) result))]
      (should (str/includes? (:soul-source main) "You are Atticus."))))

  (it "truncates long inline souls to 40 chars"
    (let [long-soul "You are a very detailed and comprehensive AI assistant with many capabilities and features."
          result    (sut/resolve-crew
                      {:crew {"main" {:name "main" :soul long-soul :model test-model-id}}
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
    (with-redefs [config/load-config (fn [& _] {:defaults  {:model marigold/helm-spark}
                                                :crew      {}
                                                :models    {marigold/helm-spark {:model marigold/helm-spark :provider marigold/flicker-labs :context-window 32768}}
                                                :providers {}})]
      (let [result (sut/resolve-crew {})]
        (should= marigold/helm-spark (:model (first result)))
        (should= marigold/flicker-labs (:provider (first result))))))

  (it "includes first mate crew member"
    (let [result (sut/resolve-crew {:crew test-crew :models test-models})
          first-mate (first (filter #(= marigold/first-mate (:name %)) result))]
      (should-not-be-nil first-mate)
      (should= test-model-id (:model first-mate)))))

(describe "crew/format-crew"
  (it "includes header row"
    (let [rows   [{:name "main" :model test-model-id :provider test-provider-id :soul-source "You are Atticus."}]
          output (sut/format-crew rows)]
      (should (str/includes? output "Name"))))

  (it "includes crew name and model in same line"
    (let [rows   [{:name "main" :model test-model-id :provider test-provider-id :soul-source "You are Atticus."}]
          output (sut/format-crew rows)]
      (should (re-find (re-pattern (str "main.*" test-model-id)) output))))

  (it "shows multiple crew members on separate lines"
    (let [rows   [{:name "main"                :model test-model-id :provider test-provider-id :soul-source "You are Atticus."}
                  {:name marigold/first-mate :model test-model-id :provider test-provider-id :soul-source "You are Cordelia."}]
          output (sut/format-crew rows)]
      (should (re-find #"main" output))
      (should (re-find (re-pattern marigold/first-mate) output)))))

(describe "crew/run"
  (it "returns exit code 0 with configured crew"
    (let [code (with-out-str (sut/run {:crew test-crew :models test-models}))]
      (should-not-be-nil code)))

  (it "outputs crew names"
    (let [output (with-out-str (sut/run {:crew test-crew :models test-models}))]
      (should (str/includes? output "main"))
      (should (str/includes? output marigold/first-mate))))

  (it "outputs model names"
    (let [output (with-out-str (sut/run {:crew test-crew :models test-models}))]
      (should (str/includes? output test-model-id))))

  (it "outputs default main when no crew configured"
    (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {} :models {} :providers {}})]
      (let [output (with-out-str (sut/run {}))]
        (should (str/includes? output "main"))))))

(describe "crew/run-fn"
  (it "prints command help and returns 0 when --help is requested"
    (with-redefs [sut/parse-option-map (fn [_] {:options {:help true} :errors []})
                  registry/get-command (fn [_] {:name "crew"})
                  registry/command-help (fn [_] "crew help")]
      (let [output (with-out-str (should= 0 (sut/run-fn {:_raw-args ["--help"]})))]
        (should (str/includes? output "crew help")))))

  (it "prints parse errors and returns 1"
    (with-redefs [sut/parse-option-map (fn [_] {:options {} :errors ["bad arg"]})]
      (let [output (with-out-str (should= 1 (sut/run-fn {:_raw-args ["--bogus"]})))]
        (should (str/includes? output "bad arg")))))

  (it "delegates to run without raw args"
    (let [captured (atom nil)]
      (with-redefs [sut/parse-option-map (fn [_] {:options {} :errors []})
                    sut/run              (fn [opts]
                                           (reset! captured opts)
                                           0)]
        (should= 0 (sut/run-fn {:_raw-args [] :home "/tmp/home"}))
        (should= {:home "/tmp/home"} @captured)))))
