# Factions ReForged — configuration

Every setting has a comment in `config/factions-common.toml` saying **why** it is there, not just
what it does. This page is the shape above that: three worked profiles, and the handful of settings
that actually decide what kind of server you are running.

Some of what governs faction play lives in **Standards**, because it is not faction-specific —
combat tagging in particular. Both files are named where it matters.

---

## The five settings that decide everything

Change nothing else and you have already chosen your server.

| setting | file | what it really decides |
|---|---|---|
| `power.mode` | factions | whether land can be **taken**, and by what |
| `pvp.betweenFactions` | factions | whether players can fight at all |
| `combat.pvpSeconds` | standards | whether a fight can be walked out of |
| `claims.chunksPerMember` | factions | how much of the map a group can hold |
| `money.claimCost` | factions | whether land is bought or merely taken |

---

## Profile 1 — Peaceful

*A build server where factions are a claim system and a chat tag, and nothing else.*

Nobody fights, nothing is taken, and a claim is a promise that your work is safe. The faction
mechanics are still worth having: shared homes, a bank, chat channels, and a border you can see.

```toml
# factions-common.toml
[claims]
  chunksPerMember = 32          # generous: nobody is competing for it
  mustBeConnected = true
  protectInteraction = true
  alliesMayInteract = true
  alliesMayBuild = false
  blockMobExplosions = true
  blockTnt = true               # nothing gets through a wall

[pvp]
  betweenFactions = false       # the whole branch goes quiet
  withinAFaction = false
  betweenAllies = false

[power]
  mode = "fixed"                # land is never takeable

[money]
  claimCost = 0.0               # land is free
```

```toml
# standards-common.toml
[combat]
  pvpSeconds = 0                # there is no PvP to tag
  pveSeconds = 0                # and the world will not trap you either
```

**Why `pvpSeconds = 0` rather than leaving it:** a duration of zero disables the whole kind. With
nobody fighting, a combat tag can only ever be an inconvenience — somebody unable to `/home`
because a skeleton found them.

---

## Profile 2 — Cosy PvE

*The world is the enemy. Territory is real and losable, but only to your own carelessness.*

This is the one no faction plugin has traditionally offered, and the one that fits a survival or
apocalypse server. Your borders shrink when the **world** beats you, and an enemy faction can take
the difference — but nobody can simply decide to hunt you.

```toml
# factions-common.toml
[claims]
  chunksPerMember = 16
  mustBeConnected = true
  blockMobExplosions = true     # a creeper is not a raid
  blockTnt = true

[pvp]
  betweenFactions = false       # or true, if you want raids to be possible but rare
  withinAFaction = false
  betweenAllies = false

[power]
  mode = "pve"                  # dying to the world costs you land
  maxPerPlayer = 10.0
  perDeath = 2.0
  perMinuteOnline = 0.2         # fifty minutes from empty to full
  perExperience = 0.02          # killing things brings it back faster
  freezeSecondsAfterDeath = 30
  overclaimEnemiesOnly = true
  regenWithStandard = 1.0
  regenWithoutStandard = 0.5
  glowWhileCarrying = true

[money]
  claimCost = 30.0              # land is an investment
  claimCostGrowth = 0.5
  claimRefund = 0.7
```

```toml
# standards-common.toml
[combat]
  pvpSeconds = 12
  pveSeconds = 8
  pvpBlocksTeleport = true
  pveBlocksTeleport = false     # a skeleton must not stop you going home
```

**The dial that sets the feel** is `perMinuteOnline`. At 0.2 a bad night is recovered from in an
evening. Halve it and a raid window is something people plan a week around.

**Set `claimCost` on a fresh world.** Refunds are priced by position rather than by receipt, so
turning costs on later lets established landholders release their way to a fortune, once.

---

## Profile 3 — War

*Land is held, not owned. Everything is takeable and everything is defended.*

```toml
# factions-common.toml
[claims]
  chunksPerMember = 10          # tighter: land should be contested
  mustBeConnected = true
  protectInteraction = true
  alliesMayInteract = true
  alliesMayBuild = false
  blockMobExplosions = true     # creepers are still not a raid
  blockTnt = false              # BUT TNT IS. This is the siege tool
  anyoneMayRotateFrames = true  # a calling card

[pvp]
  betweenFactions = true
  withinAFaction = false
  betweenAllies = false

[power]
  mode = "both"                 # every death costs, whoever dealt it
  maxPerPlayer = 10.0
  perDeath = 4.0                # the original's number: two deaths is 80% of your land
  perMinuteOnline = 0.2
  perExperience = 0.02
  freezeSecondsAfterDeath = 60  # a raid cannot be outrun by the clock
  overclaimEnemiesOnly = true   # a war is declared, not stumbled into
  regenWithStandard = 1.0
  regenWithoutStandard = 0.4    # a flag matters more here
  regenWithCapturedStandard = 0.4
  glowWhileCarrying = true

[money]
  claimCost = 30.0
  claimCostGrowth = 0.5
  claimRefund = 0.5             # losing land hurts
```

```toml
# standards-common.toml
[combat]
  pvpSeconds = 15
  pveSeconds = 8
  pvpBlocksTeleport = true
  skillBlocksTeleport = true    # no escaping a fight with a spell either
```

**`blockTnt = false` is the whole profile in one line.** Placing a block in somebody's claim is
still refused, so the way through a wall is to *deliver* a charge — which is why TNT cannons were
the entire meta in 2012, and why a siege stays a skill rather than a formality.

**Modern practice is short combat tags.** Ten to fifteen seconds, against the thirty to sixty of
the Factions era: long tags punish ordinary play, and being unable to `/home` for a minute because
a skeleton shot you erodes trust in the mechanic faster than the occasional escape does.

---

## Two settings people get wrong

**`overclaimEnemiesOnly = false`** makes any faction able to take an over-extended neighbour's land
without declaring anything. It is the original's behaviour and it is more chaotic than it sounds:
raids stop being events and become weather.

**`regenWithoutStandard`** is a penalty on the faction that has *not* planted a flag — which is a
brand-new one-person faction, and also exactly who can least afford to stand a banner in the open.
Do not set it so low that somebody's first evening is spent unable to claim anything. If you want a
standard to feel essential, raise `regenWithStandard` above 1.0 instead of dropping the floor.
