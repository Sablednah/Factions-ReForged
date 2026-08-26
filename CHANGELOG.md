# Changelog

## 1.0.0 — first release

Factions for NeoForge 1.21.11. Land you claim, allegiances you declare, and the people you hold it
with. **Everything works on an unmodded client** — no client mod, no resource pack, nothing for
players to install.

Built on [SableCraft Standards](https://github.com/Sablednah/SableCraft-Standards), which is a hard
dependency and deliberately so: the groups seam, the claims seam, the message catalogue, the safe
landing, the teleport warmups, the chat router and the economy facade are all borrowed rather than
reimplemented. Standards owns the meeting points; this mod is the first real thing on the other side
of four of them.

### Land

- **`/f claim`, `/f unclaim`, `/f unclaimall`.** Chunk claims, capped per member so recruiting has a
  point beyond the numbers and one person cannot fence off a continent. Claims must touch land you
  already hold (`mustBeConnected`), so territory is a shape you can see the edge of.
- **`/f autoclaim` — walking is the claim.** The feature is knowing when to stop: running out of
  land switches it off rather than repeating itself every sixteen blocks, walking through somebody
  else's territory says nothing at all because that is a journey and not a failed claim, and a
  standing reason is given once.
- **Borders drawn where they are borders.** Only sides where ownership actually changes, so interior
  lines vanish and what is left is the outline of the territory rather than a grid telling you where
  chunks are. **They stand on the ground**, not at your feet, because a border you cannot see while
  walking over it is the one case that mattered.
- **`/f map`** in chat, and **`/f map item`** — a real vanilla `filled_map` painted server-side. At
  maximum scale a map pixel covers exactly one chunk, which is an accident nobody designed and
  precisely the shape a claims map wants. `/f map item <zoom>` trades coverage for detail; the edge
  test is per pixel, so the outline stays one pixel wide at every zoom.

### Protection

- **Right-clicking a block in somebody's claim does nothing.** Not a list of protected materials — a
  list is something somebody maintains and every modded block is missing from it. Item frames,
  armour stands and paintings are covered separately, since none of them are blocks.
- **Pressure plates still work**, deliberately. A landowner who wants visitors puts a plate outside
  the door; protection you can open a hole in beats protection you have to switch off.
- **Explosions stop at the fence**, filtered per block rather than cancelled, so a creeper on the
  wilderness side craters the wilderness and leaves the wall standing. Players still take the
  damage. `blockTnt` is separate, because on a PvP server TNT *is* the siege tool.
- **Allies may interact and may not build**, on purpose. An ally who cannot open your gate stands
  outside it; an ally who can take your walls down is a demolition permit issued for a diplomatic
  position that changes next week.
- **A refusal says no where the no happened** — red dust at the exact face clicked, a low thud, and
  a correction for whatever the client had already drawn.

### People

- **`/f create`, `/f invite`, `/f join`, `/f kick`, `/f promote`, `/f demote`, `/f leave`,
  `/f disband`.** A leader cannot walk away from a faction that still has people in it.
- **`/f request`, `/f requests`, `/f accept`, `/f decline`** — the invitation the other way round,
  for the player who does not know anybody yet, which is exactly who the invite flow cannot help.
  Shown only to whoever can answer it.
- **Faction and ally chat** — `/f chat`, `/f c`, `/f ca`, `/f chatspy`. Through Standards' chat
  router rather than a private listener, so a muted player cannot switch channel and talk.

### Diplomacy

- **Allies agree; enemies do not.** An alliance needs both declarations, a war needs one. You cannot
  conscript a friend and you cannot decline to be somebody's target.
- **`/f peaceful`** — cannot declare, cannot be declared upon.
- **`/f status`** answers what nothing else will: an offered alliance is announced once and never
  mentioned again, and being declared upon is something you otherwise discover by being killed.
  Everything is split by direction, because an offer *to* you is your move and an offer *from* you
  is a wait. Somebody with no faction gets a status too — their invitations and their pending
  requests, the two lists that are otherwise invisible.

### Money

- **A faction bank** — `/f money`, with deposits ungated and withdrawals officer-gated, because a
  member funding the next claim should never need permission to give money away.
- **Faction-to-faction payment with a reason**, which is how tribute and ransom get paid.
- **Claims can cost money**, at a rising price, refunded at the position the chunk occupied rather
  than today's price. Off by default.

### For server owners

- Every behaviour above is a config value, and the comments say *why* rather than *what*.
- **`/f fixture`** invents neighbours to have relations with, because two people cannot test a
  relation system. Off unless asked for.
- Strings live in Standards' `messages.yml` — one file for the whole server, one set of colour
  rules, one merge-on-upgrade behaviour.

### Known

- **Power is designed and not built.** See [`POWER.md`](POWER.md), which specifies it in full along
  with the faction standard, the four land-control modes and where the numbers come from.
- Claim refunds are priced by position rather than by receipt, so enabling claim costs on an
  established world lets existing landholders cash out once. Set them on a fresh world.
