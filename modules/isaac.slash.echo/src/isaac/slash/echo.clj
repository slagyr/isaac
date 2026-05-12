(ns isaac.slash.echo
  (:require
    [clojure.string :as str]
    [isaac.config.loader :as config]))

(def ^:private command-id "echo")

(defn- command-name []
  (or (get-in (config/snapshot) [:slash-commands command-id :command-name])
      (get-in (config/snapshot) [:slash-commands (keyword command-id) :command-name])
      command-id))

(defn- parse-args [input]
  (second (str/split (str/trim input) #"\s+" 2)))

(defn handle-echo [_session-key input _ctx]
  {:type    :command
   :command :echo
   :message (or (parse-args input) "")})
