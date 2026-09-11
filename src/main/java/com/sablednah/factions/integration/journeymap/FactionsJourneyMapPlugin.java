package com.sablednah.factions.integration.journeymap;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import journeymap.api.v2.common.waypoint.WaypointGroup;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.IServerPlugin;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.sablednah.factions.FactionStore;
import com.sablednah.factions.Factions;
import com.sablednah.factions.FactionsMapEvents;

/**
 * Factions' territory and standards, on JourneyMap.
 *
 * <p><b>Nothing else in the mod may reference this class</b>, and this package is the only place a
 * {@code journeymap.*} type may appear. JourneyMap discovers it by scanning for
 * {@link JourneyMapPlugin}, so with JourneyMap absent none of this is ever loaded and the API —
 * which is {@code compileOnly} and never shipped in our jar — is never needed. The rest of Factions
 * talks to us only through {@link FactionsMapEvents}, which knows nothing about maps.
 *
 * <p>The approach and its traps are CityWorld's, written up in
 * {@code me.daddychurchill.CityWorld.integration.journeymap.package-info} — read that before
 * changing anything here. What differs is below.
 *
 * <h2>⚠ Claims move; a city plan does not</h2>
 *
 * <p>CityWorld's overlays are sent once and never re-sent, because a platmap's plan never changes.
 * Territory changes every time somebody claims a chunk, and it changes for <em>everybody who can
 * see it</em> — so this pushes on a change rather than on arrival, and pushes to every player in
 * the affected dimension rather than the one who acted.</p>
 *
 * <h2>⚠ And it is per viewer, which CityWorld's is not</h2>
 *
 * <p>A border's colour is a relation, and a relation belongs to a pair. The same chunk is white to
 * its owner, red to somebody they declared on, and grey to a bystander, simultaneously. See
 * {@link ClaimsOverlay} — there is no "the overlay for this faction", only "as seen by this
 * player".</p>
 */
@JourneyMapPlugin(apiVersion = "2.0.0", dependencies = { Factions.MODID })
public class FactionsJourneyMapPlugin implements IServerPlugin {

    private static final String STANDARD_GROUP = "Faction Standards";

    private IServerAPI api;
    private ClaimsOverlay claims;

    /** One group per player: a waypoint group is per-player state in JourneyMap's store. */
    private final Map<String, WaypointGroup> groups = new ConcurrentHashMap<>();

    @Override
    public String getModId() {
        return Factions.MODID;
    }

