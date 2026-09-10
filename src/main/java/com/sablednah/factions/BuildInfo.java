package com.sablednah.factions;

import java.io.InputStream;
import java.util.Properties;

/**
 * Which build this is — the commit these bytes came from.
 *
 * <h2>Why a version number is not enough</h2>
 *
 * <p>A version answers "which release". During development that is a different question from
 * "which bytes", and the gap between the two cost three sessions time in a single day: a jar
 * rebuilt under an unchanged version broke an already-shipped consumer, a dependency range admitted
 * a jar it could not run against, and nobody could tell a stale instance jar from its filename.</p>
 *
 * <p><b>The startup log line is the half that matters.</b> A stamp inside a jar says what is on
 * disk. The log line says what actually <em>ran</em> — which is the question a bug report needs
 * answered, and the one none of us could answer afterwards.</p>
 *
 * <h2>Three decisions worth keeping</h2>
 *
 * <ul>
 * <li><b>Read the properties file, not the manifest.</b> A dev run loads from a classes directory:
 *     there is no jar and therefore no manifest. The manifest carries the same four values for
 *     anything inspecting a jar without loading it, which is the shell-side question.</li>
 * <li><b>The resource is namespaced</b> under {@code /standards/}. A bare {@code /build.properties}
 *     collides with every other mod doing this on a shared classpath, and you would silently read
 *     somebody else's stamp — a diagnostic that lies is worse than none.</li>
 * <li><b>A missing stamp degrades to {@code unknown} and never throws.</b> This is diagnostic
 *     information, not a dependency, and it must never be the reason a mod fails to load.</li>
 * </ul>
 *
 * <p>Format agreed with LegendQuest, which built it first and proposed it. Deliberately copied
 * rather than shared — including from Standards, which Factions could perfectly well have imported
 * this from: five copies of sixty lines is less coupling than another artifact every mod depends on
 * for a diagnostic, and this one above all must not be able to break a build. Keep the four field
 * names identical so one reader works across every mod.</p>
 */
public final class BuildInfo {

    private static final String RESOURCE = "/factions/build.properties";

    private static final String COMMIT;
    private static final String BRANCH;
    private static final String TIME;
    private static final String VERSION;

    static {
        String commit = "unknown";
        String branch = "unknown";
        String time = "unknown";
        String version = "unknown";
        try (InputStream in = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                commit = p.getProperty("commit", commit);
                branch = p.getProperty("branch", branch);
                time = p.getProperty("time", time);
                version = p.getProperty("version", version);
            }
        } catch (Exception ignored) {
            // Deliberately swallowed. See the class note: never the reason a mod fails to load.
        }
        COMMIT = commit;
        BRANCH = branch;
        TIME = time;
        VERSION = version;
    }

    /** Short SHA, with a {@code -dirty} suffix when built from uncommitted changes. */
    public static String commit() {
        return COMMIT;
    }

    public static String branch() {
        return BRANCH;
    }

    /** UTC, ISO-8601. */
    public static String time() {
        return TIME;
    }

    public static String version() {
        return VERSION;
    }

    /**
     * The one-line form for the startup log.
     *
     * <p>{@code 1.5.0 (build a1b2c3d4 on main, 2026-09-10T09:15:00Z)}. A {@code -dirty} on the
     * commit means uncommitted changes went into it, which is worth seeing in somebody's log
     * before you spend an hour reproducing against a tag that is not what they ran.</p>
     */
    public static String describe() {
        return VERSION + " (build " + COMMIT + " on " + BRANCH + ", " + TIME + ")";
    }

    private BuildInfo() {}
}
