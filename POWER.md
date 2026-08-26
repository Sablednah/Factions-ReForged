# Power, money and voice

Three systems the 2012 plugin had that this one does not, designed together because they turn out
to be one system wearing three coats.

**Status: designed, not built.** Written 2026-08-26 from the archived
[MassiveCraft Factions page of 15 November 2012][archive], which the owner dug out of the Wayback
Machine. Numbers quoted below are that page's stated defaults, not guesses.

[archive]: https://web.archive.org/web/20121115155557/http://massivecraft.com/plugins/factions

---

## 1. Power

### What it is actually for

Worth settling before any arithmetic, because it decides the whole design:

> **Power exists to make land contestable. If you build power and do not build overclaiming, you
> have built nothing.**

A power number that only caps how much you may claim is a claim limit with extra steps, and this
mod already has one — `chunksPerMember`. The entire reason the original mattered is the second
half: **when a faction's power drops below the land it holds, an enemy may take the difference.**
Territory stops being a purchase and becomes a position you hold.

So this document specifies the taking as carefully as the number.

### How the original worked, precisely

There are three lineages and they disagree, so the numbers are labelled. "Classic" is the 1.6 line
everybody remembers and the one the archived page describes; "2.x" is the MassiveCore rewrite;
"FUUID" is FactionsUUID, the fork still alive today.

| | classic 1.6 | 2.x | note |
|---|---|---|---|
| Player power max | **10** | 10 | |
| Player power min | **−10** | **0** | classic lets you go negative, and it is a real state |
| **Starting** power | **0** | 0 | **not** max — a new player begins with no claim to anything |
| Lost per death | **4** | **2** | the archived page says 2; the shipped 1.6 config says 4 |
| Regenerated | **0.2/min** — 12/hour | **2/hour** | a **36× difference** in raid tempo, chosen deliberately |
| Lost while offline | **0** (off) | off | the archive describes 1/day; the code defaults it off |
| Faction power | the sum of its members' | same | |
| Land allowed | **1 chunk per power**, rounded | same | `land >= power` blocks the next claim |

Two of those are worth pausing on. **Starting at zero** means a new faction can claim nothing until
its founder has been online the best part of an hour — expansion is paced by attendance, not by
the moment of founding. And **regen rate is the single dial that sets how a server feels**: fifty
minutes to full recovery makes a raid window a thing you exploit that evening; five hours makes it
a thing you plan a week around.

And the worked example from that page, which is the clearest statement of the mechanic anyone has
written:

> SuperAnimals had two healthy members, so 20 power, and claimed 20 chunks. MrApe drowned in lava
> and dropped to 8. The faction now held 20 chunks on 18 power. Their enemy Bulldozer claimed 2
> chunks at the border, and then could claim no more, because SuperAnimals was back to 18 on 18.

Note what that example is really demonstrating: **the attacker is not rewarded for attacking.** He
is rewarded for *noticing*. Someone else's carelessness opened a window and he was standing there.

### Where we should differ, and why

**Environmental death must not cost power.** The original is explicit that it does not matter how
you die — lava, an enemy, sand. That is one rule, easy to explain, and it is the wrong one for us.
We proved why by accident earlier today: a player trapped in powder snow inside another faction's
claim cannot dig out, because claim protection is working correctly. Under the original rule that
suffocation also shrinks their faction's borders, and an enemy is now entitled to their land
because of a hole in the ground. The same argument already put a line in
[`COMBAT-API.md`](../SableCraft-Standards/COMBAT-API.md): **an attacker starts a tag, not damage.**
Power should follow the identical rule — *something killed you*, or it does not count.

**Only an enemy may overclaim.** The original's rule was simply "power too low". Requiring a
declared hostility makes `/f peaceful` coherent — a faction that cannot be declared upon cannot be
raided, and that follows from one rule instead of two — and it makes war a decision somebody made
out loud rather than an ambient condition.

**Offline decay should default off.** Losing 1 power per day away punishes having a life, and on a
server with a weekday population it means every Monday morning is a land-grab against people at
work. Configurable, because a hardcore server may want exactly that, but not the default. The
shipped 1.6 config agrees, whatever the marketing page said.

