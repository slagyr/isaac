(ns isaac.slash.echo
  (:require
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.config.schema :as schema]
    [isaac.slash.registry :as slash-registry]))

(def ^:private command-id "echo")

(defn- command-name []
  (or (get-in (config/snapshot) [:slash-commands command-id :command-name])
      (get-in (config/snapshot) [:slash-commands (keyword command-id) :command-name])
      command-id))

(defn- parse-args [input]
  (second (str/split (str/trim input) #"\s+" 2)))

(defn- handle-echo [_state-dir _session-key input _ctx]
  {:type    :command
   :command :echo
   :message (or (parse-args input) "")})

(defn -isaac-init []
  (schema/register-schema! :slash-command command-id
                           {:command-name {:type :string}})
  (slash-registry/register! {:name        (command-name)
                             :description "Echo the input back unchanged"
                             :handler     handle-echo}))
