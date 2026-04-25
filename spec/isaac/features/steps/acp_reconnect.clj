(ns isaac.features.steps.acp-reconnect
  (:require
    [gherclj.core :as g :refer [defthen defwhen helper!]]
    [isaac.logger :as log]))

(helper! isaac.features.steps.acp-reconnect)

(def ^:private await-timeout-ms 3000)

(defn reconnect-attempts-failed [n]
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

(defn acp-proxy-still-running []
  (g/should-not (future-done? (g/get :acp-proxy-runner))))

(defwhen "{int} loopback reconnect attempts have failed" acp-reconnect/reconnect-attempts-failed
  "Polls the log (up to 3s) until at least N :acp-proxy/reconnect-attempt
   entries are observed; throws on timeout. Synchronizes on the proxy's
   retry loop without real-time sleeps.")

(defthen "the acp proxy is still running" acp-reconnect/acp-proxy-still-running
  "Asserts the proxy future has not completed. Proves the 'never give up'
   reconnect contract — the proxy hasn't exited despite failed attempts.")
