(ns isaac.config.hail-loader-spec
  (:require
    [isaac.config.loader :as sut]
    [isaac.fs :as fs]
    [isaac.nexus :as nexus]
    [speclj.core :refer :all]))

(describe "config loader hail files"

  (it "loads hail band files into config"
    (let [mem  (fs/mem-fs)
          root "/tmp/isaac-home/.isaac"]
      (fs/mkdirs mem (str root "/config/hail"))
      (fs/spit mem (str root "/config/hail/bean.ready.edn")
               "{:crew-tags [:role/worker] :reach :one}")
      (nexus/-with-nested-nexus {:fs mem}
        (let [result (sut/load-config-result {:root root :fs mem})]
          (should= {:crew-tags [:role/worker] :reach :one}
                   (get-in result [:config :hail "bean.ready"]))
          (should= [] (:errors result))))))

  (it "loads hail markdown companions as prompts"
    (let [mem  (fs/mem-fs)
          root "/tmp/isaac-home/.isaac"]
      (fs/mkdirs mem (str root "/config/hail"))
      (fs/spit mem (str root "/config/hail/bean.ready.edn")
               "{:crew-tags [:role/worker]}")
       (fs/spit mem (str root "/config/hail/bean.ready.md")
                "Pick up the bean.")
       (nexus/-with-nested-nexus {:fs mem}
         (let [result (sut/load-config-result {:root root :fs mem})]
           (should= "Pick up the bean."
                    (get-in result [:config :hail "bean.ready" :prompt]))))))

  (it "rejects hail bands without any addressing fields"
    (let [mem  (fs/mem-fs)
          root "/tmp/isaac-home/.isaac"]
      (fs/mkdirs mem (str root "/config/hail"))
      (fs/spit mem (str root "/config/hail/empty.edn")
               "{}")
      (nexus/-with-nested-nexus {:fs mem}
        (let [result (sut/load-config-result {:root root :fs mem})]
          (should= [{:key "hail.empty.addressing"
                     :value "must include at least one of :crew, :crew-tags, :session, :session-tags"}]
                   (:errors result))
          (should= nil (get-in result [:config :hail "empty"])))))))
