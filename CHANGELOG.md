# Changelog

## 1.3.0 — 2026-09-04

### Added

- **`/f raid <faction>` — a declared, announced, time-boxed attack.** Being at war is a standing
  relation; a raid is an *event*. It is announced to the whole server, both sides glow by side, and
  it ends in a way somebody won: their **standard taken and planted on your own land** (attackers
  win), **ground taken from a faction flying no standard** (attackers win), **every attacker dead
  or gone** (defenders win), or the **timer expiring** (defenders held). The clock is a backstop,
  not the mechanism — a raid that could only end on a timer is one nobody can win.

  **Taking the flag is not the win; carrying it home is.** Two earlier versions got this wrong in
  opposite directions and both were found by playing it: asking whether the attackers *flew* the
  captured flag was unreachable while a faction could fly only one, and ending the raid the instant
  the flag fell deleted the walk home, which is where the drama actually lives. Taking it now leaves
  the raid running, so attackers can go for land as well, and the trophy is carried through the
  people whose flag it is.

  **Against a faction that flies no standard, taking ground is the win.** That case was literally
  unwinnable before — no sequence of moves completed it — and with the one-claim-per-raid limit it
  costs them exactly one chunk. It switches off the moment they raise a flag, checked every tick, so
  a standard planted mid-raid becomes the objective.

  **It cannot be declined, and does not need to be.** A raid requires **defenders online** to
  declare. A decline would have been the wrong tool for the problem it was meant to solve: the
  faction that most needs protecting is the one with nobody online, and they are not there to
  decline. So nobody is raided in their sleep, and a raid is always a fight.

  A cooldown runs per attacker–target **pair**, so a busy server is not frozen while "raid the same
  victim every ten minutes" stops being a strategy. A raid **never survives a restart** — it is a
  fight between people who are present, and one that outlived a server bounce could expire with
  nobody online to defend it.

  `/f raids` lists what is running. The design and its reasoning are in `POWER.md` §5, which had
  been an open question list since 1.1.0.

- **A faction may fly several standards** — its own, plus every trophy it has taken. Previously the
  store keyed one flag per faction, which was structural rather than a rule, and it is what made a
  raid's original win condition unreachable: an established faction already flying its own flag
  could never plant a captured one.

  **The power bonus stays flat however many you hold** — `regenWithCapturedStandard` rewards having
  taken a flag, not having taken six. What a stack buys instead is **ablative armour for the bonus**:
  an enemy has to come and take every one before your regen drops. Nothing implements that; it falls
  out of the flat rule.

  The sky rule is now **per flag**, so roofing one trophy over stops that one earning and leaves the
  rest alone. `/f standard` lists them with where each stands and whether it is uncovered — an
  address for each, because every one is somewhere an enemy can walk to.

- **A superseded standard is stripped of its name when somebody tries to plant it.** Take a
  faction's flag, sit on it, and let them raise a replacement: the one in your chest is now an
  ordinary banner with delusions. It was already *inert* — the duplicate rule refused it — but it
  went on calling itself their standard, so its holder had no way to learn it was worthless except
  by carting it somewhere else and trying again.

  Only the name comes off; the colour and pattern they designed stay exactly as they are. A
  de-flagging, not a confiscation.

- **Raid records, and `/f raids top`.** Every finished raid is tallied to both sides, and
  `/f who` carries a faction's record once it has been in one.

  A **disbanded** faction keeps its place, marked `(disbanded)` and wearing the name it last had.
  Deleting the row was the obvious thing and it was wrong twice over: everybody else's wins against
  them still count — erasing those would make disbanding the cheapest grief in the mod — so a
  deleted row leaves a board that visibly does not add up, and on a small server you can see there
  was a raid with no trace of who it was against.

  **Four counters rather than won and lost**, because which *end* a faction wins at is the
  interesting part: a great raider who cannot hold their own ground reads very differently from a
  fortress nobody can crack, and a single win column hides the difference. The board shows `taken`
  (won attacking) and `held` (won defending) beside the headline. A faction that has never been in
  a raid is absent from it rather than sitting at the bottom on nought — a leaderboard of everybody
  is just a list.

- **Faction names with spaces are addressable at last.** `/f who Lantern Vale`, `/f raid
  Lantern Vale` and the rest took `word()`, which accepts letters, digits and `_.+-` and nothing
  else — so such a faction was not refused, it was **unparseable**, answering "Expected whitespace
  to end one argument". The fourth time that trap has cost a feature across these two mods.

  Terminal name arguments are greedy now. `/f money pay` cannot be, since an amount follows it, so
  it takes `"Lantern Vale"` in quotes and the tab-complete supplies them. `create` and `rename`
  gained the rules `word()` had been silently enforcing: 24 characters, no colour codes, no double
  or trailing spaces, at least one letter or digit.

- **A faction in two raids at once now sees the one ending soonest.** It can happen easily —
  attacking one target while somebody else attacks you — and there is a single action bar and a
  single glow colour between them. The old code took whichever raid the map yielded first, so the
  second one's clock never appeared and nothing said it existed.

- **A ticking countdown on the action bar while a raid runs**, for everybody in it. The action bar
  rather than chat, for the reason Standards' teleport warmup uses it: chat would bury the fight
  under two hundred identical lines.

  It tightens as it goes — `4m 12s`, then `45s`, then a bare `9` in the last ten, red under thirty —
  because a countdown that reads the same at four minutes and four seconds has told you nothing
  about which one you are in. **Both sides see it**: a defender knowing how long they must hold is
  exactly as useful as an attacker knowing how long they have to win. `raidCountdown` turns it off.

