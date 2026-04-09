Feature: Built-in Tools

  Isaac provides built-in tools for file operations and
  shell command execution.

  Background:
    Given a clean test directory "target/test-state-tools"

  # --- read ---

  Scenario: Read a file
    Given a file "test.txt" exists with content "line one\nline two\nline three"
    When the tool "read" is called with:
      | filePath | test.txt |
    Then the tool result contains "line one"
    And the tool result contains "line three"
    And the tool result is not an error

  Scenario: Read a file with offset and limit
    Given a file "long.txt" exists with 100 lines
    When the tool "read" is called with:
      | filePath | long.txt |
      | offset   | 10       |
      | limit    | 5        |
    Then the tool result contains "line 10"
    And the tool result contains "line 14"
    And the tool result does not contain "line 9"
    And the tool result does not contain "line 15"

  Scenario: Read a directory
    Given a directory "mydir" exists with files "a.txt" and "b.txt"
    When the tool "read" is called with:
      | filePath | mydir |
    Then the tool result contains "a.txt"
    And the tool result contains "b.txt"

  Scenario: Read a missing file
    When the tool "read" is called with:
      | filePath | no-such-file.txt |
    Then the tool result is an error
    And the tool result contains "not found"

  # --- write ---

  Scenario: Write a new file
    When the tool "write" is called with:
      | filePath | new.txt     |
      | content  | hello world |
    Then the tool result is not an error
    And the file "new.txt" has content "hello world"

  Scenario: Overwrite an existing file
    Given a file "existing.txt" exists with content "old"
    When the tool "write" is called with:
      | filePath | existing.txt |
      | content  | new          |
    Then the file "existing.txt" has content "new"

  # --- edit ---

  Scenario: Edit replaces matching text
    Given a file "code.txt" exists with content "foo = 1\nbar = 2"
    When the tool "edit" is called with:
      | filePath  | code.txt |
      | oldString | foo = 1  |
      | newString | foo = 42 |
    Then the tool result is not an error
    And the file "code.txt" has content "foo = 42\nbar = 2"

  Scenario: Edit with no match returns error
    Given a file "code.txt" exists with content "foo = 1"
    When the tool "edit" is called with:
      | filePath  | code.txt    |
      | oldString | not here    |
      | newString | replacement |
    Then the tool result is an error
    And the tool result contains "not found"

  Scenario: Edit with multiple matches and no replaceAll returns error
    Given a file "code.txt" exists with content "x = 1\nx = 1\nx = 1"
    When the tool "edit" is called with:
      | filePath  | code.txt |
      | oldString | x = 1    |
      | newString | x = 2    |
    Then the tool result is an error
    And the tool result contains "multiple"

  Scenario: Edit with replaceAll replaces all occurrences
    Given a file "code.txt" exists with content "x = 1\ny = 2\nx = 1"
    When the tool "edit" is called with:
      | filePath   | code.txt |
      | oldString  | x = 1    |
      | newString  | x = 99   |
      | replaceAll | true     |
    Then the tool result is not an error
    And the file "code.txt" has content "x = 99\ny = 2\nx = 99"

  # --- exec ---

  Scenario: Execute a shell command
    When the tool "exec" is called with:
      | command | echo hello world |
    Then the tool result contains "hello world"
    And the tool result is not an error

  Scenario: Execute a failing command
    When the tool "exec" is called with:
      | command | exit 1 |
    Then the tool result is an error

  Scenario: Execute with a working directory
    Given a directory "subdir" exists with files "target.txt"
    When the tool "exec" is called with:
      | command | ls     |
      | workdir | subdir |
    Then the tool result contains "target.txt"

  Scenario: Execute with timeout exceeded
    Given the exec timeout is set to 100 milliseconds
    When the tool "exec" is called with:
      | command | sleep 10 |
    Then the tool result is an error
    And the tool result contains "timeout"
