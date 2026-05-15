(ns isaac.config.loader-spec
  (:require
    [c3kit.apron.schema :as cs]
    [c3kit.apron.env :as c3env]
    [clojure.string :as str]
    [isaac.config.companion :as companion]
    [isaac.system :as system]
    [isaac.logger :as log]
    [isaac.config.paths :as paths]
    [isaac.spec-helper :as helper]
    [isaac.config.loader :as sut]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(def test-root "/test/config-loader")

(defn- write-config! [path data]
  (fs/spit path (pr-str data)))

(defn- write-file! [path content]
  (fs/spit path content))

(defn- with-config-slot [f]
  (system/with-system {:config (atom nil)}
    (f)))

(defn- config-path [suffix]
  (str test-root "/.isaac/config/" suffix))

(describe "config loader"

  (helper/with-captured-logs)

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (binding [fs/*fs* (fs/mem-fs)]
      (reset! c3env/-overrides {})
      (sut/clear-env-overrides!)
      (example)))

  (describe "resolve-hook-template"

    (it "reports a missing hook template when neither inline nor companion text exists"
      (with-redefs [companion/resolve-text (fn [_]
                                             {:inline?           false
                                              :companion-exists? false
                                              :companion-empty?  false})]
        (should= {:errors [{:key "hooks.lettuce.template"
                            :value "required (inline or hooks/lettuce.md)"}]
                  :hook   {:crew :main}}
                 (#'sut/resolve-hook-template "lettuce" {:crew :main} (constantly nil) "hooks/lettuce.md"))))

    (it "reports an empty hook companion markdown file"
      (with-redefs [companion/resolve-text (fn [_]
                                             {:inline?           false
                                              :companion-exists? true
                                              :companion-empty?  true})]
        (should= {:errors [{:key "hooks.lettuce.template"
                            :value "must not be empty"}]
                  :hook   {:crew :main}}
                 (#'sut/resolve-hook-template "lettuce" {:crew :main} (constantly nil) "hooks/lettuce.md"))))

    (it "warns and keeps the inline hook template when a companion file also exists"
      (with-redefs [companion/resolve-text (fn [_]
                                             {:inline?           true
                                              :companion-exists? true
                                              :companion-empty?  false
                                              :value             "Inline template."})]
        (let [result (#'sut/resolve-hook-template "lettuce" {:template "Inline template."} (constantly nil) "hooks/lettuce.md")
              entry  (last @log/captured-logs)]
          (should= [] (:errors result))
          (should= "Inline template." (get-in result [:hook :template]))
          (should= :config/companion-inline-wins (:event entry))
          (should= :template (:field entry))
          (should= "hooks.lettuce" (:key entry)))))

    )

  (describe "load-root-config"

    (it "loads root config from overlay content"
      (with-redefs [sut/overlay-for          (fn [_ _] {:content "overlay" :relative "overlay/isaac.edn"})
                    sut/read-edn-string      (fn [_ _] {:crew {:main {}}})
                    sut/resolve-cron-prompts (fn [_ _] {:cron nil :errors []})
                    sut/top-level-warnings   (fn [_] [{:key "overlay" :value "warning"}])
                    cs/conform               (fn [_ _] :ok)
                    cs/error?                (constantly false)]
        (let [result (#'sut/load-root-config test-root {:substitute-env? true})]
          (should= {:crew {:main {}}} (:data result))
          (should= [] (:errors result))
          (should= [{:key "overlay" :value "warning"}] (:warnings result))
          (should= [(#'sut/source-path "overlay/isaac.edn")] (:sources result)))))

    (it "reports overlay EDN syntax errors"
      (with-redefs [sut/overlay-for (fn [_ _] {:content "{:broken" :relative paths/root-filename})]
        (should= {:data nil
                  :errors [{:key paths/root-filename :value "EDN syntax error"}]
                  :warnings []
                  :sources []}
                 (#'sut/load-root-config test-root {}))))

    (it "returns validation errors warnings and sources for an on-disk root file"
      (with-redefs [sut/overlay-for          (constantly nil)
                    fs/exists?               (constantly true)
                    sut/read-edn-file        (fn [_ _ _]
                                               {:data {:defaults {:model :llama}
                                                       :cron     {:health-check {:expr "0 9 * * *" :crew :main}}}})
                    sut/resolve-cron-prompts (fn [_ _]
                                               {:cron   {"health-check" {:expr "0 9 * * *" :crew "main" :prompt "Ping"}}
                                                :errors [{:key "cron.health-check.prompt" :value "bad prompt"}]})
                    sut/top-level-warnings   (fn [_] [{:key "root" :value "warning"}])
                    cs/conform               (fn [_ data]
                                               (if (= data {:model :llama})
                                                 {:defaults-error true}
                                                 :ok))
                    cs/error?                map?
                    sut/schema-error-entries (fn [prefix _]
                                               [{:key prefix :value "invalid"}])]
        (let [result (#'sut/load-root-config test-root {:raw-parse-errors? true :substitute-env? true})]
          (should= {:defaults {:model :llama}
                    :cron     {"health-check" {:expr "0 9 * * *" :crew "main" :prompt "Ping"}}}
                   (:data result))
          (should= [{:key "cron.health-check.prompt" :value "bad prompt"}
                    {:key "defaults" :value "invalid"}]
                   (:errors result))
          (should= [{:key "root" :value "warning"}] (:warnings result))
          (should= [(#'sut/source-path paths/root-filename)] (:sources result)))))

    (it "returns file read errors for an on-disk root file"
      (with-redefs [sut/overlay-for   (constantly nil)
                    fs/exists?        (constantly true)
                    sut/read-edn-file (fn [_ _ _] {:error "EDN syntax error"})]
        (should= {:data nil
                  :errors [{:key paths/root-filename :value "EDN syntax error"}]
                  :warnings []
                  :sources []}
                 (#'sut/load-root-config test-root {}))))

    (it "returns an empty result when no root config source exists"
      (with-redefs [sut/overlay-for (constantly nil)
                    fs/exists?      (constantly false)]
        (should= {:data nil :errors [] :warnings [] :sources []}
                 (#'sut/load-root-config test-root {})))))

  (describe "load-config-result caching"

    (it "reuses the cached result on mem-fs when nothing changed"
      (let [mem   (fs/mem-fs)
            calls (atom 0)]
        (binding [fs/*fs* mem]
          (with-redefs [sut/config-files-present? (constantly true)
                        sut/load-root-config      (fn [_ _]
                                                    (swap! calls inc)
                                                    {:data {} :errors [] :warnings [] :sources []})
                        sut/entity-files          (fn [& _] {:files [] :warnings []})
                        sut/dangling-md-warnings  (fn [& _] [])
                        isaac.module.loader/discover! (fn [_ _] {:index {} :errors []})
                        sut/check-comms           (fn [& _] {:errors [] :warnings []})
                        sut/check-tools           (fn [& _] {:errors [] :warnings []})
                        sut/check-slash-commands  (fn [& _] {:errors [] :warnings []})]
            (sut/clear-load-cache!)
            (sut/load-config-result {:home test-root})
            (sut/load-config-result {:home test-root})
            (should= 1 @calls)))))

    (it "invalidates the cached result after mem-fs changes"
      (let [mem   (fs/mem-fs)
            calls (atom 0)]
        (binding [fs/*fs* mem]
          (with-redefs [sut/config-files-present? (constantly true)
                        sut/load-root-config      (fn [_ _]
                                                    (swap! calls inc)
                                                    {:data {} :errors [] :warnings [] :sources []})
                        sut/entity-files          (fn [& _] {:files [] :warnings []})
                        sut/dangling-md-warnings  (fn [& _] [])
                        isaac.module.loader/discover! (fn [_ _] {:index {} :errors []})
                        sut/check-comms           (fn [& _] {:errors [] :warnings []})
                        sut/check-tools           (fn [& _] {:errors [] :warnings []})
                        sut/check-slash-commands  (fn [& _] {:errors [] :warnings []})]
            (sut/clear-load-cache!)
            (sut/load-config-result {:home test-root})
            (fs/spit (config-path "isaac.edn") "{}")
            (sut/load-config-result {:home test-root})
            (should= 2 @calls))))))

  (describe "semantic-errors"

    (it "builds known-id sets once per validation pass"
      (let [provider-calls (atom 0)
            crew-calls     (atom 0)
            model-calls    (atom 0)
            comm-calls     (atom 0)
            tool-calls     (atom 0)
            api-calls      (atom 0)
            config         {:defaults  {:crew "main" :model "llama"}
                            :crew      {"main" {:model "llama"
                                                 :provider "openai"
                                                 :tools {:allow [:grep :glob]}}}
                            :models    {"llama" {:provider "openai"}}
                            :providers {"openai" {:api "responses"}}
                            :comms     {"cli" {:impl "console" :crew "main"}}}]
        (with-redefs-fn {#'isaac.config.loader/known-provider-ids (fn [_]
                                                                    (swap! provider-calls inc)
                                                                    ["openai"])
                         #'isaac.config.loader/known-crew-ids     (fn [_]
                                                                    (swap! crew-calls inc)
                                                                    ["main"])
                         #'isaac.config.loader/known-model-ids    (fn [_]
                                                                    (swap! model-calls inc)
                                                                    ["llama"])
                         #'isaac.config.loader/known-comm-ids     (fn [_]
                                                                    (swap! comm-calls inc)
                                                                    ["console"])
                         #'isaac.config.loader/known-tool-ids     (fn [_]
                                                                    (swap! tool-calls inc)
                                                                    ["grep" "glob"])
                         #'isaac.config.loader/known-llm-api-ids  (fn [_]
                                                                    (swap! api-calls inc)
                                                                    ["responses"])}
          #(should= [] (#'sut/semantic-errors config)))
        (should= 1 @provider-calls)
        (should= 1 @crew-calls)
        (should= 1 @model-calls)
        (should= 1 @comm-calls)
        (should= 1 @tool-calls)
        (should= 1 @api-calls))))

  (describe "load-entity-file"

    (it "adds a string read error using the relative path"
      (with-redefs [sut/read-edn-file (fn [_ _ _] {:error "EDN syntax error"})]
        (should= [{:key "crew/marvin.edn" :value "EDN syntax error"}]
                 (:errors (#'sut/load-entity-file {:config {} :root {} :errors [] :warnings [] :sources []}
                                                 test-root
                                                 :crew
                                                 {:format :edn :path "/tmp/marvin.edn" :relative "crew/marvin.edn" :id "marvin"}
                                                 true
                                                 false)))))

    (it "passes through map-shaped errors unchanged"
      (with-redefs [sut/read-edn-file (fn [_ _ _] {:error {:key "crew.marvin.soul" :value "must be set"}})]
        (should= [{:key "crew.marvin.soul" :value "must be set"}]
                 (:errors (#'sut/load-entity-file {:config {} :root {} :errors [] :warnings [] :sources []}
                                                 test-root
                                                 :crew
                                                 {:format :edn :path "/tmp/marvin.edn" :relative "crew/marvin.edn" :id "marvin"}
                                                 true
                                                 false)))))

    (it "reports non-map entity content"
      (with-redefs [sut/read-edn-file (fn [_ _ _] {:data [:not-a-map]})]
        (should= [{:key "crew/marvin.edn" :value "must contain a map"}]
                 (:errors (#'sut/load-entity-file {:config {} :root {} :errors [] :warnings [] :sources []}
                                                 test-root
                                                 :crew
                                                 {:format :edn :path "/tmp/marvin.edn" :relative "crew/marvin.edn" :id "marvin"}
                                                 true
                                                 false)))))

    (it "records schema and id mismatch errors without storing invalid config"
      (with-redefs [sut/read-edn-file                (fn [_ _ _] {:data {:id "parrot" :model :grover}})
                    sut/resolve-crew-soul            (fn [_ data _] {:data data :error nil})
                    sut/collect-unknown-key-warnings (fn [& _] [{:key "crew.marvin.extra" :value "unknown key"}])
                    sut/schema-for                   (fn [_] ::crew)
                    cs/conform                       (fn [_ data] data)
                    cs/error?                        (constantly false)]
        (let [result (#'sut/load-entity-file {:config {:crew {"marvin" {:model "echo"}}}
                                              :root   {:crew {"marvin" {:model "echo"}}}
                                              :errors []
                                              :warnings []
                                              :sources []}
                                             test-root
                                             :crew
                                             {:format :edn :path "/tmp/marvin.edn" :relative "crew/marvin.edn" :id "marvin"}
                                             true
                                             false)]
          (should= [{:key "crew.marvin.id" :value "must match filename (got \"parrot\")"}
                    {:key "crew.marvin" :value "defined in both isaac.edn and crew/marvin.edn"}]
                   (:errors result))
          (should= [{:key "crew.marvin.extra" :value "unknown key"}] (:warnings result))
          (should= [(#'sut/source-path "crew/marvin.edn")] (:sources result))
          (should= {"marvin" {:model "echo"}} (get-in result [:config :crew])))))

    (it "stores valid entity config and companion extra errors"
      (with-redefs [sut/read-edn-file                (fn [_ _ _] {:data {:model :grover}})
                    sut/resolve-crew-soul            (fn [_ data _] {:data (assoc data :soul "You are Marvin.") :error nil})
                    sut/collect-unknown-key-warnings (fn [& _] [])
                    sut/schema-for                   (fn [_] ::crew)
                    cs/conform                       (fn [_ data] data)
                    cs/error?                        (constantly false)]
        (let [result (#'sut/load-entity-file {:config {} :root {} :errors [] :warnings [] :sources []}
                                             test-root
                                             :crew
                                             {:format :edn :path "/tmp/marvin.edn" :relative "crew/marvin.edn" :id "marvin"}
                                             true
                                             false)]
          (should= {"marvin" {:model :grover :soul "You are Marvin."}}
                   (get-in result [:config :crew]))
          (should= [(#'sut/source-path "crew/marvin.edn")] (:sources result)))))

    (it "records schema errors and source without storing invalid config"
      (with-redefs [sut/read-edn-file                (fn [_ _ _] {:data {:model :grover}})
                    sut/resolve-crew-soul            (fn [_ data _] {:data data :error nil})
                    sut/collect-unknown-key-warnings (fn [& _] [])
                    sut/schema-for                   (fn [_] ::crew)
                    cs/conform                       (fn [_ _] {:error :invalid})
                    cs/error?                        map?
                    sut/schema-error-entries         (fn [prefix _] [{:key prefix :value "invalid schema"}])]
        (let [result (#'sut/load-entity-file {:config {} :root {} :errors [] :warnings [] :sources []}
                                             test-root
                                             :crew
                                             {:format :edn :path "/tmp/marvin.edn" :relative "crew/marvin.edn" :id "marvin"}
                                             true
                                             false)]
          (should= [{:key "crew.marvin" :value "invalid schema"}] (:errors result))
          (should= {} (:config result))
          (should= [(#'sut/source-path "crew/marvin.edn")] (:sources result)))))

    (it "parses overlay edn content directly"
      (with-redefs [sut/read-edn-string              (fn [_ _] {:model :grover})
                    sut/resolve-crew-soul            (fn [_ data _] {:data (assoc data :soul "Overlay soul") :error nil})
                    sut/collect-unknown-key-warnings (fn [& _] [])
                    sut/schema-for                   (fn [_] ::crew)
                    cs/conform                       (fn [_ data] data)
                    cs/error?                        (constantly false)]
        (let [result (#'sut/load-entity-file {:config {} :root {} :errors [] :warnings [] :sources []}
                                             test-root
                                             :crew
                                             {:format :edn :overlay? true :content "{:model :grover}" :relative "crew/marvin.edn" :id "marvin"}
                                             true
                                             false)]
          (should= {"marvin" {:model :grover :soul "Overlay soul"}}
                   (get-in result [:config :crew]))
          (should= [(#'sut/source-path "crew/marvin.edn")] (:sources result)))))

    (it "loads markdown frontmatter hooks and records template errors"
      (with-redefs [sut/read-frontmatter-file         (fn [_ _ _] {:data {:crew :main} :body "Template body"})
                    sut/resolve-hook-template         (fn [_ data _ _] {:hook (assoc data :template "Template body")
                                                                         :errors [{:key "hooks.webhook.template" :value "warn"}]})
                    sut/collect-unknown-key-warnings (fn [& _] [])
                    sut/schema-for                   (fn [_] ::hook)
                    cs/conform                       (fn [_ data] data)
                    cs/error?                        (constantly false)]
        (let [result (#'sut/load-entity-file {:config {} :root {} :errors [] :warnings [] :sources []}
                                             test-root
                                             :hooks
                                             {:format :md-frontmatter :relative "hooks/webhook.md" :id "webhook"}
                                             true
                                             false)]
          (should= {"webhook" {:crew :main :template "Template body"}}
                   (get-in result [:config :hooks]))
          (should= [{:key "hooks.webhook.template" :value "warn"}] (:errors result))
          (should= [(#'sut/source-path "hooks/webhook.md")] (:sources result))))))

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
        (should= [{:key "crew.marvin.model" :value "references undefined model \"gpt\" (known: grover)"}
                  {:key "defaults.crew" :value "references undefined crew \"ghost\" (known: marvin)"}
                  {:key "defaults.model" :value "references undefined model \"llama\" (known: grover)"}]
                 (mapv #(select-keys % [:key :value]) (:errors result)))))

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
                    :value "references undefined provider \"foo\" (known: anthropic, claude-sdk, grover, ollama, openai, openai-chatgpt, xai)"}]
                 (mapv #(select-keys % [:key :value]) (:errors result)))))

    (it "rejects providers with an unknown api"
      (write-config! (config-path "isaac.edn")
                     {:providers {:bogus {:api "carrier-pigeon" :base-url "https://example.com" :auth "api-key" :api-key "test"}}})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [{:key "providers.bogus.api"
                   :value "unknown api \"carrier-pigeon\" (known: anthropic, anthropic-messages, claude-sdk, grover, ollama, openai-completions, openai-responses)"}]
                 (mapv #(select-keys % [:key :value])
                       (filter #(= "providers.bogus.api" (:key %)) (:errors result))))))

    (it "rejects providers with an unknown :type target"
      (write-config! (config-path "isaac.edn")
                     {:providers {:dreamy {:type :ghost-provider :api-key "test"}}})
      (let [result (sut/load-config-result {:home test-root})]
        (should= [{:key "providers.dreamy.type"
                   :value "references provider not defined in any manifest \"ghost-provider\" (known: anthropic, claude-sdk, grover, ollama, openai, openai-chatgpt, xai)"}]
                 (mapv #(select-keys % [:key :value])
                       (filter #(= "providers.dreamy.type" (:key %)) (:errors result))))))

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
                 (get-in result [:config :cron "health-check"])))))

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
        (should= "cron.health-check" (:key entry))))

  (describe "normalize-config"

    (it "normalizes modern map-based sections and preserves optional top-level config"
      (with-redefs [cs/conform (fn [_ value] value)
                    cs/error?  (constantly false)]
        (let [cfg    {:defaults            {:crew :main :model :grover}
                      :crew                {:main {:soul "You are Isaac." :model :grover}}
                      :models              {:grover {:model "echo" :provider :anthropic}}
                      :providers           {:anthropic {:api-key "sk-test"}}
                      :cron                {:nightly {:expr "0 0 * * *" :crew :main}}
                      :channels            {:web {:name "web"}}
                      :comms               {:discord {:token "abc"}}
                      :hooks               {:auth {:token "secret"}}
                      :server              {:port 6674}
                      :sessions            {:retention-days 7}
                      :gateway             {:port 9000}
                      :tz                  "UTC"
                      :dev                 {:log-level :debug}
                      :acp                 {:enabled true}
                      :prefer-entity-files true
                      :modules             {:isaac.comm.pigeon {:local/root "/tmp/pigeon"}}}
              result (sut/normalize-config cfg)]
          (should= {:crew :main :model :grover} (:defaults result))
          (should= {"main" {:soul "You are Isaac." :model :grover}} (:crew result))
          (should= {"grover" {:model "echo" :provider :anthropic}} (:models result))
          (should= {"anthropic" {:api-key "sk-test"}} (:providers result))
          (should= {"nightly" {:expr "0 0 * * *" :crew "main"}} (:cron result))
          (should= (:channels cfg) (:channels result))
          (should= (:comms cfg) (:comms result))
          (should= (:hooks cfg) (:hooks result))
          (should= (:server cfg) (:server result))
          (should= (:sessions cfg) (:sessions result))
          (should= (:gateway cfg) (:gateway result))
          (should= (:tz cfg) (:tz result))
          (should= (:dev cfg) (:dev result))
          (should= (:acp cfg) (:acp result))
          (should= true (:prefer-entity-files result))
          (should= (:modules cfg) (:modules result)))))

    (it "normalizes legacy crew lists nested models and provider vectors"
      (with-redefs [cs/conform (fn [_ value] value)
                    cs/error?  (constantly false)]
        (let [cfg    {:crew   {:defaults {:crew :main :model :grover}
                               :list     [{:id :main :soul "You are Isaac." :model :grover}
                                          {:id "ketch" :model :grover}]
                               :models   {:grover {:model "echo" :provider :anthropic :context-window 200000}}}
                      :models {:providers [{:name :anthropic :api-key "sk-test"}
                                           {:id :grover :base-url "https://grover.example"}]}}
              result (sut/normalize-config cfg)]
          (should= {:crew :main :model :grover} (:defaults result))
          (should= {"main"  {:id :main :soul "You are Isaac." :model :grover}
                    "ketch" {:id "ketch" :model :grover}}
                   (:crew result))
          (should= {"grover" {:model "echo" :provider :anthropic :context-window 200000}}
                   (:models result))
          (should= {"anthropic" {:api-key "sk-test"}
                    "grover"    {:id :grover :base-url "https://grover.example"}}
                   (:providers result))))))

  (describe "resolve-crew-context"

    (it "resolves crew model provider and context window from the new map-by-id shape"
      (let [resolve* requiring-resolve]
        (with-redefs [clojure.core/requiring-resolve (fn [sym]
                                                       (case sym
                                                         isaac.drive.dispatch/make-provider (fn [provider-id provider-cfg]
                                                                                              {:id provider-id :cfg provider-cfg})
                                                         isaac.llm.api/display-name         (fn [provider]
                                                                                             (:id provider))
                                                         isaac.llm.api/config               (fn [provider]
                                                                                             (:cfg provider))
                                                         (resolve* sym)))]
          (let [cfg {:defaults  {:crew "main" :model "llama"}
                     :crew      {"main" {:model "grover" :soul "You are Isaac."}}
                     :models    {"grover" {:model "claude-opus-4-7" :provider "anthropic" :context-window 200000}}
                     :providers {"anthropic" {:api "anthropic" :base-url "https://api.anthropic.com"}}}
                ctx (sut/resolve-crew-context cfg "main" {:home test-root})]
            (should= "You are Isaac." (:soul ctx))
            (should= "claude-opus-4-7" (:model ctx))
            (should= "anthropic" ((requiring-resolve 'isaac.llm.api/display-name) (:provider ctx)))
            (should= 200000 (:context-window ctx))
            (should= "https://api.anthropic.com" (get-in ((requiring-resolve 'isaac.llm.api/config) (:provider ctx)) [:base-url])))))))

    (it "returns crew-cfg and model-cfg for effort resolution"
      (let [resolve* requiring-resolve]
        (with-redefs [clojure.core/requiring-resolve (fn [sym]
                                                       (case sym
                                                         isaac.drive.dispatch/make-provider (fn [provider-id provider-cfg]
                                                                                              {:id provider-id :cfg provider-cfg})
                                                         (resolve* sym)))]
          (let [cfg {:defaults  {:crew "main" :model "snuffy"}
                     :crew      {"main" {:model "snuffy" :effort 9}}
                     :models    {"snuffy" {:model "snuffy-codex" :provider "grover" :effort 5}}
                     :providers {"grover" {:api "openai-responses" :effort 3}}}
                ctx (sut/resolve-crew-context cfg "main" {:home test-root})]
            (should= 9 (get-in ctx [:crew-cfg :effort]))
            (should= 5 (get-in ctx [:model-cfg :effort]))))))

  (describe "resolve-provider"

    (it "falls back from simulated provider ids to the base provider config"
      (let [cfg {:providers {"grover" {:api "grover" :effort 3}}}]
        (should= {:api "grover" :effort 3}
                 (sut/resolve-provider cfg "grover:openai-chatgpt")))))

  (describe "semantic-errors"

    (it "reports undefined defaults crew models provider cron crew and hook refs"
      (should= [{:key "hooks.webhook.crew" :value "references undefined crew \"ghost\" (known: marvin)"}
                {:key "hooks.webhook.model" :value "references undefined model \"gpt\" (known: grok)"}
                {:key "crew.marvin.model" :value "references undefined model \"gpt\" (known: grok)"}
                {:key "defaults.crew" :value "references undefined crew \"ghost\" (known: marvin)"}
                {:key "defaults.model" :value "references undefined model \"llama\" (known: grok)"}
                {:key "cron.nightly.crew" :value "references undefined crew \"ghost\" (known: marvin)"}
                {:key "models.grok.provider" :value "references undefined provider \"imaginarium\" (known: anthropic, claude-sdk, grover, ollama, openai, openai-chatgpt, xai)"}]
               (mapv #(select-keys % [:key :value])
                     (#'sut/semantic-errors {:defaults  {:crew "ghost" :model "llama"}
                                             :crew      {"marvin" {:model "gpt"}}
                                             :models    {"grok" {:provider "imaginarium"}}
                                             :providers {}
                                             :cron      {"nightly" {:crew "ghost"}}
                                             :hooks     {"webhook" {:crew "ghost" :model "gpt"}
                                                         :auth      {:token "secret"}}}))))

    (it "returns no semantic errors when all references resolve"
      (should= []
               (#'sut/semantic-errors {:defaults  {:crew "main" :model "llama"}
                                       :crew      {"main" {:model "llama"}}
                                       :models    {"llama" {:provider "anthropic"}}
                                       :providers {}
                                       :cron      {"nightly" {:crew "main"}}
                                       :hooks     {"webhook" {:crew "main" :model "llama"}
                                                   :auth      {:token "secret"}}})))

    )

  (describe "module discovery integration"

    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example]
      (binding [fs/*fs* (fs/mem-fs)]
        (sut/clear-env-overrides!)
        (example)))

    (it "attaches :module-index to loaded config for declared modules"
      (write-config! (config-path "isaac.edn") {:modules {:isaac.comm.pigeon {:local/root "/test/config-loader/.isaac/modules/isaac.comm.pigeon"}}})
      (fs/mkdirs (str test-root "/.isaac/modules/isaac.comm.pigeon"))
      (fs/spit (str test-root "/.isaac/modules/isaac.comm.pigeon/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (str test-root "/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn")
               "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= :isaac.comm.pigeon
                 (get-in result [:config :module-index :isaac.comm.pigeon :manifest :id]))
        (should= "/test/config-loader/.isaac/modules/isaac.comm.pigeon"
                 (get-in result [:config :module-index :isaac.comm.pigeon :path]))))

    (it "adds validation errors when a local/root path is not found"
      (write-config! (config-path "isaac.edn") {:modules {:isaac.comm.ghost {:local/root "/test/config-loader/.isaac/modules/isaac.comm.ghost"}}})
      (let [result (sut/load-config-result {:home test-root})]
        (should (some #(and (= "modules[\"isaac.comm.ghost\"]" (:key %))
                            (= "local/root path does not resolve" (:value %)))
                      (:errors result)))))

    (it "adds validation errors when a module manifest is invalid"
      (write-config! (config-path "isaac.edn") {:modules {:isaac.comm.pigeon {:local/root "/test/config-loader/.isaac/modules/isaac.comm.pigeon"}}})
      (fs/mkdirs (str test-root "/.isaac/modules/isaac.comm.pigeon"))
      (fs/spit (str test-root "/.isaac/modules/isaac.comm.pigeon/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (str test-root "/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn")
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
          (fs/spit (str test-root "/.isaac/modules/isaac.comm.pigeon/deps.edn")
                   "{:paths [\"resources\"]}")
          (fs/spit (str test-root "/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn")
                   "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
          (fs/mkdirs (str test-root "/.isaac/config"))
          (fs/spit (str test-root "/.isaac/config/isaac.edn")
                   "{:modules {:isaac.comm.pigeon {:local/root \"/test/config-loader/.isaac/modules/isaac.comm.pigeon\"}}}"))
        ;; load phase (like "when the config is loaded" step — NEW binding to SAME mem)
        (binding [fs/*fs* mem]
          (let [result (sut/load-config-result {:home test-root})]
            (should-not-be-nil (get-in result [:config :module-index :isaac.comm.pigeon]))
            (should= :isaac.comm.pigeon
                     (get-in result [:config :module-index :isaac.comm.pigeon :manifest :id])))))))

  (describe "provider type schema validation"

    (def kombucha-manifest
      (pr-str {:id       :isaac.providers.kombucha
               :version  "0.1.0"
               :provider {:kombucha {:template {:api      "openai-completions"
                                                :base-url "https://api.kombucha.test/v1"
                                                :auth     "api-key"
                                                :models   ["kombucha-large"]}
                                     :schema   {:fizz-level {:type :int}}}}}))

    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example]
      (binding [fs/*fs* (fs/mem-fs)]
        (sut/clear-env-overrides!)
        (example)))

    (defn- write-kombucha-module! []
      (fs/mkdirs (str test-root "/.isaac/modules/isaac.providers.kombucha"))
      (fs/spit (str test-root "/.isaac/modules/isaac.providers.kombucha/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (str test-root "/.isaac/modules/isaac.providers.kombucha/resources/isaac-manifest.edn") kombucha-manifest))

    (it "rejects a provider field that violates the manifest :schema"
      (write-config! (config-path "isaac.edn")
                     {:modules   {:isaac.providers.kombucha {:local/root (str test-root "/.isaac/modules/isaac.providers.kombucha")}}
                      :providers {:my-kombucha {:type :kombucha :api-key "fizzy-secret" :fizz-level "seven"}}})
      (write-kombucha-module!)
      (let [result (sut/load-config-result {:home test-root})]
        (should (some #(and (= "providers.my-kombucha.fizz-level" (:key %))
                            (re-find #"must be an integer" (:value %)))
                      (:errors result)))))

    (it "accepts a provider field that conforms to the manifest :schema"
      (write-config! (config-path "isaac.edn")
                     {:modules   {:isaac.providers.kombucha {:local/root (str test-root "/.isaac/modules/isaac.providers.kombucha")}}
                       :providers {:my-kombucha {:type :kombucha :api-key "fizzy-secret" :fizz-level 3}}})
      (write-kombucha-module!)
      (let [result (sut/load-config-result {:home test-root})]
        (should-not (some #(str/includes? (:key %) "providers.my-kombucha.fizz-level")
                          (:errors result))))))

    (it "rejects a self-defined provider with auth api-key but no api-key"
      (write-config! (config-path "isaac.edn")
                     {:providers {:my-thing {:api      "anthropic-messages"
                                             :base-url "https://example.test"
                                             :auth     "api-key"}}})
      (let [result (sut/load-config-result {:home test-root})]
        (should (some #(and (= "providers.my-thing.api-key" (:key %))
                            (re-find #"is required when auth is api-key" (:value %)))
                      (:errors result)))))

    (it "rejects a typed provider when auth api-key is inherited but api-key is missing"
      (write-config! (config-path "isaac.edn")
                     {:modules   {:isaac.providers.kombucha {:local/root (str test-root "/.isaac/modules/isaac.providers.kombucha")}}
                       :providers {:my-kombucha {:type :kombucha :fizz-level 3}}})
      (write-kombucha-module!)
      (let [result (sut/load-config-result {:home test-root})]
        (should (some #(and (= "providers.my-kombucha.api-key" (:key %))
                            (re-find #"is required when auth is api-key" (:value %)))
                      (:errors result)))))

  (describe "comm slot validation"

    (def telly-manifest
      (pr-str {:id      :isaac.comm.telly
               :version "0.1.0"
               :comm    {:telly {:factory 'isaac.comm.telly/make
                                 :schema  {:loft  {:type :string}
                                           :color {:type :string}}}}}))

    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example]
      (binding [fs/*fs* (fs/mem-fs)]
        (sut/clear-env-overrides!)
        (example)))

    (defn- write-telly-module! []
      (fs/mkdirs (str test-root "/.isaac/modules/isaac.comm.telly"))
      (fs/spit (str test-root "/.isaac/modules/isaac.comm.telly/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (str test-root "/.isaac/modules/isaac.comm.telly/resources/isaac-manifest.edn") telly-manifest))

    (def discord-manifest
      (pr-str {:id      :isaac.comm.discord
               :version "0.1.0"
               :comm    {:discord {:factory 'isaac.comm.discord/make
                                   :schema  {:token       {:type :string}
                                             :crew        {:type :string}
                                             :message-cap {:type :int}
                                             :allow-from  {:type :map}}}}}))

    (defn- write-discord-module! []
      (fs/mkdirs (str test-root "/.isaac/modules/isaac.comm.discord"))
      (fs/spit (str test-root "/.isaac/modules/isaac.comm.discord/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (str test-root "/.isaac/modules/isaac.comm.discord/resources/isaac-manifest.edn") discord-manifest))

    (it "validates declared module comm slot fields with no error for valid value"
      (write-config! (config-path "isaac.edn")
                     {:modules {:isaac.comm.telly {:local/root "/test/config-loader/.isaac/modules/isaac.comm.telly"}}
                      :comms {:bert {:type :telly :loft "rooftop"}}})
      (write-telly-module!)
      (let [result (sut/load-config-result {:home test-root})]
        (should= [] (:errors result))
        (should= "rooftop" (get-in result [:config :comms :bert :loft]))))

    (it "generates a validation error for wrong type in a module comm slot field"
      (write-config! (config-path "isaac.edn")
                     {:modules {:isaac.comm.telly {:local/root "/test/config-loader/.isaac/modules/isaac.comm.telly"}}
                      :comms {:bert {:type :telly :loft 42}}})
      (write-telly-module!)
      (let [result (sut/load-config-result {:home test-root})]
        (should (some #(and (= "comms.bert.loft" (:key %))
                            (= "must be a string" (:value %)))
                      (:errors result)))))

    (it "generates unknown-key warnings for comm slot fields when module is not declared"
      (write-config! (config-path "isaac.edn")
                     {:comms {:bert {:type :telly :loft "rooftop"}}})
      (let [result (sut/load-config-result {:home test-root})]
        (should (some #(and (= "comms.bert.loft" (:key %))
                            (= "unknown key" (:value %)))
                      (:warnings result)))))

    (it "does not warn for discord when its module is declared"
      (write-config! (config-path "isaac.edn")
                     {:modules {:isaac.comm.discord {:local/root "/test/config-loader/.isaac/modules/isaac.comm.discord"}}
                       :comms   {:mychan {:type :discord :token "abc"}}})
      (write-discord-module!)
      (let [result (sut/load-config-result {:home test-root})]
        (should-not (some #(str/includes? (:key %) "comms.mychan") (:warnings result))))))

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

  (describe "snapshot"

    (around [it]
      (with-config-slot it))

    (after (sut/set-snapshot! nil))

    (it "returns nil before any snapshot is set"
      (sut/set-snapshot! nil)
      (should-be-nil (sut/snapshot)))

    (it "returns the config after set-snapshot!"
      (sut/set-snapshot! {:crew {"main" {:soul "You are helpful."}}})
      (should= {:crew {"main" {:soul "You are helpful."}}} (sut/snapshot)))

    (it "returns the latest value after multiple set-snapshot! calls"
      (sut/set-snapshot! {:first true})
      (sut/set-snapshot! {:second true})
      (should= {:second true} (sut/snapshot)))

    (it "writes through the system config atom"
      (let [cfg* (atom nil)]
        (system/with-system {:config cfg*}
          (sut/set-snapshot! {:crew {"main" {:soul "Hi"}}})
          (should= {:crew {"main" {:soul "Hi"}}} @cfg*)))))
