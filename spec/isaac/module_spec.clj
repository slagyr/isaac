(ns isaac.module-spec
  (:require
    [isaac.module :as sut]
    [speclj.core :refer :all]))

(defrecord StartupOnlyModule [calls]
  sut/Module
  (on-startup [_]
    (swap! calls conj :started))
  (on-shutdown [_] nil))

(describe "isaac.module"

  (it "supports modules defined with defrecord"
    (let [calls  (atom [])
          module (StartupOnlyModule. calls)]
      (sut/on-startup module)
      (sut/on-shutdown module)
      (should (sut/module? module))
      (should= [:started] @calls)))

  (it "builds no-op modules with default lifecycle hooks"
    (let [module (sut/module)]
      (sut/on-startup module)
      (sut/on-shutdown module)
      (should (sut/module? module)))))
