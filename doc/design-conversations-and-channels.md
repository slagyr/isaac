# Conversations and channels

Design note, Micah 2026-10-06. Status: agreed in principle; not yet beaned
beyond isaac-mve9.

## The problem

A comm send reaches someone, but the conversation it belongs to never hears
about it. A cron job DMs a person; the person answers "I got it"; the crew
that owns that DM answers out of context, because the DM's session never saw
the cron message. The same gap opens for attention notices and for
`comm__send` from one session into a channel another session talks on.

Two questions were being asked as one:

1. **Where does a reply land?** (which crew and session gets the answer)
2. **Where is a send recorded?** (which transcript shows what was said)

They are the same question from two ends. Both are about *the
conversation*, and the conversation is the session.

## Model

- **Sessions are free of comms.** One session can be reached through
  several comms: hail, the CLI, ACP, a Discord channel, an iMessage chat, a
  Chat DM. A comm channel is a door into a session, not the session.
- **Comms are free of sessions.** A comm can post to a place no session
  talks in yet: a new person, a broadcast space.
- **A default is derived when sending.** Nothing has to be configured to
  know where a send goes or where it is recorded.

A session records the channels it talks on (`:channels`, strings of the form
`"<comm>:<channel>"`), written by the comm when a message arrives. A comm's
send result reports the channel it actually posted to (`:channel`; an email
target reports the DM space it resolved to, a configured name reports its
id).

## The rule: every send lands in exactly one conversation

### 1. Address a session: "continue this conversation"

The send goes out on the channel the session was most recently reached
through, and is recorded in that session. A send may name a specific
channel of the session to override the default. The session is the target;
the comm is how the message travels.

### 2. Address a comm target: "reach this person or place"

`comm__send` and a cron job's `to` stay; sometimes there is no conversation
yet. After the comm posts and reports `:channel`:

- **A session owns that channel:** append the message to that session as an
  assistant message marked with its source:
  `[sent here by crew <crew> from session <session>] <content>`
  (isaac-mve9). The owner keeps the conversation and now sees what other
  crews said in it. A send by the owning session itself is its own reply and
  is not appended twice.
- **No session owns that channel:** the send opens one, a new session for
  that channel on the sender's crew, with the message as its first entry. If
  the person answers, the reply lands in a conversation that already holds
  the opening message, owned by whoever started it.

A failed send records nothing.

## Consequences

- Replies route to whoever owns the conversation: the opener of a new
  channel, the existing owner of an old one. No routing policy is needed;
  isaac-ugvu (opt-in reply routing to the crew that texted) is superseded.
- `comm__send` never leaves a gap: its message is in the sender's transcript
  as the tool call, and in the channel's conversation as the marked note or
  the opening message.
- Crews stay separate minds. A crew never reads another crew's session; it
  sees only what was said in its own conversations, with the speaker named.

## Work

| Piece | Bean | State |
|---|---|---|
| Owned channel: marked note in the owner's session; `:channels` + `:channel` (agent, gchat) | isaac-mve9 | in progress |
| Discord records `:channels`, reports `:channel` | isaac-rjeg | draft; scenarios drafted |
| iMessage records `:channels`, reports `:channel` (key by chat id; a send addressed to a handle reports the chat it landed in) | isaac-9khs | draft |
| Unowned channel: the send opens a session on the sender's crew | — | to bean |
| Address a session: send on its most recent channel, record there | — | to bean |
| Retire opt-in reply routing | isaac-ugvu | scrap |

## Open details

- **Most recent channel.** Sessions already carry `:last-channel`/`:last-to`
  from appended messages; decide whether "most recent" reads those or the
  newest entry of `:channels`.
- **Naming the opened session.** A session opened by a send should get the
  same id the comm would give it on inbound (e.g. `discord-<channel>`), so a
  later inbound message finds it rather than creating a twin.
- **Sessions with no channel** (hail-only, CLI-only) have no default comm;
  addressing one by session without naming a channel is an error, not a
  silent drop.
