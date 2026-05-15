(ns isaac.config.mutate-spec
  (:require
    [clojure.edn :as edn]
    [isaac.config.mutate :as sut]
    [isaac.fs :as fs]
    [isaac.marigold :as marigold]
    [speclj.core :refer :all]))

(def ^:private config-root (str marigold/home "/.isaac/config"))

(defn- read-edn [relative]
  (when (fs/exists? (str config-root "/" relative))
    (edn/read-string (fs/slurp (str config-root "/" relative)))))

(defn- slurp-file [relative]
  (fs/slurp (str config-root "/" relative)))

(defn- file-exists? [relative]
  (fs/exists? (str config-root "/" relative)))

(describe "isaac.config.mutate"

  (marigold/aboard)

  (describe "set-config"

    (it "writes a new entity to isaac.edn by default"
      (marigold/write-baseline!)
      (let [result (sut/set-config marigold/home "crew.marvin.model" :helm-mark-iii)]
        (should= :ok (:status result))
        (should= "isaac.edn" (:file result))
        (should= :helm-mark-iii (get-in (read-edn "isaac.edn") [:crew :marvin :model]))))

    (it "writes to an existing entity file when the entity lives there"
      (marigold/write-baseline!)
      (marigold/write-crew! :marvin {:model :helm-mark-iii})
      (let [result (sut/set-config marigold/home "crew.marvin.model" :helm-mark-iii)]
        (should= :ok (:status result))
        (should= "crew/marvin.edn" (:file result))
        (should-not-contain :marvin (:crew (read-edn "isaac.edn")))
        (should= :helm-mark-iii (:model (read-edn "crew/marvin.edn")))))

    (it "writes to isaac.edn when the entity is already defined inline"
      (marigold/write-config! (assoc-in marigold/baseline-config [:crew :marvin] {:model :helm-mark-iii}))
      (let [result (sut/set-config marigold/home "crew.marvin.model" :helm-mark-iii)]
        (should= :ok (:status result))
        (should= "isaac.edn" (:file result))
        (should-not (file-exists? "crew/marvin.edn"))))

    (it "routes new entities to entity files when :prefer-entity-files is true"
      (marigold/write-config! (assoc marigold/baseline-config :prefer-entity-files true))
      (let [result (sut/set-config marigold/home "crew.marvin.model" :helm-mark-iii)]
        (should= :ok (:status result))
        (should= "crew/marvin.edn" (:file result))
        (should-not-contain :marvin (:crew (read-edn "isaac.edn")))))

    (it "writes soul to the companion .md when one already exists"
      (marigold/write-baseline!)
      (marigold/write-crew! :marvin {:model :helm-mark-iii} :soul "Old soul.")
      (let [result (sut/set-config marigold/home "crew.marvin.soul" "New soul.")]
        (should= :ok (:status result))
        (should= "crew/marvin.md" (:file result))
        (should= "New soul." (slurp-file "crew/marvin.md"))
        (should-not (contains? (read-edn "crew/marvin.edn") :soul))))

    (it "creates a companion .md when a new soul exceeds 64 characters"
      (marigold/write-baseline!)
      (marigold/write-crew! :marvin {:model :helm-mark-iii})
      (let [long-soul "You are Marvin, the paranoid android from Hitchhiker's Guide. Depression is your default."
            result    (sut/set-config marigold/home "crew.marvin.soul" long-soul)]
        (should= :ok (:status result))
        (should= "crew/marvin.md" (:file result))
        (should= long-soul (slurp-file "crew/marvin.md"))
        (should-not (contains? (read-edn "crew/marvin.edn") :soul))))

    (it "writes a short new soul inline"
      (marigold/write-baseline!)
      (marigold/write-crew! :marvin {:model :helm-mark-iii})
      (let [result (sut/set-config marigold/home "crew.marvin.soul" "Paranoid.")]
        (should= :ok (:status result))
        (should= "crew/marvin.edn" (:file result))
        (should= "Paranoid." (:soul (read-edn "crew/marvin.edn")))
        (should-not (file-exists? "crew/marvin.md"))))

    (it "refuses to write a value that fails schema validation"
      (marigold/write-baseline!)
      (let [result (sut/set-config marigold/home "crew.marvin.model" :nonexistent)]
        (should= :invalid (:status result))
        (should (seq (:errors result)))
        (should-not-contain :marvin (:crew (read-edn "isaac.edn")))))

    (it "warns on an unknown key but still writes"
      (marigold/write-baseline!)
      (let [result (sut/set-config marigold/home (str "crew." marigold/captain ".experimental") true)]
        (should= :ok (:status result))
        (should (seq (:warnings result)))
        (should= true (get-in (read-edn "isaac.edn") [:crew (keyword marigold/captain) :experimental]))))

    (it "accepts a whole-entity value and replaces the target"
      (marigold/write-baseline!)
      (marigold/write-provider! :starcore {:base-url "https://old" :api-key "${OLD}"})
      (let [result (sut/set-config marigold/home "providers.starcore"
                                   {:base-url "https://api.starcore.test/v1" :api-key "${STARCORE_API_KEY}"})]
        (should= :ok (:status result))
        (should= {:base-url "https://api.starcore.test/v1" :api-key "${STARCORE_API_KEY}"}
                 (read-edn "providers/starcore.edn"))))

    (it "rejects paths the grammar refuses to parse"
      (marigold/write-baseline!)
      (let [result (sut/set-config marigold/home "crew.*.model" :helm-mark-iii)]
        (should= :invalid-path (:status result))))

    (it "applies a mutation that fixes an existing error without being blocked by unrelated pre-existing errors"
      (marigold/write-config! {:defaults  {:crew :main :model :sparky}
                               :crew      {:main {}}
                               :models    {:sparky {:model "spark-1" :provider :bogus}
                                           :embery {:model "embers" :provider :bogus}}
                               :providers {:helm-systems {}}})
      (let [result (sut/set-config marigold/home "models.sparky.provider" :helm-systems)]
        (should= :ok (:status result))
        (should= :helm-systems (get-in (read-edn "isaac.edn") [:models :sparky :provider]))
        (should-contain {:key "models.embery.provider" :value "pre-existing: references undefined provider \"bogus\" (known: helm-systems)"}
                        (mapv #(select-keys % [:key :value]) (:warnings result)))))

    (it "rejects a mutation that introduces a new error even when other errors already exist"
      (marigold/write-config! {:defaults  {:crew :main :model :sparky}
                               :crew      {:main {}}
                               :models    {:sparky {:model "spark-1" :provider :helm-systems}
                                           :embery {:model "embers" :provider :bogus}}
                               :providers {:helm-systems {}}})
      (let [result (sut/set-config marigold/home "models.sparky.provider" :nonexistent)]
        (should= :invalid (:status result))
        (should= [{:key "models.sparky.provider" :value "references undefined provider \"nonexistent\" (known: helm-systems)"}]
                 (mapv #(select-keys % [:key :value]) (:errors result))))))

  (describe "unset-config"

    (it "removes a key from the file where it lives"
      (marigold/write-baseline!)
      (marigold/write-crew! :marvin {:model :helm-mark-iii :soul "Paranoid."})
      (let [result (sut/unset-config marigold/home "crew.marvin.soul")]
        (should= :ok (:status result))
        (should= "crew/marvin.edn" (:file result))
        (should= {:model :helm-mark-iii} (read-edn "crew/marvin.edn"))))

    (it "deletes the entity file when the removal empties it"
      (marigold/write-baseline!)
      (marigold/write-crew! :marvin {:model :helm-mark-iii})
      (let [result (sut/unset-config marigold/home "crew.marvin.model")]
        (should= :ok (:status result))
        (should-not (file-exists? "crew/marvin.edn"))))

    (it "rejects paths the grammar refuses to parse"
      (let [result (sut/unset-config marigold/home "crew.*.model")]
        (should= :invalid-path (:status result))))))
