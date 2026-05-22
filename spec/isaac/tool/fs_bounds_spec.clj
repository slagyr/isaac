(ns isaac.tool.fs-bounds-spec
  (:require
    [isaac.config.loader :as config-loader]
    [isaac.fs :as fs]
    [isaac.marigold :as marigold]
    [isaac.session.store :as store]
    [isaac.system :as system]
    [isaac.tool.fs-bounds :as sut]
    [speclj.core :refer [describe it should should=]]))

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

  (it "creates crew quarters through the installed runtime fs"
    (let [mem           (fs/mem-fs)
          session-store (store/create nil :memory)]
      (store/open-session! session-store "chat-1" {:crew marigold/captain})
      (with-redefs [config-loader/load-config (fn [& _] {:crew {marigold/captain {:tools {:directories []}}}})]
        (system/with-system {:state-dir "/test/runtime" :session-store session-store :fs mem}
          (should= [(str "/test/runtime/crew/" marigold/captain)]
                   (sut/allowed-directories {"session_key" "chat-1"}))
          #_{:clj-kondo/ignore [:invalid-arity]}
          (should (fs/exists? mem (str "/test/runtime/crew/" marigold/captain)))))))
