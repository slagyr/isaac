(ns isaac.cli.agents-spec
  (:require
    [clojure.string :as str]
    [speclj.core :refer :all]
    [isaac.cli.agents :as agents]
    [isaac.config.resolution :as config]))

(def test-agents
  {"main"  {:name "main"  :soul "You are Isaac."    :model "grover"}
   "ketch" {:name "ketch" :soul "You are a pirate." :model "grover"}})

(def test-models
  {"grover" {:alias "grover" :model "echo" :provider "grover" :contextWindow 32768}})

(describe "agents/resolve-agents"
  (it "returns a seq of agent maps with name, model, provider"
    (let [result (agents/resolve-agents {:agents test-agents :models test-models})]
      (should= 2 (count result))
      (let [main (first (filter #(= "main" (:name %)) result))]
        (should-not-be-nil main)
        (should= "echo" (:model main))
        (should= "grover" (:provider main)))))

  (it "shows inline soul truncated when short"
    (let [result  (agents/resolve-agents {:agents test-agents :models test-models})
          main    (first (filter #(= "main" (:name %)) result))]
      (should (str/includes? (:soul-source main) "You are Isaac."))))

  (it "truncates long inline souls to 40 chars"
    (let [long-soul "You are a very detailed and comprehensive AI assistant with many capabilities and features."
          result    (agents/resolve-agents
                      {:agents {"main" {:name "main" :soul long-soul :model "grover"}}
                       :models test-models})
          main      (first result)]
      (should (<= (count (:soul-source main)) 43)) ; 40 chars + "..."
      (should (str/ends-with? (:soul-source main) "..."))))

  (it "returns default main agent when no agents configured"
    (with-redefs [config/load-config (fn [& _] {:agents {:defaults {} :list []} :models {:providers []}})]
      (let [result (agents/resolve-agents {})]
        (should= 1 (count result))
        (should= "main" (:name (first result))))))

  (it "includes ketch agent"
    (let [result (agents/resolve-agents {:agents test-agents :models test-models})
          ketch  (first (filter #(= "ketch" (:name %)) result))]
      (should-not-be-nil ketch)
      (should= "echo" (:model ketch)))))

(describe "agents/format-agents"
  (it "includes header row"
    (let [rows   [{:name "main" :model "echo" :provider "grover" :soul-source "You are Isaac."}]
          output (agents/format-agents rows)]
      (should (str/includes? output "Name"))))

  (it "includes agent name and model in same line"
    (let [rows   [{:name "main" :model "echo" :provider "grover" :soul-source "You are Isaac."}]
          output (agents/format-agents rows)]
      (should (re-find #"main.*echo" output))))

  (it "shows multiple agents on separate lines"
    (let [rows   [{:name "main"  :model "echo" :provider "grover" :soul-source "You are Isaac."}
                  {:name "ketch" :model "echo" :provider "grover" :soul-source "You are a pirate."}]
          output (agents/format-agents rows)]
      (should (re-find #"main" output))
      (should (re-find #"ketch" output)))))

(describe "agents/run"
  (it "returns exit code 0 with configured agents"
    (let [code (with-out-str (agents/run {:agents test-agents :models test-models}))]
      (should-not-be-nil code)))

  (it "outputs agent names"
    (let [output (with-out-str (agents/run {:agents test-agents :models test-models}))]
      (should (str/includes? output "main"))
      (should (str/includes? output "ketch"))))

  (it "outputs model names"
    (let [output (with-out-str (agents/run {:agents test-agents :models test-models}))]
      (should (str/includes? output "echo"))))

  (it "outputs default main when no agents"
    (with-redefs [config/load-config (fn [& _] {:agents {:defaults {} :list []} :models {:providers []}})]
      (let [output (with-out-str (agents/run {}))]
        (should (str/includes? output "main"))))))
