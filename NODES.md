# Permissions and access

**Status: accurate as of 1.3.0, and hand-written — unlike Standards' `NODES.md`, which is
generated.** The difference is the point of this document.

## Factions declares exactly one permission node

| Node | Default | What it allows |
|---|---|---|
| `factions.bypass` | operators | Turn the **claim override** on with `/f bypass`. Grantable to a moderator so they can undo a grief inside a claim without being made an operator and handed `/stop` with it. |

That is the whole node table, and the shortness is the design rather than an omission. Factions
gates on two other things, neither of which is a permission.

**1. Your rank inside your own faction.** Leader, officer, member. This is game state, not a
permission — you get it by being promoted, and no permissions mod can or should grant it. A server
owner cannot hand somebody "officer" any more than they can hand somebody "has a faction".

**2. Operator level, for exactly two things.** `/f chatspy` and `/f fixture`.

So this file is mostly a rank matrix. There is very little here for LuckPerms or `/rank` to
configure, and that is correct: most of what `/f` does is decided by a faction, not by a server.

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
| `/f bypass` | **operator**, or anyone granted `factions.bypass` |
| `/f chatspy` | **operator** |
| `/f fixture seed` `/f fixture clear` | **operator**, and only when fixtures are enabled in config |

**Depositing is ungated and withdrawing is not**, on purpose: money going in cannot grief anybody
and money coming out can.

⚠ **`ally` is two different commands.** `/f ally <faction>` declares a relation and needs officer;
`/f chat ally` picks a chat channel and needs only membership. They share a word and nothing else,
which is worth knowing before reading either row as covering the other.

## The gaps this document was written to record

One is answered and one is still open. Both were found by writing this file, which is the argument
for having written it.

### There is no way to restrict who founds a faction

`/f create` is open to anybody who can type it. A server that wants factions limited to players
past a certain point — a rank, a playtime, a whitelist — has no lever at all. A single
`factions.create` node would give it one, and would cost nothing to anybody who does not use it.

### ~~Staff cannot build in another faction's claim~~ — answered

**Closed, and worth reading for the shape of the answer rather than the fact of it.**

The obvious fix is one line in `FactionProtection`: if the player is an operator, let them build.
That is wrong, and not for security reasons — staff are trusted. It is wrong because of
**attention**. An always-on override means every operator spends every session able to break
somebody's base by accident, with nothing to tell them whose land they are stood on, and the
resulting mistake looks exactly like a grief to the faction that finds it.

So the override is a **state you enter on purpose**: `/f bypass on`, do the job, `/f bypass off`.
It is dropped when you log out — the only piece of state in either mod designed to be lost.
Standards' switches persist across a logout because forgetting you can fly is harmless; forgetting
you can edit everybody's land is not. A staff member who logs off mid-job comes back with the
protection on and has to decide again.

**That decision is the feature.** Editing claimed land should cost a thought every time.

It takes `on`/`off`/`toggle` like every switch in Standards, so a macro or a command block can turn
it *off* reliably rather than guessing at a toggle, and every use is written to the server log —
the person asking about an override later is never the person who used it.

## If the node list ever grows

Generate it rather than maintaining it, exactly as Standards does: `scripts/nodes.py` there parses
the declarations out of the source, because a hand-kept list of nodes is the document that ships
stale and nobody notices — a missing node reads precisely like a node that does not exist.

One node is not worth a generator. Three would be. The rank matrix above
lives inside command handlers as `atLeast(ctx, player, Rank.OFFICER)` calls, which is not something
a parser can map back to command names with any confidence. **So it has to be checked by reading
when `/f` changes** — which is a real maintenance cost, and one more argument for nodes.