    @Override
    public void initialize(IServerAPI serverApi) {
        this.api = serverApi;
        this.claims = new ClaimsOverlay(serverApi);

        FactionsMapEvents.onClaimsChanged(change -> safely(() -> refreshClaims()));
        // ⚠ Standards changing means the TERRITORY has to be redrawn too, not just the pins.
        // A faction's fill colour is its own standard's banner colour (colourOf reads ownFlag and
        // falls back to white), so planting the first flag repaints every chunk it holds. Pinning
        // alone left freshly-seeded factions drawn white until something else forced a rebuild —
        // toggling the layer off and on, which is how it was found.
        FactionsMapEvents.onStandardsChanged(() -> safely(() -> {
            refreshStandards();
            refreshClaims();
        }));
        // The layer switch. It arrives here as a command the player ran — the map's button sends
        // one — because the overlays are pushed from this side, and a client-side switch alone
        // changes a label and nothing else.
        FactionsMapEvents.onLayerToggled(
                toggle -> safely(() -> claims.setVisible(toggle.player(), toggle.on())));

        // ⚠ A player who has just arrived has no overlays and no pins at all — JourneyMap does not
        // replay what was pushed before they connected. Registered here rather than in Factions'
        // own event wiring so that with JourneyMap absent this listener never exists either.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        showEverythingTo(player);
                    }
                });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        safely(() -> claims.forget(player));
                    }
                });
        // Changing dimension is the same problem wearing a different hat: overlays are per
        // dimension, so the ones they were sent for the overworld say nothing about the nether.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        showEverythingTo(player);
                    }
                });

        Factions.LOGGER.info(
                "JourneyMap server API found — faction territory and standards will be mapped");
    }

    /**
     * Push everybody's territory to everybody.
     *
     * <p>Blunter than it needs to be, and deliberately: a claim changes what one faction's shape
     * looks like, but working out which players can see that shape means knowing where every player
     * is looking, which JourneyMap does not tell us and the server has no business guessing. The
     * cost is one polygon per faction with land, which is tens of shapes on a busy server — far
     * below what CityWorld pushes for a single city.</p>
     *
     * <p>⚠ On the server thread. Overlay pushes send packets and read the faction store, and the
     * notification arrives from wherever the claim happened.</p>
     */
    private void refreshClaims() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                safely(() -> claims.showFor(player));
            }
        });
    }

    /** A player who just arrived has no overlays at all; give them the current picture. */
    public void showEverythingTo(ServerPlayer player) {
        safely(() -> claims.showFor(player));
        safely(() -> standardsFor(player));
    }

    private void refreshStandards() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                safely(() -> standardsFor(player));
            }
        });
    }

    /**
     * A pin on every standard the player is entitled to know about.
     *
     * <p>⚠ <b>Own and allied only.</b> A standard's position is exactly what an enemy wants, and the
     * map must not become a targeting aid that the game does not otherwise give — you find somebody
     * else's flag by going and looking. This is the action-bar rule in a different costume: a nicer
     * surface for what you could already learn, never a new capability.</p>
     *
     * <p>⚠ <b>It takes its own pins down first, and that is not tidiness.</b> Every call to
     * {@code createWaypoint} mints a fresh random guid, and the server keys its store by guid — so
     * re-pinning the same flag adds a <em>second</em> pin rather than replacing the first. Measured
     * rather than reasoned: one banner, one walk to the nether and back, and the waypoint manager
     * read <b>2</b>. Logging in, changing dimension and any standard moving anywhere all call this,
     * so the pins would have grown all session.</p>
     *
     * <p>The same delete is what takes a pin <em>down</em>. A standard that is captured, broken or
     * loses its sky fires a refresh in which it is simply absent — with no delete, the flag would
     * stay pinned where it no longer stands, which is worse than not pinning it at all. Same rule
     * as {@link ClaimsOverlay}: push the whole set, never a delta.</p>
     */
    private void standardsFor(ServerPlayer player) {
        clearOurPins(player.getUUID());

        FactionStore store = FactionStore.get(player.level().getServer());
        String mine = store.of(player.getUUID()).map(FactionStore.Faction::id).orElse(null);
        if (mine == null) {
            // Note the pins came down first: somebody who just left their faction should stop
            // seeing its flags, and returning before the delete is how they would keep them.
            return;
        }
        for (FactionStore.Faction faction : store.all()) {
            boolean visible = faction.id().equals(mine)
                    || store.relation(mine, faction.id()) == FactionStore.Relation.ALLY;
            if (!visible) {
                continue;
            }
            int colour = store.colourOf(faction.id()).getTextureDiffuseColor() & 0xFFFFFF;
            for (FactionStore.Placed placed : store.standardsOf(faction.id())) {
                ResourceKey<Level> where = levelKey(placed.dimension());
                if (where == null) {
                    continue;
                }
                String label = faction.name() + " standard"
                        + placed.capturedFrom().map(from -> " (from " + from + ")").orElse("");
                // A waypoint per recipient: the factory stamps identity into the instance, so
                // handing one instance to several players is not safe to assume.
                Waypoint waypoint = WaypointFactory.createWaypoint(Factions.MODID, placed.pos(),
                        label, where, true);
                waypoint.setColor(colour);
                group(player.getUUID(), waypoint);
                // Per player, NOT global. CityWorld found global waypoints simply do not reach a
                // connected client — sixteen announced, none drawn, and no error on either side.
                api.addPlayerWaypoint(player.getUUID(), waypoint);
            }
        }
    }

    /** Remove every pin this mod put on that player, leaving everybody else's alone. */
    private void clearOurPins(UUID player) {
        for (Waypoint existing : api.getWaypoints(player)) {
            if (Factions.MODID.equals(existing.getModId())) {
                safely(() -> api.deletePlayerWaypoint(player, existing.getGuid()));
            }
        }
    }

    /**
     * Turn Factions' dimension string back into Minecraft's key.
     *
     * <p>The store keys claims and standards by {@code level.dimension().identifier().toString()},
     * because a string is what a codec can persist. JourneyMap wants the key. A standard can stand
     * in a dimension the viewer is not in, so this cannot be shortcut through the player's own
     * level — a nether flag would silently never be pinned.</p>
     */
    private static ResourceKey<Level> levelKey(String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    /**
     * Put the waypoint in Factions' own group, reusing the player's if they already have one.
     *
     * <p>Reused rather than recreated because a group is persistent per-player state: making a
     * second one called the same thing gives the player two identical folders and splits their pins
     * between them.</p>
     *
     * <h2>⚠ {@code WaypointGroup.addWaypoint} is a client method, and it fails quietly</h2>
     *
     * <p>It is on the common API and it looks like the obvious call. On a dedicated server it casts
     * the waypoint to {@code ClientWaypointImpl} — ours is the server flavour — and throws
     * {@code ClassCastException} straight into whatever caught it. It also returns {@code false}
     * unconditionally, so even the return value cannot tell you. The visible result was a
     * <b>"Faction Standards" folder holding nothing</b>, with the pin in Default: a folder that
     * lies, which is the same defect as the layer button that flipped its own label. One DEBUG line
     * was the only trace.</p>
     *
     * <p>What the server actually persists is the {@code groupId} on the waypoint itself, so that
     * is what is set. It is not on the {@code Waypoint} interface, hence reflection — taken off the
     * <b>instance</b> rather than by naming {@code journeymap.common.waypoint.WaypointImpl}, so a
     * renamed or relocated implementation is a missing method rather than a missing class.</p>
     *
     * <p>And the folder is only created once that setter is known to be there. A group nothing can
     * be put into is worse than no group — it is the empty folder again, this time with the excuse
     * that we tried.</p>
     */
    private void group(UUID player, Waypoint waypoint) {
        if (Boolean.FALSE.equals(groupable)) {
            return;
        }
        try {
            java.lang.reflect.Method setter =
                    waypoint.getClass().getMethod("setGroupId", String.class);
            if (groupable == null) {
                groupable = true;
                Factions.LOGGER.debug("Factions: JourneyMap waypoints can be grouped");
            }
            WaypointGroup group = groups.computeIfAbsent(player.toString(), key -> {
                for (WaypointGroup existing : api.getAllGroups(player)) {
                    if (Factions.MODID.equals(existing.getModId())
                            && STANDARD_GROUP.equals(existing.getName())) {
                        return existing;
                    }
                }
                WaypointGroup made =
                        WaypointFactory.createWaypointGroup(Factions.MODID, STANDARD_GROUP);
                api.addPlayerGroup(player, made);
                return made;
            });
            if (group != null) {
                setter.invoke(waypoint, group.getGuid());
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // A pin without a folder is still a pin. Grouping is presentation, not the feature —
            // but say so once and at INFO, because the previous version said it at DEBUG and the
            // empty folder went unnoticed until somebody opened the waypoint manager.
            if (groupable == null) {
                Factions.LOGGER.info(
                        "Factions: JourneyMap will not group waypoints ({}); standards will be "
                                + "pinned without their own folder", e.toString());
            }
            groupable = false;
        }
    }

    /** Whether this JourneyMap lets us set a waypoint's group. Probed once, off a real waypoint. */
    private Boolean groupable;

    /**
     * ⚠ Every callback here runs inside JourneyMap's own event handling.
     *
     * <p>An exception there is a crash report rather than a log line, so nothing is allowed out.
     * CityWorld's integration says the same thing and paid for it twice.</p>
     */
    private void safely(Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            Factions.LOGGER.warn("Factions: JourneyMap integration threw ({}); carrying on",
                    e.toString());
        }
    }
}
