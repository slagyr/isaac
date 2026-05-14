(ns isaac.slash.registry-spec
  (:require
    [isaac.logger :as log]
    [isaac.module.loader :as module-loader]
    [isaac.slash.registry :as sut]
    [isaac.system :as system]
    [speclj.core :refer :all]))

(defn echo-command [cfg]
  {:command-name (or (:command-name cfg) "echo")
   :description  "Echo"
   :handler      identity})

(describe "slash registry"

  (before (sut/clear!))
  (after (sut/clear!))

  (it "registers and looks up a command by name"
    (sut/register! {:name "echo" :description "Echo" :handler identity})
    (let [command (sut/lookup "echo")]
      (should= "echo" (:name command))
      (should= "Echo" (:description command))))

  (it "returns commands sorted by name without handlers"
    (sut/register! {:name "status" :description "Status" :handler identity})
    (sut/register! {:name "echo" :description "Echo" :handler identity})
    (let [commands (mapv #(select-keys % [:name :description]) (sut/all-commands))]
      (should= [{:name "echo" :description "Echo"}
                {:name "status" :description "Status"}]
               (filterv #(contains? #{"echo" "status"} (:name %)) commands))))

  (it "logs :slash/registered when a new command is registered"
    (log/capture-logs
      (sut/register! {:name "echo" :description "Echo" :handler identity})
      (should= [{:level :info :event :slash/registered :command "echo"}]
               (mapv #(select-keys % [:level :event :command]) @log/captured-logs))))

  (it "logs :slash/override and keeps the replacement when a name collides"
    (sut/register! {:name "status" :description "Built-in" :handler (constantly :builtin)})
    (log/capture-logs
      (sut/register! {:name "status" :description "Module override" :handler (constantly :override)})
      (should= :override ((:handler (sut/lookup "status")) nil))
      (should= [{:level :warn :event :slash/override :command "status"}]
               (->> @log/captured-logs
                    (filter #(= :slash/override (:event %)))
                    (mapv #(select-keys % [:level :event :command]))))))

  (it "activates slash command modules before listing all commands"
    (let [module-index {:isaac.slash.echo {:manifest {:slash-commands {:echo {}}}}}]
      (with-redefs [module-loader/activate! (fn [_ _]
                                              (sut/register! {:name "echo" :description "Echo" :handler identity})
                                              :activated)]
        (should= [{:name "echo" :description "Echo"}]
                 (->> (sut/all-commands module-index)
                      (map #(select-keys % [:name :description]))
                      (filterv #(= "echo" (:name %))))))))

  (it "activates slash command modules before lookup"
    (let [module-index {:isaac.slash.echo {:manifest {:slash-commands {:echo {}}}}}]
      (with-redefs [module-loader/activate! (fn [_ _]
                                              (sut/register! {:name "echo" :description "Echo" :handler identity})
                                              :activated)]
        (should= "echo" (:name (sut/lookup "echo" module-index))))))

  (it "registers the factory-returned command-name from user-config"
    (let [module-index {:isaac.slash.echo {:manifest {:slash-commands {:echo {:factory 'isaac.slash.registry-spec/echo-command
                                                                               :schema  {:command-name {:type :string}}}}}}}]
      (system/with-system {:config (atom {:slash-commands {:echo {:command-name "beep"}}})}
        (module-loader/clear-activations!)
        (should= "beep" (:name (sut/lookup "beep" module-index)))))))
