(ns mdm.isaac.spec-helper
  (:require [c3kit.apron.schema :as schema]
            [c3kit.bucket.api :as db]
            [mdm.isaac.config :as config]
            [mdm.isaac.schema.full :as full]
            [speclj.core]))

(defmacro with-config
  "Temporarily merges config-overrides into config/active for the duration of the test context.
   Usage: (with-config {:embedding {:impl :mock}})"
  [config-overrides]
  `(speclj.core/redefs-around [config/active (merge config/active ~config-overrides)]))

(defn bucket-config
  "Creates a bucket config for the given database name."
  [db-name]
  {:impl        :jdbc
   :dialect     :postgres
   :host        "localhost"
   :port        5432
   :dbtype      "postgresql"
   :dbname      db-name})

(defn create-bucket
  "Creates a bucket db instance for the given database name."
  [db-name]
  (let [schemas (map schema/normalize-schema full/full-schema)]
    (db/create-db (bucket-config db-name) schemas)))
