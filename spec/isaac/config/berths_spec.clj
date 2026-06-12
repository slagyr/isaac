(ns isaac.config.berths-spec
  (:require
    [isaac.config.berths :as sut]
    [isaac.config.schema.root :as config-schema]
    [isaac.fs :as fs]
    [isaac.nexus :as nexus]
    [isaac.schema.lexicon :as lexicon]
    [isaac.schema.registered-in :as registered-in]
    [speclj.core :refer :all]))

(defn build-node [path slice]
  {:type       (:type slice)
   :path       path
   :crew       (:crew slice)
   :helm/freq  (:helm/freq slice)})

(def module-index
  {:marigold.bridge
   {:manifest
    {:berths
     {:marigold.bridge/comm
      {:description "Comm channels."
       :config      {:path   [:comms]
                     :schema {:type      :map
                              :key-spec  {:type :keyword}
                              :value-spec {:type           :map
                                           :schema         {:type {:type        :keyword
                                                                   :validations [:present?
                                                                                 [:registered-in? :marigold.bridge/comm]]}
                                                            :crew {:type :string}}
                                           :dynamic-schema [:extra-schema]
                                           :factory        'isaac.config.berths-spec/build-node}}}}}}}
   :marigold.longwave
   {:manifest
    {:marigold.bridge/comm
     {:longwave
      {:extra-schema
       {:helm/freq {:type        :string
                    :validations [:present?]}}}}}}})

(describe "config berths"

  #_{:clj-kondo/ignore [:invalid-arity :unresolved-symbol]}
  (around [it]
    (nexus/-with-nested-nexus {:fs (fs/mem-fs)}
      (it)))

  (it "overlays berth config schemas onto the root schema"
    (let [effective (sut/effective-root-schema config-schema/root module-index)
          comms-spec (get-in effective [:schema :comms])]
      (should= [[:comms]] (sut/config-paths module-index))
      (should= :map (:type comms-spec))
      (should= 'isaac.config.berths-spec/build-node
               (get-in comms-spec [:value-spec :factory]))))

  (it "composes dynamic schema fields into the effective root schema"
    (let [effective {:comms {:helm-relay {:type :longwave
                                          :crew "captain"
                                          :helm/freq "121.5"}}}
          schema    (sut/effective-root-schema config-schema/root module-index)]
      (binding [registered-in/*module-index* module-index]
        (should= effective (lexicon/conform! schema effective))
        (should-throw Exception
                      (lexicon/conform! schema
                                        {:comms {:helm-relay {:type :longwave
                                                              :crew "captain"}}})))))

  (it "installs each built node into the nexus at the same path"
    (sut/install! {:config       {:comms {:helm-relay {:type :longwave
                                                       :crew "captain"
                                                       :helm/freq "121.5"}}}
                   :module-index module-index})
    (should= {:type      :longwave
              :path      [:comms :helm-relay]
              :crew      "captain"
              :helm/freq "121.5"}
             (nexus/get-in [:comms :helm-relay]))))
