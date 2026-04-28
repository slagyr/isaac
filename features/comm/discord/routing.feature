Feature: Discord session routing
  The Discord adapter maintains a routing table mapping (channel_id,
  user_id) pairs to session names. Accepted messages dispatch through
  run-turn! against the mapped session. A first message
  from a new pair creates a session and records the route.

  Sessions are channel-agnostic; the routing lives in its own EDN
  file (comm/discord/routing.edn) so other channel adapters can have
  their own tables without polluting the session schema.

  Background:
    Given default Grover setup in "/test/discord-routing"
    And the Discord Gateway is faked in-memory
    And config:
      | comms.discord.token             | test-token |
      | comms.discord.allow-from.users  | 123        |
      | comms.discord.allow-from.guilds | G789       |
      | sessions.naming-strategy        | sequential |
    And the Discord client is ready as bot "bot-default"

  Scenario: message routes to the session recorded in the Discord routing table
    Given the following sessions exist:
      | name    |
      | primary |
    And the EDN isaac file "comm/discord/routing.edn" contains:
      | path     | value   |
      | C999.123 | primary |
    And the following model responses are queued:
      | model | type | content |
      | echo  | text | got it  |
    When Discord sends MESSAGE_CREATE:
      | channel_id | C999  |
      | guild_id   | G789  |
      | author.id  | 123   |
      | content    | hello |
    Then session "primary" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | hello           |
      | message | assistant    | got it          |

  Scenario: first message from a new channel-user pair creates a session and records the route
    Given the EDN isaac file "comm/discord/routing.edn" does not exist
    And the following model responses are queued:
      | model | type | content |
      | echo  | text | got it  |
    When Discord sends MESSAGE_CREATE:
      | channel_id | C999  |
      | guild_id   | G789  |
      | author.id  | 123   |
      | content    | hello |
    Then the EDN file "comm/discord/routing.edn" matches:
      | path     | value     |
      | C999.123 | session-1 |
    And session "session-1" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | hello           |
      | message | assistant    | got it          |
