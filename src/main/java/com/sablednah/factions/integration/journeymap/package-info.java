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
 * <h2>What has actually been seen, 2026-09-11</h2>
 *
 * <p>Rendered on a driven client on the test machine, with JourneyMap 6.0.0 present on both server
 * and client. <b>Confirmed from screenshots, not from logs:</b>
 *
 * <ul>
 *   <li>both plugins are discovered and initialise — {@code Found @JourneyMapPlugin} for the client
 *       half, {@code JourneyMap server API found} for the server half, no exception from either;
 *   <li>territory polygons draw, on the fullscreen map <em>and</em> the minimap;
 *   <li><b>relation borders are right</b>: the player's own chunk outlined white, a hostile
 *       neighbour red, an allied one green, all in one frame;
 *   <li><b>the hover tooltip works natively</b> — {@code Vivotopia · 1 chunk · 21 members · power
 *       210} appears from JourneyMap's own polygon title, so no drawn tooltip is needed. That is a
 *       real divergence from CityWorld, which had to draw its own after finding info slots belong
 *       to the minimap;
 *   <li>no error in {@code runBuddy/journeymap/journeymap.log}, which is where map-side errors go
 *       rather than {@code logs/latest.log}.
 * </ul>
 *
 * <p>And the rest of it, on a second pass:
 *
 * <ul>
 *   <li><b>Both toolbar buttons render</b>, with our own icons, in JourneyMap's addon column down
 *       the right edge of the fullscreen map — not in the top toolbar, which is where I looked
 *       first. Hovering gives {@code Claim mode off};
 *   <li><b>claim mode works end to end</b>: toggle it, click a chunk, and the click becomes
 *       {@code /f claim <x> <z>}. Two chunks were claimed that way —
 *       {@code Claimed 1, -1. (2/336 chunks)} — and a click on distant ground was refused with
 *       <em>"Claims must touch land you already hold."</em>, which is the server's own rule
 *       arriving through the map with nothing special done for it;
 *   <li><b>the overlay updates live</b> after a claim, which is {@link com.sablednah.factions.FactionsMapEvents}
 *       doing its job.
 * </ul>
 *
 * <h2>⚠ Still unverified</h2>
 *
 * <ul>
 *   <li>Right-click to unclaim — the command is self-tested, the click path is not.
 *   <li>Standard waypoints: no standard was planted during the run.
 *   <li>The layer toggle switches the option, but nothing confirmed the polygons actually vanish.
 * </ul>
 *
 * <h2>⚠ How this was nearly reported wrong</h2>
 *
 * <p>The first pass concluded the buttons "were not found on screen" and wrote up claim mode as
 * unreachable. The buttons were fine; <b>the client had lost its connection</b> and the map was
 * never open, so the keypress did nothing and the callback never fired. A feature was declared
 * broken on the strength of a test that could not have exercised it.
 *
 * <p>The fix was to stop hunting pixels and ask the code — one log line in the button callback,
 * which then said plainly that JourneyMap had asked and both buttons were added. <b>When a UI looks
 * absent, first prove the code ran.</b> "Never called" and "drew somewhere I did not look" are
 * indistinguishable from a screenshot and trivially distinguishable from a log.
 *
 * <p>⚠ The connection loss had its own cause worth knowing: <b>the dev server and the dev client
 * share a Gradle daemon</b>, and stopping the client took the server with it — the server log ends
 * in {@code BUILD SUCCESSFUL}, which looks like a clean shutdown somebody asked for. Restart the
 * server after stopping a client, or check the port before trusting a test.
 *
 * <p>⚠ <b>The magenta squares on the test machine are not ours.</b>
 * {@code [RegionTexture] Can't bind texture: java.lang.IllegalArgumentException}, repeatedly, in
 * JourneyMap's own log — llvmpipe software rendering failing to bind region textures. Judging any
 * fill or opacity against that background proves nothing, and chasing it as an overlay bug would
 * waste an evening.
 *
 */
package com.sablednah.factions.integration.journeymap;