- **`/f fixture standards`** — plants a real, stealable flag for every seeded neighbour, on their
  own claimed land, through the ordinary designation path. Behind the same `debug.fixtures` flag as
  the rest, and off on a live server.

  It exists because the flat-bonus rule **cannot be tested by two people**: proving that three
  trophies pay the same as one needs three factions to take flags from. The fixtures were already
  real factions with real claims; this gives them something worth raiding.

- **`raidGatesOverclaim`** — optionally, land only changes hands during a declared raid.
  **Off by default**, so no existing server's game changes on update. On, overclaiming becomes an
  event with a beginning and an end.

### Changed

- **Border particles are drawn on the boundary itself**, at the corners of the block grid, rather
  than centred inside the outermost block. Centred in a block, the display answered "which block is
  the edge one" when the question you actually have is "which side of the line am I on" — and you
  ended up counting blocks to work it out.

### Fixed

- **A raid ending crashed the server.** Clearing the side glow removed the player from *both* the
  attacker and defender teams, and removing somebody from a team they are not on throws — which
  killed the tick and took the server with it. Only the team they are actually on is touched now.

  It also left the JVM alive holding LuckPerms' database, so the *next* server found it locked,
  LuckPerms denied every permission, and every gated command silently vanished. Worth knowing as a
  chain: a crash here looks like broken permissions two restarts later.

- **A raid could not be won.** The objective asked whether the attackers were *flying* the
  defenders' standard as a trophy — which reads naturally and is unreachable, because a faction may
  fly exactly one standard and every established faction already flies its own. Taking the flag
  left the raid running until it expired as "held".

  The objective is now that their standard **falls**, which is the moment the defenders actually
  lose something and is true however the attacker got there. Carrying it home stays worth doing —
  it is the trophy and the power bonus — so the journey home is still dangerous without being what
  ends the fight. Whether they were flying one is recorded at declaration, or a faction with no
  standard would lose the instant it was raided.

- **A border between two claims was drawn twice, in two colours.** The comment claimed every
  boundary belonged to exactly one chunk; the code had both neighbours drawing the shared line, so
  your colour and theirs fought over the same particles. Each boundary is now drawn once — and
  because one chunk knows both sides, a shared line **alternates the two colours** and swaps each
  pulse, so it shimmers between them instead of one winning.

## 1.2.0

**Requires Standards 1.4.0.** The two are built together and released together; a new Factions
beside an old Standards starts and *then* misbehaves somewhere unrelated.

### Added

- **`/f bypass` — staff editing claimed land, as a state you enter rather than a permission you
  carry.** Until now `FactionProtection` never consulted operator status at all, so a moderator
  undoing a grief inside a claim had to join the faction or unclaim the land.

  The obvious fix — "if they are an op, let them build" — is the wrong one, and not for security
  reasons. It is wrong because of **attention**. An always-on override means every operator spends
  every session able to break somebody's base by accident, with nothing to say whose land they are
  stood on, and the result looks exactly like a grief to the faction that finds it.

  So: `/f bypass on`, do the job, `/f bypass off`. **It drops when you log out** — the one piece of
  state in either mod designed to be lost. Standards' switches persist because forgetting you can
  fly is harmless; forgetting you can edit everybody's land is not. Come back tomorrow and you have
  to decide again. That decision is the feature.

  Takes `on`/`off`/`toggle` like every switch in Standards, so a macro or command block can turn it
  *off* reliably instead of guessing at a toggle. Every use is logged: the person asking about an
  override afterwards is never the person who used it.

- **`factions.bypass`** — the first permission node this mod has ever declared, so a moderator can
  be trusted with the override without being made an operator and handed `/stop` alongside it.
  Defaults to operators, so a server that configures nothing behaves as before.

- **[`NODES.md`](NODES.md)** — the whole access model in one page: the node, the rank matrix, and
  why almost nothing here is a permission. Rank inside a faction is *game state* — you become an
  officer by being promoted, and a permissions mod should no more grant that than it should grant
  "has a faction".

## 1.1.1

### Added

- **Minecraft 26.1.2 and 26.2**, on their own branches, matching Standards. Both mixin-free halves
  of the port came out cheaper than expected; `../SableCraft-Standards/CROSS-VERSION.md` records
  what each drop moved.

- **A saved-data migration for 26.1 and later**, and a **line in the log saying what came off
  disk** — `Factions: loaded 2 faction(s) holding 18 claim(s).`

  26.1 renamed `factions.dat` to `factions/data.dat` *and* moved per-dimension data out of the world
  root into `world/dimensions/minecraft/overworld/data/`. A migration that fixed only the filename
  wrote a byte-perfect copy into the world-global folder — beside the scoreboard and the weather,
  where nothing reads it — and logged success. Every faction, claim, power value and captured
  standard would have been silently gone, because a missing saved-data file is not an error, it is
  a new world.

  The boot line is the guard against that happening again: an empty store looks exactly like a
  server nobody has played on yet.

### Fixed

- **Factions declared no NeoForge requirement at all.** The dependency reused
  `loader_version_range` — `[1,)`, the FML *loader spec*, which says nothing about NeoForge — and
  only the hard dependency on Standards was covering for it. It now declares a real floor per
  Minecraft line.

### Docs

- **`POWER.md` said "designed, not built" for four days after power shipped** and was being played.
  Power, the four land-control modes, the faction bank and the capturable standard all went out in
  1.1.0; only `/f raid` is still unbuilt. A doc that undersells is never contradicted by a failure,
  which is why it survived so long.

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
