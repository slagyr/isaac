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
   :description "Carrier pigeon comm — message delivery via trained avian"
   :entry       'isaac.comm.pigeon
   :requires    []
   :extends     {:comm {:pigeon {:loft      {:type :string :validations [:present?]}
                                 :max-bytes {:type :int    :coercions [[:default 140]]}}}}})

(describe "module manifest"

  (describe "manifest-schema"

    (it "is a named :map spec"
      (should= :map (:type sut/manifest-schema))
      (should= :module/manifest (:name sut/manifest-schema))
      (should (map? (:schema sut/manifest-schema)))))

  (describe "read-manifest"

    (with tmp-file (File/createTempFile "manifest" ".edn"))
    (after (.delete @tmp-file))

    (it "parses a valid manifest"
      (spit (.getPath @tmp-file) (pr-str pigeon-manifest))
      (should= pigeon-manifest (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects missing :id with a clear error"
      (spit (.getPath @tmp-file) (pr-str (dissoc pigeon-manifest :id)))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "rejects missing :entry with a clear error"
      (spit (.getPath @tmp-file) (pr-str (dissoc pigeon-manifest :entry)))
      (should-throw Exception (sut/read-manifest (.getPath @tmp-file))))

    (it "strips unknown top-level keys and warns"
      (spit (.getPath @tmp-file) (pr-str (assoc pigeon-manifest :unknown-field "oops")))
      (let [result (log/capture-logs (sut/read-manifest (.getPath @tmp-file)))]
        (should-not (contains? result :unknown-field))
        (should (some #(= :manifest/unknown-key (:event %)) @log/captured-logs)))))

  (describe "verify-schema-refs on :extends fragments"

    (before (refs/install!))
    (after (schema/reset-ref-registry!))

    (it ":validations [:present?] roundtrips"
      (let [frag {:loft {:type :string :validations [:present?]}}]
        (should= true (schema/verify-schema-refs frag))))

    (it "factory ref [[:> 5]] roundtrips"
      (let [frag {:score {:type :int :validations [[:> 5]]}}]
        (should= true (schema/verify-schema-refs frag))))

    (it "unregistered ref fails verify-schema-refs"
      (let [frag {:foo {:type :string :validations [:does-not-exist?]}}]
        (should-throw Exception (schema/verify-schema-refs frag))))))
