package com.sablednah.factions.integration.journeymap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The server's answer to a map click, shown on the map.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Clicking a chunk you may not claim did exactly the right thing and looked exactly like a
 * broken button: the command ran, the server refused with a reason, and the reason went to
 * <b>chat</b> — which is behind the fullscreen map. Nothing happened, twice, and the natural
 * conclusion is that the mod is broken rather than that the answer was no.</p>
 *
 * <p>That is the "correctly withheld looks exactly like broken" family again, and this is the
 * remedy the rule already prescribes: when a feature's correct behaviour is to do nothing, say
 * so where the player is looking. Modelled on LegendQuest's {@code ClientNotices}, which was
 * written for the same reason — a refused perk purchase whose explanation the GUI blur ate.</p>
 *
 * <h2>⚠ No new packet, and no second answer</h2>
 *
 * <p>It shows <b>the chat line the server already sent</b>, verbatim. There is no notice payload
 * and no second code path: a vanilla client gets the same sentence in the same words, just in the
 * chat box where it can see it. Anything else would mean the map knowing something a typist does
 * not, which is the rule the whole client half runs on.</p>
 *
 * <p>The capture is armed by <em>our own click</em> and lasts a moment, so only the reply to a
 * command we just sent is ever shown — unrelated chatter arriving while the map is open is not
 * caught, and nothing is captured at all when nobody has clicked anything.</p>
 */
final class MapNotices {

    /** Long enough for a server round trip, short enough not to catch the next person's chat. */
    private static final long WINDOW_MS = 1500;

    /** How long the notice stays up, matching LegendQuest's so the two feel like one product. */
    private static final long SHOW_MS = 4000;
    private static final long FADE_MS = 600;

    private static volatile long armedUntil;
    private static volatile Screen armedOn;

    private static volatile String message;
    private static volatile long since;

    private MapNotices() {}

    /**
     * We have just sent a command from the map; the next server line is the answer to it.
     *
     * <p>The screen is remembered rather than matched by name, so the notice is drawn over the map
     * we clicked on and nothing else — no guessing at JourneyMap's class names, and no toast
     * appearing over an unrelated screen if one opens in the meantime.</p>
     */
    static void expect() {
        armedUntil = System.currentTimeMillis() + WINDOW_MS;
        armedOn = Minecraft.getInstance().screen;
    }

    @SubscribeEvent
    static void onSystemChat(ClientChatReceivedEvent.System event) {
        if (System.currentTimeMillis() > armedUntil) {
            return;
        }
        armedUntil = 0;     // one reply per click; the rest of the conversation is chat's business
        message = event.getMessage().getString();
        since = System.currentTimeMillis();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        message = null;
        armedUntil = 0;
        armedOn = null;
    }

    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() != armedOn) {
            return;
        }
        draw(event.getGuiGraphics(), Minecraft.getInstance().font);
    }

    private static void draw(GuiGraphicsExtractor graphics, Font font) {
        String text = message;
        if (text == null || text.isEmpty()) {
            return;
        }
        long age = System.currentTimeMillis() - since;
        if (age > SHOW_MS) {
            return;
        }
        float fade = age < SHOW_MS - FADE_MS ? 1.0F : (SHOW_MS - age) / (float) FADE_MS;
        int alpha = Math.max(16, Math.round(fade * 255));

        int width = font.width(text);
        int x = (graphics.guiWidth() - width) / 2;
        // Below JourneyMap's own toolbar and its player/biome strip, which both live at the top.
        int y = 96;
        graphics.fill(x - 6, y - 5, x + width + 6, y + 13, (Math.round(alpha * 0.88F) << 24));
        int edge = (alpha << 24) | 0x8A6ACB;
        graphics.fill(x - 6, y - 5, x + width + 6, y - 4, edge);
        graphics.fill(x - 6, y + 12, x + width + 6, y + 13, edge);
        graphics.text(font, text, x, y, (alpha << 24) | 0xFFFFFF);
    }
}
