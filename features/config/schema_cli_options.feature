@wip
Feature: isaac config schema CLI shows allowed values for dynamic fields

  Scenario: comm slot :type lists registered comm kinds
    Given config file "isaac.edn" containing:
      """
      {:defaults  {:crew :main :model :local}
       :crew      {:main {}}
       :models    {:local {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    When isaac is run with "config schema comms.value.type"
    Then the stdout matches:
      | pattern          |
      | options:.*acp    |
      | options:.*cli    |
      | options:.*memory |
    And the exit code is 0
