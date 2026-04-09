Feature: Session Keys
  Session keys encode agent, channel, chat type, and conversation
  identity following OpenClaw's convention. The key reflects the
  session's origin; routing metadata in the session entry tracks
  the current channel.

  Background:
    Given an empty Isaac state directory "target/test-state"

  # --- Key Construction ---

  Scenario: CLI direct session key
    When sessions are created for agent "main":
      | agent | channel | chatType | conversation |
      | main  | cli     | direct   | micah        |
    Then agent "main" has sessions matching:
      | key                         |
      | agent:main:cli:direct:micah |

  Scenario: Telegram group session key
    When sessions are created for agent "main":
      | agent | channel  | chatType | conversation |
      | main  | telegram | group    | 12345        |
    Then agent "main" has sessions matching:
      | key                             |
      | agent:main:telegram:group:12345 |

  Scenario: Thread session key
    Given agent "main" has sessions:
      | key                            |
      | agent:main:slack:group:general |
    When sessions are created for agent "main":
      | parentKey                      | thread |
      | agent:main:slack:group:general | ts-001 |
    Then agent "main" has sessions matching:
      | key                                          |
      | agent:main:slack:group:general:thread:ts-001 |

  Scenario: Named agent session key
    When sessions are created for agent "researcher":
      | agent      | channel | chatType | conversation |
      | researcher | cli     | direct   | micah        |
    Then agent "researcher" has sessions matching:
      | key                               |
      | agent:researcher:cli:direct:micah |

  # --- Routing ---

  Scenario: Session tracks last delivery route
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:micah |
    When entries are appended to session "agent:main:cli:direct:micah":
      | type    | message.role | message.content | message.channel | message.to |
      | message | user         | Hello           | cli             | micah      |
    Then agent "main" has sessions matching:
      | key                         | lastChannel | lastTo |
      | agent:main:cli:direct:micah | cli         | micah  |

  Scenario: Delivery route updates when channel changes
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:micah |
    And entries are appended to session "agent:main:cli:direct:micah":
      | type    | message.role | message.content | message.channel | message.to |
      | message | user         | Hello           | cli             | micah      |
    When entries are appended to session "agent:main:cli:direct:micah":
      | type    | message.role | message.content | message.channel | message.to |
      | message | user         | Hello again     | telegram        | micah      |
    Then agent "main" has sessions matching:
      | key                         | lastChannel | lastTo |
      | agent:main:cli:direct:micah | telegram    | micah  |
