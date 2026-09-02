# Factions ReForged — commands reference

Source of truth: `src/main/java/com/sablednah/factions/FactionCommands.java`.

Everything is under **`/f`**. Three ranks — **member**, **officer**, **leader** — and each command
says which it needs. There is (almost) no permission node per command: rank inside the faction is the gate,
because "may this person claim land" is a question about the faction and not about the server.

Anything not listed as officer or leader is open to any member, and anything that does not need a
faction at all is open to everyone.

---

## Ranks at a glance

| | can |
|---|---|
| **Member** | build and interact in your own land, `/f home`, chat, deposit money, see everything |
| **Officer** | all of that, plus claim, unclaim, invite, kick, set the home, declare relations, withdraw money, and answer join requests |
| **Leader** | all of that, plus promote, demote, rename, set the tag, go peaceful, and disband |

`requests.officersMayAccept` moves answering join requests up to leader-only. It defaults to
**officers**, because an officer can already `/f invite` whoever they like — letting them recruit a
stranger but not one who asked first is a rule nobody could explain.

---

## Founding, joining, leaving

### `/f create <name>`

Found one. You become its leader. Names cannot carry colour codes — they are printed on other
people's screens, so they are text.

### `/f invite <player>` — officer

### `/f join <name>`

Take an invitation. Invitations are **not persisted**: one that survives a restart and gets accepted
three weeks later, by an officer who no longer remembers offering, is worse than one that lapses.

### `/f request <name>` · `/f requests` · `/f accept <player>` · `/f decline <player>`

The invitation the other way round, for the player who does not know anybody yet — which is exactly
who the invite flow cannot help.

Shown **only to whoever can answer it**, because a request nobody sees sits there until the asker
concludes the faction ignored them. `/f requests` lists who is waiting, oldest first. Joining
anywhere — or founding your own — clears your other requests, so no officer can accept somebody they
cannot have.

### `/f leave`

A leader cannot walk away from a faction that still has people in it: hand it on, or `/f disband`.
A leader **alone** just leaves.

### `/f disband` — leader

Ends it for everybody. The land, the home, the bank and the pending offers go with it.

### `/f kick <player>` — officer · `/f promote` · `/f demote <player>` — leader

## Land

### `/f claim` · `/f unclaim` — officer

The chunk you are stood in. Capped at `chunksPerMember` × members, so recruiting has a point beyond
the numbers and one person cannot fence off a continent — and the refusal names the arithmetic
rather than just saying no.

Claims must touch land you already hold (`mustBeConnected`, on by default). The first is exempt.

### `/f autoclaim` — officer

Take every chunk you walk into. **The feature is knowing when to stop**: running out of land
switches it off rather than reporting the same refusal every sixteen blocks, walking through
somebody else's territory says nothing at all because that is a journey and not a failed claim, and
a standing reason is given once rather than per chunk. Off when you log back in.

### `/f unclaimall` — officer

### `/f sethome` — officer · `/f home`

The home must be **on land you hold** — one outside it is one an enemy can camp. `/f home` uses
Standards' teleport, so the warmup, the countdown, the safe landing and cancel-on-damage all apply,
and it shows up in `/back list` as `/f home`.

## Power and the standard

Only when `power.mode` is not `fixed`.

### `/f power [player]`

Yours, or theirs. Then what your faction holds against its entitlement, how exposed you are if that
is over, and **how fast power comes back** with the reason — which is the standard's whole effect
made visible.

### `/f standard` — officer

Look at a banner on your own land under open sky and it becomes your faction's standard: its colour
and pattern become the faction's identity.

With nothing in front of you it answers **"where is my flag"** — on your tower, in an enemy's
courtyard, or in somebody's hands running for the border. It also lists the **trophies** you fly,
with where each one stands and whether it is still under open sky.

Planting a captured flag on your own land flies it as a trophy; no command needed, because the act
is the declaration. You may fly **one of your own and any number of captured ones** — but the power
bonus is the same however many you hold. What a wall of them buys is that an enemy has to come and
take *every one* before it stops paying, and each of them is standing somewhere they can reach.

## Seeing

### `/f map`

The classic chat grid, nine chunks square, coloured by your relation to each owner, with `+` for
you. No item, no inventory slot, no walking.

### `/f map item [zoom]`

**A real vanilla filled map that we painted.** At maximum zoom-out a map pixel covers exactly one
chunk, so a vanilla map is already a 128×128 chunk grid aligned to chunk boundaries.

Green yours, blue allied, red hostile, white other, black wilderness — **relation, not identity**,
because identity needs a legend and relation is the question you are actually asking. Edges are
drawn bright and interiors dim, so a field of flat colour becomes an outline.

`zoom` is 1 to 8 pixels per chunk: 1 shows 128 chunks, 2 shows 64, 4 shows 32, 8 shows 16. The
outline stays one pixel wide at every level. `map.pixelsPerChunk` sets the default.

The map is **locked**, the way a cartography table locks one, so vanilla does not slowly repaint it
with terrain as you carry it.

### `/f borders`

Show the outline of nearby claims in particles. **Or just hold a compass** — pick the tool up, see
what you are doing, put it down.

Only sides where ownership actually changes are drawn, so interior lines vanish and what is left is
the shape of the territory rather than a grid telling you where chunks are. The wall **stands on the
ground**, not at your feet.

### `/f status`

Where you stand with everybody, in one place, **split by direction**:

