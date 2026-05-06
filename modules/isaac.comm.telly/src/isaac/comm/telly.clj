(ns isaac.comm.telly
  (:require
    [c3kit.apron.env :as c3env]
    [isaac.api.comm :as comm]
    [isaac.api.lifecycle :as lifecycle]
    [isaac.api.logger :as log]))

(when (= "true" (c3env/env "ISAAC_TELLY_FAIL_ON_LOAD"))
  (throw (ex-info "telly load failed"
                  {:entry     'isaac.comm.telly
                   :module-id :isaac.comm.telly
                   :type      :module/activation-failed})))

(deftype Telly [host state]
  lifecycle/Lifecycle
  (on-startup! [_ slice]
    (log/info :telly/started
              :module (let [name (:name host)]
                        (if (keyword? name) (clojure.core/name name) (str name))))
    (reset! state {:slice      slice
                   :started?   true
                   :host       host
                   :last-event :started}))
  (on-config-change! [_ old-slice new-slice]
    (if (nil? new-slice)
      (reset! state {:slice      nil
                     :started?   false
                     :host       host
                     :last-event :stopped
                     :prior      old-slice})
      (swap! state assoc
             :slice new-slice
             :last-event :changed
             :prior old-slice))))

(defn make [host]
  (->Telly host (atom {})))

(defn telly? [x]
  (instance? Telly x))

(defn state [^Telly t]
  @(.-state t))

(defn -isaac-init [] (comm/register-comm! "telly" make))
