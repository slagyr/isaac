(ns mdm.isaac.spec-helper
  (:require [mdm.isaac.config :as config]
            [speclj.core]))

(defmacro with-config
  "Temporarily merges config-overrides into config/active for the duration of the test context.
   Usage: (with-config {:embedding {:impl :mock}})"
  [config-overrides]
  `(speclj.core/redefs-around [config/active (merge config/active ~config-overrides)]))
