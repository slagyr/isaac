Feature: Module schema composition
  At config-load time, every declared module's :extends fragment merges
  into the cfg schema before validation runs. Modules contribute slot
  config keys discriminated by :impl. Validation is strict (no
  coercion); invalid values produce validation errors.

  Scenario: Module :extends adds slot config keys for its impl
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.comm.telly {:local/root "modules/isaac.comm.telly"}}
       :comms   {:bert {:impl :telly :loft "rooftop"}}}
      """
    When the config is loaded
    Then the loaded config has:
      | key             | value   |
      | comms.bert.loft | rooftop |

  Scenario: Extended slot fields enforce their schema
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.comm.telly {:local/root "modules/isaac.comm.telly"}}
       :comms   {:bert {:impl :telly :loft 42}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key             | value            |
      | comms.bert.loft | must be a string |

  Scenario: Without the module declared, extended keys are unknown
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:comms {:bert {:impl :telly :loft "rooftop"}}}
      """
    When the config is loaded
    Then the config has validation warnings matching:
      | key             | value       |
      | comms.bert.loft | unknown key |
