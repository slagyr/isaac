(ns mdm.isaac.dev.migration
  (:require [mdm.isaac.server.main :as main]
            [c3kit.bucket.migration :as migration
             ]))

(defn -main [& args]
  (main/start-db)
  (apply migration/migrate args))
