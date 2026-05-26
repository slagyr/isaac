(ns isaac.config.install-spec
  (:require
    [speclj.core :refer :all]
    [isaac.config.install :as sut]
    [isaac.config.loader :as config]
    [isaac.config.configurator :as configurator]
    [isaac.fs :as fs]
    [isaac.nexus :as nexus]
    [isaac.session.store :as store]))

(defn- fake-component [started]
  (reify configurator/Reconfigurable
    (on-startup! [_ slice] (reset! started slice))
    (on-config-change! [_ _old _new] nil)))

(describe "config install coordinator"

  (around [it]
    (nexus/-with-nexus {:fs (fs/mem-fs)}
      (config/set-snapshot! nil "spec")
      (it)))

  (context "install!"

    (it "does not commit the snapshot — the caller commits before reconciling"
      (sut/install! {:config {:defaults {:crew "main"}}})
      (should-be-nil (config/snapshot "spec")))

    (it "ensures the object-tree slot"
      (should-be-nil (nexus/get :tree))
      (sut/install! {:config {}})
      (should-not-be-nil (nexus/get :tree)))

    (it "reuses an already-installed tree"
      (let [tree (atom {:existing true})]
        (nexus/register! [:tree] tree)
        (sut/install! {:config {}})
        (should= tree (nexus/get :tree))))

    (it "registers a session store when state-dir is known"
      (should-be-nil (store/registered-store))
      (sut/install! {:config {:state-dir "/test/isaac"}})
      (should-not-be-nil (store/registered-store)))

    (it "skips store registration when no state-dir"
      (sut/install! {:config {}})
      (should-be-nil (store/registered-store)))

    (it "reconciles injected registries into the tree"
      (let [started  (atom nil)
            registry {:kind    :component
                      :path    [:thing]
                      :impl    "thing"
                      :factory (fn [_host] (fake-component started))}]
        (sut/install! {:config {:thing {:a 1}} :registries [registry] :host {}})
        (should= {:a 1} @started)
        (should-not-be-nil (get-in @(nexus/get :tree) [:thing]))))

    (it "returns the config and tree"
      (let [result (sut/install! {:config {:defaults {}}})]
        (should= {:defaults {}} (:config result))
        (should= (nexus/get :tree) (:tree result)))))

  (context "load-and-install!"

    (it "loads via the loader and installs, surfacing loader errors"
      (let [result (sut/load-and-install! {:home "/test"})]
        (should (seq (:errors result)))
        (should-not-be-nil (nexus/get :tree))
        (should-not-be-nil (config/snapshot "spec"))))))
