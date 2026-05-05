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
                   :value (str "no config found; run `isaac init` or create " test-root "/.isaac/config/isaac.edn")}]
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

    (it "loads crew members from a single markdown file with EDN frontmatter"
      (write-config! (config-path "isaac.edn") {:models    {:llama {:model "llama3.2" :provider :ollama}}
                                                 :providers {:ollama {:api "ollama"}}})
      (write-file! (config-path "crew/marvin.md") (str "---\n"
                                                         "{:model :llama}\n"
                                                         "---\n\n"
                                                         "You are Marvin."))
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= "llama" (get-in result [:config :crew "marvin" :model]))
        (should= "You are Marvin." (get-in result [:config :crew "marvin" :soul]))))

    (it "prefers single-file crew markdown over legacy files and warns"
      (write-config! (config-path "isaac.edn") {:models    {:grover {:model "claude-opus-4-7" :provider :anthropic}}
                                                 :providers {:anthropic {:api "anthropic"}}})
      (write-config! (config-path "crew/marvin.edn") {:model :llama})
      (write-file! (config-path "crew/marvin.md") (str "---\n"
                                                         "{:model :grover}\n"
                                                         "---\n\n"
                                                         "You are Marvin."))
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= "grover" (get-in result [:config :crew "marvin" :model]))
        (should= "You are Marvin." (get-in result [:config :crew "marvin" :soul]))
        (should= [{:key "crew/marvin.md"
                   :value "single-file config overrides legacy crew/marvin.edn"}]
                 (filter #(= "crew/marvin.md" (:key %)) (:warnings result)))))

    (it "reports duplicate ids across isaac.edn and per-entity files"
      (write-config! (config-path "isaac.edn") {:crew {:marvin {:soul "First"}}})
      (write-config! (config-path "crew/marvin.edn") {:soul "Second"})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [{:key "crew.marvin"
                   :value "defined in both isaac.edn and crew/marvin.edn"}]
                  (:errors result))))

    (it "reports malformed crew EDN with the relative file path"
      (write-file! (config-path "crew/marvin.edn") "{:model :llama")
      (let [result (sut/load-config-result {:home test-root})]
        (should= [{:key "crew/marvin.edn"
                    :value "EDN syntax error"}]
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

    (it "warns about a dangling crew markdown companion without a matching entry"
      (write-config! (config-path "isaac.edn") {:defaults  {:crew :main :model :llama}
                                                 :crew      {:main {:soul "Hello"}}
                                                 :models    {:llama {:model "llama" :provider :anthropic}}
                                                 :providers {:anthropic {}}})
      (write-file! (config-path "crew/ghost.md") "I have no matching entity.")
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= [{:key "crew/ghost.md" :value "dangling: no matching crew entry"}]
                 (filter #(= "crew/ghost.md" (:key %)) (:warnings result)))))

    (it "warns about a dangling cron markdown companion without a matching cron job"
      (write-config! (config-path "isaac.edn") {:defaults  {:crew :main :model :llama}
                                                 :crew      {:main {}}
                                                 :models    {:llama {:model "llama" :provider :anthropic}}
                                                 :providers {:anthropic {}}})
      (write-file! (config-path "cron/ghost.md") "I have no matching cron job.")
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= [{:key "cron/ghost.md" :value "dangling: no matching cron entry"}]
                 (filter #(= "cron/ghost.md" (:key %)) (:warnings result)))))

    (it "does not warn when a crew markdown companion has a matching entity file"
      (write-config! (config-path "isaac.edn") {:defaults  {:crew :main :model :llama}
                                                 :models    {:llama {:model "llama" :provider :anthropic}}
                                                 :providers {:anthropic {}}})
      (write-config! (config-path "crew/main.edn") {:model :llama})
      (write-file! (config-path "crew/main.md") "You are Isaac.")
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (filter #(= "crew/main.md" (:key %)) (:warnings result)))))

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
                  {:key "defaults.model" :value "references undefined model \"llama\""}]
                  (:errors result))))

    (it "accepts built-in providers without provider entity files"
      (write-config! (config-path "isaac.edn") {:models {:claude {:model "claude-opus-4-7"
                                                                    :provider :anthropic
                                                                    :context-window 200000}}})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= "anthropic" (get-in result [:config :models "claude" :provider]))))

    (it "loads provider entity overrides on top of built-in providers"
      (write-config! (config-path "isaac.edn") {:models {:claude {:model "claude-opus-4-7"
                                                                    :provider :anthropic
                                                                    :context-window 200000}}})
      (write-config! (config-path "providers/anthropic.edn") {:api-key "sk-test"
                                                                :base-url "https://api.anthropic.com"})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= "https://api.anthropic.com" (get-in result [:config :providers "anthropic" :base-url]))
        (should= "sk-test" (get-in result [:config :providers "anthropic" :api-key]))))

    (it "reports unknown providers with the known provider list"
      (write-config! (config-path "isaac.edn") {:models {:mystery {:model "enigmatic-1"
                                                                     :provider :foo
                                                                     :context-window 1024}}})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [{:key "models.mystery.provider"
                   :value "references undefined provider \"foo\" (known: anthropic, claude-sdk, grok, grover, ollama, openai-chatgpt, openai-codex)"}]
                 (:errors result))))

    (it "substitutes environment variables in loaded config"
      (write-config! (config-path "providers/anthropic.edn")
                     {:api "anthropic" :api-key "${ANTHROPIC_API_KEY}" :base-url "https://api.anthropic.com"})
      (with-redefs [sut/env (fn [name] (when (= "ANTHROPIC_API_KEY" name) "sk-test-123"))]
        (let [result (sut/load-config-result {:home test-root})]
          (should= [] (:errors result))
          (should= "sk-test-123" (get-in result [:config :providers "anthropic" :api-key])))))

    (it "substitutes environment variables from the isaac .env file"
      (write-file! (str test-root "/.isaac/.env") "ISAAC_ENV_FILE_TEST_KEY=sk-from-isaac\n")
      (write-config! (config-path "providers/anthropic.edn")
                     {:api "anthropic" :api-key "${ISAAC_ENV_FILE_TEST_KEY}" :base-url "https://api.anthropic.com"})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= "sk-from-isaac" (get-in result [:config :providers "anthropic" :api-key]))))

    (it "prefers c3env values over the isaac .env file"
      (write-file! (str test-root "/.isaac/.env") "ISAAC_ENV_FILE_TEST_KEY=sk-from-isaac\n")
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

    (it "loads cron jobs from a single markdown file with EDN frontmatter"
      (write-config! (config-path "isaac.edn") {:crew {:main {}}})
      (write-file! (config-path "cron/health-check.md") (str "---\n"
                                                               "{:expr \"0 9 * * *\"\n"
                                                               " :crew :main}\n"
                                                               "---\n\n"
                                                               "Run the daily health checkin."))
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= {:expr "0 9 * * *"
                  :crew "main"
                  :prompt "Run the daily health checkin."}
                 (get-in result [:config :cron "health-check"]))))

    (it "loads cron jobs from legacy edn and markdown files"
      (write-config! (config-path "isaac.edn") {:crew {:main {}}})
      (write-config! (config-path "cron/health-check.edn") {:expr "0 9 * * *"
                                                              :crew :main})
      (write-file! (config-path "cron/health-check.md") "Run the daily health checkin.")
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= {:expr "0 9 * * *"
                  :crew "main"
                  :prompt "Run the daily health checkin."}
                 (get-in result [:config :cron "health-check"]))))

    (it "loads hooks from a single markdown file with EDN frontmatter"
      (write-config! (config-path "isaac.edn") {:crew  {:main {}}
                                                 :hooks {:auth {:token "secret123"}}})
      (write-file! (config-path "hooks/lettuce.md") (str "---\n"
                                                          "{:crew :main\n"
                                                          " :session-key \"hook:lettuce\"}\n"
                                                          "---\n\n"
                                                          "Emergency lettuce report: {{leaves}} leaves remaining."))
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= "secret123" (get-in result [:config :hooks :auth :token]))
        (should= {:crew "main"
                  :session-key "hook:lettuce"
                  :template "Emergency lettuce report: {{leaves}} leaves remaining."}
                 (get-in result [:config :hooks "lettuce"]))))

    (it "loads hooks from legacy edn and markdown files"
      (write-config! (config-path "isaac.edn") {:crew {:main {}}})
      (write-config! (config-path "hooks/lettuce.edn") {:crew :main
                                                          :session-key "hook:lettuce"})
      (write-file! (config-path "hooks/lettuce.md") "Emergency lettuce report: {{leaves}} leaves remaining.")
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= {:crew "main"
                  :session-key "hook:lettuce"
                  :template "Emergency lettuce report: {{leaves}} leaves remaining."}
                 (get-in result [:config :hooks "lettuce"]))))

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
        (should= "anthropic" ((requiring-resolve 'isaac.provider/display-name) (:provider ctx)))
        (should= 200000 (:context-window ctx))
        (should= "https://api.anthropic.com" (get-in ((requiring-resolve 'isaac.provider/config) (:provider ctx)) [:base-url])))))

  (describe "module discovery integration"

    (around [it]
      (binding [fs/*fs* (fs/mem-fs)]
        (sut/clear-env-overrides!)
        (it)))

    (it "attaches :module-index to loaded config for declared modules"
      (write-config! (config-path "isaac.edn") {:modules '[isaac.comm.pigeon]})
      (fs/mkdirs (str test-root "/.isaac/modules/isaac.comm.pigeon"))
      (fs/spit (str test-root "/.isaac/modules/isaac.comm.pigeon/module.edn")
               "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= :isaac.comm.pigeon
                 (get-in result [:config :module-index :isaac.comm.pigeon :manifest :id]))
        (should= "modules/isaac.comm.pigeon"
                 (get-in result [:config :module-index :isaac.comm.pigeon :path]))))

    (it "adds validation errors when a module directory is not found"
      (write-config! (config-path "isaac.edn") {:modules '[isaac.comm.ghost]})
      (let [result (sut/load-config-result {:home test-root})]
        (should (some #(and (= "modules[\"isaac.comm.ghost\"]" (:key %))
                            (= "module directory not found" (:value %)))
                      (:errors result)))))

    (it "adds validation errors when a module manifest is invalid"
      (write-config! (config-path "isaac.edn") {:modules '[isaac.comm.pigeon]})
      (fs/mkdirs (str test-root "/.isaac/modules/isaac.comm.pigeon"))
      (fs/spit (str test-root "/.isaac/modules/isaac.comm.pigeon/module.edn")
               "{:id :isaac.comm.pigeon :entry isaac.comm.pigeon}")
      (let [result (sut/load-config-result {:home test-root})]
        (should (some #(= "module-index[\"isaac.comm.pigeon\"].version" (:key %))
                      (:errors result)))))

    (it "simulates the feature scenario: separate write binding then load binding"
      ;; Mimics the feature runner: write files in one binding, load in another
      (let [mem (fs/mem-fs)]
        ;; write phase (like "the isaac file" step)
        (binding [fs/*fs* mem]
          (fs/mkdirs (str test-root "/.isaac/modules/isaac.comm.pigeon"))
          (fs/spit (str test-root "/.isaac/modules/isaac.comm.pigeon/module.edn")
                   "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
          (fs/mkdirs (str test-root "/.isaac/config"))
          (fs/spit (str test-root "/.isaac/config/isaac.edn")
                   "{:modules [isaac.comm.pigeon]}"))
        ;; load phase (like "when the config is loaded" step — NEW binding to SAME mem)
        (binding [fs/*fs* mem]
          (let [result (sut/load-config-result {:home test-root})]
            (should-not-be-nil (get-in result [:config :module-index :isaac.comm.pigeon]))
            (should= :isaac.comm.pigeon
                     (get-in result [:config :module-index :isaac.comm.pigeon :manifest :id])))))))

  (describe "comm slot validation"

    (def telly-manifest
      (pr-str {:id      :isaac.comm.telly
               :version "0.1.0"
               :entry   'isaac.comm.telly
               :extends {:comm {:telly {:loft  {:type :string}
                                        :color {:type :string}}}}}))

    (around [it]
      (binding [fs/*fs* (fs/mem-fs)]
        (sut/clear-env-overrides!)
        (it)))

    (defn- write-telly-module! []
      (fs/mkdirs (str test-root "/.isaac/modules/isaac.comm.telly"))
      (fs/spit (str test-root "/.isaac/modules/isaac.comm.telly/module.edn") telly-manifest))

    (def discord-manifest
      (pr-str {:id      :isaac.comm.discord
               :version "0.1.0"
               :entry   'isaac.comm.discord
               :extends {:comm {:discord {:token       {:type :string}
                                          :crew        {:type :string}
                                          :message-cap {:type :int}
                                          :allow-from  {:type :map}}}}}))

    (defn- write-discord-module! []
      (fs/mkdirs (str test-root "/.isaac/modules/isaac.comm.discord"))
      (fs/spit (str test-root "/.isaac/modules/isaac.comm.discord/module.edn") discord-manifest))

    (it "validates declared module comm slot fields with no error for valid value"
      (write-config! (config-path "isaac.edn")
                     {:modules '[isaac.comm.telly] :comms {:bert {:impl :telly :loft "rooftop"}}})
      (write-telly-module!)
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= "rooftop" (get-in result [:config :comms :bert :loft]))))

    (it "generates a validation error for wrong type in a module comm slot field"
      (write-config! (config-path "isaac.edn")
                     {:modules '[isaac.comm.telly] :comms {:bert {:impl :telly :loft 42}}})
      (write-telly-module!)
      (let [result (sut/load-config-result {:home test-root})]
        (should (some #(and (= "comms.bert.loft" (:key %))
                            (= "must be a string" (:value %)))
                      (:errors result)))))

    (it "generates unknown-key warnings for comm slot fields when module is not declared"
      (write-config! (config-path "isaac.edn")
                     {:comms {:bert {:impl :telly :loft "rooftop"}}})
      (let [result (sut/load-config-result {:home test-root})]
        (should (some #(and (= "comms.bert.loft" (:key %))
                            (= "unknown key" (:value %)))
                      (:warnings result)))))

    (it "does not warn for discord when its module is declared"
      (write-config! (config-path "isaac.edn")
                     {:modules [:isaac.comm.discord]
                      :comms   {:mychan {:impl :discord :token "abc"}}})
      (write-discord-module!)
      (let [result (sut/load-config-result {:home test-root})]
        (should-not (some #(clojure.string/includes? (:key %) "comms.mychan") (:warnings result))))))

  (describe "server-config"

    (it "returns default port 6674 and host 0.0.0.0 when no config is provided"
      (let [result (sut/server-config {})]
        (should= 6674 (:port result))
        (should= "0.0.0.0" (:host result))
        (should= true (:hot-reload result))))

    (it "allows server.hot-reload to disable config watching"
      (should= false (:hot-reload (sut/server-config {:server {:hot-reload false}}))))

    (it "aliases gateway.port to server.port"
      (should= 9000 (:port (sut/server-config {:gateway {:port 9000}})))))
