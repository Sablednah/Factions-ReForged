![Factions ReForged](https://media.forgecdn.net/attachments/description/null/description_369f6f82-b6d9-4a54-b6cd-3cfaa5d839b6.png)

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

**They are drawn on the line, not in the blocks beside it.** Particles sit on the corners of the
block grid — exactly where the boundary is — so there is nothing to work out. Centred inside the
outermost block instead, a border tells you which block is the edge one when the question you
actually have is which side of it you are standing on.

Where two claims meet, the shared line **alternates between their two colours** and swaps each
pulse, so a border between your land and an ally's shimmers between the two rather than one colour
winning.

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
| **Mobs** | Cannot chew through claimed land, answered through Standards' claims seam so a mob mod does not have to guess — ZombieMod asked for that hook and uses it. |
| **Explosions** | Filtered per block, so a creeper on the wilderness side of your wall craters the wilderness and leaves the wall standing. You still take the damage. TNT is a separate setting, because on a PvP server it *is* the siege tool. |

A refusal **says no where the no happened** — red dust at the exact face you clicked and a low
thud, rather than a line of text at the top of the screen while the disappointment is under your
cursor.

## Power — land you hold rather than own

Optional, and **off by default**: `power.mode = fixed` is exactly the behaviour above, so switching
to this changes a server rather than arriving with it.

Every player has power. Dying costs some; time online and killing things bring it back. A faction's
power is its members' added up — and here is the whole idea:

> **`chunksPerMember` is still the ceiling. Power decides how much of that ceiling you are actually
> holding onto.**

So power can only ever take entitlement *away*, never grant more than the fixed rule would. Turning
it on redistributes nobody's land; it adds a way to *lose* land by playing badly. And farming power
cannot inflate holdings, because the ceiling is membership — which kills the mob-grinder exploit
without a special case anywhere.

**When a faction holds more land than its power covers, a declared enemy may take the difference.**
That is the point of the whole system: without it, power is a claim limit with extra steps. Raids
start **at their border** and eat inward, so nobody reaches past a wall for the chunk with the vault
in it, and each chunk taken reduces the overreach by one — so a faction five chunks over stops being
takeable after five. **The attacker is rewarded for noticing, not for attacking.**

Four modes, differing only in what counts as *losing*:

| mode | you lose power when |
|---|---|
| `fixed` | never — power is inert |
| `pvp` | a player kills you |
| **`pve`** | **a mob kills you** |
| `both` | either |

**`pve` is the one no faction plugin has offered**, and it fits a survival or apocalypse server
exactly: your borders shrink when the *world* beats you and grow back as you push out.

**Falling in your own lava is not a raid.** Environmental deaths never cost power — nobody decided
it and nobody gains from it. Arrows and pets resolve to the person behind them, so a bow kill counts
exactly as a sword kill does.

**Experience brings it back.** A mob's XP drop is already Minecraft's own opinion of how hard it was
to kill — maintained by Mojang and extended for free by every mod on your server — so a boss from a
mob pack is worth more than a zombie without anyone maintaining a list of mob ids.

And **nobody finds out by walking home**: the victim is told the moment land changes hands, and
`/f status` says exactly how exposed you are — *"you hold 22 chunks on an entitlement of 18; 4 can
be taken."*

## The standard — a flag worth taking

Design a banner in a loom, plant it on your land, and it is your faction's standard. **Its colour
and pattern become your identity**, printed wherever your name appears — a faction personalised by
something it made rather than a setting it typed.

**It must see the sky.** That one rule is the whole game. Enemies cannot break your blocks or open
your doors, so the only way anyone reaches your flag is a path you left; without the rule everybody
entombs theirs in a sealed box and the feature is dead on arrival. It is re-checked continuously —
roof it over and it stops earning, uncover it and it resumes.

**Flying one is the difference between recovering at full speed and recovering slowly.** A reason to
have one and a reason to take somebody else's, which is the same number seen from two sides.
Breaking a standard is the *one* deliberate hole in claim protection — an enemy may take your flag
where they can take nothing else of yours.

**Carrying it home is the dangerous part.** A flag in your hands is a flag you are holding instead
of a sword, you **glow red through walls**, and its owner is told exactly where you are standing.
Plant it on your own land to fly it as a trophy — where they can come and take it back, and taking
it back is now their raid. Put it in a chest and you stop being a target, and stop denying them
anything.

Identified by faction id in the item's own data rather than by its name, because a name is something
anybody can type into an anvil. Duplicates are inert rather than policed.

## Claiming, and walking

`/f claim` takes the chunk you are stood in, capped per member so recruiting has a point beyond the
numbers and one person cannot fence off a continent. Claims must touch land you already hold, so
territory is one shape rather than a scatter of squares.

**`/f autoclaim` makes the walk the claim** — and the feature is knowing when to stop. Running out
of land switches it off rather than repeating itself every sixteen blocks. Walking through somebody
else's territory says nothing at all, because that is a journey and not a failed claim. A standing
reason is given once, not per chunk.

## Diplomacy, and the asymmetry that matters

**Allies cannot hurt each other**, which sounds obvious and was missing until 1.1.0 — an alliance
stopped you being overclaimed and did nothing whatever to stop you being shot. `pvp.betweenAllies`
turns friendly fire back on for a server that wants it.

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

## Editing inside somebody's claim — `/f bypass`

Protection has no back door for operators, on purpose. Instead there is a **state you enter
deliberately**: `/f bypass on`, do the job, `/f bypass off`.

An always-on override would mean every operator spends every session able to break somebody's base
by accident, with nothing on screen to say whose land they are stood on — and the resulting mistake
looks exactly like a grief to the faction that finds it. So the protection stays real the rest of
the time, which is what makes it a protection.

**It drops when you log out.** Come back tomorrow and you have to decide again. Editing claimed land
should cost a thought every time.

Takes `on` / `off` / `toggle` like every switch in Standards, so a macro or a command block can turn
it *off* reliably rather than guessing. Every use is written to the server log — the person asking
about an override afterwards is never the person who used it.

Gated on **`factions.bypass`**, the only permission node this mod declares, so a moderator can be
trusted with it without being made a full operator. Everything else under `/f` is gated on your rank
inside your own faction, which is game state rather than a permission: you become an officer by
being promoted, and no permissions mod should be able to hand that out.

## Requirements

Built for three Minecraft lines. **Take the jar that names your version** — every file carries it,
so `factions-1.2.0+mc26.1.2.jar` is the 26.1 one, and take the matching Standards jar with it.

| Minecraft | NeoForge | Java | Depends on |
|---|---|---|---|
| 1.21.11 | 21.11+ | **21** | [SableCraft Standards](https://www.curseforge.com/minecraft/mc-mods/sablecraft-standards) 1.4.0+ |
| 26.1.x | 26.1+ | **25** | Standards 1.4.0+ |
| 26.2.x | 26.2+ | **25** | Standards 1.4.0+ |

⚠ **26.x needs Java 25, not 21.** That is Minecraft's requirement rather than ours, and it is the
one thing here that will stop a server booting — with an error that does not obviously say so.

Standards is a **hard dependency and deliberately so**: the claims and groups seams, the message
catalogue, the safe-landing search, the teleport warmups, the chat router and the economy are all
borrowed rather than reimplemented. Install both on the server; your players install neither.

## What is not in this release

**`/f raid`** — declared attacks, with everyone glowing by side and, optionally, land only changing
hands during a raid rather than whenever somebody notices. Designed in `POWER.md`; not built,
because how a raid *ends* and whether it can be *declined* decide what game it is, and those are
worth settling properly rather than quickly.

## Credits and licence

MIT. All original work.

Inspired by **MassiveCraft's Factions**, which defined this genre — read for intent, then built for
modern Minecraft. No code is taken from it.

By **Sablednah**. Source, issue tracker and design notes on GitHub.
