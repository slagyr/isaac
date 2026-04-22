(ns isaac.config.loader-spec
  (:require
    [c3kit.apron.env :as c3env]
    [isaac.logger :as log]
    [isaac.spec-helper :as helper]
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

  (helper/with-captured-logs)

  (around [it]
    (binding [fs/*fs* (fs/mem-fs)]
      (reset! c3env/-overrides {})
      (sut/clear-env-overrides!)
      (it)))

  (describe "load-config-result"

    (it "returns an honest empty config when no files exist"
      (let [result (sut/load-config-result {:home test-root})]
        (should= [{:key "config"
                   :value (str "no config found; create " test-root "/.isaac/config/isaac.edn")}]
                 (:errors result))
        (should= {} (:config result))
        (should= true (:missing-config? result))
        (should= [] (:warnings result))
        (should= [] (:sources result))))

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

    (it "treats camelCase config keys as unknown after the hard cutover"
      (write-config! (config-path "providers/anthropic.edn") {:apiKey "${ANTHROPIC_API_KEY}"})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= [{:key "providers.anthropic.apiKey" :value "unknown key"}] (:warnings result))))

    (it "validates semantic references across defaults crew model and providers"
      (write-config! (config-path "isaac.edn") {:defaults {:crew :ghost :model :llama}
                                                 :crew {:marvin {:model :gpt}}
                                                 :models {:grover {:model "claude-opus-4-7" :provider :anthropic :context-window 200000}}})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [{:key "crew.marvin.model" :value "references undefined model \"gpt\""}
                  {:key "defaults.crew" :value "references undefined crew \"ghost\""}
                  {:key "defaults.model" :value "references undefined model \"llama\""}
                  {:key "models.grover.provider" :value "references undefined provider \"anthropic\""}]
                 (:errors result))))

    (it "substitutes environment variables in loaded config"
      (write-config! (config-path "providers/anthropic.edn")
                     {:api "anthropic" :api-key "${ANTHROPIC_API_KEY}" :base-url "https://api.anthropic.com"})
      (with-redefs [sut/env (fn [name] (when (= "ANTHROPIC_API_KEY" name) "sk-test-123"))]
        (let [result (sut/load-config-result {:home test-root})]
          (should= [] (:errors result))
          (should= "sk-test-123" (get-in result [:config :providers "anthropic" :api-key])))))

    (it "substitutes environment variables from the isaac .env file"
      (write-file! (str test-root "/.env") "ISAAC_ENV_FILE_TEST_KEY=sk-from-isaac\n")
      (write-config! (config-path "providers/anthropic.edn")
                     {:api "anthropic" :api-key "${ISAAC_ENV_FILE_TEST_KEY}" :base-url "https://api.anthropic.com"})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= "sk-from-isaac" (get-in result [:config :providers "anthropic" :api-key]))))

    (it "prefers c3env values over the isaac .env file"
      (write-file! (str test-root "/.env") "ISAAC_ENV_FILE_TEST_KEY=sk-from-isaac\n")
      (write-config! (config-path "providers/anthropic.edn")
                     {:api "anthropic" :api-key "${ISAAC_ENV_FILE_TEST_KEY}" :base-url "https://api.anthropic.com"})
      (c3env/override! "ISAAC_ENV_FILE_TEST_KEY" "sk-from-override")
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= "sk-from-override" (get-in result [:config :providers "anthropic" :api-key]))))

    (it "loads config when the isaac .env file is absent"
      (write-config! (config-path "isaac.edn") {:defaults {:crew :main :model :llama}
                                                 :crew {:main {}}
                                                 :models {:llama {:model "llama3.3:1b" :provider :anthropic}}
                                                 :providers {:anthropic {}}})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= "main" (get-in result [:config :defaults :crew]))))

    (it "preserves cron jobs and timezone from the root config"
      (write-config! (config-path "isaac.edn") {:crew {:main {}}
                                                 :tz   "America/Chicago"
                                                 :cron {:health-check {:expr  "0 9 * * *"
                                                                       :crew  :main
                                                                       :prompt "Run the health checkin."}}})
      (let [result (sut/load-config-result {:home test-root})]
        (should= "America/Chicago" (get-in result [:config :tz]))
        (should= {:expr  "0 9 * * *"
                  :crew  "main"
                  :prompt "Run the health checkin."}
                 (get-in result [:config :cron "health-check"])))))

    (it "loads cron prompt from a companion markdown file"
      (write-config! (config-path "isaac.edn") {:crew {:main {}}
                                                 :cron {:health-check {:expr "0 9 * * *"
                                                                       :crew :main}}})
      (write-file! (config-path "cron/health-check.md") "Run the daily health checkin.")
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= "Run the daily health checkin."
                 (get-in result [:config :cron "health-check" :prompt]))))

    (it "reports an error when a cron prompt is missing inline and in markdown"
      (write-config! (config-path "isaac.edn") {:crew {:main {}}
                                                 :cron {:health-check {:expr "0 9 * * *"
                                                                       :crew :main}}})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [{:key "cron.health-check.prompt"
                   :value "required (inline or cron/health-check.md)"}]
                 (filter #(= "cron.health-check.prompt" (:key %)) (:errors result)))))

    (it "reports an error when a cron companion markdown file is empty"
      (write-config! (config-path "isaac.edn") {:crew {:main {}}
                                                 :cron {:health-check {:expr "0 9 * * *"
                                                                       :crew :main}}})
      (write-file! (config-path "cron/health-check.md") "")
      (let [result (sut/load-config-result {:home test-root})]
        (should= [{:key "cron.health-check.prompt"
                   :value "must not be empty"}]
                 (filter #(= "cron.health-check.prompt" (:key %)) (:errors result)))))

    (it "warns and keeps the inline cron prompt when both inline and markdown are present"
      (write-config! (config-path "isaac.edn") {:crew {:main {}}
                                                 :cron {:health-check {:expr   "0 9 * * *"
                                                                       :crew   :main
                                                                       :prompt "Inline prompt."}}})
      (write-file! (config-path "cron/health-check.md") "Markdown prompt.")
      (let [result (sut/load-config-result {:home test-root})
            entry  (last @log/captured-logs)]
        (should= [] (:errors result))
        (should= "Inline prompt." (get-in result [:config :cron "health-check" :prompt]))
        (should= :config/companion-inline-wins (:event entry))
        (should= :prompt (:field entry))
        (should= "cron.health-check" (:key entry)))))

  (describe "resolve-crew-context"

    (it "resolves crew model provider and context window from the new map-by-id shape"
      (let [cfg {:defaults  {:crew "main" :model "llama"}
                 :crew      {"main" {:model "grover" :soul "You are Isaac."}}
                 :models    {"grover" {:model "claude-opus-4-7" :provider "anthropic" :context-window 200000}}
                 :providers {"anthropic" {:api "anthropic" :base-url "https://api.anthropic.com"}}}
            ctx (sut/resolve-crew-context cfg "main" {:home test-root})]
        (should= "You are Isaac." (:soul ctx))
        (should= "claude-opus-4-7" (:model ctx))
        (should= "anthropic" (:provider ctx))
        (should= 200000 (:context-window ctx))
        (should= "https://api.anthropic.com" (get-in ctx [:provider-config :base-url])))))

  (describe "server-config"

    (it "returns default port 6674 and host 0.0.0.0 when no config is provided"
      (let [result (sut/server-config {})]
        (should= 6674 (:port result))
        (should= "0.0.0.0" (:host result))))

    (it "aliases gateway.port to server.port"
      (should= 9000 (:port (sut/server-config {:gateway {:port 9000}})))))
