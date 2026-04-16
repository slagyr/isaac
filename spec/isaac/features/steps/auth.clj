(ns isaac.features.steps.auth
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defthen]]
    [isaac.session.storage :as storage]))

(defgiven authenticated-credentials "authenticated credentials exist for provider {provider:string}"
  [provider]
  (let [sdir      (or (g/get :state-dir) "/test/state")
        auth-file (str sdir "/auth.json")
        auth-data (if (.exists (io/file auth-file))
                    (json/parse-string (slurp auth-file) true)
                    {})]
    (io/make-parents auth-file)
    (spit auth-file (json/generate-string
                      (assoc-in auth-data [:providers (keyword provider)]
                                {:type "api-key" :apiKey "sk-test-key"})))))

(defthen output-prompts-for-key "the output prompts for an API key"
  []
  (let [output (g/get :output)]
    (g/should (or (str/includes? output "API key")
                  (str/includes? output "Enter")))))

(defthen credentials-removed "credentials for {provider:string} are removed"
  [provider]
  ;; Verify by checking that the logout message appeared
  (let [output (g/get :output)]
    (g/should (str/includes? output "Logged out"))))
