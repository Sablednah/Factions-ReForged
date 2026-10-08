package com.sablednah.factions.client;

import java.lang.reflect.Method;
import java.util.Locale;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.sablednah.factions.Factions;

import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.fml.ModList;

/**
 * Makes the border's tinted ground and wall panels survive a shader pack.
 *
 * <h2>Why they vanished</h2>
 *
 * <p>The border is drawn with vanilla's gizmos: lines on {@code LINES}, filled quads on
 * {@code DEBUG_FILLED_BOX}. Iris keeps a table of which vanilla pipeline is drawn by which
 * shader-pack program, and {@code LINES} is in it — which is why the edges stayed — but no
 * {@code DEBUG_*} pipeline is. An unmapped pipeline falls through to Iris's placeholder and never
 * reaches the screen. Read from Iris 1.11.4's {@code IrisPipelines}, not guessed. Complementary was
 * not refusing anything; Iris simply had no answer for it.</p>
 *
 * <h2>The fix stays on gizmos</h2>
 *
 * <p>Iris has a public answer for exactly this — {@code IrisApi.assignPipeline(RenderPipeline,
 * IrisProgram)} — so we tell it which program to use, and change nothing else. With no pack
 * loaded Iris never consults the table, so the look without shaders is untouched.</p>
 *
 * <p><b>Reflection, deliberately.</b> Iris is optional and nothing here may make it a dependency,
 * at compile time or at class-load time. Every failure is logged once and costs only the fill.</p>
 *
 * <p>⚠ The mapping is <b>global</b>: it covers every gizmo filled box drawn in the world, other
 * mods' and vanilla's debug fills included. Those were equally invisible under a pack, so this is
 * a fix for them too — but it is not ours alone, and {@code shaderFillProgram = "off"} exists for
 * the day another mod disagrees.</p>
 */
final class ShaderPackCompat {

    /** Call once, after the client config has loaded. */
    static void install() {
        if (!ModList.get().isLoaded("iris")) {
            return;
        }
        String wanted = FactionsClientConfig.SHADER_FILL_PROGRAM.get().trim();
        if (wanted.isEmpty() || wanted.equalsIgnoreCase("off")) {
            Factions.LOGGER.info("Factions: Iris present, shaderFillProgram is off — border fills "
                    + "stay invisible under a shader pack");
            return;
        }
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<?> programs = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object instance = api.getMethod("getInstance").invoke(null);
            Method assign = api.getMethod("assignPipeline", RenderPipeline.class, programs);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object program = Enum.valueOf((Class<? extends Enum>) programs.asSubclass(Enum.class),
                    wanted.toUpperCase(Locale.ROOT));
            assign.invoke(instance, RenderPipelines.DEBUG_FILLED_BOX, program);
            Factions.LOGGER.info("Factions: border fills mapped to Iris program {}", program);
        } catch (IllegalArgumentException e) {
            Factions.LOGGER.warn("Factions: '{}' is not an Iris program name; border fills stay "
                    + "invisible under a shader pack", wanted);
        } catch (ReflectiveOperationException | LinkageError e) {
            // An Iris without the API, or one that renamed it: the fills go, the walls stay.
            Factions.LOGGER.warn("Factions: could not map border fills for Iris ({})",
                    e.toString());
        }
    }

    private ShaderPackCompat() {}
}
