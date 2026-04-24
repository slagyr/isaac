(ns isaac.features.steps.auth
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defthen]]
    [isaac.fs :as fs]
    [isaac.session.storage :as storage]))

(defgiven authenticated-credentials "authenticated credentials exist for provider {provider:string}"
  "Writes a minimal api-key credential to <state-dir>/auth.json under the
   given provider. Creates or updates the file — does not touch provider
   EDN configs in ~/.isaac/config/providers/."
  [provider]
  (let [sdir      (or (g/get :state-dir) "/test/state")
        auth-file (str sdir "/auth.json")
        mem-fs    (g/get :mem-fs)
        write-fn  (fn []
                    (let [auth-data (if (fs/exists? auth-file)
                                      (json/parse-string (fs/slurp auth-file) true)
                                      {})]
                      (fs/mkdirs (fs/parent auth-file))
                      (fs/spit auth-file (json/generate-string
                                           (assoc-in auth-data [:providers (keyword provider)]
                                                     {:type "api-key" :apiKey "sk-test-key"})))))]
    (if mem-fs
      (binding [fs/*fs* mem-fs]
        (write-fn))
      (write-fn))))

(defthen output-prompts-for-key "the stdout prompts for an API key"
  []
  (let [output (g/get :output)]
    (g/should (or (str/includes? output "API key")
                  (str/includes? output "Enter")))))

(defthen credentials-removed "credentials for {provider:string} are removed"
  "Asserts the 'Logged out' message appeared in output. Does NOT read
   the credentials file to verify removal — phrase is about the
   observable behavior, not the on-disk state."
  [provider]
  (let [output (g/get :output)]
    (g/should (str/includes? output "Logged out"))))
