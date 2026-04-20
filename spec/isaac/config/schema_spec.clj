(ns isaac.config.schema-spec
  (:require
    [c3kit.apron.schema :as schema]
    [isaac.config.schema :as sut]
    [speclj.core :refer :all]))

(describe "config schema"

  (describe "shape"

    (it "each entity is a named wrapped map spec"
      (doseq [[nm spec] {:defaults sut/defaults
                         :crew     sut/crew
                         :tools    sut/tools
                         :model    sut/model
                         :provider sut/provider
                         :acp      sut/acp
                         :server   sut/server
                         :gateway  sut/gateway
                         :root     sut/root}]
        (should= :map (:type spec))
        (should= nm (:name spec))
        (should (map? (:schema spec))))))

  (describe "entity conformance"

    (it "defaults conforms keyword ids to strings"
      (should= {:crew "main" :model "llama"}
               (schema/conform sut/defaults {:crew :main :model :llama})))

    (it "crew conforms with tools nested"
      (should= {:id    "marvin"
                :model "gpt"
                :soul  "Paranoid."
                :tools {:allow       [:read :write]
                        :directories [:cwd "/tmp/playground"]}}
               (schema/conform sut/crew
                               {:id    :marvin
                                :model :gpt
                                :soul  "Paranoid."
                                :tools {:allow       [:read :write]
                                        :directories [:cwd "/tmp/playground"]}})))

    (it "model conforms with all required + optional fields"
      (should= {:id "gpt" :model "gpt-5" :provider "openai" :context-window 128000}
               (schema/conform sut/model
                               {:id             :gpt
                                :model          "gpt-5"
                                :provider       :openai
                                :context-window 128000})))

    (it "provider conforms including string→string headers"
      (should= {:base-url "https://api" :api "openai" :headers {"X-Foo" "bar"}}
               (schema/conform sut/provider
                               {:base-url "https://api"
                                :api      "openai"
                                :headers  {"X-Foo" "bar"}})))

    (it "acp conforms"
      (should= {:proxy-max-reconnects 5}
               (schema/conform sut/acp {:proxy-max-reconnects 5})))

    (it "server conforms"
      (should= {:host "localhost" :port 8080}
               (schema/conform sut/server {:host "localhost" :port 8080})))

    (it "gateway conforms with nested auth"
      (should= {:host "0.0.0.0" :port 6674 :auth {:mode :token :token "secret"}}
               (schema/conform sut/gateway
                               {:host "0.0.0.0" :port 6674
                                :auth {:mode :token :token "secret"}})))

    (it "root conforms a complete config"
      (should= {:defaults  {:crew "main" :model "llama"}
                :crew      {"main" {:soul "You are Isaac."}}
                :models    {"llama" {:model "llama3.3:1b" :provider "ollama"}}
                :providers {"ollama" {:base-url "http://localhost:11434"}}
                :dev       false
                :prefer-entity-files false}
               (schema/conform sut/root
                               {:defaults  {:crew :main :model :llama}
                                :crew      {"main" {:soul "You are Isaac."}}
                                :models    {"llama" {:model "llama3.3:1b" :provider :ollama}}
                                :providers {"ollama" {:base-url "http://localhost:11434"}}
                                :dev       false
                                :prefer-entity-files false}))))

  (describe "custom validation"

    (it "tools.:directories rejects a non-:cwd keyword"
      (let [result (schema/conform sut/tools {:directories [:not-cwd]})]
        (should (schema/error? result))))

    (it "tools.:directories rejects non-keyword non-string entries"
      (let [result (schema/conform sut/tools {:directories [42]})]
        (should (schema/error? result))))

    (it "root rejects invalid types with per-field errors"
      (let [result (schema/conform sut/root
                                   {:providers {"openai" {:headers 42}}
                                    :server    {:port "not-a-number"}})]
        (should (schema/error? result))
        (should= {:providers {"openai" {:headers "can't coerce 42 to map"}}
                  :server    {:port "can't coerce \"not-a-number\" to int"}}
                 (schema/message-map result))))))
