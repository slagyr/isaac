Feature: Discord as a module
  Discord, ported from a built-in comm to a module subproject, is
  activated lazily when its slot is configured. This feature covers
  the bootstrap path; existing features/comm/discord/*.feature
  scenarios continue to verify Discord's runtime behavior.

  Scenario: Discord activates from a module declaration
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:log     {:output :memory}
       :modules [isaac.comm.discord]
       :comms   {:main {:impl :discord :token "fake-token"}}}
      """
    When the Isaac server is started
    Then the log has entries matching:
      | level | event                   | module             |
      | :info | :module/activated       | isaac.comm.discord |
      | :info | :discord.client/started |                    |
