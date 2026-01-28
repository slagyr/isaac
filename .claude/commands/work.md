# Work on Next Bead

Pick up the next ready bead and work on it using TDD/SOLID principles.

## Steps

1. Run `bd ready` to find issues with no blockers
2. If no issues are ready, inform the user and stop
3. Select the highest priority issue (lowest P number)
4. Show the issue details to the user with `bd show <id>`
5. Run `bd update <id> --status=in_progress` to claim it
6. Load and apply the `/solid` skill for TDD workflow

## Working the Issue

Follow the solid skill's TDD process:

1. **RED** - Write a failing test first that describes the expected behavior
2. **GREEN** - Write the minimal code to make the test pass
3. **REFACTOR** - Clean up while keeping tests green

## When Complete

1. Ensure all tests pass
2. Run `bd close <id>` to mark the issue complete
3. Run `bd sync` to sync changes
4. Commit code changes with a descriptive message

## Arguments

$ARGUMENTS - Optional: specific issue ID to work on instead of picking from ready queue
