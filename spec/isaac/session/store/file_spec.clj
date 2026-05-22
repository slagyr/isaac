(ns isaac.session.store.file-spec
  (:require
    [isaac.fs :as fs]
    [isaac.session.store :as store]
    [isaac.session.store.file :as sut]
    [isaac.system :as system]
    [speclj.core :refer [describe it should should=]]))

(def test-dir "/test/file-store")

(describe "file session store"

  (it "uses the installed runtime fs without binding fs/*fs*"
    (let [mem      (fs/mem-fs)
          fs-store (system/with-system {:fs mem}
                     (sut/create-store test-dir))]
      (store/open-session! fs-store "friday-debug" {:crew "main"})
      (should= "friday-debug" (:id (store/get-session fs-store "friday-debug")))
      (should (fs/exists? mem (str test-dir "/sessions/friday-debug.edn")))
      (should (fs/exists? mem (str test-dir "/sessions/friday-debug.jsonl"))))))
