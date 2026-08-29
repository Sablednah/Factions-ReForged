package com.sablednah.factions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Carry the faction store forward into Minecraft 26.1's namespaced data folders.
 *
 * <p>Two changes, not one. {@code SavedDataType} started naming its file from an
 * {@code Identifier}, so {@code factions.dat} became {@code factions/data.dat} — <b>and</b>
 * per-dimension saved data moved out of the world root into
 * {@code world/dimensions/minecraft/overworld/data/}. Fixing only the filename lands a perfect copy
 * in {@code world/data/}, where nothing reads it.</p>
 *
 * <p>Nothing errors either way: the world loads with no factions, no claims, no power and no
 * standards, and the first sign is somebody asking why their territory is gone. See Standards'
 * {@code SaveMigration} for the same trap at more length.</p>
 *
 * <p>The original is copied rather than moved, so a server that upgrades and then rolls back still
 * has it. See Standards' {@code SaveMigration} for the same reasoning at more length.</p>
 */
public final class FactionSaveMigration {

    public static void run(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path data = root.resolve("data");
        Path from = data.resolve("factions.dat");
        Path to = net.minecraft.world.level.dimension.DimensionType
                .getStorageFolder(net.minecraft.world.level.Level.OVERWORLD, root)
                .resolve("data").resolve("factions").resolve("data.dat");
        try {
            if (!Files.isDirectory(data) || !Files.isRegularFile(from) || Files.exists(to)) {
                return;
            }
            Files.createDirectories(to.getParent());
            Files.copy(from, to);
            Factions.LOGGER.info("Carried factions.dat forward to factions/data.dat for "
                    + "Minecraft 26.1+. The original is left in place.");
        } catch (IOException e) {
            Factions.LOGGER.error("Could not carry factions.dat forward — factions will look "
                    + "empty. The original is untouched at {}", from, e);
        }
    }

    private FactionSaveMigration() {}
}
