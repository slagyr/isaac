(ns isaac.marigold
  "Test data for the spaceship Marigold and her crew. Use these defs and
   builders in place of real-name fixtures so tests read like scenes
   aboard the ship.

   Themed api strings (helm, sky, groves, anvil) are aliases for the
   real api factories. A spec opts in by calling (marigold/setup!)
   inside its describe — that wires a before-all that registers the
   aliases against the real factories.

   Example:

     (describe \"my spec\"
       (marigold/setup!)
       (it \"...\" ...))"
  (:require
    [clojure.string :as str]
    [isaac.llm.api :as api]
    ;; Require the impl namespaces so their factories are in the
    ;; registry before we alias them.
    [isaac.llm.api.messages]
    [isaac.llm.api.chat-completions]
    [isaac.llm.api.ollama]
    [isaac.llm.api.responses]
    [isaac.llm.api.grover]
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

;; ----- API wire protocols (themed aliases, real factories) ---------

;; Helm protocol — mainstream wire format. Aliases :messages.
(def helm-api "helm")
;; Sky protocol — OpenAI-style fan-in. Aliases :chat-completions.
(def sky-api "sky")
;; Groves protocol — local thinking. Aliases :ollama.
(def groves-api "groves")
;; Anvil protocol — OAuth-bound. Aliases :responses.
(def anvil-api "anvil")
;; Grover stub — canonical test stub. Pass-through to the real :grover.
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

(defn register!
  "Register Marigold's themed api aliases against the real api factories.
   Idempotent. Most specs should prefer (marigold/setup!) which wires
   this into a before-all hook automatically."
  []
  (api/register! (keyword helm-api)   (api/factory-for :messages))
  (api/register! (keyword sky-api)    (api/factory-for :chat-completions))
  (api/register! (keyword groves-api) (api/factory-for :ollama))
  (api/register! (keyword anvil-api)  (api/factory-for :responses)))

(defmacro setup!
  "Inside a `(describe ...)` block, registers a before-all hook that
   wires Marigold's themed api aliases. Place once near the top of
   the describe."
  []
  `(speclj/before-all (register!)))

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
