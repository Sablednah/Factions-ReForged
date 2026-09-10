/**
 * Faction territory and standards on JourneyMap.
 *
 * <h2>Read CityWorld's first</h2>
 *
 * <p>{@code me.daddychurchill.CityWorld.integration.journeymap.package-info} is the reference for
 * this whole approach — the server/client split, the soft-dependency discipline, wrapping every
 * callback, isolating what versions rename, and eight traps each worth an hour. All of it applies
 * here and none of it is repeated. <b>This file records only what Factions does differently, and
 * why.</b>
 *
 * <h2>1. ⚠ Claims move; a city plan does not</h2>
 *
 * <p>CityWorld sends a platmap's overlay once and never re-sends it, because a plan never changes.
 * Territory changes every time anybody claims a chunk. So {@link com.sablednah.factions.FactionsMapEvents}
 * exists — an internal hook fired by the store's mutators — and the server plugin pushes on change
 * rather than on arrival.
 *
 * <p>Re-showing the same overlay id is the update mechanism, which is why the id is per faction and
 * dimension. Sending the whole set rather than a delta is deliberate: a faction that lost its last
 * chunk stops being drawn only because a set that no longer contains it replaced the one that did.
 *
 * <h2>2. ⚠ Every polygon is per viewer, which CityWorld's are not</h2>
 *
 * <p>A border's colour is a <b>relation</b>, and a relation belongs to a pair rather than to a
 * faction. The same chunk is white to its owner, red to somebody they declared on, and grey to a
 * bystander, at the same instant. There is no "the overlay for Ashfell" to cache; there is only
 * "Ashfell as seen by this player".
 *
 * <p>The fill is the opposite and is the same for everybody: the faction's own banner colour, which
 * is its identity rather than its politics. <b>Border says what it is to you, fill says who it
 * is.</b>
 *
 * <h2>3. Clicking a chunk runs a command</h2>
 *
 * <p>Claim mode sends {@code /f claim <x> <z>} exactly as if it had been typed — no packet, no
 * second code path, and no way for the map to reach something a player at a keyboard cannot. That
 * is Standards' decision 2 applied to a map: <em>same answers, nicer surface</em>. The coordinate
 * forms were added to {@code FactionCommands} for this and share one body with the bare forms, so
 * the rules cannot drift between them.
 *
 * <p>It is not a shortcut past those rules. A claim must still connect to land the faction holds,
 * still costs, still fits the limit, still needs a raid to take somebody else's. The furthest a
 * click can reach is one chunk beyond a border somebody already walked to.
 *
 * <h2>4. Whether to offer claim mode is answered, not guessed</h2>
 *
 * <p>The button appears only when the server has told this client it may claim — read from
 * Standards' capability set, the same one the action bar draws from, where {@code factions:claim}
 * already appears when the player is in a faction. No permission packet of our own. The button and
 * the action bar therefore cannot disagree, and a client cannot show itself a button by lying,
 * because the server refuses the command regardless.
 *
 * <h2>5. ⚠ Standards are pinned for allies only</h2>
 *
 * <p>A standard's position is exactly what an enemy wants, and the map must not become a targeting
 * aid the game does not otherwise provide — you find somebody else's flag by going and looking.
 * Waypoints go to the owning faction and its allies and nobody else. Same rule as above wearing a
 * different hat: a nicer surface for what you could already learn, never a new capability.
 *
 * <h2>What is not built yet</h2>
 *
 * <ul>
 *   <li><b>Nothing has been seen on a real map.</b> Every claim here is compile-verified and
 *       reasoned; the polygons, the colours, the icons and the click handling have never been
 *       rendered. JourneyMap is not in the dev server's mods folder yet — CityWorld's notes are
 *       clear that <em>only a real client catches a client-plugin crash</em>, and that both of its
 *       crashes reached a player.
 *   <li><b>Hover info leans on JourneyMap's own polygon tooltip</b> rather than a drawn one. The
 *       title is built in {@link com.sablednah.factions.integration.journeymap.ClaimsOverlay};
 *       whether it presents well is unknown. CityWorld ended up drawing its own on NeoForge's
 *       {@code ScreenEvent.Render.Post} after finding info slots belong to the minimap and the
 *       fullscreen block-info bar is read-only to addons.
 *   <li>No vertical merging of claim runs, no per-player overlay budget, and no minimap-specific
 *       treatment.
 * </ul>
 */
package com.sablednah.factions.integration.journeymap;
