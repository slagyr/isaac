(ns isaac.marigold
  "Test data for the spaceship Marigold and her crew. Use these defs and
   builders in place of real-name fixtures so tests read like scenes
   aboard the ship.

   Themed api strings (helm, sky, groves, anvil) all route to the
   grover test stub — tests do not make real HTTP calls. Use real api
   names (messages, chat-completions, etc.) only when a test needs to
   exercise wire-format-specific behavior.

   A spec opts in by calling (marigold/with-apis) inside its describe;
   that wires a before-all that registers the themed api aliases.

   Example:

     (describe \"my spec\"
       (marigold/with-apis)
       (it \"...\" ...))"
  (:require
    [c3kit.apron.env :as c3env]
    [clojure.string :as str]
    [isaac.config.loader :as config-loader]
    [isaac.fs :as fs]
    [isaac.llm.api :as api]
    ;; Grover is the only impl namespace we need — all themed apis
    ;; route to its factory.
    [isaac.llm.api.grover]
    [isaac.module.loader :as module-loader]
    [speclj.core :as speclj]))

;; ----- Crew --------------------------------------------------------

;; Captain Atticus — sets direction, decides. Default :main in baselines.
(def captain "atticus")
;; First Mate Cordelia — second-in-command; for multi-crew scenarios.
(def first-mate "cordelia")
;; Chief Engineer Bartholomew — fixer; use when tests need a tools-heavy crew.
(def engineer "bartholomew")
;; Navigator Mavis — router; use when tests need a slash-command-heavy crew.
(def navigator "mavis")
;; Botanist Hieronymus — the turtle. Tends lettuce. Canonical in hooks.feature.
(def botanist "hieronymus")
;; Apprentice Periwinkle — junior crew; minimal-config / newcomer scenarios.
(def apprentice "periwinkle")
;; Cook Wormwood — background extra; a third crew that doesn't carry plot.
(def cook "wormwood")
;; The Loom — ship's AI. Opt-in test character.
(def ship-ai "the-loom")

;; ----- API wire protocols (themed aliases, all route to grover) ----

;; Helm protocol — flavor name for "Helm Systems' wire format." Stub via grover.
(def helm-api "helm")
;; Sky protocol — flavor name for "Starcore's wire format." Stub via grover.
(def sky-api "sky")
;; Groves protocol — flavor name for "Flicker Labs' wire format." Stub via grover.
(def groves-api "groves")
;; Anvil protocol — flavor name for "Quantum Anvil's wire format." Stub via grover.
(def anvil-api "anvil")
;; Grover — canonical test stub, available directly under its real name.
(def grover-api "grover")

;; ----- Provider corporations ---------------------------------------

;; Helm Systems — mainstream, reliable. The Captain's workhorse provider.
(def helm-systems "helm-systems")
;; Starcore — premium / expensive thinking.
(def starcore "starcore")
;; Flicker Labs — experimental / open-weights vibe.
(def flicker-labs "flicker-labs")
;; Quantum Anvil — heavy-reasoning, OAuth-bound.
(def quantum-anvil "quantum-anvil")
;; Grover stub — the test stand-in.
(def grover-stub "grover-stub")

;; ----- Model designations ------------------------------------------

(def helm-mark-i     "helm-mark-i")      ;; everyday workhorse
(def helm-mark-iii   "helm-mark-iii")    ;; flagship
(def helm-spark      "helm-spark")       ;; fast/cheap
(def starcore-7      "starcore-7")       ;; premium flagship
(def starcore-7-mini "starcore-7-mini")  ;; premium small
(def flicker-13b     "flicker-13b")      ;; open weights
(def anvil-x         "anvil-x")          ;; reasoning-heavy

;; ----- Comm channels (themed names for tests) ----------------------

(def longwave "longwave")   ;; broadcast — Discord analog
(def skybeam  "skybeam")    ;; direct/streaming — ACP analog
(def logbook  "logbook")    ;; persisted-local — memory comm analog

;; ----- Hooks (inbound webhooks the crew receives) ------------------

(def lettuce-hook    "lettuce")     ;; Hieronymus's garden status. Existing.
(def heartbeat-hook  "heartbeat")   ;; Crew health reports.
(def trajectory-hook "trajectory")  ;; Navigation updates.
(def dispatch-hook   "dispatch")    ;; External mission orders.

;; ----- API alias registration --------------------------------------

(defn register-apis!
  "Register Marigold's themed api aliases. All themed apis route to
   the grover test stub. Idempotent. Most specs should prefer
   (marigold/with-apis) which wires this into a before-all hook."
  []
  (let [grover-factory (api/factory-for :grover)]
    (api/register! (keyword helm-api)   grover-factory)
    (api/register! (keyword sky-api)    grover-factory)
    (api/register! (keyword groves-api) grover-factory)
    (api/register! (keyword anvil-api)  grover-factory)))

(defn with-apis
  "Inside a `(describe ...)` block, registers a before-all hook that
   wires Marigold's themed api aliases. Place once near the top of
   the describe."
  []
  (speclj/before-all (register-apis!)))

;; ----- Provider templates ------------------------------------------

(def helm-provider
  {:api helm-api :base-url "https://api.helm-systems.test" :auth "api-key"})

(def starcore-provider
  {:api sky-api :base-url "https://api.starcore.test/v1" :auth "api-key"})

(def flicker-provider
  {:api groves-api :base-url "http://localhost:11434" :auth "none"})

(def quantum-provider
  {:api anvil-api :base-url "https://anvil.quantum.test/codex" :auth "oauth-device"})

;; ----- Builders ----------------------------------------------------

(defn provider-cfg
  "Merge overrides into a provider template (e.g., add :api-key)."
  [base & {:as overrides}]
  (merge base overrides))

(defn crew-cfg
  "Build a crew config map with a sensible default :soul derived from the name."
  [name & {:as overrides}]
  (merge {:soul (str "You are " (str/capitalize name) ".")} overrides))

(defn model-cfg
  "Build a model config map."
  [provider model & {:as overrides}]
  (merge {:model model :provider provider} overrides))

;; ----- Baseline isaac.edn ------------------------------------------

(def baseline-config
  "A fully-valid baseline isaac.edn map. Tests start from this and
   merge in their own overrides."
  {:defaults  {:crew captain :model helm-mark-iii}
   :providers {(keyword helm-systems) (provider-cfg helm-provider :api-key "helm-test-key")}
   :models    {(keyword helm-mark-iii) (model-cfg (keyword helm-systems) "helm-mk-3-1.0")}
   :crew      {(keyword captain) (crew-cfg captain :model helm-mark-iii)}})

;; ----- Themed core manifest ----------------------------------------

(def baseline-manifest
  "A stand-in for src/isaac-manifest.edn that declares Marigold's themed
   apis, providers, and crew-facing extensions. Swap it in with
   (marigold/with-manifest) so tests assert against themed names instead
   of the real built-ins (anthropic, openai, etc.). All :factory symbols
   point at isaac.llm.api.grover/make for apis; tools/comms/slash-commands
   use placeholder symbols since the loader_spec tests don't invoke them."
  {:id      :isaac.core
   :version "0.1.0"

   :llm/api {(keyword helm-api)   {:factory 'isaac.llm.api.grover/make}
             (keyword sky-api)    {:factory 'isaac.llm.api.grover/make}
             (keyword groves-api) {:factory 'isaac.llm.api.grover/make}
             (keyword anvil-api)  {:factory 'isaac.llm.api.grover/make}
             (keyword grover-api) {:factory 'isaac.llm.api.grover/make}}

   :provider {(keyword helm-systems)   {:template (dissoc helm-provider :api-key)}
              (keyword starcore)       {:template (dissoc starcore-provider :api-key)}
              (keyword flicker-labs)   {:template flicker-provider}
              (keyword quantum-anvil)  {:template quantum-provider}
              (keyword grover-stub)    {:template {:api grover-api :auth "none"}}}})

(def ^:private baseline-core-index
  {:isaac.core {:coord {} :manifest baseline-manifest :path nil}})

(defn with-manifest
  "Inside a `(describe ...)` block, swaps the core manifest for Marigold's
   themed `baseline-manifest` for the duration of all examples in the
   describe. Tests then assert against themed provider/api names (e.g.
   `helm-systems`, `helm`) instead of `anthropic`, `messages`, etc."
  []
  (speclj/around-all [ctx]
    (binding [module-loader/*core-index-override* baseline-core-index]
      (ctx))))

;; ----- Aboard the Marigold -----------------------------------------
;;
;; The "aboard" pattern sets the scene: a fresh mem-fs, the themed
;; manifest bound, env-var caches cleared. Tests can then call the
;; write-X! helpers to add wrinkles and (load-config) to run the loader
;; against the resulting world.

(def home
  "Canonical test root path used by all aboard-style tests."
  "/marigold")

(defn- config-path [suffix]
  (str home "/.isaac/config/" suffix))

(defn aboard
  "Inside a `(describe ...)` block, sets the scene aboard the Marigold:
   per-example mem-fs, themed manifest bound, c3env + loader caches
   cleared. Tests write entity files with the write-X! helpers and load
   via (marigold/load-config)."
  []
  (speclj/around [example]
    (binding [fs/*fs*                          (fs/mem-fs)
              module-loader/*core-index-override* baseline-core-index]
      (reset! c3env/-overrides {})
      (config-loader/clear-env-overrides!)
      (config-loader/clear-load-cache!)
      (example))))

(defn load-config
  "Load the configuration from the Marigold's home. Optional opts merge
   into the loader call (e.g. {:raw-parse-errors? true})."
  ([] (load-config nil))
  ([opts]
   (config-loader/load-config-result (merge {:home home} opts))))

(defn write-config!
  "Write isaac.edn at the Marigold home, replacing any prior contents."
  [data]
  (fs/spit (config-path "isaac.edn") (pr-str data)))

(defn write-baseline!
  "Write the baseline-config as isaac.edn — Marigold's standard wiring,
   ready for tests to perturb."
  []
  (write-config! baseline-config))

(defn write-provider!
  "Write a per-provider entity file. `provider-id` may be a keyword or
   string. `cfg` is the provider config map (use provider-cfg + a
   marigold provider template to build it)."
  [provider-id cfg]
  (fs/spit (config-path (str "providers/" (name provider-id) ".edn"))
           (pr-str cfg)))

(defn write-crew!
  "Write a per-crew entity file. Pass :soul to also write the companion
   markdown soul file."
  [crew-id cfg & {:keys [soul]}]
  (fs/spit (config-path (str "crew/" (name crew-id) ".edn")) (pr-str cfg))
  (when soul
    (fs/spit (config-path (str "crew/" (name crew-id) ".md")) soul)))

(defn write-crew-md!
  "Write a single-file crew markdown (frontmatter + soul body) or a
   companion-only markdown for a crew id."
  [crew-id body]
  (fs/spit (config-path (str "crew/" (name crew-id) ".md")) body))

(defn write-model!
  "Write a per-model entity file."
  [model-id cfg]
  (fs/spit (config-path (str "models/" (name model-id) ".edn")) (pr-str cfg)))

(defn write-cron!
  "Write a per-cron entity file. Pass :prompt to also write the
   companion markdown prompt file."
  [cron-id cfg & {:keys [prompt]}]
  (fs/spit (config-path (str "cron/" (name cron-id) ".edn")) (pr-str cfg))
  (when prompt
    (fs/spit (config-path (str "cron/" (name cron-id) ".md")) prompt)))

(defn write-cron-md!
  "Write a single-file cron markdown (or companion-only markdown)."
  [cron-id body]
  (fs/spit (config-path (str "cron/" (name cron-id) ".md")) body))

(defn write-hook!
  "Write a per-hook entity file."
  [hook-id cfg]
  (fs/spit (config-path (str "hooks/" (name hook-id) ".edn")) (pr-str cfg)))

(defn write-hook-md!
  "Write a single-file hook markdown (frontmatter + template body)."
  [hook-id body]
  (fs/spit (config-path (str "hooks/" (name hook-id) ".md")) body))

(defn write-env-file!
  "Write the .isaac/.env file at the Marigold home."
  [content]
  (fs/spit (str home "/.isaac/.env") content))

(defn write-raw!
  "Write arbitrary text at a path relative to .isaac/config/. Used by
   low-level tests that need to scribble malformed bytes (EDN syntax
   errors, etc.)."
  [relative content]
  (fs/spit (config-path relative) content))
