(ns isaac.cron.cron-spec
  (:require
    [isaac.cron.cron :as sut]
    [speclj.core :refer :all])
  (:import
    (java.time ZoneId ZonedDateTime)
    (java.time.format DateTimeFormatter)))

(def ^:private offset-formatter
  (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ssZ"))

(defn- zdt [s]
  (ZonedDateTime/parse s offset-formatter))

(describe "cron"

  (it "finds the next fire for a daily schedule"
    (let [zone (ZoneId/of "America/Chicago")]
      (should= "2026-04-21T09:00:00-0500"
               (.format offset-formatter
                        (sut/next-fire-at "0 9 * * *"
                                          (zdt "2026-04-20T09:00:00-0500")
                                          (zdt "2026-04-21T08:59:00-0500")
                                          zone)))))

  (it "supports ranges lists and steps"
    (let [zone (ZoneId/of "America/Chicago")]
      (should= "2026-04-21T09:30:00-0500"
               (.format offset-formatter
                        (sut/next-fire-at "*/15 9-10 * * 1,2,3,4,5"
                                          (zdt "2026-04-21T09:15:00-0500")
                                          (zdt "2026-04-21T09:16:00-0500")
                                          zone)))))

  (it "finds the previous fire for a daily schedule"
    (let [zone (ZoneId/of "America/Chicago")]
      (should= "2026-04-21T09:00:00-0500"
               (.format offset-formatter
                        (sut/previous-fire-at "0 9 * * *"
                                              (zdt "2026-04-21T11:30:00-0500")
                                              zone))))))
