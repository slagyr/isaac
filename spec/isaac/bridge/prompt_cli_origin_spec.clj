(ns isaac.bridge.prompt-cli-origin-spec
  (:require
    [isaac.bridge.core :as bridge]
    [isaac.bridge.prompt-cli :as sut]
    [isaac.fs :as fs]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]
    [speclj.core :refer :all]))

(describe "CLI Prompt origin"

  (around [it]
    (binding [fs/*fs* (fs/mem-fs)]
      (it)))

  (it "creates prompt sessions with a cli origin"
    (let [captured (atom nil)]
      (with-redefs [file-store/create-store      (fn [_] :store)
                    store/get-session            (fn [& _] nil)
                    builtin/register-all!        (fn [& _] nil)
                    bridge/dispatch!             (fn [request]
                                                   (reset! captured request)
                                                   {:content "Hello"})
                    sut/ensure-local-config!     (fn [_] true)]
        (with-out-str (should= 0 (sut/run {:message "Hi"}))))
      (should= {:kind :cli} (:origin @captured)))))
