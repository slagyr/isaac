(ns isaac.config.mutate-spec
  (:require
    [clojure.edn :as edn]
    [isaac.config.mutate :as sut]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(def test-home "/test/home")
(def config-root "/test/home/.isaac/config")

(defn- write! [path content]
  (let [parent (fs/parent path)]
    (when parent (fs/mkdirs parent))
    (fs/spit path content)))

(defn- write-edn! [relative data]
  (write! (str config-root "/" relative) (pr-str data)))

(defn- read-edn [relative]
  (when (fs/exists? (str config-root "/" relative))
    (edn/read-string (fs/slurp (str config-root "/" relative)))))

(defn- slurp-file [relative]
  (fs/slurp (str config-root "/" relative)))

(defn- file-exists? [relative]
  (fs/exists? (str config-root "/" relative)))

(describe "isaac.config.mutate"

  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (describe "set-config"

    (it "writes a new entity to isaac.edn by default"
      (write-edn! "isaac.edn" {:defaults {:crew :main :model :llama}
                               :crew     {:main {}}
                               :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
                               :providers {:anthropic {}}})
      (let [result (sut/set-config test-home "crew.marvin.model" :llama)]
        (should= :ok (:status result))
        (should= "isaac.edn" (:file result))
        (should= :llama (get-in (read-edn "isaac.edn") [:crew :marvin :model]))))

    (it "writes to an existing entity file when the entity lives there"
      (write-edn! "isaac.edn" {:defaults {:crew :main :model :llama}
                               :crew     {:main {}}
                               :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
                               :providers {:anthropic {}}})
      (write-edn! "crew/marvin.edn" {:model :llama})
      (let [result (sut/set-config test-home "crew.marvin.model" :llama)]
        (should= :ok (:status result))
        (should= "crew/marvin.edn" (:file result))
        (should-not-contain :marvin (:crew (read-edn "isaac.edn")))
        (should= :llama (:model (read-edn "crew/marvin.edn")))))

    (it "writes to isaac.edn when the entity is already defined inline"
      (write-edn! "isaac.edn" {:defaults {:crew :main :model :llama}
                               :crew     {:main   {}
                                          :marvin {:model :llama}}
                               :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
                               :providers {:anthropic {}}})
      (let [result (sut/set-config test-home "crew.marvin.model" :llama)]
        (should= :ok (:status result))
        (should= "isaac.edn" (:file result))
        (should-not (file-exists? "crew/marvin.edn"))))

    (it "routes new entities to entity files when :prefer-entity-files is true"
      (write-edn! "isaac.edn" {:defaults            {:crew :main :model :llama}
                               :prefer-entity-files true
                               :crew                {:main {}}
                               :models              {:llama {:model "llama3.3:1b" :provider :anthropic}}
                               :providers           {:anthropic {}}})
      (let [result (sut/set-config test-home "crew.marvin.model" :llama)]
        (should= :ok (:status result))
        (should= "crew/marvin.edn" (:file result))
        (should-not-contain :marvin (:crew (read-edn "isaac.edn")))))

    (it "writes soul to the companion .md when one already exists"
      (write-edn! "isaac.edn" {:defaults {:crew :main :model :llama}
                               :crew     {:main {}}
                               :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
                               :providers {:anthropic {}}})
      (write-edn! "crew/marvin.edn" {:model :llama})
      (write! (str config-root "/crew/marvin.md") "Old soul.")
      (let [result (sut/set-config test-home "crew.marvin.soul" "New soul.")]
        (should= :ok (:status result))
        (should= "crew/marvin.md" (:file result))
        (should= "New soul." (slurp-file "crew/marvin.md"))
        (should-not (contains? (read-edn "crew/marvin.edn") :soul))))

    (it "creates a companion .md when a new soul exceeds 64 characters"
      (write-edn! "isaac.edn" {:defaults {:crew :main :model :llama}
                               :crew     {:main {}}
                               :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
                               :providers {:anthropic {}}})
      (write-edn! "crew/marvin.edn" {:model :llama})
      (let [long-soul "You are Marvin, the paranoid android from Hitchhiker's Guide. Depression is your default."
            result    (sut/set-config test-home "crew.marvin.soul" long-soul)]
        (should= :ok (:status result))
        (should= "crew/marvin.md" (:file result))
        (should= long-soul (slurp-file "crew/marvin.md"))
        (should-not (contains? (read-edn "crew/marvin.edn") :soul))))

    (it "writes a short new soul inline"
      (write-edn! "isaac.edn" {:defaults {:crew :main :model :llama}
                               :crew     {:main {}}
                               :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
                               :providers {:anthropic {}}})
      (write-edn! "crew/marvin.edn" {:model :llama})
      (let [result (sut/set-config test-home "crew.marvin.soul" "Paranoid.")]
        (should= :ok (:status result))
        (should= "crew/marvin.edn" (:file result))
        (should= "Paranoid." (:soul (read-edn "crew/marvin.edn")))
        (should-not (file-exists? "crew/marvin.md"))))

    (it "refuses to write a value that fails schema validation"
      (write-edn! "isaac.edn" {:defaults {:crew :main :model :llama}
                               :crew     {:main {}}
                               :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
                               :providers {:anthropic {}}})
      (let [result (sut/set-config test-home "crew.marvin.model" :nonexistent)]
        (should= :invalid (:status result))
        (should (seq (:errors result)))
        (should-not-contain :marvin (:crew (read-edn "isaac.edn")))))

    (it "warns on an unknown key but still writes"
      (write-edn! "isaac.edn" {:defaults {:crew :main :model :llama}
                               :crew     {:main {}}
                               :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
                               :providers {:anthropic {}}})
      (let [result (sut/set-config test-home "crew.main.experimental" true)]
        (should= :ok (:status result))
        (should (seq (:warnings result)))
        (should= true (get-in (read-edn "isaac.edn") [:crew :main :experimental]))))

    (it "accepts a whole-entity value and replaces the target"
      (write-edn! "isaac.edn" {:defaults {:crew :main :model :llama}
                               :crew     {:main {}}
                               :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
                               :providers {:anthropic {}}})
      (write-edn! "providers/grok.edn" {:base-url "https://old" :api-key "${OLD}"})
      (let [result (sut/set-config test-home "providers.grok"
                                   {:base-url "https://api.x.ai/v1" :api-key "${GROK_API_KEY}"})]
        (should= :ok (:status result))
        (should= {:base-url "https://api.x.ai/v1" :api-key "${GROK_API_KEY}"}
                 (read-edn "providers/grok.edn"))))

    (it "rejects paths the grammar refuses to parse"
      (write-edn! "isaac.edn" {:defaults {:crew :main :model :llama}
                               :crew     {:main {}}
                               :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
                               :providers {:anthropic {}}})
      (let [result (sut/set-config test-home "crew.*.model" :llama)]
        (should= :invalid-path (:status result))))

    (it "applies a mutation that fixes an existing error without being blocked by unrelated pre-existing errors"
      (write-edn! "isaac.edn" {:defaults  {:crew :main :model :gpt}
                               :crew      {:main {}}
                               :models    {:gpt   {:model "g" :provider :bogus}
                                           :codex {:model "c" :provider :bogus}}
                               :providers {:anthropic {}}})
      (let [result (sut/set-config test-home "models.gpt.provider" :anthropic)]
        (should= :ok (:status result))
        (should= :anthropic (get-in (read-edn "isaac.edn") [:models :gpt :provider]))
        (should-contain {:key "models.codex.provider" :value "pre-existing: references undefined provider \"bogus\" (known: anthropic)"}
                        (mapv #(select-keys % [:key :value]) (:warnings result)))))

    (it "rejects a mutation that introduces a new error even when other errors already exist"
      (write-edn! "isaac.edn" {:defaults  {:crew :main :model :gpt}
                               :crew      {:main {}}
                               :models    {:gpt   {:model "g" :provider :anthropic}
                                           :codex {:model "c" :provider :bogus}}
                               :providers {:anthropic {}}})
      (let [result (sut/set-config test-home "models.gpt.provider" :nonexistent)]
        (should= :invalid (:status result))
        (should= [{:key "models.gpt.provider" :value "references undefined provider \"nonexistent\" (known: anthropic)"}]
                 (mapv #(select-keys % [:key :value]) (:errors result))))))

  (describe "unset-config"

    (it "removes a key from the file where it lives"
      (write-edn! "isaac.edn" {:defaults {:crew :main :model :llama}
                               :crew     {:main {}}
                               :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
                               :providers {:anthropic {}}})
      (write-edn! "crew/marvin.edn" {:model :llama :soul "Paranoid."})
      (let [result (sut/unset-config test-home "crew.marvin.soul")]
        (should= :ok (:status result))
        (should= "crew/marvin.edn" (:file result))
        (should= {:model :llama} (read-edn "crew/marvin.edn"))))

    (it "deletes the entity file when the removal empties it"
      (write-edn! "isaac.edn" {:defaults {:crew :main :model :llama}
                               :crew     {:main {}}
                               :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
                               :providers {:anthropic {}}})
      (write-edn! "crew/marvin.edn" {:model :llama})
      (let [result (sut/unset-config test-home "crew.marvin.model")]
        (should= :ok (:status result))
        (should-not (file-exists? "crew/marvin.edn"))))

    (it "rejects paths the grammar refuses to parse"
      (let [result (sut/unset-config test-home "crew.*.model")]
        (should= :invalid-path (:status result))))))
