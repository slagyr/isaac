(ns isaac.spec-helper
  (:require [isaac.logger :as log]
            [speclj.core :refer :all]))

(defn with-captured-logs []
  (around [it] (log/capture-logs (it))))

