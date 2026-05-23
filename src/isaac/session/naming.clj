(ns isaac.session.naming
  (:require
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.naming :as naming]
    [isaac.system :as system]))


(defn- runtime-fs [state]
  (or (:fs state)
      (system/get :fs)
      (throw (ex-info "session.naming requires :fs" {}))))

(defn- state-dir->home [state-dir]
  (if (= ".isaac" (fs/filename state-dir))
    (fs/parent state-dir)
    state-dir))

(defn- name->id
  "Convert a display name to a session ID slug, matching the store's key format."
  [s]
  (let [slug (-> (or s "")
                 str/lower-case
                 (str/replace #"[^a-z0-9]+" "-")
                 (str/replace #"^-+|-+$" ""))]
    (if (str/blank? slug) "session" slug)))

(defrecord SessionDomain [store]
  naming/NamedDomain
  (name-taken? [_ name]
    (contains? store (name->id name))))

(defn generate
  "Generate a session name. Checks the system for a registered strategy first;
   falls back to constructing one from config for test scenarios."
  [strategy-kw {:keys [state-dir store] :as state}]
  (if-let [strat (get-in (system/current) [:sessions :naming-strategy])]
    (naming/generate strat)
    (case strategy-kw
      :sequential
      (naming/generate (naming/->SequentialStrategy state-dir "sessions" "session-" (runtime-fs state)))
      (naming/generate (naming/->AdjectiveNounStrategy (->SessionDomain store) naming/adjectives naming/nouns)))))

(defn strategy
  [state-dir fs*]
  (let [value (get-in (config/load-config {:home (state-dir->home state-dir) :fs fs*}) [:sessions :naming-strategy])]
    (cond
      (keyword? value) value
      (string? value)  (keyword value)
      :else            :adjective-noun)))
