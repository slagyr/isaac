  (ns isaac.config.cli.mutate-common-spec
    (:require
      [clojure.string :as str]
      [isaac.config.cli.common :as common]
      [isaac.config.cli.mutate-common :as sut]
      [speclj.core :refer :all]))

 (describe "config cli mutate common"

   (describe "target-home+path!"

     (it "returns derived home and normalized path for a valid argument"
       (should= {:home "/tmp/home" :path-str "crew.main.soul"}
                (sut/target-home+path! {:home "/tmp/home"} "/crew/main/soul")))

     (it "prints missing path and returns nil for a blank argument"
       (let [err (java.io.StringWriter.)]
         (binding [*err* (java.io.PrintWriter. err)]
           (should= nil (sut/target-home+path! {:home "/tmp/home"} nil)))
         (should (str/includes? (str err) "missing path")))))

   (describe "handle-mutate-result!"

     (it "logs successful set and unset operations"
       (let [logged (atom [])]
         (with-redefs [common/print-warnings! (fn [_] nil)
                       sut/log-mutation!                        (fn [& args] (swap! logged conj args))]
           (should= 0 (sut/handle-mutate-result! :set "crew.main.soul" {:status :ok :file "config"} "hi"))
           (should= 0 (sut/handle-mutate-result! :unset "crew.main.soul" {:status :ok :file "config"} nil)))
         (should= [[:info :config/set "config" "crew.main.soul" :value "hi"]
                   [:info :config/unset "config" "crew.main.soul"]]
                  @logged)))

     (it "prints validation errors and logs set failures"
       (let [printed (atom nil)
             logged  (atom nil)]
         (with-redefs [common/print-warnings! (fn [_] nil)
                       common/print-errors!   (fn [errors level] (reset! printed [errors level]))
                       sut/log-mutation!                        (fn [& args] (reset! logged args))]
           (should= 1 (sut/handle-mutate-result! :set "crew.main.soul" {:status :invalid :errors [{:key "crew.main.soul" :value "bad"}]} "hi")))
         (should= [[{:key "crew.main.soul" :value "bad"}] "error"] @printed)
         (should= [:error :config/set-failed "config" "crew.main.soul" :error "crew.main.soul - bad"] @logged)))

     (it "prints config errors and status errors without logging mutation success"
       (let [printed (atom nil)
             status  (java.io.StringWriter.)]
         (with-redefs [common/print-warnings! (fn [_] nil)
                       common/print-errors!   (fn [errors level] (reset! printed [errors level]))]
           (should= 1 (sut/handle-mutate-result! :unset "crew.main.soul" {:status :invalid-config :errors [{:key "config" :value "bad"}]} nil))
           (binding [*err* (java.io.PrintWriter. status)]
             (should= 1 (sut/handle-mutate-result! :unset "crew.main.soul" {:status :not-found} nil))))
         (should= [[{:key "config" :value "bad"}] "error"] @printed)
         (should (str/includes? (str status) "not found: crew.main.soul")))))

   (describe "print-status-error!"

     (it "prints missing path to stderr"
       (let [err (java.io.StringWriter.)]
         (binding [*err* (java.io.PrintWriter. err)]
           (#'sut/print-status-error! :missing-path "crew.main.soul"))
         (should (str/includes? (str err) "missing path"))))

     (it "prints invalid path with the path string"
       (let [err (java.io.StringWriter.)]
         (binding [*err* (java.io.PrintWriter. err)]
           (#'sut/print-status-error! :invalid-path "crew.main.soul"))
         (should (str/includes? (str err) "invalid path: crew.main.soul"))))

     (it "prints unknown statuses as generic config errors"
       (let [err (java.io.StringWriter.)]
         (binding [*err* (java.io.PrintWriter. err)]
           (#'sut/print-status-error! :kaboom "crew.main.soul"))
         (should (str/includes? (str err) "config error: kaboom"))))))
