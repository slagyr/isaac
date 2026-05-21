(ns isaac.tool.fs-bounds-spec
  (:require
    [isaac.fs :as fs]
    [isaac.session.store :as store]
    [isaac.system :as system]
    [isaac.tool.fs-bounds :as sut]
    [speclj.core :refer [describe it should=]]))

(describe "tool fs bounds"

  (it "prefers the explicit state_dir arg over the installed runtime"
    (system/with-system {:state-dir "/test/runtime"}
      (should= "/test/explicit"
               (sut/state-dir {"state_dir" "/test/explicit"}))))

  (it "uses the installed runtime session store when args omit it"
    (let [session-store (store/create nil :memory)]
      (system/with-system {:state-dir "/test/runtime" :session-store session-store}
        (should= session-store
                 (sut/session-store {"session_key" "chat-1"})))))

  (it "uses the installed runtime fs when args omit it"
    (let [mem (fs/mem-fs)]
      (system/with-system {:fs mem}
        (should= mem
                 (sut/filesystem {"session_key" "chat-1"}))))))
