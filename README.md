# Factions ReForged

Land, allegiance, and the people you hold it with.

Inspired by [MassiveCraft's Factions](https://github.com/MassiveCraft/Factions), which is where
most people's mental model of this comes from. **Not a port of it** — the original is a decade of
Bukkit, and the interesting parts of the idea deserve a modern implementation rather than a
faithful one.

Requires **[SableCraft Standards](../SableCraft-Standards)**, and not optionally.

## What it does

```
/f create <name>         found one
/f invite <player>       officer+
/f join <name>           take an invitation
/f claim | unclaim       the chunk you are stood in
/f sethome | home        on your own land, with Standards' warmup and safe landing
/f ally|enemy|neutral    declare towards another faction
/f peaceful              opt out of fighting entirely
/f tag SBL               the short label chat uses
/f map [item]            see below
/f borders               show the edges
/f who <name> | list
```

## Three things worth the read

### The map is a real map

A vanilla map's scale sets how many blocks each pixel covers: `1 << scale`. At the maximum scale of
4 that is sixteen blocks — **exactly one chunk per pixel**. A fully zoomed-out vanilla map is
already a 128×128 chunk grid with its pixels on chunk boundaries, which is precisely the shape a
claims map wants and is not something anybody designed on purpose.

So `/f map item` hands you an ordinary `filled_map` that we painted: green yours, blue allied, red
hostile, white everyone else, black wilderness. It is **locked**, the way a cartography table locks
one, so vanilla does not slowly repaint it with terrain as you walk.

The four brightness levels each map colour has are spent on **edges** — a chunk with a
differently-owned neighbour is drawn bright, the interior dim. A field of flat colour becomes an
outlined territory you can read the shape of.

An unmodded client renders all of this. The server owns the pixels and sends them; there is no
client mod, no resource pack and no rendering code.

`/f map` on its own gives the classic chat grid, for when you have no hands free.

### Borders are only drawn where they are borders

The obvious implementation outlines every chunk and produces a grid, which tells you where chunks
are — something you already knew. Each side is drawn only when the chunk beyond it has a different
owner, so interior lines vanish and what is left is the outline of the territory.

Coloured by **relation, not identity**: colouring by faction would need a legend and a good memory,
while colouring by what it means to you needs neither, and is the only question you are asking when
you walk up to a line.

Two ways to see them: `/f borders` for surveying, or just **hold the tool** — a compass by default.
Pick it up, see what you are doing, put it down. The same shape as vanilla's debug stick, and
nobody leaves it on and forgets why their screen is full of dust.

### Allies agree, enemies do not

Declaring an ally is a *wish*: two factions are allied only when both have said so, and the offer
is announced to the other side so it does not look like nothing happened. Declaring an enemy takes
effect immediately and alone.

You cannot conscript a friend, and you cannot decline to be somebody's target by not filing the
paperwork.

**Peaceful** is a faction-level declaration rather than a server config flag, so a co-operative
corner can exist on a server that otherwise fights. It holds in both directions: a peaceful faction
cannot declare enemies and cannot be declared upon.

## What it borrows

Standards is a hard dependency, and that is the architecture rather than a shortcut. Factions
publishes the two things it knows — who is allied with whom, and who owns this chunk — through
Standards' **groups** and **claims** seams, and takes everything else back:

| | from |
|---|---|
| chat tags | registering as a group kind; there is no chat code in this mod |
| homes, warmups, safe landings | Standards' teleports |
| every player-facing string | Standards' `messages.yml`, contributed at setup |

That last one means **one catalogue for the whole server**. Rename "faction" to "clan" in one file
and both mods follow.

To put faction tags in chat, add the kind to Standards' config:

```toml
groupTagKinds = ["factions:faction", "standards:group"]
```

Listed outermost-first, so a faction tag renders furthest from the name.

### The rule that keeps it honest

- **`api/`** — the seams. Swappable: an FTB Chunks bridge must be able to answer anything Factions
  can. If Factions needs a seam to grow, the seam is wrong and should grow.
- **`neoforge/`** — shared utilities. Fair game for a hard dependant; reimplementing them would
  give one player two different messages about the same cooldown. What it may not do is reach past
  a public method into internal state.

## Building

Its build is driven from the Standards project next door:

```bash
cd ../SableCraft-Standards
./gradlew :factions:build
./gradlew runServer          # loads both mods
```
