# Conversations and comms

Design note, Micah 2026-10-06. Status: agreed (Micah 2026-10-06); beaned (see Work).

## The problem

A comm send reaches someone, but the conversation it belongs to never hears
about it. A cron job DMs a person; the person answers "I got it"; the crew
that owns that DM answers out of context, because the DM's session never saw
the cron message. The same gap opens for attention notices and for
`comm__send` from one session to a comm target another session talks on.

Two questions were being asked as one:

1. **Where does a reply land?** (which crew and session gets the answer)
2. **Where is a send recorded?** (which transcript shows what was said)

They are the same question from two ends. Both are about *the
conversation*, and the conversation is the session.

## Model

- **Sessions are free of comms.** One session can be reached through
  several comms: hail, the CLI, ACP, a Discord channel, an iMessage chat, a
  Chat DM. A comm target is a door into a session, not the session.
- **Comms are free of sessions.** A comm can post to a place no session
  talks in yet: a new person, a broadcast space.
- **A default is derived when sending.** Nothing has to be configured to
  know where a send goes or where it is recorded.

A session records the comm targets it talks on (`:comms`, strings of the form
`"<comm>:<target>"`), written by the comm when a message arrives. A comm's
send result reports the target it actually posted to (`:target`; an email
reports the DM space it resolved to, a configured name reports its id).
Vocabulary: comms and targets, never "channel" (ISAAC.md; Discord's channel
is the only channel).

## The rule: every send lands in exactly one conversation

### 1. Address a session: "continue this conversation"

The send goes out on the comm target the session was most recently reached
through, and is recorded in that session. A send may name a specific comm
target of the session to override the default. The session is the target;
the comm is how the message travels.

### 2. Address a comm target: "reach this person or place"

`comm__send` and a cron job's `to` stay; sometimes there is no conversation
yet. After the comm posts and reports `:target`:

- **A session talks on that target:** append the message to that session as an
  assistant message marked with its source:
  `[sent here by crew <crew> from session <session>] <content>`
  (isaac-mve9). The owner keeps the conversation and now sees what other
  crews said in it. A send by the owning session itself is its own reply and
  is not appended twice.
- **No session talks on that target:** the send opens one, a new session
  for that target on the sender's crew, with the message as its first entry. If
  the person answers, the reply lands in a conversation that already holds
  the opening message, owned by whoever started it.

A failed send records nothing.

## Consequences

- Replies route to whoever owns the conversation: the opener of a new
  target, the existing owner of an old one. No routing policy is needed;
  isaac-ugvu (opt-in reply routing to the crew that texted) is superseded.
- `comm__send` never leaves a gap: its message is in the sender's transcript
  as the tool call, and in the target's conversation as the marked note or
  the opening message.
- Crews stay separate minds. A crew never reads another crew's session; it
  sees only what was said in its own conversations, with the speaker named.

## Work

| Piece | Bean | State |
|---|---|---|
| Owned target: marked note in the owner's session; `:comms` + `:target` (agent, gchat) | isaac-mve9 | done, deployed 10-06 |
| Discord records `:comms`, reports `:target` | isaac-rjeg | done, deployed 10-06 |
| iMessage records `:comms`, reports `:target` (key by chat id; a send addressed to a handle reports the chat it landed in) | isaac-9khs | done, deployed 10-06 |
| Unowned target: the send opens a session on the sender's crew | isaac-4vj9 | draft |
| Address a session: send on its most recent comm target, record there | isaac-ros0 | draft |
| Retire opt-in reply routing | isaac-ugvu | scrapped |

## Open details

- **Most recent comm target.** Sessions already carry `:last-channel`/`:last-to`
  from appended messages; decide whether "most recent" reads those or the
  newest entry of `:comms` (and whether `:last-channel` itself is renamed).
- **Naming the opened session.** A session opened by a send should get the
  same id the comm would give it on inbound (e.g. `discord-<channel-id>`), so a
  later inbound message finds it rather than creating a twin.
- **Sessions with no comm target** (hail-only, CLI-only) have no default;
  addressing one by session without naming a target is an error, not a
  silent drop.
