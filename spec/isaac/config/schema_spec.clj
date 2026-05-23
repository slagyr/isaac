(ns isaac.config.schema-spec
  (:require
    [c3kit.apron.schema :as schema]
    [isaac.marigold :as marigold]
    [isaac.config.schema :as sut]
    [speclj.core :refer :all]))

(def test-model-id marigold/helm-mark-iii)
(def test-provider-id marigold/helm-systems)

(defn- runtime-spec [spec]
  (sut/strip-validation-annotations spec))

(describe "config schema"

  (describe "shape"

    (it "each entity is a named wrapped map spec"
      (doseq [[nm spec] {:defaults sut/defaults
                         :crew     sut/crew
                         :tools    sut/tools
                         :model    sut/model
                         :provider sut/provider
                         :acp      sut/acp
                         :server   sut/server
                         :gateway  sut/gateway
                         :isaac    sut/root}]
        (should= :map (:type spec))
        (should= nm (:name spec))
        (should (map? (:schema spec))))))

  (describe "entity conformance"

    (it "defaults conforms keyword ids to strings"
      (should= {:crew "main" :model test-model-id}
               (schema/conform (runtime-spec sut/defaults) {:crew :main :model (keyword test-model-id)})))

    (it "crew conforms with tools nested"
      (should= {:id    marigold/first-mate
                 :model test-model-id
                 :soul  "You are Cordelia."
                 :tools {:allow       [:read :write]
                          :directories [:cwd "/tmp/playground"]}}
               (schema/conform (runtime-spec sut/crew)
                                {:id    (keyword marigold/first-mate)
                                 :model (keyword test-model-id)
                                 :soul  "You are Cordelia."
                                 :tools {:allow       [:read :write]
                                          :directories [:cwd "/tmp/playground"]}})))

    (it "crew conforms with context-mode"
      (should= {:context-mode :reset
                :model        test-model-id}
               (schema/conform (runtime-spec sut/crew)
                                {:context-mode :reset
                                 :model        (keyword test-model-id)})))

    (it "crew conforms with max-in-flight"
      (should= {:max-in-flight 3}
               (schema/conform (runtime-spec sut/crew)
                               {:max-in-flight 3})))

    (it "model conforms with all required + optional fields"
      (should= {:id test-model-id :model marigold/helm-mark-iii :provider test-provider-id :context-window 128000}
               (schema/conform (runtime-spec sut/model)
                                {:id             (keyword test-model-id)
                                 :model          marigold/helm-mark-iii
                                 :provider       (keyword test-provider-id)
                                 :context-window 128000})))

    (it "provider conforms including string→string headers"
      (should= {:base-url "https://api" :api marigold/helm-api :auth "oauth-device" :headers {"X-Foo" "bar"}}
               (schema/conform (runtime-spec sut/provider)
                                {:base-url "https://api"
                                 :api      marigold/helm-api
                                 :auth     "oauth-device"
                                 :headers  {"X-Foo" "bar"}})))

    (it "acp conforms"
      (should= {:proxy-max-reconnects 5
                :proxy-reconnect-delay-ms 1000
                :proxy-reconnect-max-delay-ms 5000}
               (schema/conform sut/acp {:proxy-max-reconnects 5
                                        :proxy-reconnect-delay-ms 1000
                                        :proxy-reconnect-max-delay-ms 5000})))

    (it "server conforms"
      (should= {:host "localhost" :port 8080}
               (schema/conform sut/server {:host "localhost" :port 8080})))

    (it "server conforms with nested auth token"
      (should= {:host "localhost" :auth {:token "s3cr3t"}}
               (schema/conform sut/server {:host "localhost" :auth {:token "s3cr3t"}})))

    (it "gateway ignores retired auth keys during conformance"
      (should= {:host "0.0.0.0" :port 6674}
               (schema/conform sut/gateway
                                {:host "0.0.0.0" :port 6674
                                 :auth {:mode :token :token "secret"}})))

    (it "root conforms a complete config"
      (should= {:defaults  {:crew "main" :model test-model-id}
                 :crew      {"main" {:soul "You are Isaac."}}
                 :models    {test-model-id {:model marigold/helm-mark-iii :provider test-provider-id}}
                 :providers {test-provider-id {:base-url "http://localhost:11434"}}
                 :dev       false
                 :prefer-entity-files false}
               (schema/conform (runtime-spec sut/root)
                                {:defaults  {:crew :main :model (keyword test-model-id)}
                                 :crew      {"main" {:soul "You are Isaac."}}
                                 :models    {test-model-id {:model marigold/helm-mark-iii :provider (keyword test-provider-id)}}
                                 :providers {test-provider-id {:base-url "http://localhost:11434"}}
                                 :dev       false
                                 :prefer-entity-files false}))))

    (it "root conforms cron jobs and timezone"
      (should= {:tz   "America/Chicago"
                :cron {"health-check" {:expr  "0 9 * * *"
                                        :crew  "main"
                                        :prompt "Run the health checkin."}}}
               (schema/conform (runtime-spec sut/root)
                                 {:tz   "America/Chicago"
                                  :cron {"health-check" {:expr  "0 9 * * *"
                                                          :crew  :main
                                                         :prompt "Run the health checkin."}}})))

  (describe "custom validation"

    (it "tools.:directories rejects a non-:cwd keyword"
      (let [result (schema/conform (runtime-spec sut/tools) {:directories [:not-cwd]})]
        (should (schema/error? result))))

    (it "tools.:directories rejects non-keyword non-string entries"
      (let [result (schema/conform (runtime-spec sut/tools) {:directories [42]})]
        (should (schema/error? result))))

    (it "crew accepts an absolute :cwd path"
      (let [result (schema/conform (runtime-spec sut/crew) {:cwd "/lab/world-domination"})]
        (should-not (schema/error? result))
        (should= "/lab/world-domination" (:cwd result))))

    (it "crew rejects a relative :cwd path"
      (let [result (schema/conform (runtime-spec sut/crew) {:cwd "cheese-helmets"})]
        (should (schema/error? result))
        (should= "must be an absolute path"
                 (get-in (schema/message-map result) [:cwd]))))

    (it "crew allows nil :cwd"
      (let [result (schema/conform (runtime-spec sut/crew) {})]
        (should-not (schema/error? result))
        (should-be-nil (:cwd result))))

    (it "crew rejects unknown context-mode values"
      (let [result (schema/conform sut/crew {:context-mode :ponder})]
        (should (schema/error? result))
        (should= "must be one of :full, :reset"
                 (get-in (schema/message-map result) [:context-mode]))))

    (it "crew rejects non-positive max-in-flight"
      (let [result (schema/conform sut/crew {:max-in-flight 0})]
        (should (schema/error? result))
        (should= "must be a positive integer"
                 (get-in (schema/message-map result) [:max-in-flight]))))

    (it "root rejects invalid types with per-field errors"
       (let [result (schema/conform (runtime-spec sut/root)
                                    {:providers {test-provider-id {:headers 42}}
                                     :server    {:port "not-a-number"}})]
        (should (schema/error? result))
        (should= {:providers {test-provider-id {:headers "can't coerce 42 to map"}}
                  :server    {:port "can't coerce \"not-a-number\" to int"}}
                 (schema/message-map result))))))
