(ns isaac.features.steps.acp-reconnect
  (:require
    [gherclj.core :as g :refer [defthen defwhen]]
    [isaac.logger :as log]))

(def ^:private await-timeout-ms 3000)

(defwhen reconnect-attempts-failed "{int} loopback reconnect attempts have failed"
  [n]
  (let [n        (if (string? n) (parse-long n) n)
        deadline (+ (System/currentTimeMillis) await-timeout-ms)]
    (loop []
      (let [attempts (count (filter #(= :acp-proxy/reconnect-attempt (:event %)) (log/get-entries)))]
        (cond
          (>= attempts n) nil
          (< deadline (System/currentTimeMillis))
          (throw (ex-info "Timed out waiting for reconnect attempts" {:expected n :actual attempts}))
          :else
          (do
            (Thread/sleep 10)
            (recur)))))))

(defthen acp-proxy-still-running "the acp proxy is still running"
  []
  (g/should-not (future-done? (g/get :acp-proxy-runner))))
