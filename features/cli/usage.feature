Feature: Top-level CLI usage
  The 'isaac' command shown without a subcommand (or with --help)
  prints usage: global options, then the list of commands. Users
  need to see --home here — otherwise they have no idea the flag
  exists.

  @wip
  Scenario: top-level usage lists global options including --home
    When isaac is run with "--help"
    Then the output matches:
      | pattern                                        |
      | Usage: isaac <command> \[options\]             |
      | Global Options:                                |
      | --home <dir>\s+Override Isaac's home directory |
      | --help, -h\s+Show this message                 |
      | Commands:                                      |
    And the exit code is 0
