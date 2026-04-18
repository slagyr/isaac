(ns isaac.config.loader-spec
  (:require
    [isaac.config.loader :as sut]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(def test-root "/test/config-loader")

(defn- write-config! [path data]
  (fs/spit path (pr-str data)))

(defn- write-file! [path content]
  (fs/spit path content))

(defn- config-path [suffix]
  (str test-root "/.isaac/config/" suffix))

(describe "config loader"

  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (describe "load-config-result"

    (it "returns the built-in default config when no files exist"
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= [] (:warnings result))
        (should= "main" (get-in result [:config :defaults :crew]))
        (should= "llama" (get-in result [:config :defaults :model]))
        (should= "llama3.3:1b" (get-in result [:config :models "llama" :model]))
        (should= "ollama" (get-in result [:config :models "llama" :provider]))))

    (it "loads crew members from per-entity files and companion md soul"
      (write-config! (config-path "crew/marvin.edn") {:model :llama})
      (write-file! (config-path "crew/marvin.md") "You are Marvin.")
      (let [result (sut/load-config-result {:home test-root})]
        (should= "llama" (get-in result [:config :crew "marvin" :model]))
        (should= "You are Marvin." (get-in result [:config :crew "marvin" :soul]))))

    (it "reports duplicate ids across isaac.edn and per-entity files"
      (write-config! (config-path "isaac.edn") {:crew {:marvin {:soul "First"}}})
      (write-config! (config-path "crew/marvin.edn") {:soul "Second"})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [{:key "crew.marvin"
                   :value "defined in both isaac.edn and crew/marvin.edn"}]
                 (:errors result))))

    (it "reports a soul conflict when both edn and companion md define soul"
      (write-config! (config-path "crew/marvin.edn") {:soul "Inline soul."})
      (write-file! (config-path "crew/marvin.md") "File soul.")
      (let [result (sut/load-config-result {:home test-root})]
        (should= [{:key "crew.marvin.soul"
                   :value "must be set in .edn OR .md"}]
                 (:errors result))))

    (it "warns about unknown keys in entity files but still loads"
      (write-config! (config-path "crew/marvin.edn") {:crew {:marvin {:model :llama}}})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= [{:key "crew.marvin.crew" :value "unknown key"}] (:warnings result))))

    (it "validates semantic references across defaults crew model and providers"
      (write-config! (config-path "isaac.edn") {:defaults {:crew :ghost :model :llama}
                                                 :crew {:marvin {:model :gpt}}
                                                 :models {:grover {:model "claude-opus-4-7" :provider :anthropic :contextWindow 200000}}})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [{:key "crew.marvin.model" :value "references undefined model \"gpt\""}
                  {:key "defaults.crew" :value "references undefined crew \"ghost\""}
                  {:key "models.grover.provider" :value "references undefined provider \"anthropic\""}]
                 (:errors result))))

    (it "substitutes environment variables in loaded config"
      (write-config! (config-path "providers/anthropic.edn")
                     {:api "anthropic" :apiKey "${ANTHROPIC_API_KEY}" :baseUrl "https://api.anthropic.com"})
      (with-redefs [sut/env (fn [name] (when (= "ANTHROPIC_API_KEY" name) "sk-test-123"))]
        (let [result (sut/load-config-result {:home test-root})]
          (should= [] (:errors result))
          (should= "sk-test-123" (get-in result [:config :providers "anthropic" :apiKey]))))))

  (describe "resolve-crew-context"

    (it "resolves crew model provider and context window from the new map-by-id shape"
      (let [cfg {:defaults  {:crew "main" :model "llama"}
                 :crew      {"main" {:model "grover" :soul "You are Isaac."}}
                 :models    {"grover" {:model "claude-opus-4-7" :provider "anthropic" :contextWindow 200000}}
                 :providers {"anthropic" {:api "anthropic" :baseUrl "https://api.anthropic.com"}}}
            ctx (sut/resolve-crew-context cfg "main" {:home test-root})]
        (should= "You are Isaac." (:soul ctx))
        (should= "claude-opus-4-7" (:model ctx))
        (should= "anthropic" (:provider ctx))
        (should= 200000 (:context-window ctx))
        (should= "https://api.anthropic.com" (get-in ctx [:provider-config :baseUrl]))))))
