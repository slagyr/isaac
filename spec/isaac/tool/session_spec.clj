(ns isaac.tool.session-spec
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.spec-helper :as helper]
    [isaac.system :as system]
    [isaac.tool.session :as sut]
    [isaac.tool.support :as support]
    [speclj.core :refer :all]))

(describe "Session tools"
  (before (support/clean!))

  (around [it]
    (helper/with-memory-store
      (system/with-system {:state-dir support/test-dir}
        (it))))

  (let [base-cfg {:defaults  {:crew "main" :model "grover"}
                  :crew      {"main" {:model :grover :soul "You are Isaac."}}
                  :models    {"grover" {:model "echo" :provider :grover :context-window 32768}
                              "parrot" {:model "squawk" :provider :grover :context-window 16384}}
                  :providers {}}]

    (describe "session_info"

      (it "returns current session state with snake_case keys"
        (helper/create-session! support/test-dir "si-basic" {:crew "main" :cwd support/test-dir})
        (helper/update-session! support/test-dir "si-basic" {:createdAt "2026-04-27T10:00:00" :updated-at "2026-04-27T10:00:00"})
        (let [result (with-redefs [config/load-config (fn [& _] base-cfg)]
                       (sut/session-info-tool {"session_key" "si-basic"}))
              data   (json/parse-string (:result result) true)]
          (should= "main" (:crew data))
          (should= "grover" (get-in data [:model :alias]))
          (should= "echo" (get-in data [:model :upstream]))
          (should= "grover" (:provider data))
          (should= "si-basic" (:session data))
          (should= 0 (:compactions data))
          (should= 0 (get-in data [:context :used]))
          (should= 32768 (get-in data [:context :window]))
          (should= "2026-04-27T10:00:00Z" (:created_at data))))

      (it "resolves alias and provider when the session stores the upstream model name"
        (helper/create-session! support/test-dir "si-upstream" {:crew "main" :cwd support/test-dir})
        (helper/update-session! support/test-dir "si-upstream" {:model "lettuce-grande"})
        (let [cfg    {:defaults  {:crew "main" :model "grover"}
                      :crew      {"main" {:model :grover :soul "You are Isaac."}}
                      :models    {"grover"  {:model "echo" :provider :grover :context-window 32768}
                                  "lettuce" {:model "lettuce-grande" :provider :hieronymus :context-window 128000}}
                      :providers {"hieronymus" {:api "grover" :auth "none"}}}
              result (with-redefs [config/load-config (fn [& _] cfg)]
                       (sut/session-info-tool {"session_key" "si-upstream"}))
              data   (json/parse-string (:result result) true)]
          (should= "lettuce" (get-in data [:model :alias]))
          (should= "lettuce-grande" (get-in data [:model :upstream]))
          (should= "hieronymus" (:provider data))
          (should= 128000 (get-in data [:context :window])))))

    (describe "session_model"

      (it "switches model when model arg is provided"
        (helper/create-session! support/test-dir "sm-switch" {:crew "main" :cwd support/test-dir})
        (helper/update-session! support/test-dir "sm-switch" {:compaction-disabled true
                                                               :compaction {:consecutive-failures 5}})
        (let [result (with-redefs [config/load-config (fn [& _] base-cfg)]
                       (sut/session-model-tool {"session_key" "sm-switch" "model" "parrot"}))
              data   (json/parse-string (:result result) true)]
          (should= "parrot" (get-in data [:model :alias]))
          (should= "squawk" (get-in data [:model :upstream]))
          (should= "parrot" (:model (helper/get-session support/test-dir "sm-switch")))
          (should= false (:compaction-disabled (helper/get-session support/test-dir "sm-switch")))
          (should= 0 (get-in (helper/get-session support/test-dir "sm-switch") [:compaction :consecutive-failures]))))

      (it "resets model to crew default when reset is true"
        (helper/create-session! support/test-dir "sm-reset" {:crew "main" :cwd support/test-dir})
        (helper/update-session! support/test-dir "sm-reset" {:model "parrot"})
        (let [result (with-redefs [config/load-config (fn [& _] base-cfg)]
                       (sut/session-model-tool {"session_key" "sm-reset" "reset" true}))
              data   (json/parse-string (:result result) true)]
          (should= "grover" (get-in data [:model :alias]))
          (should= "grover" (:model (helper/get-session support/test-dir "sm-reset")))))

      (it "errors when both model and reset are provided"
        (helper/create-session! support/test-dir "sm-both" {:crew "main" :cwd support/test-dir})
        (let [result (sut/session-model-tool {"session_key" "sm-both" "model" "grover" "reset" true})]
          (should (:isError result))
          (should (str/includes? (:error result) "mutually exclusive"))))

      (it "errors when model alias does not exist"
        (helper/create-session! support/test-dir "sm-nomodel" {:crew "main" :cwd support/test-dir})
        (let [result (with-redefs [config/load-config (fn [& _] base-cfg)]
                       (sut/session-model-tool {"session_key" "sm-nomodel" "model" "nonexistent"}))]
          (should (:isError result))
          (should (str/includes? (:error result) "unknown model: nonexistent")))))))
