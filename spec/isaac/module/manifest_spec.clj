(ns isaac.module.manifest-spec
  (:require
    [c3kit.apron.schema :as schema]
    [isaac.fs :as fs]
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
   :route   {[:get "/status"]   'isaac.server.status/handle
             [:post "/hooks/*"] 'isaac.hooks/handler}})

(def cli-manifest
  {:id      :isaac.cli.greeter
   :version "0.1.0"
   :cli     {:greet {:factory     'isaac.cli.greeter/make-command
                     :description "Print a greeting"}}})

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
      (should= pigeon-manifest (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "parses a manifest that extends :llm/api"
      (spit (.getPath @tmp-file) (pr-str api-manifest))
      (should= api-manifest (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "parses a tools manifest with :factory and :schema"
      (spit (.getPath @tmp-file) (pr-str tool-manifest))
      (should= tool-manifest (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "parses a provider-only manifest without :bootstrap"
      (spit (.getPath @tmp-file) (pr-str provider-only-manifest))
      (should= provider-only-manifest (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "parses a manifest with declarative routes"
      (spit (.getPath @tmp-file) (pr-str route-manifest))
      (should= route-manifest (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "parses a manifest with :cli extensions"
      (spit (.getPath @tmp-file) (pr-str cli-manifest))
      (should= cli-manifest (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "reads string paths from an explicit fs"
      (let [mem  (fs/mem-fs)
            path "/tmp/manifest.edn"]
        (fs/spit mem path (pr-str pigeon-manifest))
        (should= pigeon-manifest (sut/read-manifest path mem))))

    (it "rejects cli manifest entry missing :factory"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :cli {:greet {:description "no factory here"}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects v1 manifests that use :extends"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0" :extends {:comm {:pigeon {:factory 'foo/make}}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects manifests with :requires (removed in v2)"
      (spit (.getPath @tmp-file) (pr-str (assoc pigeon-manifest :requires [])))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects manifests with :isaac/factory (v1 namespace)"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :comm {:pigeon {:isaac/factory 'foo/make}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects manifests with unknown top-level kind"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :mystery {:echo {:factory 'foo/bar}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects provider manifest entry missing :template"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :provider {:my-prov {:api "chat-completions"}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects comm manifest entry missing :factory"
      (spit (.getPath @tmp-file)
             (pr-str {:id :foo :version "1.0"
                      :comm {:my-comm {:schema {:token {:type :string}}}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects tool manifest entry missing :factory"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :tools {:doodad {:schema {:api-key {:type :string}}}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects slash-command manifest entry missing :factory"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :slash-commands {:echo {:schema {:command-name {:type :string}}}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects tool manifest entry with :sort-index"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :tools {:doodad {:factory 'isaac.tool.doodad/doodad-tool
                                      :sort-index 1}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects slash-command manifest entry with :sort-index"
      (spit (.getPath @tmp-file)
            (pr-str {:id :foo :version "1.0"
                     :slash-commands {:echo {:factory 'isaac.slash.echo/echo-command
                                             :sort-index 1}}}))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects missing :id with a clear error"
      (spit (.getPath @tmp-file) (pr-str (dissoc pigeon-manifest :id)))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects missing :version with a clear error"
      (spit (.getPath @tmp-file) (pr-str (dissoc pigeon-manifest :version)))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects legacy :entry"
      (spit (.getPath @tmp-file) (pr-str (assoc pigeon-manifest :entry 'isaac.comm.pigeon)))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects malformed route keys"
      (spit (.getPath @tmp-file) (pr-str (assoc route-manifest :route {[:get] 'isaac.hooks/handler})))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "rejects route handlers that are not symbols"
      (spit (.getPath @tmp-file) (pr-str (assoc route-manifest :route {[:get "/acp"] {:handler 'isaac.hooks/handler}})))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file) (fs/real-fs))))

    (it "strips unknown scalar top-level keys and warns"
      (spit (.getPath @tmp-file) (pr-str (assoc pigeon-manifest :unknown-field "oops")))
      (let [result (log/capture-logs (sut/read-manifest (.getPath @tmp-file) (fs/real-fs)))]
        (should-not (contains? result :unknown-field))
        (should (some #(= :manifest/unknown-key (:event %)) @log/captured-logs)))))

  (describe "verify-schema-lexes on :comm :schema fragments"

    #_{:clj-kondo/ignore [:invalid-arity]}
    (around [example]
      (binding [schema/*lexicon* schema/default-lexicon]
        (example)))

    (it ":validations [:present?] roundtrips"
      (let [frag {:loft {:type :string :validations [:present?]}}]
        (should= true (schema/verify-schema-lexes frag))))

    (it "factory ref [[:> 5]] roundtrips"
      (let [frag {:score {:type :int :validations [[:> 5]]}}]
        (should= true (schema/verify-schema-lexes frag))))

    (it "unregistered ref fails verify-schema-lexes"
      (let [frag {:foo {:type :string :validations [:does-not-exist?]}}]
        (should-throw Exception (schema/verify-schema-lexes frag))))))