**Requiring a declared enemy is a genuine divergence, not a restoration.** The original checks only
that the victim is not your *ally* — neutral land is fair game and no declaration is needed. So
"only enemies may overclaim" is a change to how the game plays, and worth making knowingly: it
means a raid is preceded by a `/f enemy`, which is a warning, a public record, and something the
victim can see in `/f status` before anything is taken.

### Fixed is the ceiling; power is the erosion

The unifying idea, and the reason all three of the owner's modes are one mechanism:

> **`chunksPerMember` still decides what a faction is entitled to. Power decides how much of that
> entitlement it is currently holding onto.**

```
entitlement = floor( chunksPerMember × Σ power(member) / maxPower )
```

A faction at full power gets exactly what it gets today. Power can only ever take entitlement
*away*; it can never grant more than the fixed rule would. Three consequences worth having:

- **Turning power on does not change anybody's allowance.** A server that enables it does not
  hand out or confiscate land on restart; it adds a way to lose land by playing badly.
- **`chunksPerMember` keeps meaning what it says**, instead of being silently replaced by a second
  land formula that happens to also exist.
- **Farming power cannot inflate land**, because the ceiling is membership, not power. Killing
  things recovers you *toward* your entitlement and never past it — which quietly kills the
  mob-grinder exploit without a special case.

### The modes

One config, `power.mode`, and the modes differ only in **what drains and what restores**:

