(ns isaac.config.schema.term-spec
  (:require [c3kit.apron.schema :as schema]
            [clojure.string :as s]
            [isaac.config.schema :as config-schema]
            [isaac.config.schema.term :as sut]
            [speclj.core :refer [context describe it should should-contain should-not-contain should=]]))

(def ^:private plain {:color? false :width 80})
(def ^:private plain-no-paths {:color? false :paths? false :width 80})

(describe "schema.term"

  (context "plain (no color) output"

    (it "renders a leaf type using the apron type name verbatim"
      (should-contain "type  string" (sut/spec->term {:type :string} plain))
      (should-contain "type  float" (sut/spec->term {:type :float} plain))
      (should-contain "type  ref" (sut/spec->term {:type :ref} plain))
      (should-contain "type  long" (sut/spec->term {:type :long} plain))
      (should-contain "type  kw-ref" (sut/spec->term {:type :kw-ref} plain)))

    (it "leaf includes description and example when present"
      (let [out (sut/spec->term {:type :int :description "a count" :example 42} plain)]
        (should-contain "int" out)
        (should-contain "a count" out)
        (should-contain "example: 42" out)))

    (it "leaf prefixes the type with a label"
      (should-contain "type  string" (sut/spec->term {:type :string} plain)))

    (it "renders a map as a header with one line per field"
      (let [out (sut/spec->term
                  {:type :map :schema {:name {:type :string}
                                       :age  {:type :int}}}
                  plain)]
        (should-contain "Schema" out)
        (should-contain "age" out)
        (should-contain "int" out)
        (should-contain "name" out)
        (should-contain "string" out)))

    (it "sorts fields alphabetically"
      (let [out   (sut/spec->term
                    {:type :map :schema {:zeta {:type :string}
                                         :alpha {:type :string}}}
                    plain)
            alpha (s/index-of out "alpha")
            zeta  (s/index-of out "zeta")]
        (should (< alpha zeta))))

    (it "includes field description on its own line"
      (let [out (sut/spec->term
                  {:type :map :schema {:name {:type :string
                                              :description "User's name."}}}
                  plain)]
        (should-contain "User's name." out)))

    (it "uses :doc from config specs as the field description"
      (let [out (sut/spec->term {:type :map :schema config-schema/defaults-schema} plain)]
        (should-contain "Default crew member id" out)
        (should-contain "Default model alias" out)))

    (it "shows shell-safe field paths by default"
      (let [out (sut/spec->term config-schema/root-doc-spec plain)]
        (should-contain "defaults.crew" out)
        (should-contain "crew._.model" out)
        (should-contain "providers._.api-key" out)))

    (it "uses 0 for sequence item paths"
      (let [item {:type :map :name :item :schema {:name {:type :string}}}
            out  (sut/spec->term {:type :map :schema {:items {:type :seq :spec item}}} plain)]
        (should-contain "items.0.name" out)))

    (it ":paths? false suppresses field paths"
      (let [out (sut/spec->term config-schema/root-doc-spec plain-no-paths)]
        (should-not-contain "defaults.crew" out)
        (should-not-contain "crew._.model" out)
        (should-not-contain "providers._.api-key" out)))

    (it "marks required fields"
      (let [out (sut/spec->term
                  {:type :map :schema {:name {:type :string
                                              :validate schema/present?}}}
                  plain)]
        (should-contain "required" out)))

    (it "shows example on its own line"
      (let [out (sut/spec->term
                  {:type :map :schema {:age {:type :int :example 30}}}
                  plain)]
        (should-contain "example: 30" out)))

    (it "shows default on its own line when present"
      (let [out (sut/spec->term {:type :map :schema config-schema/defaults-schema} plain)]
        (should-contain "default: \"main\"" out)
        (should-contain "default: \"llama\"" out)))

    (it "shows named ref with an arrow"
      (let [pet {:type :map :name :pet :schema {:name {:type :string}}}
            out (sut/spec->term
                  {:type :map :schema {:pet pet}}
                  plain)]
        (should-contain "map → pet" out)))

    (it "emits a section for each named schema"
      (let [pet {:type :map :name :pet :schema {:name {:type :string}}}
            out (sut/spec->term
                  {:type :map :schema {:pet pet}}
                  plain)]
        (should-contain "Schema" out)
        (should-contain "pet" out)))

    (it "dedups when the same named schema is reached twice"
      (let [pet {:type :map :name :pet :schema {:name {:type :string}}}
            out (sut/spec->term
                  {:type :map :schema {:a pet :b pet}}
                  plain)
            pet-headings (re-seq #"(?m)^pet" out)]
        (should= 1 (count pet-headings))))

    (it "when the root spec is named, omits the generic Schema heading"
      (let [out (sut/spec->term
                  {:type :map :name :user :schema {:name {:type :string}}}
                  plain)]
        (should-contain "user" out)
        (should-not-contain "Schema" out)))

    (it "map with key-spec + value-spec renders 'map of K → V' in a parent cell"
      (let [spec {:type :map
                  :schema {:crew {:type :map
                                  :key-spec   {:type :keyword}
                                  :value-spec {:type :map :name :crew-entity
                                               :schema {:name {:type :string}}}}}}
            out  (sut/spec->term spec plain)]
        (should-contain "map of keyword → crew-entity" out)))

    (it "map with only value-spec renders 'map → V'"
      (let [spec {:type :map :schema {:counts {:type :map :value-spec {:type :int}}}}
            out  (sut/spec->term spec plain)]
        (should-contain "map → int" out)))

    (it "named value-spec (no :schema on parent) gets its own section"
      (let [entity {:type :map :name :crew-entity :schema {:name {:type :string}}}
            spec   {:type :map :value-spec entity}
            out    (sut/spec->term spec plain)]
        (should-contain "map → crew-entity" out)
        (should-contain "crew-entity" out)
        (should-contain "name" out)))

    (it ":deep? false suppresses named sub-schema sections"
      (let [pet  {:type :map :name :pet :schema {:species {:type :string}}}
            spec {:type :map :schema {:pet pet}}
            deep    (sut/spec->term spec (assoc plain :deep? true))
            shallow (sut/spec->term spec (assoc plain :deep? false))]
        (should-contain "pet" deep)
        (should-contain "species" deep)
        (should-contain "pet" shallow)
        (should-not-contain "species" shallow)))

    )

  (context "colored output"

    (it "emits ANSI escape codes when :color? is true"
      (let [out (sut/spec->term
                  {:type :map :schema {:name {:type :string}}}
                  {:color? true :width 80})]
        (should-contain "\033[" out)))

    (it "suppresses ANSI codes when :color? is false"
      (let [out (sut/spec->term
                  {:type :map :schema {:name {:type :string}}}
                  plain)]
        (should-not-contain "\033[" out)))

    )

  (context "section headings"

    (it "underlines the heading with a rule"
      (let [out (sut/spec->term
                  {:type :map :schema {:name {:type :string}}}
                  plain)]
        (should-contain "Schema\n──" out)))

    (it "named section also gets a rule"
      (let [pet {:type :map :name :pet :schema {:name {:type :string}}}
            out (sut/spec->term
                  {:type :map :schema {:pet pet}}
                  plain)]
        (should-contain "pet\n──" out)))

    ))
