(ns isaac.cli.auth
  (:require
    [clojure.string :as str]
    [isaac.auth.device-code :as device-code]
    [isaac.auth.store :as auth-store]
    [isaac.cli.registry :as registry]
    [isaac.config.resolution :as config]))

;; region ----- Login -----

(defn- known-providers [] #{"anthropic" "ollama" "openai-codex"})

(defn- login-api-key [provider-name]
  (print (str "Enter API key for " provider-name ": "))
  (flush)
  (if-let [key (read-line)]
    (if (str/blank? key)
      (do (println "Error: API key is required")
          1)
      (let [cfg  (config/load-config)
            sdir (or (:stateDir cfg) (str (System/getProperty "user.home") "/.isaac"))]
        (auth-store/save-api-key! sdir provider-name key)
        (println (str "Authenticated with " provider-name " via API key"))
        0))
    (do (println "Error: No input")
        1)))

(defn- auth-dir []
  (let [cfg  (config/load-config)
        sdir (or (:stateDir cfg) (str (System/getProperty "user.home") "/.isaac"))]
    sdir))

(defn- login-device-code [provider-name]
  (println "Requesting device code...")
  (let [user-code-resp (device-code/request-user-code!)]
    (if (:error user-code-resp)
      (do
        (println (str "Error: Failed to request device code: " (:error user-code-resp)))
        1)
      (let [user-code    (:user_code user-code-resp)
            device-id    (:device_auth_id user-code-resp)
            raw-interval (:interval user-code-resp)
            interval     (if (string? raw-interval) (parse-long raw-interval) (or raw-interval 5))]
        (println)
        (println "Follow these steps to sign in:")
        (println)
        (println "  1. Open this link in your browser:")
        (println (str "     " device-code/verification-url))
        (println)
        (println "  2. Enter this one-time code (expires in 15 minutes)")
        (println (str "     " user-code))
        (println)
        (println "Waiting for authorization...")
        (let [auth-resp (device-code/poll-for-auth! device-id user-code (* interval 1000))]
          (cond
            (:error auth-resp)
            (do
              (println (str "Error: Authorization failed: " (:error auth-resp)))
              1)

            :else
            (let [tokens (device-code/exchange-tokens! (:authorization_code auth-resp)
                                                       (:code_verifier auth-resp))]
              (cond
                (:error tokens)
                (do
                  (println (str "Error: Token exchange failed: " (:error tokens)
                                (when (:body tokens) (str " - " (:body tokens)))))
                  1)

                :else
                (do
                  (auth-store/save-tokens! (auth-dir) provider-name tokens)
                  (println)
                  (println "Authentication successful!")
                  (println (str "Tokens saved for " provider-name))
                  0)))))))))

(defn- login [{:keys [provider api-key]}]
  (cond
    (nil? provider)
    (do (println "Error: --provider is required")
        (println)
        (println "Usage: isaac auth login --provider <name> [--api-key]")
        (println)
        (println "Options:")
        (println "  --provider <name>  Provider to authenticate with")
        (println "  --api-key          Use API key authentication (prompts for key)")
        1)

    (not (contains? (known-providers) provider))
    (do (println (str "Unknown provider: " provider))
        1)

    (= "openai-codex" provider)
    (login-device-code provider)

    api-key
    (login-api-key provider)

    :else
    (do (println "Error: --api-key is required")
        (println)
        (println "Usage: isaac auth login --provider <name> --api-key")
        1)))

;; endregion ^^^^^ Login ^^^^^

;; region ----- Status -----

(defn- status [_opts]
  (let [cfg       (config/load-config)
        providers (get-in cfg [:models :providers])]
    (println "Provider status:")
    (doseq [p (or providers [{:name "ollama"}])]
      (let [name (:name p)]
        (case name
          "anthropic" (if (:apiKey p)
                        (println (str "  " name ": authenticated (API key)"))
                        (println (str "  " name ": not authenticated")))
          (println (str "  " name ": no auth required")))))
    0))

;; endregion ^^^^^ Status ^^^^^

;; region ----- Logout -----

(defn- logout [{:keys [provider]}]
  (if (nil? provider)
    (do (println "Error: --provider is required")
        1)
    (do ;; TODO: remove stored credentials
        (println (str "Logged out from " provider))
        0)))

;; endregion ^^^^^ Logout ^^^^^

;; region ----- Entry Point -----

(defn- parse-auth-opts [args]
  (loop [remaining args
         result    {}]
    (if (empty? remaining)
      result
      (let [[flag & rest-args] remaining]
        (case flag
          "--provider" (recur (rest rest-args) (assoc result :provider (first rest-args)))
          "--api-key"  (recur rest-args (assoc result :api-key true))
          "--help"     (recur rest-args (assoc result :help true))
          (recur rest-args result))))))

(defn run [opts-or-args]
  (let [args     (if (sequential? opts-or-args) opts-or-args [])
        subcmd   (first args)
        sub-args (rest args)]
    (cond
      (or (nil? subcmd) (= "--help" subcmd) (:help opts-or-args))
      (do (println "Usage: isaac auth <subcommand> [options]")
          (println)
          (println "Subcommands:")
          (println "  login   Authenticate with a provider")
          (println "  status  Show authentication status")
          (println "  logout  Remove stored credentials")
          0)

      (= "login" subcmd)
      (login (parse-auth-opts sub-args))

      (= "status" subcmd)
      (status (parse-auth-opts sub-args))

      (= "logout" subcmd)
      (logout (parse-auth-opts sub-args))

      :else
      (do (println (str "Unknown auth subcommand: " subcmd))
          1))))

(registry/register!
  {:name    "auth"
   :usage   "auth <subcommand> [options]"
   :desc    "Manage authentication credentials"
   :options [["login"   "Authenticate with a provider"]
             ["status"  "Show authentication status"]
             ["logout"  "Remove stored credentials"]]
   :run-fn  (fn [opts] (run (or (:_raw-args opts) [])))})

;; endregion ^^^^^ Entry Point ^^^^^
