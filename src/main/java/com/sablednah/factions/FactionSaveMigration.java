package com.sablednah.factions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Carry the faction store forward into Minecraft 26.1's namespaced data folders.
 *
 * <p>{@code data/factions.dat} became {@code data/factions/data.dat} when {@code SavedDataType}
 * started naming its file from an {@code Identifier}. Nothing errors: the world simply loads with
 * no factions, no claims, no power and no standards, and the first sign is somebody asking why
 * their territory is gone.</p>
 *
 * <p>The original is copied rather than moved, so a server that upgrades and then rolls back still
 * has it. See Standards' {@code SaveMigration} for the same reasoning at more length.</p>
 */
public final class FactionSaveMigration {

    public static void run(MinecraftServer server) {
        Path data = server.getWorldPath(LevelResource.ROOT).resolve("data");
        Path from = data.resolve("factions.dat");
        Path to = data.resolve("factions").resolve("data.dat");
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
