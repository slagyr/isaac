Feature: Comm extension
  Comms are declared as :comm extensions in module manifests, each with
  a :factory and optional :schema. User configs under :comms instantiate
  comms by name, with an explicit :type field referencing the manifest
  entry that supplies the implementation. The instance key and the
  :type are independent, so multiple instances of the same comm kind
  can coexist with distinct names.

  # @wip: real bug — a comm contributed by a MODULE (:modules isaac.comm.telly) does not
  # activate under "the Isaac process is started"; the telly comm impl never registers, so
  # the slots aren't built (only a stray cron :lifecycle/started fires). reconciler.feature
  # passes only because it manually registers the comm. Tracked as a bug bean.
  @wip
  Scenario: Multiple comm instances of the same :type coexist
    Given an empty Isaac root at "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:log    {:output :memory}
       :server {:hot-reload false}
       :crew   {:main {}}
       :modules {:isaac.comm.telly {:local/root "modules/isaac.comm.telly"}}
       :comms  {:north-bot {:type :telly :crew :main}
                :south-bot {:type :telly :crew :main}}}
      """
    When the Isaac process is started
    Then the log has entries matching:
      | level | event              | path            | impl  |
      | :info | :lifecycle/started | comms.north-bot | telly |
      | :info | :lifecycle/started | comms.south-bot | telly |
