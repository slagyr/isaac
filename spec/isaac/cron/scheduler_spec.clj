(ns isaac.cron.scheduler-spec
  (:require
    [isaac.comm.null :as null-comm]
    [isaac.cron.scheduler :as sut]
    [isaac.cron.state :as cron-state]
    [isaac.drive.turn :as turn]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.session.storage :as storage]
    [isaac.spec-helper :as helper]
    [speclj.core :refer :all])
  (:import
    (java.time ZonedDateTime)
    (java.time.format DateTimeFormatter)))

(def ^:private offset-formatter
  (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ssZ"))

(defn- zdt [s]
  (ZonedDateTime/parse s offset-formatter))

(describe "cron scheduler"

  (helper/with-captured-logs)

  (around [it]
    (binding [fs/*fs* (fs/mem-fs)]
      (it)))

  (it "fires due cron jobs through the normal turn flow"
    (let [calls (atom [])]
      (with-redefs [storage/create-session! (fn [_state-dir _identifier opts]
                                              {:id   "session-1"
                                               :crew (:crew opts)})
                    turn/run-turn! (fn [state-dir session-key input opts]
                                               (swap! calls conj {:state-dir   state-dir
                                                                  :session-key session-key
                                                                  :input       input
                                                                  :opts        opts})
                                               {:ok true})]
        (sut/tick! {:cfg       {:tz      "America/Chicago"
                                :crew    {"main" {:soul "You are Isaac." :model "grover"}}
                                :models  {"grover" {:model "echo" :provider "grover" :context-window 32768}}
                                :providers {"grover" {}}
                                :cron    {"health-check" {:expr  "0 9 * * *"
                                                            :crew  "main"
                                                            :prompt "Run the health checkin."}}}
                    :now       (zdt "2026-04-21T09:00:00-0500")
                    :state-dir "/test/isaac"}))
        (let [actual (first @calls)]
          (should= "/test/isaac" (:state-dir actual))
          (should= "session-1" (:session-key actual))
          (should= "Run the health checkin." (:input actual))
          (let [opts (:opts actual)]
            (should= null-comm/channel (:comm opts))
            (should= 32768 (:context-window opts))
            (should= "echo" (:model opts))
            (should= "You are Isaac." (:soul opts))
            (should= "grover" ((requiring-resolve 'isaac.llm.api/display-name) (:provider opts))))))
    (should= {"health-check" {:last-run    "2026-04-21T09:00:00-0500"
                               :last-status :succeeded
                               :last-error  nil}}
             (cron-state/read-state "/test/isaac")))

  (it "logs and skips a missed cron window"
    (with-redefs [storage/create-session! (fn [& _]
                                            (throw (ex-info "should not create" {})))
                  turn/run-turn! (fn [& _]
                                             (throw (ex-info "should not run" {})))]
      (sut/tick! {:cfg       {:tz   "America/Chicago"
                              :cron {"health-check" {:expr  "0 9 * * *"
                                                      :crew  "main"
                                                      :prompt "Run the health checkin."}}}
                  :now       (zdt "2026-04-21T11:30:00-0500")
                  :state-dir "/test/isaac"}))
    (let [entry (first (filter #(= :cron/missed-schedule (:event %)) @log/captured-logs))]
      (should-not-be-nil entry)
      (should= "health-check" (:job entry)))
    (should= {} (cron-state/read-state "/test/isaac")))

  (it "records failed job runs"
    (with-redefs [storage/create-session! (fn [_state-dir _identifier opts]
                                            {:id   "session-1"
                                             :crew (:crew opts)})
                  turn/run-turn! (fn [& _]
                                             (throw (ex-info "boom" {})))]
      (sut/tick! {:cfg       {:tz      "America/Chicago"
                              :crew    {"main" {:soul "You are Isaac." :model "grover"}}
                              :models  {"grover" {:model "echo" :provider "grover"}}
                              :providers {"grover" {}}
                              :cron    {"health-check" {:expr  "0 9 * * *"
                                                          :crew  "main"
                                                          :prompt "Run the health checkin."}}}
                  :now       (zdt "2026-04-21T09:00:00-0500")
                  :state-dir "/test/isaac"}))
    (should= {"health-check" {:last-run    "2026-04-21T09:00:00-0500"
                                :last-status :failed
                                :last-error  "boom"}}
             (cron-state/read-state "/test/isaac")))

  (it "creates cron sessions with a cron origin"
    (let [captured (atom nil)]
      (with-redefs [storage/create-session! (fn [_state-dir _identifier opts]
                                              (reset! captured opts)
                                              {:id "session-1" :crew (:crew opts)})
                    turn/run-turn! (fn [& _] {:ok true})]
        (sut/tick! {:cfg       {:tz      "America/Chicago"
                                :crew    {"main" {:soul "You are Isaac." :model "grover"}}
                                :models  {"grover" {:model "echo" :provider "grover" :context-window 32768}}
                                :providers {"grover" {}}
                                :cron    {"health-check" {:expr   "0 9 * * *"
                                                           :crew   "main"
                                                           :prompt "Run the health checkin."}}}
                    :now       (zdt "2026-04-21T09:00:00-0500")
                    :state-dir "/test/isaac"}))
      (should= {:kind :cron :name "health-check"} (:origin @captured)))))
