# Changelog

## 1.1.0

**Power, and the standard.** Land stops being a purchase and becomes a position you hold.

Requires [SableCraft Standards](https://github.com/Sablednah/SableCraft-Standards) **1.1.1+**.

### Power

- **Four modes** — `fixed`, `pvp`, `pve`, `both` — differing only in what counts as *losing*.
  `fixed` is the default and is today's behaviour exactly, so upgrading rearranges nothing.

  **`pve` is the one nothing else offers**: your territory shrinks when the *world* beats you,
  which is the actual fiction of a survival or apocalypse server.

- **Fixed is the ceiling; power is the erosion.** `chunksPerMember` still decides what a faction is
  entitled to, and power decides how much of that it is currently holding. Power can only ever take
  entitlement *away* — never grant more than the fixed rule would. So turning it on redistributes
  nobody's land, and **farming power cannot inflate holdings**, because the ceiling is membership.
  That kills the mob-grinder exploit without a special case to maintain.

- **Environmental deaths never cost power.** Falling in your own lava is not a raid: nobody decided
  it and nobody gains from it. The original charged for it deliberately and was wrong. Attribution
  comes from Standards' combat API, so arrows and pets resolve to the person behind them.

- **Experience restores it.** A mob's XP drop is already Minecraft's own opinion of how hard it was
  to kill — maintained by Mojang, extended free by every mod on the server — so a ZombieMod tank
  outweighs a walker with no registry of mob ids to keep and nothing to be wrong about the day a
  modpack adds a boss. Read from the drop, never from your balance, or smelting is a land claim.

- **Overclaiming**, which is the entire point: build power without it and you have built a claim
  limit with extra steps. A declared enemy may take the difference from a faction holding more land
  than its power covers — **at their border**, so a raid eats inward rather than reaching past a
  wall for the vault chunk, and each chunk taken reduces the overreach by one. The attacker is
  rewarded for *noticing*, not for attacking.

- **Nobody finds out by walking home.** The victim is told the moment land changes hands, and
  `/f status` carries the exposure line: *"you hold 22 chunks on an entitlement of 18 — 4 can be
  taken."* A raid you did not know was possible is indistinguishable from a bug.

- `/f power [player]`, and regen rate with your standard's state everywhere it is relevant.

### The standard

A banner your faction designed, planted on its own land, that an enemy can come and take.

- **Its colour and pattern are your identity** — a faction personalised by an object it made rather
  than a setting it typed — and its name is printed in that colour wherever identity appears. The
  map and borders stay relation-coloured, because nothing should make an enemy's land look friendly.

- **It must see the sky**, re-checked continuously. That single rule is the whole game: enemies
  cannot break your blocks or open your doors, so the only way anyone reaches your flag is a path
  you left. Roof it over and it stops earning; uncover it and it resumes.

- **Flying one is the difference between recovering at full speed and recovering slowly** — a reason
  to have one and a reason to take somebody else's, which is the same number from two sides.
  Breaking a standard is the one deliberate hole in claim protection.

- **Carrying it home is the dangerous part.** A flag in your hands is a flag you are holding instead
  of a sword; you glow red through walls, and its owner is told where you are standing. Put it in a
  chest and you stop being a target — and stop denying them anything.

- **Identified by faction id in the item's custom data**, not by its name, because a name is
  something anybody can type into an anvil. Duplicates are inert rather than policed.

### Also

- **PvP rules moved onto Standards' harm seam**, so a hostile *skill* is refused for the same
  reasons a sword is. Before this, a faction that had declared itself peaceful was peaceful against
  arrows and defenceless against spells.
- **Allies were never actually protected.** peaceful, same-faction and pvp-off were checked; ally
  was simply missing. `pvp.betweenAllies`, off by default.
- **`griefAllowed`** — mobs may not chew through claimed land, answered here so a war zone letting
  mobs grief while a home claim does not stays this mod's decision.
- **Three worked config profiles** in [`CURSEFORGE-CONFIGURATION.md`](CURSEFORGE-CONFIGURATION.md):
  peaceful, cosy PvE, and war.
- A **42-check self-test** over the arithmetic that decides who owns what.

### Known

`/f raid` — declared attacks with glow by side, and optionally gating overclaiming behind a raid —
is designed in [`POWER.md`](POWER.md) and not built.

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
