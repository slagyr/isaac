(ns isaac.plugin-spec
  (:require
    [isaac.comm.discord :as discord]
    [isaac.plugin :as sut]
    [speclj.core :refer :all]))

(describe "Plugin manager"

  (before
    (sut/clear-builders!))

  (after
    (sut/clear-builders!)
    (sut/register! discord/plugin))

  (it "builds registered plugins and syncs changed config slices"
    (let [events  (atom [])
          builder (fn [_ctx]
                    (reify sut/Plugin
                      (config-path [_]
                        [:comms :discord])
                      (on-config-change! [_ old new]
                        (swap! events conj [old new]))))]
      (sut/register! builder)
      (let [plugins (sut/build-all {:state-dir "/tmp/isaac"})]
        (sut/start! plugins {:comms {:discord {:token "test-token"}}})
        (sut/sync-config! plugins
                          {:comms {:discord {:token "test-token"}}}
                          {:comms {:discord {:token "next-token"}}})
        (sut/sync-config! plugins
                          {:comms {:discord {:token "next-token"}}}
                          {:comms {:discord {:token "next-token"}}})
        (sut/stop! plugins {:comms {:discord {:token "next-token"}}})
        (should= [[nil {:token "test-token"}]
                  [{:token "test-token"} {:token "next-token"}]
                  [{:token "next-token"} nil]]
                 @events)))))
