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
   :comm        {:pigeon {:factory 'isaac.comm.pigeon/make
                           :schema  {:loft      {:type :string :validations [:present?]}
                                     :max-bytes {:type :int :coercions [[:default 140]]}}}}})

(def api-manifest
  {:id      :isaac.api.tin-can
   :version "0.1.0"
   :llm/api {:tin-can {:factory 'isaac.api.tin-can/make}}})

(def slash-echo-manifest
  {:id             :isaac.slash.echo
   :version        "0.1.0"
   :slash-commands {:echo {:factory 'isaac.slash.echo/echo-command
                           :schema  {:command-name {:type :string}}}}})

(def tool-manifest
  {:id      :isaac.tool.doodad
   :version "0.1.0"
   :tools   {:doodad {:factory 'isaac.tool.doodad/doodad-tool
                      :schema  {:api-key {:type :string}}}}})

(def provider-only-manifest
  {:id       :isaac.providers.kombucha
   :version  "0.1.0"
   :provider {:kombucha {:template {:api "chat-completions"}}}})

(def route-manifest
  {:id      :isaac.routes.bibelot
   :version "0.1.0"
   :route   {[:get "/acp"]      'isaac.comm.acp.websocket/handler
             [:post "/hooks/*"] 'isaac.hooks/handler}})

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

    (it "parses a v2 manifest with :comm and :factory"
      (spit (.getPath @tmp-file) (pr-str pigeon-manifest))
      (should= pigeon-manifest (sut/read-manifest (.getPath @tmp-file))))

    (it "parses a manifest that extends :llm/api"
      (spit (.getPath @tmp-file) (pr-str api-manifest))
      (should= api-manifest (sut/read-manifest (.getPath @tmp-file))))

    (it "parses a tools manifest with :factory and :schema"
      (spit (.getPath @tmp-file) (pr-str tool-manifest))
      (should= tool-manifest (sut/read-manifest (.getPath @tmp-file))))

    (it "parses a provider-only manifest without :bootstrap"
      (spit (.getPath @tmp-file) (pr-str provider-only-manifest))
      (should= provider-only-manifest (sut/read-manifest (.getPath @tmp-file))))

    (it "parses a manifest with declarative routes"
      (spit (.getPath @tmp-file) (pr-str route-manifest))
      (should= route-manifest (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects v1 manifests that use :extends"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0" :extends {:comm {:pigeon {:factory 'foo/make}}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects manifests with :requires (removed in v2)"
      (spit (.getPath @tmp-file) (pr-str (assoc pigeon-manifest :requires [])))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects manifests with :isaac/factory (v1 namespace)"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :comm {:pigeon {:isaac/factory 'foo/make}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects manifests with unknown top-level kind"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :mystery {:echo {:factory 'foo/bar}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects provider manifest entry missing :template"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :provider {:my-prov {:api "chat-completions"}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects comm manifest entry missing :factory"
      (spit (.getPath @tmp-file)
             (pr-str {:id :foo :version "1.0"
                      :comm {:my-comm {:schema {:token {:type :string}}}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects tool manifest entry missing :factory"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :tools {:doodad {:schema {:api-key {:type :string}}}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects slash-command manifest entry missing :factory"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :slash-commands {:echo {:schema {:command-name {:type :string}}}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects tool manifest entry with :sort-index"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :tools {:doodad {:factory 'isaac.tool.doodad/doodad-tool
                                      :sort-index 1}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects slash-command manifest entry with :sort-index"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :slash-commands {:echo {:factory 'isaac.slash.echo/echo-command
                                             :sort-index 1}}}))
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

    (it "rejects malformed route keys"
      (spit (.getPath @tmp-file) (pr-str (assoc route-manifest :route {[:get] 'isaac.hooks/handler})))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects route handlers that are not symbols"
      (spit (.getPath @tmp-file) (pr-str (assoc route-manifest :route {[:get "/acp"] {:handler 'isaac.hooks/handler}})))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "strips unknown scalar top-level keys and warns"
      (spit (.getPath @tmp-file) (pr-str (assoc pigeon-manifest :unknown-field "oops")))
      (let [result (log/capture-logs (sut/read-manifest (.getPath @tmp-file)))]
        (should-not (contains? result :unknown-field))
        (should (some #(= :manifest/unknown-key (:event %)) @log/captured-logs)))))

  (describe "verify-schema-refs on :comm :schema fragments"

    #_{:clj-kondo/ignore [:invalid-arity]}
    (around [it]
      (binding [schema/*ref-registry* (atom @schema/*ref-registry*)]
        (refs/install!)
        (it)))

    (it ":validations [:present?] roundtrips"
      (let [frag {:loft {:type :string :validations [:present?]}}]
        (should= true (schema/verify-schema-refs frag))))

    (it "factory ref [[:> 5]] roundtrips"
      (let [frag {:score {:type :int :validations [[:> 5]]}}]
        (should= true (schema/verify-schema-refs frag))))

    (it "unregistered ref fails verify-schema-refs"
      (let [frag {:foo {:type :string :validations [:does-not-exist?]}}]
        (should-throw Exception (schema/verify-schema-refs frag))))))
