(ns isaac.config.loader-spec
  (:require
    [c3kit.apron.schema :as cs]
    [c3kit.apron.env :as c3env]
    [clojure.string :as str]
    [isaac.config.companion :as companion]
    [isaac.marigold :as marigold]
    [isaac.system :as system]
    [isaac.logger :as log]
    [isaac.config.paths :as paths]
    [isaac.spec-helper :as helper]
    [isaac.config.loader :as sut]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(defn- with-config-slot [f]
  (system/with-system {:config (atom nil)}
    (f)))

(describe "config loader"

  (marigold/aboard)
  (helper/with-captured-logs)

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

  (describe "resolve-history-retention"

    (it "defaults to retain"
      (should= :retain (sut/resolve-history-retention {} "main" nil)))

    (it "prefers explicit override over crew model provider and defaults"
      (should= :retain
               (sut/resolve-history-retention {:defaults  {:history-retention :prune}
                                              :crew      {"main" {:model "gpt" :history-retention :prune}}
                                              :models    {"gpt" {:provider marigold/starcore :history-retention :prune}}
                                              :providers {marigold/starcore {:history-retention :prune}}}
                                             "main"
                                             :retain)))

    (it "falls through crew model provider defaults in order"
      (should= :prune
               (sut/resolve-history-retention {:defaults  {:history-retention :retain}
                                              :crew      {"main" {:model "gpt" :history-retention :prune}}
                                              :models    {"gpt" {:provider marigold/starcore :history-retention :retain}}
                                              :providers {marigold/starcore {:history-retention :retain}}}
                                             "main"
                                             nil))
      (should= :prune
               (sut/resolve-history-retention {:defaults  {:history-retention :retain}
                                              :crew      {"main" {:model "gpt"}}
                                              :models    {"gpt" {:model "gpt-5" :provider marigold/starcore :history-retention :prune}}
                                              :providers {marigold/starcore {:history-retention :retain}}}
                                             "main"
                                             nil))
      (should= :prune
               (sut/resolve-history-retention {:defaults  {:history-retention :retain}
                                              :crew      {"main" {:model "gpt"}}
                                              :models    {"gpt" {:model "gpt-5" :provider marigold/starcore}}
                                              :providers {marigold/starcore {:history-retention :prune}}}
                                             "main"
                                             nil))))

  (describe "load-root-config"

    (it "loads root config from overlay content"
      (with-redefs [sut/overlay-for          (fn [_ _] {:content "overlay" :relative "overlay/isaac.edn"})
                    sut/read-edn-string      (fn [_ _] {:crew {:main {}}})
                    sut/resolve-cron-prompts (fn [_ _] {:cron nil :errors []})
                    sut/top-level-warnings   (fn [_] [{:key "overlay" :value "warning"}])
                    cs/conform               (fn [_ _] :ok)
                    cs/error?                (constantly false)]
        (let [result (#'sut/load-root-config marigold/home {:substitute-env? true})]
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
                 (#'sut/load-root-config marigold/home {}))))

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
        (let [result (#'sut/load-root-config marigold/home {:raw-parse-errors? true :substitute-env? true})]
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
                 (#'sut/load-root-config marigold/home {}))))

    (it "returns an empty result when no root config source exists"
      (with-redefs [sut/overlay-for (constantly nil)
                    fs/exists?      (constantly false)]
        (should= {:data nil :errors [] :warnings [] :sources []}
                 (#'sut/load-root-config marigold/home {})))))

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
            (marigold/load-config)
            (marigold/load-config)
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
            (marigold/load-config)
            (marigold/write-raw! "isaac.edn" "{}")
            (marigold/load-config)
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
                            :crew      {"main" {:model    "llama"
                                                :provider marigold/starcore
                                                :tools    {:allow [:grep :glob]}}}
                            :models    {"llama" {:provider marigold/starcore}}
                            :providers {marigold/starcore {:api marigold/sky-api}}
                            :comms     {"cli" {:impl "console" :crew "main"}}}]
        (with-redefs-fn {#'isaac.config.loader/known-provider-ids (fn [_]
                                                                    (swap! provider-calls inc)
                                                                    [marigold/starcore])
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
                                                                    [marigold/sky-api])}
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
                                                 marigold/home
                                                 :crew
                                                 {:format :edn :path "/tmp/marvin.edn" :relative "crew/marvin.edn" :id "marvin"}
                                                 true
                                                 false)))))

    (it "passes through map-shaped errors unchanged"
      (with-redefs [sut/read-edn-file (fn [_ _ _] {:error {:key "crew.marvin.soul" :value "must be set"}})]
        (should= [{:key "crew.marvin.soul" :value "must be set"}]
                 (:errors (#'sut/load-entity-file {:config {} :root {} :errors [] :warnings [] :sources []}
                                                 marigold/home
                                                 :crew
                                                 {:format :edn :path "/tmp/marvin.edn" :relative "crew/marvin.edn" :id "marvin"}
                                                 true
                                                 false)))))

    (it "reports non-map entity content"
      (with-redefs [sut/read-edn-file (fn [_ _ _] {:data [:not-a-map]})]
        (should= [{:key "crew/marvin.edn" :value "must contain a map"}]
                 (:errors (#'sut/load-entity-file {:config {} :root {} :errors [] :warnings [] :sources []}
                                                 marigold/home
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
                                             marigold/home
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
                                             marigold/home
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
                                             marigold/home
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
                                             marigold/home
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
                                             marigold/home
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
      (let [result (marigold/load-config)]
        (should= [{:key "config"
                   :value (str "no config found; run `isaac init` or create " marigold/home "/.isaac/config/isaac.edn")}]
                 (:errors result))
        (should= {} (:config result))
        (should= true (:missing-config? result))
        (should= [] (:warnings result))
        (should= [] (:sources result))))

    (it "loads crew members from per-entity files and companion md soul"
      (marigold/write-crew! :marvin {:model :llama})
      (marigold/write-crew-md! :marvin "You are Marvin.")
      (let [result (marigold/load-config)]
        (should= "llama" (get-in result [:config :crew "marvin" :model]))
        (should= "You are Marvin." (get-in result [:config :crew "marvin" :soul]))))

    (it "loads crew members from a single markdown file with EDN frontmatter"
      (marigold/write-config!
                     {:models    {:llama (marigold/model-cfg (keyword marigold/flicker-labs) "llama3.2")}
                      :providers {(keyword marigold/flicker-labs) {:api marigold/groves-api}}})
      (marigold/write-crew-md! :marvin (str "---\n"
                                                         "{:model :llama}\n"
                                                         "---\n\n"
                                                         "You are Marvin."))
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= "llama" (get-in result [:config :crew "marvin" :model]))
        (should= "You are Marvin." (get-in result [:config :crew "marvin" :soul]))))

    (it "prefers single-file crew markdown over legacy files and warns"
      (marigold/write-config!
                     {:models    {:grover (marigold/model-cfg (keyword marigold/helm-systems) "helm-mk-3-1.0")}
                      :providers {(keyword marigold/helm-systems) {:api marigold/helm-api}}})
      (marigold/write-crew! :marvin {:model :llama})
      (marigold/write-crew-md! :marvin (str "---\n"
                                                         "{:model :grover}\n"
                                                         "---\n\n"
                                                         "You are Marvin."))
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= "grover" (get-in result [:config :crew "marvin" :model]))
        (should= "You are Marvin." (get-in result [:config :crew "marvin" :soul]))
        (should= [{:key "crew/marvin.md"
                   :value "single-file config overrides legacy crew/marvin.edn"}]
                 (filter #(= "crew/marvin.md" (:key %)) (:warnings result)))))

    (it "reports duplicate ids across isaac.edn and per-entity files"
      (marigold/write-config! {:crew {:marvin {:soul "First"}}})
      (marigold/write-crew! :marvin {:soul "Second"})
      (let [result (marigold/load-config)]
        (should= [{:key "crew.marvin"
                   :value "defined in both isaac.edn and crew/marvin.edn"}]
                  (:errors result))))

    (it "reports malformed crew EDN with the relative file path"
      (marigold/write-raw! "crew/marvin.edn" "{:model :llama")
      (let [result (marigold/load-config)]
        (should= [{:key "crew/marvin.edn"
                    :value "EDN syntax error"}]
                  (:errors result))))

    (it "reports a soul conflict when both edn and companion md define soul"
      (marigold/write-crew! :marvin {:soul "Inline soul."})
      (marigold/write-crew-md! :marvin "File soul.")
      (let [result (marigold/load-config)]
        (should= [{:key "crew.marvin.soul"
                   :value "must be set in .edn OR .md"}]
                 (:errors result))))

    (it "warns about unknown keys in entity files but still loads"
      (marigold/write-crew! :marvin {:crew {:marvin {:model :llama}}})
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= [{:key "crew.marvin.crew" :value "unknown key"}] (:warnings result))))

    (it "warns about a dangling crew markdown companion without a matching entry"
      (marigold/write-config! marigold/baseline-config)
      (marigold/write-crew-md! :ghost "I have no matching entity.")
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= [{:key "crew/ghost.md" :value "dangling: no matching crew entry"}]
                 (filter #(= "crew/ghost.md" (:key %)) (:warnings result)))))

    (it "warns about a dangling cron markdown companion without a matching cron job"
      (marigold/write-config! marigold/baseline-config)
      (marigold/write-cron-md! :ghost "I have no matching cron job.")
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= [{:key "cron/ghost.md" :value "dangling: no matching cron entry"}]
                 (filter #(= "cron/ghost.md" (:key %)) (:warnings result)))))

    (it "does not warn when a crew markdown companion has a matching entity file"
      (marigold/write-config! marigold/baseline-config)
      (marigold/write-crew! marigold/captain {:model (keyword marigold/helm-mark-iii)})
      (marigold/write-crew-md! marigold/captain "You are Atticus.")
      (let [result (marigold/load-config)]
        (should= [] (filter #(= (str "crew/" marigold/captain ".md") (:key %)) (:warnings result)))))

    (it "treats camelCase config keys as unknown after the hard cutover"
      (marigold/write-provider! :helm-systems {:apiKey "${HELM_API_KEY}"})
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= [{:key "providers.helm-systems.apiKey" :value "unknown key"}] (:warnings result))))

    (it "validates semantic references across defaults crew model and providers"
      (marigold/write-config!
                     {:defaults  {:crew :ghost :model :llama}
                      :crew      {:marvin {:model :gpt}}
                      :models    {:grover (marigold/model-cfg (keyword marigold/helm-systems) "helm-mk-3-1.0" :context-window 200000)}
                      :providers {(keyword marigold/helm-systems) {}}})
      (let [result (marigold/load-config)]
        (should= [{:key "crew.marvin.model" :value "references undefined model \"gpt\" (known: grover)"}
                  {:key "defaults.crew" :value "references undefined crew \"ghost\" (known: marvin)"}
                  {:key "defaults.model" :value "references undefined model \"llama\" (known: grover)"}]
                 (mapv #(select-keys % [:key :value]) (:errors result)))))

    (it "rejects model references to a manifest template that is not instantiated in user config"
      (marigold/write-config!
                     {:models {(keyword marigold/helm-mark-iii)
                               (marigold/model-cfg (keyword marigold/helm-systems) "helm-mk-3-1.0" :context-window 200000)}})
      (let [result (marigold/load-config)]
        (should= [{:key (str "models." marigold/helm-mark-iii ".provider")
                   :value (str "references undefined provider \"" marigold/helm-systems "\"")}]
                 (mapv #(select-keys % [:key :value]) (:errors result)))))

    (it "accepts a model reference once the template is instantiated via an empty entity file"
      (marigold/write-config!
                     {:models {(keyword marigold/helm-mark-iii)
                               (marigold/model-cfg (keyword marigold/helm-systems) "helm-mk-3-1.0" :context-window 200000)}})
      (marigold/write-provider! marigold/helm-systems {})
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= marigold/helm-systems (get-in result [:config :models marigold/helm-mark-iii :provider]))))

    (it "loads provider entity overrides on top of built-in providers"
      (marigold/write-config!
                     {:models {(keyword marigold/helm-mark-iii)
                               (marigold/model-cfg (keyword marigold/helm-systems) "helm-mk-3-1.0" :context-window 200000)}})
      (marigold/write-provider! marigold/helm-systems
                     {:api-key  "sk-test"
                      :base-url (:base-url marigold/helm-provider)})
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= (:base-url marigold/helm-provider) (get-in result [:config :providers marigold/helm-systems :base-url]))
        (should= "sk-test" (get-in result [:config :providers marigold/helm-systems :api-key]))))

    (it "reports unknown providers with the configured provider list"
      (marigold/write-config! {:models    {:mystery {:model           "enigmatic-1"
                                                                      :provider        :foo
                                                                      :context-window  1024}}
                                                :providers {(keyword marigold/helm-systems) {}
                                                            (keyword marigold/starcore)     {}}})
      (let [result (marigold/load-config)]
        (should= [{:key "models.mystery.provider"
                   :value (str "references undefined provider \"foo\" (known: " marigold/helm-systems ", " marigold/starcore ")")}]
                 (mapv #(select-keys % [:key :value]) (:errors result)))))

    (it "rejects providers with an unknown api"
      (marigold/write-config!
                     {:providers {:bogus {:api "carrier-pigeon" :base-url "https://example.com" :auth "api-key" :api-key "test"}}})
      (let [result    (marigold/load-config)
            known-apis (->> marigold/baseline-manifest :llm/api keys (map name) sort (str/join ", "))]
        (should= [{:key "providers.bogus.api"
                   :value (str "unknown api \"carrier-pigeon\" (known: " known-apis ")")}]
                 (mapv #(select-keys % [:key :value])
                       (filter #(= "providers.bogus.api" (:key %)) (:errors result))))))

    (it "rejects providers with an unknown :type target"
      (marigold/write-config!
                     {:providers {:dreamy {:type :ghost-provider :api-key "test"}}})
      (let [result         (marigold/load-config)
            known-providers (->> marigold/baseline-manifest :provider keys (map name) sort (str/join ", "))]
        (should= [{:key "providers.dreamy.type"
                   :value (str "references provider not defined in any manifest \"ghost-provider\" (known: " known-providers ")")}]
                 (mapv #(select-keys % [:key :value])
                       (filter #(= "providers.dreamy.type" (:key %)) (:errors result))))))

    (it "substitutes environment variables in loaded config"
      (marigold/write-provider! marigold/helm-systems
                     (marigold/provider-cfg marigold/helm-provider :api-key "${HELM_API_KEY}"))
      (with-redefs [sut/env (fn [name] (when (= "HELM_API_KEY" name) "sk-test-123"))]
        (let [result (marigold/load-config)]
          (should= [] (:errors result))
          (should= "sk-test-123" (get-in result [:config :providers marigold/helm-systems :api-key])))))

    (it "substitutes environment variables from the isaac .env file"
      (marigold/write-env-file! "ISAAC_ENV_FILE_TEST_KEY=sk-from-isaac\n")
      (marigold/write-provider! marigold/helm-systems
                     (marigold/provider-cfg marigold/helm-provider :api-key "${ISAAC_ENV_FILE_TEST_KEY}"))
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= "sk-from-isaac" (get-in result [:config :providers marigold/helm-systems :api-key]))))

    (it "prefers c3env values over the isaac .env file"
      (marigold/write-env-file! "ISAAC_ENV_FILE_TEST_KEY=sk-from-isaac\n")
      (marigold/write-provider! marigold/helm-systems
                     (marigold/provider-cfg marigold/helm-provider :api-key "${ISAAC_ENV_FILE_TEST_KEY}"))
      (c3env/override! "ISAAC_ENV_FILE_TEST_KEY" "sk-from-override")
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= "sk-from-override" (get-in result [:config :providers marigold/helm-systems :api-key]))))

    (it "loads config when the isaac .env file is absent"
      (marigold/write-config!
                     {:defaults  {:crew :main :model :llama}
                      :crew      {:main {}}
                      :models    {:llama (marigold/model-cfg (keyword marigold/helm-systems) "llama3.3:1b")}
                      :providers {(keyword marigold/helm-systems) {}}})
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= "main" (get-in result [:config :defaults :crew]))))

    (it "preserves cron jobs and timezone from the root config"
      (marigold/write-config! {:crew {:main {}}
                                                 :tz   "America/Chicago"
                                                 :cron {:health-check {:expr  "0 9 * * *"
                                                                       :crew  :main
                                                                       :prompt "Run the health checkin."}}})
      (let [result (marigold/load-config)]
        (should= "America/Chicago" (get-in result [:config :tz]))
        (should= {:expr  "0 9 * * *"
                  :crew  "main"
                  :prompt "Run the health checkin."}
                 (get-in result [:config :cron "health-check"])))))

    (it "loads cron prompt from a companion markdown file"
      (marigold/write-config! {:crew {:main {}}
                                                 :cron {:health-check {:expr "0 9 * * *"
                                                                       :crew :main}}})
      (marigold/write-cron-md! :health-check "Run the daily health checkin.")
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= "Run the daily health checkin."
                 (get-in result [:config :cron "health-check" :prompt]))))

    (it "loads cron jobs from a single markdown file with EDN frontmatter"
      (marigold/write-config! {:crew {:main {}}})
      (marigold/write-cron-md! :health-check (str "---\n"
                                                               "{:expr \"0 9 * * *\"\n"
                                                               " :crew :main}\n"
                                                               "---\n\n"
                                                               "Run the daily health checkin."))
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= {:expr "0 9 * * *"
                  :crew "main"
                  :prompt "Run the daily health checkin."}
                 (get-in result [:config :cron "health-check"])))))

    (it "loads cron jobs from legacy edn and markdown files"
      (marigold/write-config! {:crew {:main {}}})
      (marigold/write-cron! :health-check {:expr "0 9 * * *"
                                                              :crew :main})
      (marigold/write-cron-md! :health-check "Run the daily health checkin.")
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= {:expr "0 9 * * *"
                  :crew "main"
                  :prompt "Run the daily health checkin."}
                 (get-in result [:config :cron "health-check"]))))

    (it "loads hooks from a single markdown file with EDN frontmatter"
      (marigold/write-config! {:crew  {:main {}}
                                                 :hooks {:auth {:token "secret123"}}})
      (marigold/write-hook-md! :lettuce (str "---\n"
                                                          "{:crew :main\n"
                                                          " :session-key \"hook:lettuce\"}\n"
                                                          "---\n\n"
                                                          "Emergency lettuce report: {{leaves}} leaves remaining."))
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= "secret123" (get-in result [:config :hooks :auth :token]))
        (should= {:crew "main"
                  :session-key "hook:lettuce"
                  :template "Emergency lettuce report: {{leaves}} leaves remaining."}
                 (get-in result [:config :hooks "lettuce"]))))

    (it "loads hooks from legacy edn and markdown files"
      (marigold/write-config! {:crew {:main {}}})
      (marigold/write-hook! :lettuce {:crew :main
                                                          :session-key "hook:lettuce"})
      (marigold/write-hook-md! :lettuce "Emergency lettuce report: {{leaves}} leaves remaining.")
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= {:crew "main"
                  :session-key "hook:lettuce"
                  :template "Emergency lettuce report: {{leaves}} leaves remaining."}
                 (get-in result [:config :hooks "lettuce"]))))

    (it "reports an error when a cron prompt is missing inline and in markdown"
      (marigold/write-config! {:crew {:main {}}
                                                 :cron {:health-check {:expr "0 9 * * *"
                                                                       :crew :main}}})
      (let [result (marigold/load-config)]
        (should= [{:key "cron.health-check.prompt"
                   :value "required (inline or cron/health-check.md)"}]
                 (filter #(= "cron.health-check.prompt" (:key %)) (:errors result)))))

    (it "reports an error when a cron companion markdown file is empty"
      (marigold/write-config! {:crew {:main {}}
                                                 :cron {:health-check {:expr "0 9 * * *"
                                                                       :crew :main}}})
      (marigold/write-cron-md! :health-check "")
      (let [result (marigold/load-config)]
        (should= [{:key "cron.health-check.prompt"
                   :value "must not be empty"}]
                 (filter #(= "cron.health-check.prompt" (:key %)) (:errors result)))))

    (it "warns and keeps the inline cron prompt when both inline and markdown are present"
      (marigold/write-config! {:crew {:main {}}
                                                 :cron {:health-check {:expr   "0 9 * * *"
                                                                       :crew   :main
                                                                       :prompt "Inline prompt."}}})
      (marigold/write-cron-md! :health-check "Markdown prompt.")
      (let [result (marigold/load-config)
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
        (let [helm-kw (keyword marigold/helm-systems)
              cfg     {:defaults            {:crew :main :model :grover}
                       :crew                {:main {:soul "You are Isaac." :model :grover}}
                       :models              {:grover {:model "echo" :provider helm-kw}}
                       :providers           {helm-kw {:api-key "sk-test"}}
                       :cron                {:nightly {:expr "0 0 * * *" :crew :main}}
                       :channels            {:web {:name "web"}}
                       :comms               {(keyword marigold/longwave) {:token "abc"}}
                       :hooks               {(keyword marigold/lettuce-hook) {:token "secret"}}
                       :server              {:port 6674}
                       :sessions            {:retention-days 7}
                       :gateway             {:port 9000}
                       :tz                  "UTC"
                       :dev                 {:log-level :debug}
                       :acp                 {:enabled true}
                       :prefer-entity-files true
                       :modules             {:isaac.comm.pigeon {:local/root "/tmp/pigeon"}}}
              result  (sut/normalize-config cfg)]
          (should= {:crew :main :model :grover} (:defaults result))
          (should= {"main" {:soul "You are Isaac." :model :grover}} (:crew result))
          (should= {"grover" {:model "echo" :provider helm-kw}} (:models result))
          (should= {marigold/helm-systems {:api-key "sk-test"}} (:providers result))
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
        (let [helm-kw (keyword marigold/helm-systems)
              cfg     {:crew   {:defaults {:crew :main :model :grover}
                                :list     [{:id :main :soul "You are Isaac." :model :grover}
                                           {:id "ketch" :model :grover}]
                                :models   {:grover {:model "echo" :provider helm-kw :context-window 200000}}}
                       :models {:providers [{:name helm-kw :api-key "sk-test"}
                                            {:id :grover :base-url "https://grover.example"}]}}
              result  (sut/normalize-config cfg)]
          (should= {:crew :main :model :grover} (:defaults result))
          (should= {"main"  {:id :main :soul "You are Isaac." :model :grover}
                    "ketch" {:id "ketch" :model :grover}}
                   (:crew result))
          (should= {"grover" {:model "echo" :provider helm-kw :context-window 200000}}
                   (:models result))
          (should= {marigold/helm-systems {:api-key "sk-test"}
                    "grover"              {:id :grover :base-url "https://grover.example"}}
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
                     :models    {"grover" {:model "helm-mk-3-1.0" :provider marigold/helm-systems :context-window 200000}}
                     :providers {marigold/helm-systems {:api marigold/helm-api :base-url (:base-url marigold/helm-provider)}}}
                ctx (sut/resolve-crew-context cfg "main" {:home marigold/home})]
            (should= "You are Isaac." (:soul ctx))
            (should= "helm-mk-3-1.0" (:model ctx))
            (should= marigold/helm-systems ((requiring-resolve 'isaac.llm.api/display-name) (:provider ctx)))
            (should= 200000 (:context-window ctx))
            (should= (:base-url marigold/helm-provider) (get-in ((requiring-resolve 'isaac.llm.api/config) (:provider ctx)) [:base-url])))))))

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
                     :providers {"grover" {:api "responses" :effort 3}}}
                ctx (sut/resolve-crew-context cfg "main" {:home marigold/home})]
            (should= 9 (get-in ctx [:crew-cfg :effort]))
            (should= 5 (get-in ctx [:model-cfg :effort]))))))

  (describe "resolve-provider"

    (it "falls back from simulated provider ids to the base provider config"
      (let [cfg {:providers {marigold/grover-api {:api marigold/grover-api :effort 3}}}]
        (should= {:api marigold/grover-api :effort 3}
                 (sut/resolve-provider cfg (str marigold/grover-api ":" marigold/quantum-anvil))))))

  (describe "semantic-errors"

    (it "reports undefined defaults crew models provider cron crew and hook refs"
      (should= [{:key "hooks.webhook.crew" :value "references undefined crew \"ghost\" (known: marvin)"}
                {:key "hooks.webhook.model" :value (str "references undefined model \"phantom\" (known: " marigold/anvil-x ")")}
                {:key "crew.marvin.model" :value (str "references undefined model \"phantom\" (known: " marigold/anvil-x ")")}
                {:key "defaults.crew" :value "references undefined crew \"ghost\" (known: marvin)"}
                {:key "defaults.model" :value (str "references undefined model \"llama\" (known: " marigold/anvil-x ")")}
                {:key "cron.nightly.crew" :value "references undefined crew \"ghost\" (known: marvin)"}
                {:key (str "models." marigold/anvil-x ".provider") :value "references undefined provider \"imaginarium\""}]
               (mapv #(select-keys % [:key :value])
                     (#'sut/semantic-errors {:defaults  {:crew "ghost" :model "llama"}
                                             :crew      {"marvin" {:model "phantom"}}
                                             :models    {marigold/anvil-x {:provider "imaginarium"}}
                                             :providers {}
                                             :cron      {"nightly" {:crew "ghost"}}
                                             :hooks     {"webhook" {:crew "ghost" :model "phantom"}
                                                         :auth      {:token "secret"}}}))))

    (it "returns no semantic errors when all references resolve"
      (should= []
               (#'sut/semantic-errors {:defaults  {:crew "main" :model "llama"}
                                       :crew      {"main" {:model "llama"}}
                                       :models    {"llama" {:provider marigold/helm-systems}}
                                       :providers {marigold/helm-systems {}}
                                       :cron      {"nightly" {:crew "main"}}
                                       :hooks     {"webhook" {:crew "main" :model "llama"}
                                                   :auth      {:token "secret"}}})))

    )

  (describe "module discovery integration"

    (marigold/aboard)

    (it "attaches :module-index to loaded config for declared modules"
      (marigold/write-config! {:modules {:isaac.comm.pigeon {:local/root "/marigold/.isaac/modules/isaac.comm.pigeon"}}})
      (fs/mkdirs (str marigold/home "/.isaac/modules/isaac.comm.pigeon"))
      (fs/spit (str marigold/home "/.isaac/modules/isaac.comm.pigeon/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (str marigold/home "/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn")
               "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= :isaac.comm.pigeon
                 (get-in result [:config :module-index :isaac.comm.pigeon :manifest :id]))
        (should= "/marigold/.isaac/modules/isaac.comm.pigeon"
                 (get-in result [:config :module-index :isaac.comm.pigeon :path]))))

    (it "adds validation errors when a local/root path is not found"
      (marigold/write-config! {:modules {:isaac.comm.ghost {:local/root "/marigold/.isaac/modules/isaac.comm.ghost"}}})
      (let [result (marigold/load-config)]
        (should (some #(and (= "modules[\"isaac.comm.ghost\"]" (:key %))
                            (= "local/root path does not resolve" (:value %)))
                      (:errors result)))))

    (it "adds validation errors when a module manifest is invalid"
      (marigold/write-config! {:modules {:isaac.comm.pigeon {:local/root "/marigold/.isaac/modules/isaac.comm.pigeon"}}})
      (fs/mkdirs (str marigold/home "/.isaac/modules/isaac.comm.pigeon"))
      (fs/spit (str marigold/home "/.isaac/modules/isaac.comm.pigeon/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (str marigold/home "/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn")
               "{:id :isaac.comm.pigeon :entry isaac.comm.pigeon}")
      (let [result (marigold/load-config)]
        (should (some #(= "module-index[\"isaac.comm.pigeon\"].version" (:key %))
                      (:errors result)))))

    (it "simulates the feature scenario: separate write binding then load binding"
      ;; Mimics the feature runner: write files in one binding, load in another
      (let [mem (fs/mem-fs)]
        ;; write phase (like "the isaac file" step)
        (binding [fs/*fs* mem]
          (fs/mkdirs (str marigold/home "/.isaac/modules/isaac.comm.pigeon"))
          (fs/spit (str marigold/home "/.isaac/modules/isaac.comm.pigeon/deps.edn")
                   "{:paths [\"resources\"]}")
          (fs/spit (str marigold/home "/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn")
                   "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
          (fs/mkdirs (str marigold/home "/.isaac/config"))
          (fs/spit (str marigold/home "/.isaac/config/isaac.edn")
                   "{:modules {:isaac.comm.pigeon {:local/root \"/marigold/.isaac/modules/isaac.comm.pigeon\"}}}"))
        ;; load phase (like "when the config is loaded" step — NEW binding to SAME mem)
        (binding [fs/*fs* mem]
          (let [result (marigold/load-config)]
            (should-not-be-nil (get-in result [:config :module-index :isaac.comm.pigeon]))
            (should= :isaac.comm.pigeon
                     (get-in result [:config :module-index :isaac.comm.pigeon :manifest :id])))))))

  (describe "provider type schema validation"

    (marigold/aboard)

    (def kombucha-manifest
      (pr-str {:id       :isaac.providers.kombucha
               :version  "0.1.0"
               :provider {:kombucha {:template {:api      "chat-completions"
                                                :base-url "https://api.kombucha.test/v1"
                                                :auth     "api-key"
                                                :models   ["kombucha-large"]}
                                     :schema   {:fizz-level {:type :int}}}}}))

    (defn- write-kombucha-module! []
      (fs/mkdirs (str marigold/home "/.isaac/modules/isaac.providers.kombucha"))
      (fs/spit (str marigold/home "/.isaac/modules/isaac.providers.kombucha/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (str marigold/home "/.isaac/modules/isaac.providers.kombucha/resources/isaac-manifest.edn") kombucha-manifest))

    (it "rejects a provider field that violates the manifest :schema"
      (marigold/write-config!
                     {:modules   {:isaac.providers.kombucha {:local/root (str marigold/home "/.isaac/modules/isaac.providers.kombucha")}}
                      :providers {:my-kombucha {:type :kombucha :api-key "fizzy-secret" :fizz-level "seven"}}})
      (write-kombucha-module!)
      (let [result (marigold/load-config)]
        (should (some #(and (= "providers.my-kombucha.fizz-level" (:key %))
                            (re-find #"must be an integer" (:value %)))
                      (:errors result)))))

    (it "accepts a provider field that conforms to the manifest :schema"
      (marigold/write-config!
                     {:modules   {:isaac.providers.kombucha {:local/root (str marigold/home "/.isaac/modules/isaac.providers.kombucha")}}
                       :providers {:my-kombucha {:type :kombucha :api-key "fizzy-secret" :fizz-level 3}}})
      (write-kombucha-module!)
      (let [result (marigold/load-config)]
        (should-not (some #(str/includes? (:key %) "providers.my-kombucha.fizz-level")
                          (:errors result))))))

    (it "rejects a self-defined provider with auth api-key but no api-key"
      (marigold/write-config!
                     {:providers {:my-thing {:api      "messages"
                                             :base-url "https://example.test"
                                             :auth     "api-key"}}})
      (let [result (marigold/load-config)]
        (should (some #(and (= "providers.my-thing.api-key" (:key %))
                            (re-find #"is required when auth is api-key" (:value %)))
                      (:errors result)))))

    (it "rejects a typed provider when auth api-key is inherited but api-key is missing"
      (marigold/write-config!
                     {:modules   {:isaac.providers.kombucha {:local/root (str marigold/home "/.isaac/modules/isaac.providers.kombucha")}}
                       :providers {:my-kombucha {:type :kombucha :fizz-level 3}}})
      (write-kombucha-module!)
      (let [result (marigold/load-config)]
        (should (some #(and (= "providers.my-kombucha.api-key" (:key %))
                            (re-find #"is required when auth is api-key" (:value %)))
                      (:errors result)))))

  (describe "comm slot validation"

    (marigold/aboard)

    (def telly-manifest
      (pr-str {:id      :isaac.comm.telly
               :version "0.1.0"
               :comm    {:telly {:factory 'isaac.comm.telly/make
                                 :schema  {:loft  {:type :string}
                                           :color {:type :string}}}}}))

    (defn- write-telly-module! []
      (fs/mkdirs (str marigold/home "/.isaac/modules/isaac.comm.telly"))
      (fs/spit (str marigold/home "/.isaac/modules/isaac.comm.telly/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (str marigold/home "/.isaac/modules/isaac.comm.telly/resources/isaac-manifest.edn") telly-manifest))

    (def discord-manifest
      (pr-str {:id      :isaac.comm.discord
               :version "0.1.0"
               :comm    {:discord {:factory 'isaac.comm.discord/make
                                   :schema  {:token       {:type :string}
                                             :crew        {:type :string}
                                             :message-cap {:type :int}
                                             :allow-from  {:type :map}}}}}))

    (defn- write-discord-module! []
      (fs/mkdirs (str marigold/home "/.isaac/modules/isaac.comm.discord"))
      (fs/spit (str marigold/home "/.isaac/modules/isaac.comm.discord/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (str marigold/home "/.isaac/modules/isaac.comm.discord/resources/isaac-manifest.edn") discord-manifest))

    (it "validates declared module comm slot fields with no error for valid value"
      (marigold/write-config!
                     {:modules {:isaac.comm.telly {:local/root "/marigold/.isaac/modules/isaac.comm.telly"}}
                      :comms {:bert {:type :telly :loft "rooftop"}}})
      (write-telly-module!)
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= "rooftop" (get-in result [:config :comms :bert :loft]))))

    (it "generates a validation error for wrong type in a module comm slot field"
      (marigold/write-config!
                     {:modules {:isaac.comm.telly {:local/root "/marigold/.isaac/modules/isaac.comm.telly"}}
                      :comms {:bert {:type :telly :loft 42}}})
      (write-telly-module!)
      (let [result (marigold/load-config)]
        (should (some #(and (= "comms.bert.loft" (:key %))
                            (= "must be a string" (:value %)))
                      (:errors result)))))

    (it "generates unknown-key warnings for comm slot fields when module is not declared"
      (marigold/write-config!
                     {:comms {:bert {:type :telly :loft "rooftop"}}})
      (let [result (marigold/load-config)]
        (should (some #(and (= "comms.bert.loft" (:key %))
                            (= "unknown key" (:value %)))
                      (:warnings result)))))

    (it "does not warn for discord when its module is declared"
      (marigold/write-config!
                     {:modules {:isaac.comm.discord {:local/root "/marigold/.isaac/modules/isaac.comm.discord"}}
                       :comms   {:mychan {:type :discord :token "abc"}}})
      (write-discord-module!)
      (let [result (marigold/load-config)]
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
