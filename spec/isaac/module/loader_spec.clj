(ns isaac.module.loader-spec
  (:require
    [isaac.fs :as fs]
    [isaac.module.loader :as sut]
    [speclj.core :refer :all]))

(def ctx {:state-dir "/state/.isaac" :cwd "/workspace"})

(defn- mod-dir! [path]
  (fs/mkdirs path))

(defn- mod-manifest! [path content]
  (fs/mkdirs (fs/parent path))
  (fs/spit path content))

(describe "module loader"

  (describe "discover!"

    (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

    (it "returns an empty index when :modules is absent"
      (let [{:keys [index errors]} (sut/discover! {} ctx)]
        (should= {} index)
        (should= [] errors)))

    (it "returns an empty index when :modules is empty"
      (let [{:keys [index errors]} (sut/discover! {:modules []} ctx)]
        (should= {} index)
        (should= [] errors)))

    (it "builds an index entry for a valid module"
      (mod-dir! "/state/.isaac/modules/isaac.comm.pigeon")
      (mod-manifest! "/state/.isaac/modules/isaac.comm.pigeon/module.edn"
                     "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
      (let [{:keys [index errors]} (sut/discover! {:modules [:isaac.comm.pigeon]} ctx)]
        (should= [] errors)
        (should= :isaac.comm.pigeon (get-in index [:isaac.comm.pigeon :manifest :id]))
        (should= "modules/isaac.comm.pigeon" (get-in index [:isaac.comm.pigeon :path]))))

    (it "normalizes symbol module ids (as written in EDN config files)"
      (mod-dir! "/state/.isaac/modules/isaac.comm.pigeon")
      (mod-manifest! "/state/.isaac/modules/isaac.comm.pigeon/module.edn"
                     "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
      (let [{:keys [index errors]} (sut/discover! {:modules ['isaac.comm.pigeon]} ctx)]
        (should= [] errors)
        (should-not-be-nil (get index :isaac.comm.pigeon))))

    (it "adds an error when a module directory is not found"
      (let [{:keys [index errors]} (sut/discover! {:modules [:isaac.comm.ghost]} ctx)]
        (should= nil (get index :isaac.comm.ghost))
        (should= 1 (count errors))
        (should= "modules[\"isaac.comm.ghost\"]" (:key (first errors)))
        (should= "module directory not found" (:value (first errors)))))

    (it "adds errors when a manifest fails schema validation"
      (mod-dir! "/state/.isaac/modules/isaac.comm.pigeon")
      (mod-manifest! "/state/.isaac/modules/isaac.comm.pigeon/module.edn"
                     "{:id :isaac.comm.pigeon :entry isaac.comm.pigeon}")
      (let [{:keys [index errors]} (sut/discover! {:modules [:isaac.comm.pigeon]} ctx)]
        (should= nil (get index :isaac.comm.pigeon))
        (should (some #(and (= "module-index[\"isaac.comm.pigeon\"].version" (:key %))
                            (= "must be present" (:value %)))
                      errors))))

    (it "reports a duplicate-id error for repeated module entries"
      (mod-dir! "/state/.isaac/modules/isaac.comm.pigeon")
      (mod-manifest! "/state/.isaac/modules/isaac.comm.pigeon/module.edn"
                     "{:id :isaac.comm.pigeon :version \"0.1.0\" :entry isaac.comm.pigeon}")
      (let [{:keys [errors]} (sut/discover! {:modules [:isaac.comm.pigeon :isaac.comm.pigeon]} ctx)]
        (should (some #(= "duplicate module id" (:value %)) errors))))

    (it "reports a cycle error in :requires"
      (mod-dir! "/state/.isaac/modules/mod.a")
      (mod-dir! "/state/.isaac/modules/mod.b")
      (mod-manifest! "/state/.isaac/modules/mod.a/module.edn"
                     "{:id :mod.a :version \"1\" :entry mod.a :requires [:mod.b]}")
      (mod-manifest! "/state/.isaac/modules/mod.b/module.edn"
                     "{:id :mod.b :version \"1\" :entry mod.b :requires [:mod.a]}")
      (let [{:keys [errors]} (sut/discover! {:modules [:mod.a :mod.b]} ctx)]
        (should (some #(re-find #"cycle" (:value %)) errors))))))
