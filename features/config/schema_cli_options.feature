@wip
Feature: isaac config schema CLI shows allowed values for dynamic fields

  Scenario: comm slot :type lists user-configurable comm kinds from manifests
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:defaults  {:crew :main :model :local}
       :crew      {:main {}}
       :models    {:local {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {}}
       :modules   {:isaac.comm.telly {:local/root "modules/isaac.comm.telly"}}}
      """
    When isaac is run with "config schema comms.value.type"
    Then the stdout matches:
      | pattern         |
      | options:.*telly |
    And the stdout does not match:
      | pattern          |
      | options:.*acp    |
      | options:.*cli    |
      | options:.*hooks  |
      | options:.*memory |
      | options:.*null   |
    And the exit code is 0
