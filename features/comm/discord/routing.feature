@wip
Feature: Discord channel-based session routing
  Each Discord channel maps to its own session. By default the session
  name is `discord-<channel-id>` and the channel uses the global default
  crew and model. A channel can override session name, crew, or model
  via `:comms.discord.channels.<channel-id>` in config. All authors
  writing in the same channel share its session — the conversation is
  the channel, not the (channel, user) pair.

  Routing is configuration, not state — there is no routing.edn.

  Background:
    Given default Grover setup
    And the Discord Gateway is faked in-memory
    And config:
      | comms.discord.token             | test-token      |
      | comms.discord.allow-from.users  | ["alice","bob"] |
      | comms.discord.allow-from.guilds | ["G789"]        |
      | defaults.crew                   | main            |
      | defaults.model                  | echo            |
    And the Discord client is ready as bot "bot-default"

  Scenario: first message in a channel routes to discord-<channel-id> using defaults
    Given the following model responses are queued:
      | model | type | content     |
      | echo  | text | hello there |
    When Discord sends MESSAGE_CREATE:
      | channel_id | 555001 |
      | guild_id   | G789   |
      | author.id  | alice  |
      | content    | hi     |
    Then session "discord-555001" exists
    And session "discord-555001" matches:
      | crew | main |
    And the session count is 1

  Scenario: a channel override sets session name, crew, and model
    Given the isaac EDN file "config/isaac.edn" exists with:
      | path                                      | value             |
      | comms.discord.token                       | test-token        |
      | comms.discord.allow-from.users            | ["alice"]         |
      | comms.discord.channels.555002.session     | kitchen           |
      | comms.discord.channels.555002.crew        | sous-chef         |
      | comms.discord.channels.555002.model       | echo              |
      | crew.sous-chef.soul                       | crew/sous-chef.md |
      | crew.sous-chef.model                      | echo              |
      | defaults.crew                             | main              |
      | defaults.model                            | echo              |
    And the isaac file "config/crew/sous-chef.md" exists with:
      """
      You are a sous chef. Speak in measured spoonfuls.
      """
    And the following model responses are queued:
      | model | type | content       |
      | echo  | text | mise en place |
    When Discord sends MESSAGE_CREATE:
      | channel_id | 555002      |
      | author.id  | alice       |
      | content    | what's next |
    Then session "kitchen" exists
    And session "kitchen" matches:
      | crew | sous-chef |
    And session "discord-555002" does not exist

  Scenario: two authors in the same channel share one session
    Given the following model responses are queued:
      | model | type | content |
      | echo  | text | one     |
      | echo  | text | two     |
    When Discord sends MESSAGE_CREATE:
      | channel_id | 555003 |
      | guild_id   | G789   |
      | author.id  | alice  |
      | content    | first  |
    And Discord sends MESSAGE_CREATE:
      | channel_id | 555003 |
      | guild_id   | G789   |
      | author.id  | bob    |
      | content    | second |
    Then the session count is 1
    And session "discord-555003" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | #"(?s)first"    |
      | message | assistant    | one             |
      | message | user         | #"(?s)second"   |
      | message | assistant    | two             |
