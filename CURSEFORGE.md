# Factions ReForged — land, allegiance, and the people you hold it with

**Claim chunks, declare allies and enemies, and hold territory you can see the edge of.** A modern
rebuild of the faction mechanic that defined a generation of servers — chunk claims, war and
alliance, a faction bank, faction chat, and borders drawn in particles that follow the ground.

**Your players do not need to install anything.** The particle borders, the action-bar territory
names, the chat channels and the claims map are all server-side: they work on a stock client off
the Mojang launcher, with no mod and no resource pack.

---

## The map is a real map

A vanilla map's scale sets how many blocks each pixel covers. At maximum zoom-out that is sixteen
blocks — **exactly one chunk per pixel**. A fully zoomed-out vanilla map is already a 128×128 chunk
grid aligned to chunk boundaries, which is precisely what a claims map wants and is not something
anybody designed on purpose.

So **`/f map item` hands you an ordinary filled map that we painted**: green yours, blue allied, red
hostile, white everyone else, black wilderness. Colour is *relation*, not identity — identity needs
a legend and a good memory, relation is the question you are actually asking. The four brightness
levels each map colour has are spent on **edges**, so a field of flat colour becomes an outlined
territory you can read the shape of.

`/f map item <zoom>` trades coverage for detail — 64, 32 or 16 chunks across instead of 128 — and
the outline stays one pixel wide at every level. `/f map` on its own gives the classic chat grid,
for when you have no hands free.

## Borders you can walk

Not a grid. **Each side of a chunk is drawn only where ownership actually changes**, so interior
lines vanish and what is left is the outline of the territory — the thing you wanted to know.

**They stand on the ground, not at your feet.** Drawn at a flat height a border is buried in the
first hill it crosses and floating over the first valley, and being underground is worst at the
exact moment you are walking over the line. Each column sits on the surface beneath it.

Two ways to see them: `/f borders` for surveying, or just **hold a compass** — pick the tool up, see
what you are doing, put it down. The same shape as vanilla's debug stick, and nobody leaves the
display on and forgets why their screen is full of dust.

Walk across a border and the **action bar names whose land you have entered**, once, on the way in.

## Protection that covers the actual thefts

| | |
|---|---|
| **Blocks** | Break and place, members only. Allies do not build in your land by default — an alliance is a diplomatic position and those change. |
| **Right-clicks** | Doors, buttons, levers, chests, furnaces. **Not a list of block types** — a list is something somebody has to maintain, and every modded block is missing from it. A stranger who cannot mine your chest could still *open* it, and that is the theft the claim was bought to prevent. |
| **Pressure plates** | Still work, deliberately. Put one outside the door for visitors: protection you can open a hole in beats protection you have to switch off. |
| **Item frames, armour stands, paintings** | Covered separately, because none of them are blocks and every block-shaped guard misses them. |
| **Explosions** | Filtered per block, so a creeper on the wilderness side of your wall craters the wilderness and leaves the wall standing. You still take the damage. TNT is a separate setting, because on a PvP server it *is* the siege tool. |

A refusal **says no where the no happened** — red dust at the exact face you clicked and a low
thud, rather than a line of text at the top of the screen while the disappointment is under your
cursor.

## Claiming, and walking

`/f claim` takes the chunk you are stood in, capped per member so recruiting has a point beyond the
numbers and one person cannot fence off a continent. Claims must touch land you already hold, so
territory is one shape rather than a scatter of squares.

**`/f autoclaim` makes the walk the claim** — and the feature is knowing when to stop. Running out
of land switches it off rather than repeating itself every sixteen blocks. Walking through somebody
else's territory says nothing at all, because that is a journey and not a failed claim. A standing
reason is given once, not per chunk.

## Diplomacy, and the asymmetry that matters

**Allies must agree; enemies need not.** An alliance holds only when both factions have declared
it. A war needs one declaration. You cannot conscript a friend, and you cannot decline to be
somebody's target by not filing the paperwork.

**`/f peaceful`** opts a faction out entirely — it cannot declare and cannot be declared upon, in
both directions, so opting out is not the same as disarming yourself while everyone else keeps
shooting.

**`/f status`** answers the questions nothing else will. An offered alliance is announced once and
never mentioned again, so an offer made while you were offline is otherwise invisible forever;
being declared upon is worse, in that you find out by being killed. Everything is split by
**direction** — an offer *to* you is a decision waiting on you, an offer *from* you is a wait —
because a single "pending" list hides which is which behind a name you have to recognise.

Somebody with **no faction** gets a status too: their invitations and their outstanding requests,
which are the two lists otherwise invisible to exactly the person who most needs them.

## Joining, both ways round

`/f invite` finds a specific person, which only helps if an officer already knows who they want.
**`/f request` is the other direction** — for the player who logged in an hour ago, read a tag in
chat, and knows nobody. It is shown only to whoever can answer it, because a request nobody sees
sits there until the asker concludes they were ignored.

## Faction chat, and the mute that holds

`/f chat` cycles public → faction → ally. `/f c <message>` sends one line without switching, which
is most of what people actually want. `/f chatspy` lets staff watch.

It runs through **Standards' chat router** rather than its own event listener, and that is not a
detail: a channel that cancels chat itself runs *before* the mute check, so a muted player would
simply switch channel and carry on. A mute that only silences public chat is not a mute.

## Money

A **faction bank**. Deposits are ungated — a member funding the next claim should never need
permission to give money away — and withdrawals are officer-gated, because that is the direction
that can grief.

**Faction-to-faction payment, with a reason**, which is how tribute and ransom get paid. Money
arriving with no explanation is money the recipient treats as a bug.

Claims can **cost money**, at a price that rises with every chunk held: a flat price means the
largest faction, the one that needs land least, buys it most easily. Refunds are priced at the
position the chunk occupied rather than today's price. Off by default, so a server with no economy
mod never finds claiming silently impossible.

## For server owners

Every behaviour above is a config value, and the comments say **why** rather than what. PvP between
factions, PvP inside one, connected claims, chunks per member, border refresh rate and radius, the
held tool, ally permissions, explosion handling, claim costs and refunds, who may accept a request.

**`/f fixture seed`** invents nine neighbour factions with every relation state — allied, offered,
hostile, neutral, peaceful — because two people cannot test a relation system and inviting six
friends to sit still while you declare war on them is not a test plan. Off unless you ask for it.

Strings live in Standards' single `messages.yml`, so there is one file for the whole server, one set
of colour rules and one merge-on-upgrade behaviour.

## Requirements

| Minecraft | NeoForge | Java | Depends on |
|---|---|---|---|
| 1.21.11 | 21.11.42+ | 21 | [SableCraft Standards](https://www.curseforge.com/minecraft/mc-mods/sablecraft-standards) 1.0.1+ |

Standards is a **hard dependency and deliberately so**: the claims and groups seams, the message
catalogue, the safe-landing search, the teleport warmups, the chat router and the economy are all
borrowed rather than reimplemented. Install both on the server; your players install neither.

## What is not in this release

**Power.** The old plugin's mechanic where a faction that keeps dying holds less land than it has
claimed, and an enemy can take the difference. It is designed in full — including a PvE mode where
your territory shrinks when the *world* beats you, and a faction standard an enemy can steal — and
deliberately not shipped half-built. See `POWER.md` in the repository.

## Credits and licence

MIT. All original work.

Inspired by **MassiveCraft's Factions**, which defined this genre — read for intent, then built for
modern Minecraft. No code is taken from it.

By **Sablednah**. Source, issue tracker and design notes on GitHub.
