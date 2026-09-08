package com.sablednah.factions;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import com.sablednah.standards.api.actions.Action;
import com.sablednah.standards.api.actions.Actions;

/**
 * Factions' entries on Standards' action bar.
 *
 * <p>Registered through the public seam from another repository, which is the only real evidence
 * that seam is usable by somebody who did not write it — the same argument that makes LegendQuest's
 * {@code VanishSupport} worth more than any amount of our own testing.</p>
 *
 * <h2>Offered only when they would do something</h2>
 *
 * <p>Every one of these asks whether the player is in a faction first, so a button never appears
 * for somebody it can only refuse. A bar full of things that answer "you are not in a faction" is
 * worse than a bar with nothing on it.</p>
 */
public final class FactionActions {

    /**
     * Called from setup, and defensively: Standards is a hard dependency here, but a version too
     * old to have the seam would otherwise take the whole mod down at class-load rather than
     * costing it three buttons.
     */
    public static void registerAll() {
        try {
            register();
        } catch (LinkageError e) {
            Factions.LOGGER.info(
                    "Factions: Standards has no action seam; skipping the buttons ({})",
                    e.getClass().getSimpleName());
        }
    }

    private static void register() {
        // Below Standards' own switches (100–88) so the rows read Standards first, then us. The
        // bar groups by mod anyway, so this only orders us within our own row.
        Actions.register(new Action("factions:home", 60,
                Identifier.withDefaultNamespace("orange_bed"), "msg.factions.action_home",
                "f home",
                // Only with a home set: /f home on a faction that has none is a refusal, and a
                // button that can only refuse is worse than no button.
                p -> inFaction(p) && FactionStore.get(p.level().getServer())
                        .of(p.getUUID()).map(f -> f.home().isPresent()).orElse(false)));

        Actions.register(new Action("factions:claim", 59,
                Identifier.withDefaultNamespace("oak_fence"), "msg.factions.action_claim",
                "f claim",
                FactionActions::inFaction));

        // A state, not an act — so the bar lights it while it is on, which is the whole reason
        // autoclaim is worth a button rather than a command you have to remember you left running.
        Actions.register(new Action("factions:autoclaim", 58,
                Identifier.withDefaultNamespace("lead"), "msg.factions.action_autoclaim",
                "f autoclaim",
                FactionActions::inFaction,
                FactionAutoClaim::isOn));

        // The panel. Its command prints the same facts for a client that cannot draw, so this is
        // a nicer surface rather than a second capability.
        Actions.register(new Action("factions:panel", 61,
                Identifier.withDefaultNamespace("writable_book"), "msg.factions.action_panel",
                "f panel",
                FactionActions::inFaction));

        Actions.register(new Action("factions:map", 57,
                Identifier.withDefaultNamespace("filled_map"), "msg.factions.action_map",
                "f map",
                p -> true));

        Factions.LOGGER.info("Factions: registered 5 action bar buttons");
    }

    private static boolean inFaction(ServerPlayer player) {
        return FactionStore.get(player.level().getServer()).of(player.getUUID()).isPresent();
    }

    private FactionActions() {}
}
