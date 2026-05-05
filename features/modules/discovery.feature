Feature: Module discovery
  Isaac reads :modules from config, locates each module's manifest,
  validates it, and builds a module index attached to the loaded config
  under :module-index. No module source code is loaded at this stage.

  Scenario: Discover declared modules
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "/tmp/isaac/.isaac/modules/isaac.comm.pigeon/deps.edn" exists with:
      """
      {:paths ["resources"]}
      """
    And the isaac file "/tmp/isaac/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn" exists with:
      """
      {:id isaac.comm.pigeon :version "0.1.0" :entry isaac.comm.pigeon}
      """
    And the isaac file "/tmp/isaac/.isaac/modules/isaac.comm.discord/deps.edn" exists with:
      """
      {:paths ["resources"]}
      """
    And the isaac file "/tmp/isaac/.isaac/modules/isaac.comm.discord/resources/isaac-manifest.edn" exists with:
      """
      {:id isaac.comm.discord :version "0.1.0" :entry isaac.comm.discord}
      """
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.comm.pigeon  {:local/root "/tmp/isaac/.isaac/modules/isaac.comm.pigeon"}
                 :isaac.comm.discord {:local/root "/tmp/isaac/.isaac/modules/isaac.comm.discord"}}}
      """
    When the config is loaded
    Then the loaded config has:
      | key                                              | value                      |
      | /module-index/isaac.comm.pigeon/manifest/id      | isaac.comm.pigeon          |
      | /module-index/isaac.comm.pigeon/manifest/version | 0.1.0                      |
      | /module-index/isaac.comm.pigeon/path             | /tmp/isaac/.isaac/modules/isaac.comm.pigeon |
      | /module-index/isaac.comm.discord/manifest/id     | isaac.comm.discord         |

  Scenario: Hard error when a declared module's manifest is invalid
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "/tmp/isaac/.isaac/modules/isaac.comm.pigeon/deps.edn" exists with:
      """
      {:paths ["resources"]}
      """
    And the isaac file "/tmp/isaac/.isaac/modules/isaac.comm.pigeon/resources/isaac-manifest.edn" exists with:
      """
      {:id isaac.comm.pigeon :entry isaac.comm.pigeon}
      """
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.comm.pigeon {:local/root "/tmp/isaac/.isaac/modules/isaac.comm.pigeon"}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key                                        | value           |
      | module-index["isaac.comm.pigeon"].version  | must be present |
    And the loaded config has:
      | key                             | value |
      | /module-index/isaac.comm.pigeon |       |

  Scenario: Hard error when a declared module is not found
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.comm.ghost {:local/root "/tmp/isaac/.isaac/modules/isaac.comm.ghost"}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key                           | value                      |
      | modules["isaac.comm.ghost"]   | local/root path does not resolve |
    And the loaded config has:
      | key                            | value |
      | /module-index/isaac.comm.ghost |       |
