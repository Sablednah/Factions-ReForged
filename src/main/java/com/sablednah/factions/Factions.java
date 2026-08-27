package com.sablednah.factions;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Factions ReForged — land, allegiance, and the people you hold it with.
 *
 * <p>Inspired by MassiveCraft's Factions, which is where most people's mental model of this comes
 * from. Not a port of it: the original is a decade of Bukkit, and the interesting parts of the
 * idea deserve a modern implementation rather than a faithful one.</p>
 *
 * <h2>What it borrows rather than rebuilds</h2>
 *
 * <p>Standards is a <b>hard dependency</b>, and that is the whole architecture. Factions supplies
 * the two things it actually knows — who is allied with whom, and who owns this chunk — through
 * Standards' groups and claims seams, and takes everything else back:</p>
 *
 * <ul>
 *   <li>chat tags, because a faction registered as a group kind renders through the decorator
 *       Standards already has;</li>
 *   <li>shared homes, warmups, safe landings and the cooldown, from its teleports;</li>
 *   <li>a faction bank, from its economy;</li>
 *   <li>every player-facing string, from its {@code messages.yml} catalogue — so a server that
 *       re-skins "faction" to "clan" changes one key and both mods follow.</li>
 * </ul>
 *
 * <p>The discipline that makes that honest is going through the published API rather than reaching
 * into Standards' internals. A required dependency still crosses a jar boundary, so the seam is
 * genuinely exercised — but only while nothing here picks the lock.</p>
 */
@Mod(Factions.MODID)
public final class Factions {

    public static final String MODID = "factions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Factions(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON,
                FactionsConfig.SPEC);

        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            // Before Standards writes messages.yml at ServerAboutToStartEvent, so these appear in
            // the file on this very start rather than the next one.
            FactionLang.contribute();
            LOGGER.info("Factions ReForged: strings contributed to the Standards catalogue");
        }));

        NeoForge.EVENT_BUS.register(FactionsEvents.class);
        // Dormant unless -Pselftest; see FactionsSelfTest.
        NeoForge.EVENT_BUS.register(FactionsSelfTest.class);
    }
}
