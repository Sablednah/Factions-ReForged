# Permissions and access

**Status: accurate as of 1.1.1, and hand-written — unlike Standards' `NODES.md`, which is
generated.** The difference is the point of this document.

## Factions declares no permission nodes

Not an oversight, and worth stating plainly because the obvious assumption is the opposite:
`grep -r PermissionNode src/` returns nothing. Factions gates on two things instead.

**1. Your rank inside your own faction.** Leader, officer, member. This is game state, not a
permission — you get it by being promoted, and no permissions mod can or should grant it. A server
owner cannot hand somebody "officer" any more than they can hand somebody "has a faction".

**2. Operator level, for exactly two things.** `/f chatspy` and `/f fixture`.

That is the whole access model. There is nothing here for LuckPerms or `/rank` to configure, which
is why this file is a rank matrix rather than a node table.

## The rank matrix

⚠ **[`CURSEFORGE-COMMANDS.md`](CURSEFORGE-COMMANDS.md) is the authority**, and says the same thing
per command with the reasoning attached. This table is a summary for reading at a glance and for
the website — **so the two can disagree, and one day will**. Change `/f`'s gating and change both,
or delete this table and link there instead.

That duplication is a real cost and it is recorded rather than hidden: a second list of the same
facts is the shape of every stale document this project has caught itself shipping.

| Command | Who |
|---|---|
| `/f create` `/f join` `/f request` `/f list` `/f who` `/f map` `/f item` `/f power` `/f pay` | anybody |
| `/f requests` `/f accept` `/f decline` | whoever the invite or request is addressed to |
| `/f home` `/f money` `/f deposit` `/f leave` `/f status` `/f borders` `/f c` `/f ca` | any member |
| `/f chat faction` `/f chat ally` | any member — `/f chat public` needs no faction |
| `/f invite` `/f kick` `/f claim` `/f autoclaim` `/f unclaim` `/f sethome` `/f standard` | **officer** or above |
| `/f ally <faction>` `/f enemy <faction>` `/f neutral <faction>` | **officer** or above |
| `/f disband` `/f promote` `/f demote` `/f unclaimall` `/f tag` `/f rename` `/f peaceful` | **leader** |
| `/f withdraw` | **leader**, or **officer** when `officersMayWithdraw = true` |
| `/f chatspy` | **operator** |
| `/f fixture seed` `/f fixture clear` | **operator**, and only when fixtures are enabled in config |

**Depositing is ungated and withdrawing is not**, on purpose: money going in cannot grief anybody
and money coming out can.

⚠ **`ally` is two different commands.** `/f ally <faction>` declares a relation and needs officer;
`/f chat ally` picks a chat channel and needs only membership. They share a word and nothing else,
which is worth knowing before reading either row as covering the other.

## Two gaps, both real, neither yet decided

Recorded here rather than fixed quietly, because both change behaviour on a shipped mod.

### There is no way to restrict who founds a faction

`/f create` is open to anybody who can type it. A server that wants factions limited to players
past a certain point — a rank, a playtime, a whitelist — has no lever at all. A single
`factions.create` node would give it one, and would cost nothing to anybody who does not use it.

### Staff cannot build in another faction's claim

`FactionProtection.may()` asks who owns the chunk and which faction the player is in, and **never
consults operator status**. So a moderator undoing a grief inside somebody's claim cannot place a
block; their options are to join the faction or to unclaim the land.

This may well be deliberate — `POWER.md` is explicit that `/f stuck` exists so a *player* trapped
in a claim is answered without holing the protection, and the same instinct argues against a
blanket op override. But a moderator with a shovel is a different case from a player with an
escape route, and right now there is no distinction. A `factions.bypass` node, off by default,
would draw it.

## If Factions ever does declare nodes

Generate this file rather than maintaining it, exactly as Standards does: `scripts/nodes.py`
there parses the declarations out of the source, because a hand-kept list of nodes is the document
that ships stale and nobody notices — a missing node reads precisely like a node that does not
exist.

This file is hand-written only because there is nothing to generate from. The rank matrix above
lives inside command handlers as `atLeast(ctx, player, Rank.OFFICER)` calls, which is not something
a parser can map back to command names with any confidence. **So it has to be checked by reading
when `/f` changes** — which is a real maintenance cost, and one more argument for nodes.
