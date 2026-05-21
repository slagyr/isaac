(ns isaac.charge-spec
  (:require
    [isaac.charge :as sut]
    [isaac.config.loader :as config]
    [isaac.session.context :as session-ctx]
    [speclj.core :refer :all]))

(def stub-comm (reify Object))

(defn- stub-behavior [crew soul model ctx-window]
  {:crew           crew
   :soul           soul
   :model          model
   :model-cfg      {:model model}
   :context-window ctx-window
   :provider       nil
   :provider-cfg   nil})

(describe "charge"

  (describe "charge?"

    (it "true for a charge map"
      (should (sut/charge? {:charge/type :charge})))

    (it "false for a plain map"
      (should-not (sut/charge? {:session-key "s1" :input "hi"})))

    (it "false for nil"
      (should-not (sut/charge? nil))))

  (describe "slash?"

    (it "true when input starts with /"
      (should (sut/slash? {:charge/type :charge :input "/status"})))

    (it "false for normal input"
      (should-not (sut/slash? {:charge/type :charge :input "hello"})))

    (it "false for nil input"
      (should-not (sut/slash? {:charge/type :charge :input nil}))))

  (describe "unresolved?"

    (it "true when :charge/unresolved is true"
      (should (sut/unresolved? {:charge/type :charge :charge/unresolved true})))

    (it "false for a resolved charge"
      (should-not (sut/unresolved? {:charge/type :charge :input "hi" :model "m"})))

    (it "false for a plain map"
      (should-not (sut/unresolved? {:input "hi"}))))

  (describe "channel"

    (it "returns the :comm value"
      (should= stub-comm (sut/channel {:charge/type :charge :comm stub-comm})))

    (it "nil when :comm absent"
      (should-be-nil (sut/channel {:charge/type :charge}))))

  (describe "agent"

    (it "returns the resolved crew id"
      (should= "ketch" (sut/agent {:charge/type :charge :crew "ketch"})))

    (it "nil when crew not set"
      (should-be-nil (sut/agent {:charge/type :charge}))))

  (describe "build"

    (it "stamps :charge/type on the returned map"
      (with-redefs [config/snapshot          (fn [] {:defaults {:crew "main"}
                                                     :crew     {"main" {:soul "You are helpful." :model "m1"}}
                                                     :models   {"m1" {:model "llm-1" :provider "grover" :context-window 4096}}})
                   session-ctx/resolve-behavior (fn [_ _] (stub-behavior "main" "You are helpful." "llm-1" 4096))]
        (should (sut/charge? (sut/build {:session-key "s1" :input "hi"})))))

    (it "builds a resolved charge from session-key and input"
      (with-redefs [config/snapshot          (fn [] {:defaults {:crew "main"}
                                                     :crew     {"main" {:soul "You are helpful." :model "m1"}}
                                                     :models   {"m1" {:model "llm-1" :provider "grover" :context-window 4096}}})
                   session-ctx/resolve-behavior (fn [_ _] (stub-behavior "main" "You are helpful." "llm-1" 4096))]
        (let [ch     stub-comm
              charge (sut/build {:session-key "s1" :input "hello there" :comm ch})]
          (should-not (sut/unresolved? charge))
          (should= "s1" (:session-key charge))
          (should= "hello there" (:input charge))
          (should= ch (sut/channel charge))
          (should= "main" (sut/agent charge))
          (should= "llm-1" (:model charge))
          (should= "You are helpful." (:soul charge))
          (should= 4096 (:context-window charge)))))

    (it "uses explicit crew override when provided"
      (with-redefs [config/snapshot          (fn [] {:defaults {:crew "main"}
                                                     :crew     {"main"  {:soul "Main soul" :model "fast"}
                                                                "ketch" {:soul "Ketch soul" :model "smart"}}
                                                     :models   {"fast"  {:model "llm-fast"  :provider "g" :context-window 4096}
                                                                "smart" {:model "llm-smart" :provider "g" :context-window 8192}}})
                   session-ctx/resolve-behavior (fn [_ opts]
                                                  (stub-behavior (:crew opts) "Ketch soul" "llm-smart" 8192))]
        (let [charge (sut/build {:session-key "s1" :input "hi" :crew "ketch"})]
          (should= "ketch" (sut/agent charge))
          (should= "Ketch soul" (:soul charge))
          (should= "llm-smart" (:model charge)))))

    (it "appends soul-prepend when provided"
      (with-redefs [config/snapshot          (fn [] {:defaults {:crew "main"}
                                                     :crew     {"main" {:soul "Base." :model "m"}}
                                                     :models   {"m" {:model "llm" :provider "g" :context-window 4096}}})
                   session-ctx/resolve-behavior (fn [_ _] (stub-behavior "main" "Base." "llm" 4096))]
        (let [charge (sut/build {:session-key "s1" :input "hi" :soul-prepend "Addendum."})]
          (should= "Base.\n\nAddendum." (:soul charge)))))

    (it "returns an unresolved charge with :no-model when no model is configured"
      (with-redefs [config/snapshot          (fn [] {:defaults {:crew "main"}
                                                     :crew     {"main" {:soul "You are helpful."}}})
                   session-ctx/resolve-behavior (fn [_ _] {:crew "main" :soul "You are helpful."})]
        (let [charge (sut/build {:session-key "s1" :input "hi"})]
          (should (sut/unresolved? charge))
          (should= :no-model (:charge/reason charge)))))

    (it "returns an unresolved charge when dispatch-error is set"
      (let [charge (sut/build {:session-key "s1" :input "hi"
                               :dispatch-error {:error :unknown-crew}})]
        (should (sut/unresolved? charge))
        (should= :unknown-crew (:charge/reason charge))))))
