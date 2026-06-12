(ns isaac.config.loader-spec
  (:require
    [c3kit.apron.schema :as cs]
    [c3kit.apron.env :as c3env]
    [clojure.string :as str]
    [isaac.config.companion :as companion]
    [isaac.marigold :as marigold]
    [isaac.nexus :as nexus]
    [isaac.logger :as log]
    [isaac.config.paths :as paths]
    [isaac.spec-helper :as helper]
    [isaac.config.loader :as sut]
    [isaac.fs :as fs]
    [isaac.llm.provider :as llm-provider]
    [isaac.module.loader :as module-loader]
    [speclj.core :refer :all]))

(defn- with-config-slot [f]
  (nexus/-with-nexus {:config (atom nil)}
    (f)))

(def ^:private test-crew marigold/first-mate)
(def ^:private test-crew-kw (keyword test-crew))
(def ^:private test-crew-file (str "crew/" test-crew ".edn"))
(def ^:private test-crew-md (str "crew/" test-crew ".md"))
(def ^:private test-crew-path (str "crew." test-crew))
(def ^:private test-crew-tmp-path (str "/tmp/" test-crew ".edn"))

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

  (describe "read-frontmatter-file"

    (it "parses YAML frontmatter and applies env substitution"
      (sut/set-env-override! "TEST_CREW" "main")
      (should= {:body "You are Cordelia."
                :data {:crew "main"
                       :model "llama"}}
               (#'sut/read-frontmatter-file {:overlay? true
                                             :relative "crew/cordelia.md"
                                             :content  "---\ncrew: ${TEST_CREW}\nmodel: llama\n---\n\nYou are Cordelia."}
                                            true
                                            false)))

    (it "reports YAML syntax errors for malformed frontmatter"
      (should= {:error "YAML syntax error"}
               (#'sut/read-frontmatter-file {:overlay? true
                                             :relative "crew/cordelia.md"
                                             :content  "---\nmodel: [broken\n---\n\nYou are Cordelia."}
                                            true
                                            false))))

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

  (describe "resolve-crew-context model override"

    (it "uses a named model override passed in the crew config"
      (with-redefs [llm-provider/make-provider (fn [provider-id provider-cfg]
                                                 {:id provider-id :cfg provider-cfg})]
        (let [cfg {:module-index {:source :spec}
                   :crew         {"main" {:model "grover"}}
                   :models       {"grover" {:model                  "helm-mk-3-1.0"
                                             :provider               marigold/helm-systems
                                             :context-window         200000
                                             :enforce-context-window true
                                             :thinking-budget-max    7
                                             :think-mode             :deep}}
                   :providers    {marigold/helm-systems {:api marigold/helm-api
                                                         :base-url (:base-url marigold/helm-provider)}}}
              ctx (sut/resolve-crew-context cfg "main")]
          (should= "helm-mk-3-1.0" (:model ctx))
          (should= {:model "helm-mk-3-1.0"
                    :provider marigold/helm-systems
                    :context-window 200000
                    :thinking-budget-max 7
                    :think-mode :deep}
                   (select-keys (:model-cfg ctx) [:model :provider :context-window :thinking-budget-max :think-mode]))
          (should= {:api marigold/helm-api
                    :base-url (:base-url marigold/helm-provider)}
                   (select-keys (:provider-cfg ctx) [:api :base-url]))
          (should= {:id marigold/helm-systems}
                   (select-keys (:provider ctx) [:id]))
          (should= {:api marigold/helm-api
                    :base-url (:base-url marigold/helm-provider)
                    :module-index {:source :spec}
                    :thinking-budget-max 7
                    :think-mode :deep}
                   (select-keys (get-in ctx [:provider :cfg]) [:api :base-url :module-index :thinking-budget-max :think-mode]))
          (should= 200000 (:context-window ctx)))))

    (it "accepts provider slash model refs in the crew config"
      (with-redefs [llm-provider/make-provider (fn [provider-id provider-cfg]
                                                 {:id provider-id :cfg provider-cfg})]
        (let [cfg {:module-index {:source :spec}
                   :crew         {"main" {:model (str marigold/helm-systems "/helm-mk-3-1.0")}}
                   :providers    {marigold/helm-systems {:api marigold/helm-api
                                                         :context-window 64000}}}
              ctx (sut/resolve-crew-context cfg "main")]
          (should= "helm-mk-3-1.0" (:model ctx))
          (should= {:provider marigold/helm-systems
                    :model "helm-mk-3-1.0"}
                   (:model-cfg ctx))
          (should= {:api marigold/helm-api
                    :context-window 64000}
                   (select-keys (:provider-cfg ctx) [:api :context-window]))
          (should= {:id marigold/helm-systems}
                   (select-keys (:provider ctx) [:id]))
          (should= {:api marigold/helm-api
                    :context-window 64000
                    :module-index {:source :spec}}
                   (select-keys (get-in ctx [:provider :cfg]) [:api :context-window :module-index]))
          (should= 64000 (:context-window ctx))))))

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
      (let [mem  (fs/mem-fs)
            path (str marigold/home "/" paths/root-filename)]
        (fs/mkdirs mem marigold/home)
        (fs/spit mem path "{:defaults {:model :llama}}")
        (with-redefs [sut/overlay-for          (constantly nil)
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
          (nexus/-with-nexus {:fs mem}
            (let [result (#'sut/load-root-config marigold/home {:raw-parse-errors? true :substitute-env? true})]
              (should= {:defaults {:model :llama}
                        :cron     {"health-check" {:expr "0 9 * * *" :crew "main" :prompt "Ping"}}}
                       (:data result))
              (should= [{:key "cron.health-check.prompt" :value "bad prompt"}
                        {:key "defaults" :value "invalid"}]
                       (:errors result))
              (should= [{:key "root" :value "warning"}] (:warnings result))
              (should= [(#'sut/source-path paths/root-filename)] (:sources result)))))))

    (it "returns file read errors for an on-disk root file"
      (let [mem  (fs/mem-fs)
            path (str marigold/home "/" paths/root-filename)]
        (fs/mkdirs mem marigold/home)
        (fs/spit mem path "{:broken")
        (with-redefs [sut/overlay-for   (constantly nil)
                      sut/read-edn-file (fn [_ _ _] {:error "EDN syntax error"})]
          (nexus/-with-nexus {:fs mem}
            (should= {:data nil
                      :errors [{:key paths/root-filename :value "EDN syntax error"}]
                      :warnings []
                      :sources []}
                     (#'sut/load-root-config marigold/home {}))))))

    (it "returns an empty result when no root config source exists"
      (let [mem (fs/mem-fs)]
        (with-redefs [sut/overlay-for (constantly nil)]
          (nexus/-with-nexus {:fs mem}
            (should= {:data nil :errors [] :warnings [] :sources []}
                     (#'sut/load-root-config marigold/home {})))))))

  (describe "runtime fs"

    (it "loads the root config from the installed runtime fs without binding fs/*fs*"
      (let [mem  (fs/mem-fs)
            root (paths/config-root marigold/home)
            path (str root "/" paths/root-filename)]
        (fs/mkdirs mem root)
        (fs/spit mem path "{:crew {:main {}}}")
        (with-redefs [sut/overlay-for          (constantly nil)
                      sut/resolve-cron-prompts (fn [_ data] {:cron (:cron data) :errors []})
                      sut/top-level-warnings   (constantly [])
                      cs/conform               (fn [_ _] :ok)
                      cs/error?                (constantly false)]
          (nexus/-with-nexus {:fs mem}
            (let [result (#'sut/load-root-config root {:substitute-env? true})]
              (should= {:crew {:main {}}} (:data result))
              (should= [] (:errors result)))))))

    (it "loads config from an explicit fs option without installing runtime fs"
      (let [mem  (fs/mem-fs)
            root (paths/config-root marigold/root)
            path (str root "/" paths/root-filename)]
        (fs/mkdirs mem root)
        (fs/spit mem path (pr-str marigold/baseline-config))
        (nexus/reset!)
        (let [result (sut/load-config-result {:root marigold/root :fs mem})]
          (should= [] (:errors result))
          (should= "atticus" (get-in result [:config :defaults :crew])))))

    )

  (describe "semantic-errors"

    (it "builds known-id sets once per validation pass"
      ;; Phase 6 (isaac-w7o5): :tool-exists? / known-tool-ids no longer
      ;; live in existence-refs — crew :tools :allow validates via
      ;; [:registered-in? :isaac.server/tools] against the live
      ;; module-index, which short-circuits the known-set memoization
      ;; this test covers for the other capabilities.
      ;; Phase 8 (isaac-qqgv): :comm-exists? no longer lives in
      ;; existence-refs — comm validation goes through
      ;; [:registered-in? :isaac.server/comm [:comms]] which reads
      ;; the live module-index instead of a memoized known-set.
      (let [crew-calls     (atom 0)
            model-calls    (atom 0)
            config         {:defaults  {:crew "main" :model "llama"}
                            :crew      {"main" {:model    "llama"
                                                :provider marigold/starcore}}
                            :models    {"llama" {:provider marigold/starcore}}
                            :providers {marigold/starcore {:api marigold/sky-api}}}]
        (with-redefs-fn {#'isaac.config.loader/known-crew-ids  (fn [_]
                                                                  (swap! crew-calls inc)
                                                                  ["main"])
                         #'isaac.config.loader/known-model-ids (fn [_]
                                                                  (swap! model-calls inc)
                                                                  ["llama"])}
          #(should= [] (#'sut/semantic-errors config)))
        (should= 1 @crew-calls)
        (should= 1 @model-calls))))

  (describe "load-entity-file"

    (it "adds a string read error using the relative path"
      (with-redefs [sut/read-edn-file (fn [_ _ _] {:error "EDN syntax error"})]
        (should= [{:key test-crew-file :value "EDN syntax error"}]
                 (:errors (#'sut/load-entity-file {:config {} :root {} :errors [] :warnings [] :sources []}
                                                  marigold/home
                                                  :crew
                                                  {:format :edn :path test-crew-tmp-path :relative test-crew-file :id test-crew}
                                                  true
                                                  false)))))

    (it "passes through map-shaped errors unchanged"
      (with-redefs [sut/read-edn-file (fn [_ _ _] {:error {:key (str test-crew-path ".soul") :value "must be set"}})]
        (should= [{:key (str test-crew-path ".soul") :value "must be set"}]
                 (:errors (#'sut/load-entity-file {:config {} :root {} :errors [] :warnings [] :sources []}
                                                  marigold/home
                                                  :crew
                                                  {:format :edn :path test-crew-tmp-path :relative test-crew-file :id test-crew}
                                                  true
                                                  false)))))

    (it "reports non-map entity content"
      (with-redefs [sut/read-edn-file (fn [_ _ _] {:data [:not-a-map]})]
        (should= [{:key test-crew-file :value "must contain a map"}]
                 (:errors (#'sut/load-entity-file {:config {} :root {} :errors [] :warnings [] :sources []}
                                                  marigold/home
                                                  :crew
                                                  {:format :edn :path test-crew-tmp-path :relative test-crew-file :id test-crew}
                                                  true
                                                  false)))))

    (it "records schema and id mismatch errors without storing invalid config"
      (with-redefs [sut/read-edn-file                (fn [_ _ _] {:data {:id "parrot" :model :grover}})
                    sut/resolve-crew-soul            (fn [_ data _] {:data data :error nil})
                    sut/collect-unknown-key-warnings (fn [& _] [{:key (str test-crew-path ".extra") :value "unknown key"}])
                    sut/schema-for                   (fn [_] ::crew)
                    cs/conform                       (fn [_ data] data)
                    cs/error?                        (constantly false)]
        (let [result (#'sut/load-entity-file {:config {:crew {test-crew {:model "echo"}}}
                                              :root   {:crew {test-crew {:model "echo"}}}
                                              :errors []
                                              :warnings []
                                              :sources []}
                                             marigold/home
                                             :crew
                                             {:format :edn :path test-crew-tmp-path :relative test-crew-file :id test-crew}
                                             true
                                             false)]
          (should= [{:key (str test-crew-path ".id") :value "must match filename (got \"parrot\")"}
                    {:key test-crew-path :value (str "defined in both isaac.edn and " test-crew-file)}]
                   (:errors result))
          (should= [{:key (str test-crew-path ".extra") :value "unknown key"}] (:warnings result))
          (should= [(#'sut/source-path test-crew-file)] (:sources result))
          (should= {test-crew {:model "echo"}} (get-in result [:config :crew])))))

    (it "stores valid entity config and companion extra errors"
      (with-redefs [sut/read-edn-file                (fn [_ _ _] {:data {:model :grover}})
                    sut/resolve-crew-soul            (fn [_ data _] {:data (assoc data :soul "You are Cordelia.") :error nil})
                    sut/collect-unknown-key-warnings (fn [& _] [])
                    sut/schema-for                   (fn [_] ::crew)
                    cs/conform                       (fn [_ data] data)
                    cs/error?                        (constantly false)]
        (let [result (#'sut/load-entity-file {:config {} :root {} :errors [] :warnings [] :sources []}
                                             marigold/home
                                             :crew
                                             {:format :edn :path test-crew-tmp-path :relative test-crew-file :id test-crew}
                                             true
                                             false)]
          (should= {test-crew {:model :grover :soul "You are Cordelia."}}
                   (get-in result [:config :crew]))
          (should= [(#'sut/source-path test-crew-file)] (:sources result)))))

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
                                             {:format :edn :path test-crew-tmp-path :relative test-crew-file :id test-crew}
                                             true
                                             false)]
          (should= [{:key test-crew-path :value "invalid schema"}] (:errors result))
          (should= {} (:config result))
          (should= [(#'sut/source-path test-crew-file)] (:sources result)))))

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
                                             {:format :edn :overlay? true :content "{:model :grover}" :relative test-crew-file :id test-crew}
                                             true
                                             false)]
          (should= {test-crew {:model :grover :soul "Overlay soul"}}
                   (get-in result [:config :crew]))
          (should= [(#'sut/source-path test-crew-file)] (:sources result)))))

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

    (it "discovers declared modules before conforming the config schema"
      (let [mem     (fs/mem-fs)
            root    (paths/config-root marigold/root)
            path    (str root "/" paths/root-filename)
            modules {:isaac.comm.pigeon {:local/root "/marigold/.isaac/modules/isaac.comm.pigeon"}}
            events  (atom [])]
        (fs/mkdirs mem root)
        (fs/spit mem path (pr-str {:modules modules}))
        (with-redefs [module-loader/discover! (fn [config _context]
                                                (swap! events conj [:discover (:modules config)])
                                                {:index {} :errors []})
                      cs/conform              (fn [_ data]
                                                (swap! events conj [:conform data])
                                                data)
                      cs/error?               (constantly false)]
          (sut/load-config-result {:root marigold/root :fs mem :skip-entity-files? true})
          (should= [:discover modules] (first @events)))))

    (it "returns an honest empty config when no files exist"
      (let [result (marigold/load-config)]
        (should= [{:key "config"
                   :value (str "no config found; run `isaac init` or create " marigold/home "/.isaac/config/isaac.edn")}]
                 (:errors result))
        (should= {:root (str marigold/home "/.isaac")} (:config result))
        (should= true (:missing-config? result))
        (should= [] (:warnings result))
        (should= [] (:sources result))))

    (it "loads crew members from per-entity files and companion md soul"
      (marigold/write-crew! test-crew-kw {:model :llama})
      (marigold/write-crew-md! test-crew-kw "You are Cordelia.")
      (let [result (marigold/load-config)]
        (should= "llama" (get-in result [:config :crew test-crew :model]))
        (should= "You are Cordelia." (get-in result [:config :crew test-crew :soul]))))

    (it "loads crew members from a single markdown file with YAML frontmatter"
      (marigold/write-config!
                     {:models    {:llama (marigold/model-cfg (keyword marigold/flicker-labs) "llama3.2")}
                      :providers {(keyword marigold/flicker-labs) {:api marigold/groves-api}}})
      (marigold/write-crew-md! test-crew-kw (str "---\n"
                                                         "model: llama\n"
                                                         "---\n\n"
                                                         "You are Cordelia."))
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= "llama" (get-in result [:config :crew test-crew :model]))
        (should= "You are Cordelia." (get-in result [:config :crew test-crew :soul]))))

    (it "prefers single-file crew markdown over legacy files and warns"
      (marigold/write-config!
                     {:models    {:grover (marigold/model-cfg (keyword marigold/helm-systems) "helm-mk-3-1.0")}
                      :providers {(keyword marigold/helm-systems) {:api marigold/helm-api}}})
      (marigold/write-crew! test-crew-kw {:model :llama})
      (marigold/write-crew-md! test-crew-kw (str "---\n"
                                                         "model: grover\n"
                                                         "---\n\n"
                                                         "You are Cordelia."))
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= "grover" (get-in result [:config :crew test-crew :model]))
        (should= "You are Cordelia." (get-in result [:config :crew test-crew :soul]))
        (should= [{:key test-crew-md
                   :value (str "single-file config overrides legacy " test-crew-file)}]
                 (filter #(= test-crew-md (:key %)) (:warnings result)))))

    (it "reports duplicate ids across isaac.edn and per-entity files"
      (marigold/write-config! {:crew {test-crew-kw {:soul "First"}}})
      (marigold/write-crew! test-crew-kw {:soul "Second"})
      (let [result (marigold/load-config)]
        (should= [{:key test-crew-path
                   :value (str "defined in both isaac.edn and " test-crew-file)}]
                  (:errors result))))

    (it "reports malformed crew EDN with the relative file path"
      (marigold/write-raw! test-crew-file "{:model :llama")
      (let [result (marigold/load-config)]
        (should= [{:key test-crew-file
                     :value "EDN syntax error"}]
                   (:errors result))))

    (it "reports malformed crew YAML frontmatter with the relative file path"
      (marigold/write-raw! test-crew-md "---\nmodel: [broken\n---\n\nYou are Cordelia.")
      (let [result (marigold/load-config)]
        (should= [{:key test-crew-md
                   :value "YAML syntax error"}]
                 (:errors result))))

    (it "reports a soul conflict when both edn and companion md define soul"
      (marigold/write-crew! test-crew-kw {:soul "Inline soul."})
      (marigold/write-crew-md! test-crew-kw "File soul.")
      (let [result (marigold/load-config)]
        (should= [{:key (str test-crew-path ".soul")
                   :value "must be set in .edn OR .md"}]
                 (:errors result))))

    (it "warns about unknown keys in entity files but still loads"
      (marigold/write-crew! test-crew-kw {:crew {test-crew-kw {:model :llama}}})
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= [{:key (str test-crew-path ".crew") :value "unknown key"}] (:warnings result))))

    (it "warns about unknown keys in inline root entities"
      (marigold/write-config! {:defaults {:crew :main :model :llama}
                               :crew     {:main {:experimental true}}
                               :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
                               :providers {:anthropic {}}})
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should-contain {:key "crew.main.experimental" :value "unknown key"}
                        (:warnings result))))

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
                      :crew      {test-crew-kw {:model :gpt}}
                      :models    {:grover (marigold/model-cfg (keyword marigold/helm-systems) "helm-mk-3-1.0" :context-window 200000)}
                      :providers {(keyword marigold/helm-systems) {}}})
      (let [result (marigold/load-config)]
        (should= [{:key (str test-crew-path ".model") :value "references undefined model" :bad-value "gpt" :valid-values ["grover"]}
                  {:key "defaults.crew" :value "references undefined crew" :bad-value "ghost" :valid-values [test-crew]}
                  {:key "defaults.model" :value "references undefined model" :bad-value "llama" :valid-values ["grover"]}]
                 (mapv #(select-keys % [:key :value :bad-value :valid-values]) (:errors result)))))

    (it "rejects model references to a manifest template that is not instantiated in user config"
      ;; Phase 7 (isaac-ho18): :provider-exists? was replaced by
      ;; [:registered-in? :isaac.server/provider]. When no providers
      ;; are instantiated at all, the validator reports an empty-impl
      ;; berth.
      (marigold/write-config!
                     {:models {(keyword marigold/helm-mark-iii)
                               (marigold/model-cfg (keyword marigold/helm-systems) "helm-mk-3-1.0" :context-window 200000)}})
      (let [result (marigold/load-config)]
        (should= [{:key      (str "models." marigold/helm-mark-iii ".provider")
                   :value    "no registered impls for berth :isaac.server/provider"
                   :bad-value marigold/helm-systems}]
                 (mapv #(select-keys % [:key :value :bad-value]) (:errors result)))))

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
      ;; Phase 7 (isaac-ho18): :registered-in? message form when the
      ;; accepted set is small (≤5 ids).
      (marigold/write-config! {:models    {:mystery {:model           "enigmatic-1"
                                                                      :provider        :foo
                                                                      :context-window  1024}}
                                                :providers {(keyword marigold/helm-systems) {}
                                                            (keyword marigold/starcore)     {}}})
      (let [result (marigold/load-config)
            valid  (vec (sort [marigold/helm-systems marigold/starcore]))]
        (should= [{:key          "models.mystery.provider"
                   :value        (str "must be one of " valid)
                   :bad-value    "foo"
                   :valid-values [marigold/helm-systems marigold/starcore]}]
                 (mapv #(select-keys % [:key :value :bad-value :valid-values]) (:errors result)))))

    (it "rejects providers with an unknown api"
      ;; Phase 7 (isaac-ho18): :llm-api-exists? was replaced by
      ;; [:registered-in? :isaac.server/llm-api]. Error wording shifted
      ;; from "unknown api" to the validator's "must be one of …"
      ;; (small set) failure; valid-values list is preserved.
      (marigold/write-config!
                     {:providers {:bogus {:api "carrier-pigeon" :base-url "https://example.com" :auth "api-key" :api-key "test"}}})
      (let [result     (marigold/load-config)
            known-apis (->> marigold/baseline-manifest :isaac.server/llm-api keys (map name) sort vec)
            expected-msg (str "must be one of " (vec (sort known-apis)))]
        (should= [{:key          "providers.bogus.api"
                   :value        expected-msg
                   :bad-value    "carrier-pigeon"
                   :valid-values known-apis}]
                 (mapv #(select-keys % [:key :value :bad-value :valid-values])
                       (filter #(= "providers.bogus.api" (:key %)) (:errors result))))))

    (it "rejects providers with an unknown :type target"
      ;; Phase 7 (isaac-ho18): :manifest-provider-exists? was replaced
      ;; by [:registered-in? :isaac.server/provider-template].
      (marigold/write-config!
                     {:providers {:dreamy {:type :ghost-provider :api-key "test"}}})
      (let [result          (marigold/load-config)
            known-providers (->> marigold/baseline-manifest :isaac.server/provider-template keys (map name) sort vec)
            expected-msg    (str "must be one of " (vec (sort known-providers)))]
        (should= [{:key          "providers.dreamy.type"
                   :value        expected-msg
                   :bad-value    "ghost-provider"
                   :valid-values known-providers}]
                 (mapv #(select-keys % [:key :value :bad-value :valid-values])
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

    (it "rejects the retired hooks.auth.token slot"
      (marigold/write-config! {:server {:auth {:token "s3cr3t"}}
                               :hooks  {:auth {:token "leftover"}}})
      (let [result (marigold/load-config)]
        (should= [{:key "hooks.auth.token"
                   :value "retired; use :server :auth :token"}]
                 (mapv #(select-keys % [:key :value]) (:errors result)))))

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

    (it "loads cron jobs from a single markdown file with YAML frontmatter"
      (marigold/write-config! {:crew {:main {}}})
      (marigold/write-cron-md! :health-check (str "---\n"
                                                               "expr: \"0 9 * * *\"\n"
                                                               "crew: main\n"
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

    (it "loads hooks from a single markdown file with YAML frontmatter"
      (marigold/write-config! {:crew  {:main {}}
                                                 :hooks {:auth {:token "secret123"}}})
      (marigold/write-hook-md! :lettuce (str "---\n"
                                                          "crew: main\n"
                                                          "session-key: hook:lettuce\n"
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

    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example] (nexus/-with-nested-nexus {:fs (fs/mem-fs)} (example)))

    (it "resolves crew model provider and context window from the new map-by-id shape"
      (with-redefs [llm-provider/make-provider (fn [provider-id provider-cfg]
                                                 {:id provider-id :cfg provider-cfg})]
        (let [cfg {:defaults  {:crew "main" :model "llama"}
                   :crew      {"main" {:model "grover" :soul "You are Isaac."}}
                   :models    {"grover" {:model "helm-mk-3-1.0" :provider marigold/helm-systems :context-window 200000}}
                   :providers {marigold/helm-systems {:api marigold/helm-api :base-url (:base-url marigold/helm-provider)}}}
              ctx (sut/resolve-crew-context cfg "main")]
          (should= "You are Isaac." (:soul ctx))
          (should= "helm-mk-3-1.0" (:model ctx))
          (should= marigold/helm-systems (:id (:provider ctx)))
          (should= 200000 (:context-window ctx))
          (should= (:base-url marigold/helm-provider) (get-in ctx [:provider :cfg :base-url])))))

    (it "returns crew-cfg and model-cfg for effort resolution"
      (with-redefs [llm-provider/make-provider (fn [provider-id provider-cfg]
                                                 {:id provider-id :cfg provider-cfg})]
        (let [cfg {:defaults  {:crew "main" :model "snuffy"}
                   :crew      {"main" {:model "snuffy" :effort 9}}
                   :models    {"snuffy" {:model "snuffy-codex" :provider "grover" :effort 5}}
                   :providers {"grover" {:api "responses" :effort 3}}}
              ctx (sut/resolve-crew-context cfg "main")]
          (should= 9 (get-in ctx [:crew-cfg :effort]))
          (should= 5 (get-in ctx [:model-cfg :effort]))))))

  (describe "resolve-provider"

    (it "falls back from simulated provider ids to the base provider config"
      (let [cfg {:providers {marigold/grover-api {:api marigold/grover-api :effort 3}}}]
        (should= {:api marigold/grover-api :effort 3}
                 (sut/resolve-provider cfg (str marigold/grover-api ":" marigold/quantum-anvil))))))

  (describe "semantic-errors"

    (it "reports undefined defaults crew models provider cron crew and hook refs"
      (should= [{:key "hooks.webhook.crew"  :value "references undefined crew"     :bad-value "ghost"   :valid-values [test-crew]}
                 {:key "hooks.webhook.model" :value "references undefined model"    :bad-value "phantom" :valid-values [marigold/anvil-x]}
                {:key (str test-crew-path ".model") :value "references undefined model" :bad-value "phantom" :valid-values [marigold/anvil-x]}
                {:key "defaults.crew"       :value "references undefined crew"     :bad-value "ghost"   :valid-values [test-crew]}
                 {:key "defaults.model"      :value "references undefined model"    :bad-value "llama"   :valid-values [marigold/anvil-x]}
                {:key "cron.nightly.crew"   :value "references undefined crew"     :bad-value "ghost"   :valid-values [test-crew]}
                 {:key (str "models." marigold/anvil-x ".provider")
                  :value "no registered impls for berth :isaac.server/provider" :bad-value "imaginarium" :valid-values []}]
               (mapv #(select-keys % [:key :value :bad-value :valid-values])
                     (#'sut/semantic-errors {:defaults  {:crew "ghost" :model "llama"}
                                             :crew      {test-crew {:model "phantom"}}
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
      (fs/mkdirs (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.pigeon"))
      (fs/spit (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.pigeon/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn")
               "{:id :isaac.comm.pigeon :version \"0.1.0\"}")
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
      (fs/mkdirs (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.pigeon"))
      (fs/spit (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.pigeon/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn")
               "{:id :isaac.comm.pigeon}")
      (let [result (marigold/load-config)]
        (should (some #(= "module-index[\"isaac.comm.pigeon\"].version" (:key %))
                      (:errors result)))))

    (it "simulates the feature scenario: separate write binding then load binding"
      ;; Mimics the feature runner: write files in one binding, load in another
      (let [mem (fs/mem-fs)]
        ;; write phase (like "the isaac file" step)
        (nexus/-with-nested-nexus {:fs mem}
          (fs/mkdirs mem (str marigold/home "/.isaac/modules/isaac.comm.pigeon"))
          (fs/spit   mem (str marigold/home "/.isaac/modules/isaac.comm.pigeon/deps.edn")
                          "{:paths [\"resources\"]}")
          (fs/spit   mem (str marigold/home "/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn")
                          "{:id :isaac.comm.pigeon :version \"0.1.0\"}")
          (fs/mkdirs mem (str marigold/home "/.isaac/config"))
          (fs/spit   mem (str marigold/home "/.isaac/config/isaac.edn")
                          "{:modules {:isaac.comm.pigeon {:local/root \"/marigold/.isaac/modules/isaac.comm.pigeon\"}}}"))
        ;; load phase (like "when the config is loaded" step — NEW binding to SAME mem)
        (nexus/-with-nested-nexus {:fs mem}
          (let [result (marigold/load-config)]
            (should-not-be-nil (get-in result [:config :module-index :isaac.comm.pigeon]))
            (should= :isaac.comm.pigeon
                     (get-in result [:config :module-index :isaac.comm.pigeon :manifest :id])))))))

  (describe "tool schema validation"

    (marigold/aboard)

    (it "rejects a missing required tool field from manifest validations"
      (marigold/write-config!
        {:tools {(keyword marigold/signal-flare) {:provider :brave}}})
      (let [result (marigold/load-config)]
        (should (some #(and (= "tools.signal-flare.api-key" (:key %))
                            (re-find #"is required" (:value %)))
                      (:errors result)))))

    (it "warns when a tool provider falls outside a manifest enum"
      (marigold/write-config!
        {:tools {(keyword marigold/signal-flare) {:provider :duckduckgo
                                                  :api-key  "search-key"}}})
      (let [result (marigold/load-config)]
        (should (some #(and (= "tools.signal-flare.provider" (:key %))
                            (= "unknown provider" (:value %)))
                      (:warnings result))))))

  (describe "provider type schema validation"

    (marigold/aboard)

    (def kombucha-manifest
      (pr-str {:id      :isaac.providers.kombucha
               :version "0.1.0"
               :isaac.server/provider-template
               {:kombucha {:template {:api      "chat-completions"
                                      :base-url "https://api.kombucha.test/v1"
                                      :auth     "api-key"
                                      :models   ["kombucha-large"]}
                           :schema   {:fizz-level {:type :int}}}}}))

    (defn- write-kombucha-module! []
      (fs/mkdirs (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.providers.kombucha"))
      (fs/spit (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.providers.kombucha/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.providers.kombucha/resources/isaac-manifest.edn") kombucha-manifest))

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
                 :isaac.server/comm    {:telly {:factory 'isaac.comm.telly/make
                                  :schema  {:loft  {:type :string
                                                   :validations [[:present-when? :type :telly]]}
                                           :color {:type :string}
                                           :mood  {:type :string
                                                   :validations [[:one-of? "happy" "sad" "grumpy"]]}}}}}))

    (defn- write-telly-module! []
      (fs/mkdirs (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.telly"))
      (fs/spit (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.telly/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.telly/resources/isaac-manifest.edn") telly-manifest))

    (def crow-manifest
      (pr-str {:id      :isaac.comm.crow
               :version "0.1.0"
               :isaac.server/comm    {:crow {:factory 'isaac.comm.crow/make
                                :schema  {:token       {:type :string}
                                          :crew        {:type :string}
                                          :message-cap {:type :int}
                                          :allow-from  {:type :map}}}}}))

    (defn- write-crow-module! []
      (fs/mkdirs (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.crow"))
      (fs/spit (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.crow/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.crow/resources/isaac-manifest.edn") crow-manifest))

    (it "validates declared module comm slot fields with no error for valid value"
      (marigold/write-config!
                     {:modules {:isaac.comm.telly {:local/root "/marigold/.isaac/modules/isaac.comm.telly"}}
                      :comms {:bert {:type :telly :loft "rooftop" :mood "happy"}}})
      (write-telly-module!)
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= "rooftop" (get-in result [:config :comms :bert :loft]))
        (should= "happy" (get-in result [:config :comms :bert :mood]))))

    (it "generates a validation error for wrong type in a module comm slot field"
      (marigold/write-config!
                     {:modules {:isaac.comm.telly {:local/root "/marigold/.isaac/modules/isaac.comm.telly"}}
                      :comms {:bert {:type :telly :loft 42}}})
      (write-telly-module!)
      (let [result (marigold/load-config)]
        (should (some #(and (= "comms.bert.loft" (:key %))
                            (= "must be a string" (:value %)))
                      (:errors result)))))

    (it "requires a manifest field guarded by [:present-when? :type :telly]"
      (marigold/write-config!
                     {:modules {:isaac.comm.telly {:local/root "/marigold/.isaac/modules/isaac.comm.telly"}}
                      :comms   {:bert {:type :telly}}})
      (write-telly-module!)
      (let [result (marigold/load-config)]
        (should (some #(and (= "comms.bert.loft" (:key %))
                            (re-find #"is required when type is telly" (:value %)))
                      (:errors result)))))

    (it "infers the comm type from the slot-id when :type is omitted"
      (marigold/write-config!
                     {:modules {:isaac.comm.telly {:local/root "/marigold/.isaac/modules/isaac.comm.telly"}}
                      :comms   {:telly {:loft 42}}})
      (write-telly-module!)
      (let [result (marigold/load-config)]
        (should (some #(and (= "comms.telly.loft" (:key %))
                            (= "must be a string" (:value %)))
                      (:errors result)))))

    (it "does not warn 'unknown key' on a base comm-instance field"
      (marigold/write-config!
                     {:modules {:isaac.comm.telly {:local/root "/marigold/.isaac/modules/isaac.comm.telly"}}
                      :crew    {:tempest {}}
                      :comms   {:bert {:type :telly :crew "tempest" :loft "rooftop"}}})
      (write-telly-module!)
      (let [result (marigold/load-config)]
        (should-not (some #(= "comms.bert.crew" (:key %))
                          (:warnings result)))))

    (it "resolves :crew-exists? refs inside manifest-supplied schemas"
      ;; Manifest validation must bind *config* so refs see the known-crew set.
      (let [crew-aware (pr-str {:id      :isaac.comm.telly
                                :version "0.1.0"
                                :isaac.server/comm    {:telly {:factory 'isaac.comm.telly/make
                                                  :schema  {:override-crew {:type :string
                                                                            :validations [[:crew-exists?]]}}}}})]
        (fs/mkdirs (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.telly"))
        (fs/spit (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.telly/deps.edn")
                 "{:paths [\"resources\"]}")
        (fs/spit (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.comm.telly/resources/isaac-manifest.edn") crew-aware))
      (marigold/write-config!
                     {:modules {:isaac.comm.telly {:local/root "/marigold/.isaac/modules/isaac.comm.telly"}}
                      :crew    {:tempest {}}
                      :comms   {:bert {:type :telly :crew "tempest" :override-crew "tempest"}}})
      (let [result (marigold/load-config)]
        (should-not (some #(and (= "comms.bert.override-crew" (:key %))
                                (re-find #"undefined crew" (:value %)))
                          (:errors result)))))

    (it "rejects a manifest enum value outside [:one-of? ...]"
      (marigold/write-config!
                     {:modules {:isaac.comm.telly {:local/root "/marigold/.isaac/modules/isaac.comm.telly"}}
                      :comms   {:bert {:type :telly :loft "rooftop" :mood "elated"}}})
      (write-telly-module!)
      (let [result (marigold/load-config)]
        (should (some #(and (= "comms.bert.mood" (:key %))
                            (re-find #"must be one of" (:value %)))
                      (:errors result)))))

    (it "accepts a manifest enum value inside [:one-of? ...]"
      (marigold/write-config!
                     {:modules {:isaac.comm.telly {:local/root "/marigold/.isaac/modules/isaac.comm.telly"}}
                      :comms   {:bert {:type :telly :loft "rooftop" :mood "happy"}}})
      (write-telly-module!)
      (let [result (marigold/load-config)]
        (should= [] (:errors result))
        (should= "happy" (get-in result [:config :comms :bert :mood]))))

    (it "fails fast when a manifest schema references an unregistered ref"
      (fs/mkdirs (nexus/get :fs) "/marigold/.isaac/modules/isaac.comm.broken")
      (fs/spit   (nexus/get :fs) "/marigold/.isaac/modules/isaac.comm.broken/deps.edn"
                  "{:paths [\"resources\"]}")
      (fs/spit   (nexus/get :fs) "/marigold/.isaac/modules/isaac.comm.broken/resources/isaac-manifest.edn"
               (pr-str {:id      :isaac.comm.broken
                        :version "0.1.0"
                        :isaac.server/comm    {:broken {:factory 'isaac.comm.broken/make
                                           :schema  {:thing {:type :string
                                                             :validations [:no-such-ref?]}}}}}))
      (marigold/write-config!
                     {:modules {:isaac.comm.broken {:local/root "/marigold/.isaac/modules/isaac.comm.broken"}}})
      (let [result (marigold/load-config)]
        (should (some #(and (= "modules.isaac.comm.broken" (:key %))
                            (= "unregistered ref :no-such-ref?" (:value %)))
                      (:errors result)))))

    (it "generates unknown-key warnings for comm slot fields when module is not declared"
      (marigold/write-config!
                     {:comms {:bert {:type :telly :loft "rooftop"}}})
      (let [result (marigold/load-config)]
        (should (some #(and (= "comms.bert.loft" (:key %))
                            (= "unknown key" (:value %)))
                      (:warnings result)))))

    (it "does not warn for a module-declared comm slot when its module is declared"
      (marigold/write-config!
                     {:modules {:isaac.comm.crow {:local/root "/marigold/.isaac/modules/isaac.comm.crow"}}
                       :comms   {:mychan {:type :crow :token "abc"}}})
      (write-crow-module!)
      (let [result (marigold/load-config)]
        (should-not (some #(str/includes? (:key %) "comms.mychan") (:warnings result))))))

  (describe "slash command schema validation"

    (marigold/aboard)

    (def echo-manifest
      (pr-str {:id      :isaac.slash.echo
               :version "0.1.0"
               :isaac.server/slash-commands
               {:echo {:factory 'isaac.slash.echo/echo-command
                       :schema  {:command-name {:type :string
                                                :coercions [[:default "echo"]]}}}}}))

    (defn- write-echo-module! []
      (fs/mkdirs (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.slash.echo"))
      (fs/spit (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.slash.echo/deps.edn")
               "{:paths [\"resources\"]}")
      (fs/spit (nexus/get :fs) (str marigold/home "/.isaac/modules/isaac.slash.echo/resources/isaac-manifest.edn") echo-manifest))

    (it "rejects a slash-command field that violates the manifest :schema"
      (marigold/write-config!
        {:modules        {:isaac.slash.echo {:local/root "/marigold/.isaac/modules/isaac.slash.echo"}}
         :slash-commands {:echo {:command-name 42}}})
      (write-echo-module!)
      (let [result (marigold/load-config)]
        (should (some #(and (= "slash-commands.echo.command-name" (:key %))
                            (= "must be a string" (:value %)))
                      (:errors result))))))

  (describe "server-config"

    (it "returns default port 6674 and host 0.0.0.0 when no config is provided"
      (let [result (sut/server-config {})]
        (should= 6674 (:port result))
        (should= "127.0.0.1" (:host result))
        (should= true (:hot-reload result))))

    (it "allows server.hot-reload to disable config watching"
      (should= false (:hot-reload (sut/server-config {:server {:hot-reload false}}))))

    (it "aliases gateway.port to server.port"
      (should= 9000 (:port (sut/server-config {:gateway {:port 9000}})))))

  (describe "snapshot"

    (around [it]
      (with-config-slot it))

    (after (sut/set-snapshot! nil "spec"))

    (it "returns nil before any snapshot is set"
      (sut/set-snapshot! nil "spec")
      (should-be-nil (sut/snapshot "spec")))

    (it "returns the config after set-snapshot!"
      (sut/set-snapshot! {:crew {"main" {:soul "You are helpful."}}} "spec")
      (should= {:crew {"main" {:soul "You are helpful."}}} (sut/snapshot "spec")))

    (it "returns the latest value after multiple set-snapshot! calls"
      (sut/set-snapshot! {:first true} "spec")
      (sut/set-snapshot! {:second true} "spec")
      (should= {:second true} (sut/snapshot "spec")))

    (it "writes through the system config atom"
      (let [cfg* (atom nil)]
        (nexus/-with-nexus {:config cfg*}
          (sut/set-snapshot! {:crew {"main" {:soul "Hi"}}} "spec")
          (should= {:crew {"main" {:soul "Hi"}}} @cfg*)))))

  (describe "load-config!"

    (around [it]
      (with-config-slot it))

    (after (sut/set-snapshot! nil "spec"))

    (it "loads, commits, and returns the config"
      (with-redefs [sut/load-config-result (fn [_] {:config {:crew {"main" {}}} :errors []})]
        (should= {:crew {"main" {}}} (sut/load-config! "/sd" (fs/mem-fs) "spec"))
        (should= {:crew {"main" {}}} (sut/snapshot "spec"))))

    (it "throws carrying ALL validation errors when the config is invalid, and does not commit"
      (with-redefs [sut/load-config-result (fn [_] {:config {} :errors [{:key "a" :value "bad"}
                                                                        {:key "b" :value "worse"}]})]
        (let [ex (try (sut/load-config! "/sd" (fs/mem-fs) "spec") nil
                      (catch clojure.lang.ExceptionInfo e e))]
          (should-not-be-nil ex)
          (should= 2 (count (:errors (ex-data ex))))
          (should-be-nil (sut/snapshot "spec")))))

    (it "commits the empty default for a missing config without throwing"
      (with-redefs [sut/load-config-result (fn [_] {:config {:root "/sd"}
                                                    :errors [{:key "config" :value "missing"}]
                                                    :missing-config? true})]
        (should= {:root "/sd"} (sut/load-config! "/sd" (fs/mem-fs) "spec")))))
