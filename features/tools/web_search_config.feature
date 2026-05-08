Feature: web_search tool configuration
  The web_search tool reads its API credentials and provider choice from
  isaac.edn under :tools :web_search. web_search is a built-in (no
  manifest), but it registers its config schema programmatically at
  activation so the same validation pipeline that handles module-declared
  tool config also covers built-ins.

  The schema at :tools :web_search is composed: web_search's own keys
  (:provider) plus the active provider's keys (:brave's :api-key, etc).
  Each registered provider owns its own required-field schema.

  @wip
  Scenario: Valid web_search config loads cleanly
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:tools {:web_search {:api-key  "marmalade-9000"
                            :provider :brave}}}
      """
    When the config is loaded
    Then the loaded config has:
      | key                       | value          |
      | tools.web_search.api-key  | marmalade-9000 |
      | tools.web_search.provider | :brave         |

  @wip
  Scenario: Invalid web_search config type fails validation
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:tools {:web_search {:api-key 42}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key                      | value            |
      | tools.web_search.api-key | must be a string |

  @wip
  Scenario: Unknown web_search config keys produce a warning
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:tools {:web_search {:api-keyy "marmalade-9000"}}}
      """
    When the config is loaded
    Then the config has validation warnings matching:
      | key                       | value       |
      | tools.web_search.api-keyy | unknown key |

  @wip
  Scenario: web_search registers its config schema at startup
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:log {:output :memory}}
      """
    When the config is loaded
    Then the log has entries matching:
      | level | event                     | tool       |
      | :info | :config/schema-registered | web_search |

  @wip
  Scenario: The brave provider requires :api-key
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:tools {:web_search {:provider :brave}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key                      | value       |
      | tools.web_search.api-key | is required |

  @wip
  Scenario: Provider validation accepts a registered alternate provider
    Given an empty Isaac state directory "/tmp/isaac"
    And the "tofu-search" provider is registered for web_search
    And the isaac file "isaac.edn" exists with:
      """
      {:tools {:web_search {:provider :tofu-search}}}
      """
    When the config is loaded
    Then the loaded config has:
      | key                       | value        |
      | tools.web_search.provider | :tofu-search |

  @wip
  Scenario: Provider validation rejects an unregistered provider
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:tools {:web_search {:api-key  "marmalade-9000"
                            :provider :google}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key                       | value            |
      | tools.web_search.provider | unknown provider |

  @wip
  Scenario: An alternate provider's own required field is enforced
    Given an empty Isaac state directory "/tmp/isaac"
    And a "spice-search" provider is registered for web_search with schema:
      """
      {:level {:type        :keyword
               :validations [:required]}}
      """
    And the isaac file "isaac.edn" exists with:
      """
      {:tools {:web_search {:provider :spice-search}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key                    | value       |
      | tools.web_search.level | is required |
