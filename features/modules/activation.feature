Feature: Module activation
  Modules declared in :modules are activated on first use of a capability
  they extend. Activation requires the module's :entry namespace, which
  in turn registers the module's contributions (comms, providers, tools).

  Scenario: Activating the telly module on first comm slot use
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:log     {:output :memory}
       :modules [isaac.comm.telly]
       :comms   {:bert {:impl :telly}}}
      """
    When the Isaac server is started
    Then the log has entries matching:
      | level | event             | module           |
      | :info | :module/activated | isaac.comm.telly |
      | :info | :telly/started    | bert             |
