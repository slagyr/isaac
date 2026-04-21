Feature: Discord message intake
  The Discord channel adapter accepts MESSAGE_CREATE events only from
  users and guilds listed in allow-from. Other senders, and the bot's
  own messages, are silently ignored.

  Background:
    Given the Discord Gateway is faked in-memory
    And Discord is configured with:
      | token             | test-token |
      | allow-from.users  | 123456     |
      | allow-from.guilds | 789012     |
    And the Discord client is ready as bot "bot-default"

  @wip
  Scenario: accept MESSAGE_CREATE from an allowed user and guild
    When the Gateway sends MESSAGE_CREATE:
      | channel_id | 999001 |
      | guild_id   | 789012 |
      | author.id  | 123456 |
      | content    | hello  |
    Then the Discord client accepted a message with:
      | content   | hello  |
      | author.id | 123456 |

  @wip
  Scenario: ignore MESSAGE_CREATE from a user not on the allow list
    When the Gateway sends MESSAGE_CREATE:
      | channel_id | 999001 |
      | guild_id   | 789012 |
      | author.id  | 999999 |
      | content    | hi     |
    Then the Discord client accepted no messages

  @wip
  Scenario: ignore MESSAGE_CREATE from a guild not on the allow list
    When the Gateway sends MESSAGE_CREATE:
      | channel_id | 999001 |
      | guild_id   | 888888 |
      | author.id  | 123456 |
      | content    | hi     |
    Then the Discord client accepted no messages

  @wip
  Scenario: ignore MESSAGE_CREATE from the bot itself even if on allow list
    Given Discord is configured with:
      | token             | test-token |
      | allow-from.users  | 555        |
      | allow-from.guilds | 789012     |
    And the Discord client is ready as bot "555"
    When the Gateway sends MESSAGE_CREATE:
      | channel_id | 999001 |
      | guild_id   | 789012 |
      | author.id  | 555    |
      | content    | echo   |
    Then the Discord client accepted no messages
