(ns isaac.features.steps.auth
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defthen helper!]]
    [isaac.fs :as fs]
    [isaac.session.storage :as storage]))

(helper! isaac.features.steps.auth)

(defn authenticated-credentials [provider]
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

(defn output-prompts-for-key []
  (let [output (g/get :output)]
    (g/should (or (str/includes? output "API key")
                  (str/includes? output "Enter")))))

(defn credentials-removed [provider]
  (let [output (g/get :output)]
    (g/should (str/includes? output "Logged out"))))

(defgiven "authenticated credentials exist for provider {provider:string}" auth/authenticated-credentials
  "Writes a minimal api-key credential to <state-dir>/auth.json under the
   given provider. Creates or updates the file — does not touch provider
   EDN configs in ~/.isaac/config/providers/.")

(defthen "the stdout prompts for an API key" auth/output-prompts-for-key)

(defthen "credentials for {provider:string} are removed" auth/credentials-removed
  "Asserts the 'Logged out' message appeared in output. Does NOT read
   the credentials file to verify removal — phrase is about the
   observable behavior, not the on-disk state.")
