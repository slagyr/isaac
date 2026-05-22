(ns isaac.comm.registry-spec
  (:require
    [isaac.comm.registry :as sut]
    [isaac.module.loader :as module-loader]
    [speclj.core :refer :all]))

(describe "comm registry"

  (around [it]
    (binding [sut/*registry* (atom (sut/fresh-registry))]
      (it)))

  (it "registers and returns a factory"
    (let [factory (fn [_] ::instance)]
      (sut/register-factory! "telly" factory)
      (should= factory (sut/factory-for :telly))))

  (it "self-registers the :comm module-loader handler at load time"
    ;; module-loader requires a handler for :comm before activate! can
    ;; register a comm module's :comm extension. comm.registry installs
    ;; the handler at load time so callers don't need to require isaac.api.
    (let [handler (#'module-loader/handler-for :comm)
          factory (fn [_] ::instance)]
      (should-not-be-nil handler)
      (handler "telly" factory)
      (should= factory (sut/factory-for "telly")))))