| mode | drains | restores |
|---|---|---|
| `fixed` | nothing | — (power is inert; today's behaviour exactly) |
| `pvp` | being killed by a player | time online, and killing enemy players |
| `pve` | being killed by a mob | time online, and killing hostile mobs |
| `both` | either | either |

`fixed` is the default, because it is what the mod does now and an upgrade must not rearrange
somebody's server.

**`pve` is the interesting one and it is not a compromise.** On a zombie-apocalypse server your
territory shrinks when the horde beats you and grows back as you push out — which is the actual
fiction, and something no PvP faction plugin has ever expressed. It also gives ZombieMod and
CityWorld a natural hook without either of them knowing what a faction is.

### The exploit each mode has, and the guard

- **`pvp`: alt farming.** Two accounts, one kills the other on repeat; the killer's faction never
  erodes. Guards: gain only from killing a member of an **enemy** faction, gain **less** than the
  victim lost, and a per-victim cooldown so the same person is worth power once an hour.
  *(FactionsUUID calls the transfer **vampirism** and defaults it to 0 — off. It also has
  **powerFreeze**: after a death, that faction's regen stops for N seconds, resetting on each
  further death rather than stacking. Worth having; it is what stops a raid being outrun by the
  clock.)*
- **`pve`: the grinder.** A zombie farm is infinite power. Already guarded by the ceiling above —
  farming restores you to your entitlement faster and cannot exceed it. Scale the gain by the mob,
  so a warden is worth a hundred zombies and a spawner is worth grinding only if you are actually
  behind.
- **`both`: double jeopardy.** A player killed by a creeper an enemy lured in loses power twice
  under a naive implementation. One death, one deduction, attributed to whoever the combat API
  says was behind it.

### XP is the right *rate*, and the wrong *balance*

Two different ideas wear the same words, and only one of them is bad.

**Power tied to your XP balance** would be wrong, and obviously so once stated: XP is *spent*, so
enchanting a sword would cost your faction land and every player would learn to choose between
their gear and their borders for no thematic reason at all.

**Power restored in proportion to XP *earned*** is a different proposal and a much better one than
the mob table I was going to write. The experience a mob drops is already Minecraft's own opinion
about how hard it was to kill, maintained by Mojang, and — the part that matters here — **extended
for free by every mod on the server.** A ZombieMod tank is worth more than a walker without
ZombieMod telling us anything, without a registry of mob ids, and without us being wrong about
some modpack's boss the day it is added.

So: take the drop from `LivingExperienceDropEvent`, not from the player's balance. Spending XP
costs nothing. Two consequences worth stating:

- **It must be the mob's drop, not XP picked up**, or smelting and mining and breeding all become
  power, and a furnace full of iron is a land claim.
- **Scale is a config, not a constant.** Experience is denominated for enchanting, not for
  territory, so `power.xpPerPower` converts — and it is the dial a server tunes to decide whether
  recovering from a bad night takes an evening or a week.

This also folds the two active modes together neatly. `pvp` restores from kills that drop XP
because a player drops XP; `pve` restores from mobs for the same reason; the rule is one sentence
and the modes only differ in what they *lose* power to.

### The seam

Same shape as everything else here — Standards owns the question, whoever is closer owns the
answer:

```java
Power.drain(player, 2.0, "factions:killed-by-player");
Power.restore(player, 0.5, "zombiemod:horde-cleared");
Power.of(player);          // current
Power.of(faction);         // the sum
```

LegendQuest knows a ritual failed; ZombieMod knows a horde was cleared; CityWorld knows a district
was defended. None of them should have to know what a chunk claim is, and Factions cannot possibly
enumerate what counts.

### Commands

```
/f power [player]        yours, or theirs
/f status                gains a power line: 18/20, and what that entitles you to
/f who <faction>         gains theirs, so you can see who is exposed
```

`/f status` naming the *exposure* — "you hold 22 chunks on an entitlement of 18; 4 are takeable" —
is the line that earns the feature. A raid you did not know was possible is indistinguishable from
a bug, which is the same reasoning that put narration on `/tpa`.

---

## 2. The faction bank

The original made most commands cost money and claims cost **more each time** — 30, then 45, then
60 — with a configurable refund on unclaim, defaulting to **70%**. Land became an investment with
a carrying cost rather than a thing you took because you were standing there.

**The bank is an account, not an economy provider.** Standards' `Economy` facade already decides
who holds *player* money, and exactly one provider wins that. A faction balance is a different
question — it is a container, like a chest — so Factions stores it and moves money in and out
through the facade. Nothing about this contests the provider seam, and a server running a
dedicated economy mod keeps it.

```
/f money                 balance
/f money deposit <n>     anyone — paying in is never gated
/f money withdraw <n>    officer+, configurable to leader-only
/f money pay <faction> <n>
```

Deposits ungated on purpose: a member who wants to help fund the next claim should never need
permission to give money away, and the griefing direction is withdrawal.

**Claims should be paid by the faction, not the claimer.** That is what makes the bank matter
rather than being a shared piggy bank nobody uses. It also gives a small faction something to do
together, and gives `/f money deposit` a point.

Pairs with power: a faction that is over-extended and losing land is also losing the 70% it would
have got back, so bad expansion is punished twice, which is correct.

---

## 3. Faction chat

Standards has a `ChatRouter` seam and **it has never had a real consumer** — only the self-test.
This is exactly the risk category `CLAUDE.md` names, so faction chat is worth building partly to
find out what is wrong with the seam.

```
/f chat            cycle: public → faction → ally → public
/f chat faction    or say which
/f c <message>     one message, without switching
```

Ally chat is an addition, not a restoration; the original had faction-only. It is worth having
because an alliance with no way to talk is an alliance in a config file.

**It must go through the router.** A channel that cancels `ServerChatEvent` itself bypasses mutes,
and a mute that only silences public chat is a lie — the muted player simply switches channel. The
seam exists so the gate cannot be skipped, and being its first consumer means proving that.

`/f chatspy` for staff, as the original had, gated on a permission node.

---

## Worth stealing from the archive

Read for intent, not lifted — the standing rule.

**Take, and two of these are urgent:**

- **`territoryEnemyDenyCommands`.** A list of command *names* that simply do not work while you are
  standing in enemy territory, defaulting to `home, sethome, spawn, tpahere, tpaccept, tpa`. This
  is remarkable: it is the combat-logging fix, solved in 2012, and it works by blocking **other
  plugins' escape hatches** rather than only its own. It belongs in
  [`COMBAT-API.md`](../SableCraft-Standards/COMBAT-API.md) — a combat tag answers "was I fighting",
  and this answers "am I somewhere I should not be able to leave from", which is a different and
  cheaper question with no damage event required. Standards owns the commands, so Standards should
  own the gate, with Factions supplying the territory answer through the claims seam.
- **`/f stuck`.** A slow teleport out of enemy land you are trapped in. This is the answer to the
  powder-snow problem from earlier today that I could not find: protection stays absolute, and the
  way out is a long, cancellable, announced teleport rather than a hole in the claim rules. Slow
  on purpose — it must be useless as a combat escape and adequate as a rescue.

- **Per-chunk ownership inside a faction** (`/f owner`). Sets a chunk to specific members, so a
  leader can have a vault their own recruits cannot open. The archive is candid that this exists
  because in-faction griefers exist, which is a real problem a claim system otherwise ignores.
- **Relation-based interaction rules.** The original protected doors, chests, furnaces, dispensers
  and repeaters by default and told owners to put a pressure plate outside for guests. That
  pressure-plate line is a whole design philosophy: protection you can deliberately open a hole in
  beats protection you must switch off.
- **Damage reduction in your own territory.** Not immunity — a percentage. Defenders should have
  an edge at home, and it gives claiming a benefit beyond keeping people out.
- **`/f open`** — join without an invitation. Now that `/f request` exists, this is the same
  question already half-answered: an open faction accepts anyone, a closed one queues them.
- **Safe zones and war zones.** Spawn should not be claimable; an arena should have friendly fire
  and no power loss. Both are one flag on a claim rather than a new system.
- **`/f description` and `/f title`.** The archive calls titles "just fun", which is exactly why
  they are worth having. FactionsUUID adds `/f announce`, a faction MOTD shown to members who log
  in later — which is the half that makes it useful to a leader who is not online at 3am.
- **A faction home you cannot use with an enemy nearby.** `homesTeleportAllowedEnemyDistance = 32`.
  A narrower, simpler version of the combat tag that needs no damage tracking at all.
- **Inactivity pruning with a tick budget.** Members auto-leave after N days idle, swept
  incrementally with an explicit *"max 5ms per tick, which is 10% of a tick"* cap rather than one
  stop-the-world pass. The budget is the part worth copying.
- **TRUCE, as a fourth relation.** Added in 2.x, and the distinction is sharp: **truce is a
  ceasefire, ally is shared infrastructure.** Neither can hurt the other, but a truce grants no
  door or chest access and — crucially — *truce land is still overclaimable*. It gives two
  factions a way to stop fighting without trusting each other, which is most of the ceasefires
  anybody actually wants.

**Leave:**

- **Spout capes and floating tags.** Client mod. Decision 2 — a vanilla client gets everything.
- **`/f config` editing live config from chat.** A live server is not the place to discover a typo
  in a number that governs land ownership.
- **Power loss on offline time**, as a default. See above.
- **`/f noboom`** as a peaceful-only perk. Explosion control should be a claim setting for
  everyone, not a reward for a diplomatic stance.

---

## The mode switch has a precedent

Worth knowing before building it: FactionsUUID already put land control behind an interface, with
power as one implementation and **DTR** — "deaths till raidable", one pooled faction counter rather
than per-player power — as the other, chosen by a single config line. Its shape is
`isRaidable`, `hasLandInflation`, `getLandLimit`, `canJoinFaction`, `canLeaveFaction`, `onDeath`,
`onRespawn`, `onQuit`, `onJoin`.

That is independent confirmation that `power.mode` wants to be an interface rather than a switch
statement threaded through the claim code — and it names the methods we would have discovered one
at a time. DTR itself becomes a fourth mode later at almost no cost, which is worth having: it is
the convention competitive servers expect, and the one thing `fixed`, `pvp` and `pve` cannot
express is a faction-wide pool that individual deaths draw down.

## Order

1. **Faction chat** — smallest, and it exercises a seam nothing has ever used.
2. **The bank** — self-contained, and claim costs give power something to interact with.
3. **Power** — largest, and wants the other two in place so `/f status` has everything to say at
   once.

Nothing here is stable until built. Related: [`README.md`](README.md),
[`../SableCraft-Standards/ECONOMY-API.md`](../SableCraft-Standards/ECONOMY-API.md),
[`../SableCraft-Standards/COMBAT-API.md`](../SableCraft-Standards/COMBAT-API.md).
