(ns isaac.module.manifest-spec
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.schema.refs :as refs]
    [isaac.logger :as log]
    [isaac.module.manifest :as sut]
    [speclj.core :refer :all])
  (:import (java.io File)))

(def pigeon-manifest
  {:id          :isaac.comm/pigeon
   :version     "0.1.0"
   :bootstrap   'isaac.comm.pigeon/bootstrap
   :description "Carrier pigeon comm"
   :requires    []
   :extends     {:comm {:pigeon {:isaac/factory 'isaac.comm.pigeon/make
                                 :loft           {:type :string :validations [:present?]}
                                 :max-bytes      {:type :int :coercions [[:default 140]]}}}}})

(def api-manifest
  {:id       :isaac.api.tin-can
   :version  "0.1.0"
   :requires []
   :extends  {:llm/api {:tin-can {:isaac/factory 'isaac.api.tin-can/make}}}})

(def slash-echo-manifest
  {:id       :isaac.slash.echo
   :version  "0.1.0"
   :requires []
   :extends  {:slash-command {:echo {:isaac/factory 'isaac.slash.echo/handle-echo
                                     :description    "Echo the input back unchanged"
                                     :command-name   {:type :string}}}}})

(def provider-only-manifest
  {:id       :isaac.providers.kombucha
   :version  "0.1.0"
   :requires []
   :extends  {:provider {:kombucha {:api "openai-completions"}}}})

(describe "module manifest"

  (describe "manifest-schema"

    (it "is a named :map spec"
      (should= :map (:type sut/manifest-schema))
      (should= :module/manifest (:name sut/manifest-schema))
      (should (map? (:schema sut/manifest-schema)))))

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (describe "read-manifest"

    (with tmp-file (File/createTempFile "manifest" ".edn"))
    (after (.delete @tmp-file))

    (it "parses a manifest with :bootstrap and :isaac/factory"
      (spit (.getPath @tmp-file) (pr-str pigeon-manifest))
      (should= pigeon-manifest (sut/read-manifest (.getPath @tmp-file))))

    (it "parses a manifest that extends :llm/api"
      (spit (.getPath @tmp-file) (pr-str api-manifest))
      (should= api-manifest (sut/read-manifest (.getPath @tmp-file))))

    (it "parses a provider-only manifest without :bootstrap"
      (spit (.getPath @tmp-file) (pr-str provider-only-manifest))
      (should= provider-only-manifest (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects unknown extends kinds"
      (spit (.getPath @tmp-file)
            (pr-str (assoc pigeon-manifest :extends {:mystery {:echo {}}})))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects missing :id with a clear error"
      (spit (.getPath @tmp-file) (pr-str (dissoc pigeon-manifest :id)))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects missing :version with a clear error"
      (spit (.getPath @tmp-file) (pr-str (dissoc pigeon-manifest :version)))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects legacy :entry"
      (spit (.getPath @tmp-file) (pr-str (assoc pigeon-manifest :entry 'isaac.comm.pigeon)))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects legacy :api extends kind"
      (spit (.getPath @tmp-file) (pr-str {:id :legacy
                                          :version "0.1.0"
                                          :extends {:api {:tin-can {:isaac/factory 'isaac.api.tin-can/make}}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "strips unknown top-level keys and warns"
      (spit (.getPath @tmp-file) (pr-str (assoc pigeon-manifest :unknown-field "oops")))
      (let [result (log/capture-logs (sut/read-manifest (.getPath @tmp-file)))]
        (should-not (contains? result :unknown-field))
        (should (some #(= :manifest/unknown-key (:event %)) @log/captured-logs)))))

  (describe "verify-schema-refs on :extends fragments"

    ;; Scope registry mutations to this describe so sibling specs (e.g. the
    ;; existence refs registered by isaac.config.loader on namespace load)
    ;; aren't wiped by our reset.
    (around [example] (binding [schema/*ref-registry* (atom @schema/*ref-registry*)]
                        (refs/install!)
                        (example)))

    (it ":validations [:present?] roundtrips"
      (let [frag {:loft {:type :string :validations [:present?]}}]
        (should= true (schema/verify-schema-refs frag))))

    (it "factory ref [[:> 5]] roundtrips"
      (let [frag {:score {:type :int :validations [[:> 5]]}}]
        (should= true (schema/verify-schema-refs frag))))

    (it "unregistered ref fails verify-schema-refs"
      (let [frag {:foo {:type :string :validations [:does-not-exist?]}}]
        (should-throw Exception (schema/verify-schema-refs frag))))))