- alliances **offered to you** — a decision waiting on you
- alliances **you have offered** — a wait
- who you are allied with
- who **you declared on**, and who **declared on you**
- your bank, and what the next chunk costs
- who is waiting to join, if you can answer them

Everything here is otherwise invisible: an offered alliance is announced once and never mentioned
again, and being declared upon is something you find out by being killed.

**With no faction** you get your invitations and your outstanding requests, which are the two lists
nothing else will show you.

### `/f who <name>` · `/f list`

## Identity and diplomacy

### `/f tag <ABC>` · `/f tag -` — leader

Up to five characters, unique across the server, shown in chat. `-` removes it.

### `/f rename <name>` — leader

Keeps the faction's id, so claims, homes and relations do not move.

### `/f ally <faction>` · `/f enemy <faction>` · `/f neutral <faction>` — officer

**Allies must agree; enemies need not.** An alliance holds only when both sides have declared it,
and the offer is announced to the other faction so it does not look like nothing happened. A war
takes effect immediately and alone.

You cannot conscript a friend, and you cannot decline to be somebody's target by not filing the
paperwork.

### `/f peaceful` — leader

Cannot declare, cannot be declared upon — in **both** directions, so opting out is not the same as
disarming yourself while everyone else keeps shooting.

## Talking

### `/f chat [public|faction|ally]`

With no argument it cycles public → faction → ally → public. **Resets to public when you log back
in** — coming back from a crash still in faction chat is how a private remark reaches the wrong
room.

### `/f c <message>` · `/f ca <message>`

One line to your faction, or to your faction and its allies, without switching. This is most of what
people actually want.

Both go through Standards' chat router, so **a muted player cannot use them** — a channel that
cancelled chat itself would run before the mute check, and a mute that only silences public chat is
not a mute.

### `/f chatspy` — server operator

Watch every faction channel. Overheard messages are marked as such, so they cannot be mistaken for
a room you are in. A spy is never counted as an audience: a lone member still hears "nobody else is
listening", or the message would quietly announce that somebody is watching.

### `/f raid <faction>` — officer

Declare an attack. Announced to the **whole server**, and everyone involved glows by side —
attackers one colour, defenders another, the standard carrier keeping its own red.

It ends when you take their **standard and plant it on your own land** (attackers win), when you
take **ground from a faction flying no standard** (attackers win), when **every attacker is dead or
logged off** (defenders win), or when the **timer expires** (defenders held). The clock is a
backstop, not the mechanism: a raid that could only end on a timer is one nobody can win.

**Taking the flag does not end it — carrying it home does.** Stealing it is one lucky sprint; the
walk back through the people whose flag it is has always been the good part. So the raid keeps
running after the theft, the attackers can go for land as well, and a raid can be lost on the road
thirty seconds from home.

Against a faction that flies **no** standard there is nothing to steal, so taking their ground is
the win instead — and with the one-claim-per-raid limit that costs them exactly one chunk. The
moment they raise a flag it becomes the objective again, even if they raise it mid-raid.

**It cannot be declined**, and it does not need to be — a raid requires **defenders online** to
declare. That protects a small faction far better than declining could, since the faction that most
needs protecting is the one with nobody online. Peaceful factions neither raid nor are raided.

A cooldown stops the same attacker raiding the same target repeatedly. A raid never survives a
server restart.

`/f raids` (or a bare `/f raid`) lists what is running.

### `/f bypass [on|off|toggle]` — server operator, or `factions.bypass`

Edit inside other people's claims. **A state you turn on, not a permission you carry.**

An always-on operator override means every op spends every session able to break somebody's base by
accident, with nothing on screen to say whose land they are stood on — and the mistake that follows
looks exactly like a grief to the faction that finds it. So this is something you switch on, do the
job, and switch off.

**It drops when you log out**, on purpose. Come back tomorrow and the protection is on again; you
have to decide a second time. Editing claimed land should cost a thought every time.

Grantable through `factions.bypass` so a moderator can undo a grief without being made a full
operator. Every use is written to the server log — the person asking about an override later is
never the person who used it.

## Money

### `/f money`

The bank, and what the next chunk costs.

### `/f money deposit <amount>`

**Any member.** Paying in is never gated — somebody funding the next claim should not need
permission to give money away, and the direction that can grief is out.

### `/f money withdraw <amount>` — officer

`money.officersMayWithdraw` moves this to leader-only.

### `/f money pay <faction> <amount> [reason]` — officer

Faction to faction. The reason reaches them, which is what makes tribute and ransom possible —
money arriving with no explanation is money the recipient treats as a bug.

## Testing

### `/f fixture seed [chunks]` · `/f fixture clear` — operator, and off unless enabled

Invents nine neighbour factions around you covering **every** relation state: allied, offered to
you, waiting on you, hostile, neutral and peaceful.

Two people cannot test a relation system — those states need four counterparties, and inviting six
friends to sit still while you declare war on them is not a test plan.

They are not mocks: ordinary factions with ordinary claims, held by ordinary offline players whose
UUIDs are derived exactly as an offline-mode server derives them. They never claim a chunk somebody
already holds, and `clear` matches on the leader as well as the name, so a real faction that happens
to share a name survives.

Enable with `debug.fixtures = true`. Off by default, and **unregistered** rather than refused — an
operator who tab-completes their way into inventing eight factions was failed by the mod, not by
their fingers.
