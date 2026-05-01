Feature: Discord session routing
  Each Discord channel maps to a single session. The default session
  name is "discord-<channel-id>". Crew and model defaults come from
  :defaults.crew / :defaults.model; per-channel overrides live under
  comms.discord.channels.<channel-id>. Multiple authors in one channel
  share one session — routing is a pure function of config + payload
  with no routing-table state file.

  Background:
    Given default Grover setup in "/test/discord-routing"
    And the Discord Gateway is faked in-memory
    And config:
      | comms.discord.token             | test-token |
      | comms.discord.allow-from.users  | 123        |
      | comms.discord.allow-from.guilds | G789       |
      | sessions.naming-strategy        | sequential |
    And the Discord client is ready as bot "bot-default"

  Scenario: first message in a channel creates a session named discord-<channel-id>
    Given the following model responses are queued:
      | model | type | content |
      | echo  | text | got it  |
    When Discord sends MESSAGE_CREATE:
      | channel_id | C999  |
      | guild_id   | G789  |
      | author.id  | 123   |
      | content    | hello |
    Then session "discord-C999" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | hello           |
      | message | assistant    | got it          |

  Scenario: second author in the same channel routes to the same session
    Given the following sessions exist:
      | name         |
      | discord-C999 |
    And the following model responses are queued:
      | model | type | content |
      | echo  | text | got it  |
    When Discord sends MESSAGE_CREATE:
      | channel_id | C999  |
      | guild_id   | G789  |
      | author.id  | 456   |
      | content    | hello |
    Then session "discord-C999" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | hello           |
      | message | assistant    | got it          |

  Scenario: per-channel session override routes to the configured session
    Given the following sessions exist:
      | name    |
      | kitchen |
    And config:
      | comms.discord.channels.C999.session | kitchen |
    And the following model responses are queued:
      | model | type | content |
      | echo  | text | got it  |
    When Discord sends MESSAGE_CREATE:
      | channel_id | C999  |
      | guild_id   | G789  |
      | author.id  | 123   |
      | content    | hello |
    Then session "kitchen" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | hello           |
      | message | assistant    | got it          |
