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

  (describe "reconcile!"

    (defn- things-index [factory]
      {:mod.x {:manifest {:berths {:mod.x/things
                                   {:description "things"
                                    :config {:path   [:things]
                                             :schema {:type       :map
                                                      :key-spec   {:type :keyword}
                                                      :value-spec {:type    :map
                                                                   :factory factory}}}}}}}})

    (it "creates a node when a slot appears and removes it when the slot goes"
      (nexus/-with-nested-nexus {}
        (let [index (things-index (fn [_path _slice] ::node))]
          (sut/reconcile! {:config {:things {:a {:x 1}}} :module-index index})
          (should= ::node (nexus/get-in [:things :a]))
          (sut/reconcile! {:config {} :old-config {:things {:a {:x 1}}} :module-index index})
          (should-be-nil (nexus/get-in [:things :a])))))

    (it "delivers on-config-change! to a Reconfigurable node instead of recreating it"
      (nexus/-with-nested-nexus {}
        (let [changes (atom [])
              node    (reify sut/Reconfigurable
                        (on-startup! [_ _])
                        (on-config-change! [_ old new] (swap! changes conj [old new])))
              index   (things-index (fn [_ _] node))]
          (sut/reconcile! {:config {:things {:a {:x 1}}} :module-index index})
          (sut/reconcile! {:config     {:things {:a {:x 2}}}
                           :old-config {:things {:a {:x 1}}}
                           :module-index index})
          (should= node (nexus/get-in [:things :a]))
          (should= [[{:x 1} {:x 2}]] @changes))))

    (it "recreates a non-Reconfigurable node when its slice changes"
      (nexus/-with-nested-nexus {}
        (let [made  (atom 0)
              index (things-index (fn [_ _] (swap! made inc) [::node @made]))]
          (sut/reconcile! {:config {:things {:a {:x 1}}} :module-index index})
          (sut/reconcile! {:config     {:things {:a {:x 2}}}
                           :old-config {:things {:a {:x 1}}}
                           :module-index index})
          (should= [::node 2] (nexus/get-in [:things :a])))))

    (it "unifies string and keyword slot keys across boot and reload"
      (nexus/-with-nested-nexus {}
        (let [made  (atom 0)
              index (things-index (fn [_ _] (swap! made inc) ::node))]
          (sut/reconcile! {:config {:things {:a {:x 1}}} :module-index index})
          (sut/reconcile! {:config     {:things {"a" {:x 1}}}
                           :old-config {:things {:a {:x 1}}}
                           :module-index index})
          (should= 1 @made)))))

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
