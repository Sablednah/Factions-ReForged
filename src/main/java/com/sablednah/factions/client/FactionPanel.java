package com.sablednah.factions.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import com.sablednah.factions.FactionPanelPayload;
import com.sablednah.standards.client.ClientCapabilities;
import com.sablednah.standards.client.panels.InventoryPanel;
import com.sablednah.standards.client.panels.Panels;

/**
 * The faction sheet, drawn <b>on</b> the inventory screen rather than instead of it.
 *
 * <h2>Why it is a pane and not a screen</h2>
 *
 * <p>It began as a {@code Screen}: the button sent {@code f panel}, the reply opened a full screen,
 * and your inventory went away. That is the wrong shape for something you consult — you check your
 * power the way you check your armour, mid-task, and a screen that replaces the inventory makes
 * looking at both a round trip. LegendQuest's character sheet is the pattern, and the owner asked
 * for this one to match it: the button <b>toggles</b> the pane, and pressing it again puts it
 * away.</p>
 *
 * <h2>Standards decides where it goes</h2>
 *
 * <p>It went right for one afternoon and landed on two things at once: vanilla's potion effects,
 * which render at exactly {@code leftPos + imageWidth + 2}, and JEI, whose tooltips went on firing
 * underneath it because nothing had told JEI it was there. Both were this mod being reasonable on
 * its own and having no way to see anybody else.</p>
 *
 * <p>So the position is not ours any more. This registers with {@code Panels} and is handed a
 * rectangle: Standards owns the margin, draws the frame, arbitrates one-pane-at-a-time, and stands
 * the pane down when the recipe book or a LegendQuest pane takes the space. What is left here is
 * the only part that was ever Factions' business — what a faction panel says.</p>
 *
 * <h2>Everything is drawn by hand</h2>
 *
 * <p>No {@link net.minecraft.client.gui.components.Button} widgets. Vanilla's button has a minimum
 * height of 20 pixels and these rows are twelve, which is exactly the bug the owner spotted in the
 * screen version — the promote/demote/kick buttons sat below the name they belonged to, because
 * a 20px button cannot be centred on a 12px row without hanging off both ends. Hand-drawn hotspots
 * are the row's height by construction, so they line up because they cannot do otherwise.</p>
 *
 * <p>The other reason is scrolling: a widget list has to be rebuilt on every scroll tick, and the
 * rebuild is what makes a scrolling widget panel feel sticky. Immediate mode redraws anyway.</p>
 *
 * <h2>The labels here are chrome, and that is why they are not in the catalogue</h2>
 *
 * <p>Decision 6 says every player-facing string lives in {@code Lang}, and every string that
 * carries an <em>answer</em> here does: the standard's state arrives already resolved in the
 * payload, and so does every name and number. What is hardcoded is the furniture — "Power", "Land",
 * the tab captions — which the client draws around the answer. Standards' own bar does the same,
 * and {@code ClientLang} exists to say why: the client cannot reach the server's catalogue, and a
 * second catalogue beside it would drift. Don't start one here.</p>
 */
public final class FactionPanel implements InventoryPanel {

    /** The one instance, because there is one faction panel and Standards holds it by reference. */
    public static final FactionPanel INSTANCE = new FactionPanel();

    /** Standards' id for this pane, and the action id that toggles it — deliberately the same. */
    public static final String ID = "factions:panel";

    private static final int WANT_WIDTH = 168;
    private static final int PAD = 6;
    private static final int ROW = 12;
    private static final int TAB_H = 14;
    /** The little square buttons on a member row — exactly one row tall, see the class note. */
    private static final int MARK = ROW;
    /** The scrollbar. Narrow on purpose: it is a position indicator that happens to be draggable. */
    private static final int BAR_W = 4;

    private static final int LABEL = 0xFFAAAAAA;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int DIM = 0xFF888888;
    private static final int ONLINE = 0xFF55FF55;

    private enum Tab { OVERVIEW, RELATIONS, MEMBERS }

    private static Tab tab = Tab.OVERVIEW;
    private static int scroll;

    /** Mid-drag on the scrollbar. Set on a press in the track, cleared on release. */
    private static boolean dragging;

    // Where the track was drawn last frame. A drag arrives knowing only the cursor, so the
    // geometry has to be remembered rather than recomputed — recomputing it means a second copy
    // of the layout arithmetic, which is the drift this pane is built to avoid.
    private static int trackX;
    private static int trackTop;
    private static int trackHeight;
    private static int trackThumb;
    private static int trackMax;

    /**
     * Click targets, rebuilt every frame.
     *
     * <p>Immediate mode: the pane draws itself and records where it drew, and the click handler
     * reads that. The alternative — laying out in one place and hit-testing from another — is two
     * copies of the same arithmetic that drift the first time either is edited, and the symptom is
     * a button that works everywhere except where it is drawn.</p>
     */
    private record Hot(int x0, int y0, int x1, int y1, Runnable action) {}

    private static final List<Hot> HOTSPOTS = new ArrayList<>();

    /**
     * The tooltip to draw at the end of this frame, if anything under the cursor asked for one.
     *
     * <p>Collected during the draw and rendered last, because a tooltip drawn where it is asked for
     * would be painted over by whatever the pane draws next.</p>
     */
    private static String tooltip;

    /** Whether the pane is showing — the bar asks, so its button can light up. */
    public static boolean isOpen() {
        return Panels.isOpen(ID);
    }

    /**
     * The action-bar button's handler: show it, or put it away.
     *
     * <p>Standards does the arbitrating, so this does not have to know that opening the pane closes
     * whatever else was open.</p>
     */
    public static void toggle() {
        Panels.toggle(ID);
    }

    @Override
    public int preferredWidth() {
        return WANT_WIDTH;
    }

    /**
     * Ask the server for a fresh answer, every time the pane is shown.
     *
     * <p>The same {@code f panel} a vanilla client sends. The reply lands in
     * {@link FactionPanelData} and the next frame draws it; until then the pane draws whatever it
     * had, or says it is asking. Deliberately not a blocking wait — an empty box for one tick reads
     * as broken, and last tick's power is a better answer than no answer.</p>
     *
     * <p>Asked on every open rather than once, because the numbers do not stay true: power
     * regenerates, land changes hands, people log in. A stale power figure presented as current is
     * worse than no pane at all.</p>
     */
    @Override
    public void onOpen() {
        scroll = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            mc.getConnection().sendCommand("f panel");
        }
    }

    /**
     * Offered only while the server is still offering the button that opens it.
     *
     * <p>Left the faction while the pane was open? Then the button has already been withheld, and a
     * pane still showing a faction you are no longer in would be the only thing on screen insisting
     * you are. Asking Standards' capability set is the same question the bar asks, so the pane and
     * the button cannot disagree.</p>
     */
    @Override
    public boolean available() {
        return ClientCapabilities.has(ID);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Font font,
            int x, int y, int width, int height, int mouseX, int mouseY) {
        HOTSPOTS.clear();
        tooltip = null;
        // Cleared every frame and set only by a drawn scrollbar, so switching to a tab that has
        // none cannot leave a strip of dead space that still scrolls the members list.
        trackMax = 0;

        FactionPanelPayload data = FactionPanelData.latest();
        if (data == null) {
            graphics.text(font, "Asking the server\u2026", x + PAD, y + PAD, DIM);
            return;
        }
        if (data.isNone()) {
            none(graphics, font, x, y, width, mouseX, mouseY);
            if (tooltip != null) {
                // 26.x dropped the Font argument: the tooltip renderer takes the component alone.
            graphics.setTooltipForNextFrame(Component.literal(tooltip), mouseX, mouseY);
            }
            return;
        }

        int line = y + PAD;
        boolean mayEdit = data.yourRank().equalsIgnoreCase("leader");
        // The pencils sit on the title line, so the name and the button that changes it are the
        // same thing to look at. Reserved out of the title's width whether or not they are drawn,
        // or a leader's name would clip where a member's did not.
        int pencils = mayEdit ? MARK * 2 + 2 : 0;
        String title = data.name() + (data.tag().isEmpty() ? "" : " [" + data.tag() + "]");
        graphics.text(font, clip(font, title, width - PAD * 2 - pencils), x + PAD, line,
                VALUE);
        if (mayEdit) {
            int px = x + width - PAD - MARK * 2 - 2;
            mark(graphics, font, px, line - 2, mouseX, mouseY, "\u270e",
                    "Rename the faction\nOpens chat, pre-filled", () -> prefill("/f rename "));
            mark(graphics, font, px + MARK + 2, line - 2, mouseX, mouseY, "#",
                    "Set the tag\nOpens chat, pre-filled", () -> prefill("/f tag "));
        }
        line += ROW + 2;
        if (data.peaceful()) {
            graphics.text(font, "peaceful", x + PAD, line, 0xFF77DDFF);
            line += ROW;
        }

        line = tabs(graphics, font, x, line, width);
        // Everything below the chips scrolls, and nothing above does — so the tabs stay reachable
        // however far down a long members list you are.
        int bodyBottom = y + height - PAD;
        switch (tab) {
            case OVERVIEW -> overview(graphics, font, data, x, line, width);
            case RELATIONS -> relations(graphics, font, data, x, line, bodyBottom, width);
            case MEMBERS -> members(graphics, font, data, x, line, bodyBottom, width,
                    mouseX, mouseY);
        }
        if (tooltip != null) {
            // 26.x dropped the Font argument: the tooltip renderer takes the component alone.
            graphics.setTooltipForNextFrame(Component.literal(tooltip), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        // The track first: it overlaps nothing, but it is the one control that needs the click's
        // own coordinates rather than a Runnable recorded during the draw.
        if (trackMax > 0 && mouseX >= trackX && mouseX < trackX + BAR_W
                && mouseY >= trackTop && mouseY < trackTop + trackHeight) {
            dragging = true;
            scrollTo(mouseY, trackTop, trackHeight, trackThumb, trackMax);
            return true;
        }
        for (Hot hot : HOTSPOTS) {
            if (mouseX >= hot.x0() && mouseX < hot.x1()
                    && mouseY >= hot.y0() && mouseY < hot.y1()) {
                hot.action().run();
                return true;
            }
        }
        return false;
    }

    /**
     * Dragging the scrollbar thumb.
     *
     * <p>No bounds check on the cursor, and Standards does not apply one either: a thumb pulled
     * quickly is outside the pane within a frame, and one that stops tracking when the cursor
     * strays looks broken rather than bounded.</p>
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
            double dragX, double dragY) {
        if (!dragging || trackMax <= 0) {
            return false;
        }
        scrollTo(mouseY, trackTop, trackHeight, trackThumb, trackMax);
        return true;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
    }

    /** Only the members tab has anything to scroll, so only it claims the wheel. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab != Tab.MEMBERS) {
            return false;
        }
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    /**
     * What the pane says to somebody in no faction: what one is for, and an offer to make one.
     *
     * <p>This state is the reason the panel button is the one Factions button not gated on
     * membership. A button withheld from the player who most needs it is a feature that only ever
     * reaches people who already have it.</p>
     */
    private static void none(GuiGraphicsExtractor graphics, Font font, int x, int y, int width,
            int mouseX, int mouseY) {
        int line = y + PAD;
        graphics.text(font, "No faction", x + PAD, line, VALUE);
        line += ROW + 4;
        // What it is for, before what to press. Two short lines rather than a paragraph: this is a
        // pane, and anybody who wanted the manual would have typed /f help.
        for (String pitch : new String[] {
                "Claim land nobody can build in,",
                "pool money, and hold a standard",
                "that pays you back in power." }) {
            graphics.text(font, pitch, x + PAD, line, DIM);
            line += ROW;
        }
        line += 6;
        line = wide(graphics, font, x, line, width, mouseX, mouseY, "Create a faction",
                "Opens chat, pre-filled with /f create", () -> prefill("/f create "));
        wide(graphics, font, x, line, width, mouseX, mouseY, "Who is out there?",
                "Runs /f list", () -> send("f list"));
    }

    /**
     * A full-width button, for the one or two things a pane offers rather than a list of them.
     *
     * <p>Hand-drawn like everything else here — see the class note. A vanilla {@code Button} would
     * bring its own 20-pixel minimum height and its own idea of where it is.</p>
     */
    private static int wide(GuiGraphicsExtractor graphics, Font font, int x, int y, int width,
            int mouseX, int mouseY, String label, String tip, Runnable action) {
        int x0 = x + PAD;
        int x1 = x + width - PAD;
        int h = ROW + 6;
        boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + h;
        graphics.fill(x0, y, x1, y + h, hover ? 0xFF4A3A6A : 0xFF2A2A32);
        graphics.fill(x0, y, x1, y + 1, hover ? 0xFF9A7AD0 : 0xFF4A4A55);
        graphics.fill(x0, y + h - 1, x1, y + h, hover ? 0xFF9A7AD0 : 0xFF4A4A55);
        graphics.text(font, label, x0 + (x1 - x0 - font.width(label)) / 2, y + 5,
                hover ? 0xFFFFFFFF : 0xFFDDDDDD);
        if (hover) {
            tooltip = tip;
        }
        HOTSPOTS.add(new Hot(x0, y, x1, y + h, action));
        return y + h + 4;
    }

    /**
     * Open the chat box with a command already typed, for the things that need words.
     *
     * <p>A faction's name is text, and a pane has no text field — the panel seam hands out a
     * rectangle and the mouse, not the keyboard. Pre-filling chat is what LegendQuest's party panel
     * does for the same problem, and it is better than a hand-rolled text box would be: the player
     * gets vanilla's own editing, history and paste, and the command they end up sending is one
     * they can see and could have typed. Closes the pane, because the inventory has to go.</p>
     */
    private static void prefill(String command) {
        Panels.close();
        Minecraft.getInstance().setScreen(
                new net.minecraft.client.gui.screens.ChatScreen(command, false));
    }

    /** The Overview / Relations / Members chips. */
    private static int tabs(GuiGraphicsExtractor graphics, Font font, int x, int y, int width) {
        String[] names = {"Overview", "Relations", "Members"};
        Tab[] values = Tab.values();
        int chip = (width - PAD * 2) / names.length;
        for (int i = 0; i < names.length; i++) {
            int cx = x + PAD + i * chip;
            boolean on = tab == values[i];
            graphics.fill(cx, y, cx + chip - 2, y + TAB_H, on ? 0xFF3A2A5A : 0xFF1A1A22);
            graphics.fill(cx, y + TAB_H - 1, cx + chip - 2, y + TAB_H, on ? 0xFF9A7AD0 : 0xFF2A2A32);
            graphics.text(font, names[i],
                    cx + (chip - 2 - font.width(names[i])) / 2, y + 3, on ? VALUE : DIM);
            final Tab which = values[i];
            HOTSPOTS.add(new Hot(cx, y, cx + chip - 2, y + TAB_H, () -> {
                tab = which;
                scroll = 0;
            }));
        }
        return y + TAB_H + 4;
    }

    private static void overview(GuiGraphicsExtractor graphics, Font font, FactionPanelPayload d,
            int x, int y, int width) {
        y = row(graphics, font, x, y, width, "Power", trim(d.power()) + " / " + trim(d.maxPower()));
        y = row(graphics, font, x, y, width, "Land", d.claims() + " of " + d.entitlement());
        // Overreach decides whether they can be raided, so it is said outright rather than left to
        // be worked out from the two numbers above.
        if (d.claims() > d.entitlement()) {
            y = row(graphics, font, x, y, width, "Exposed", (d.claims() - d.entitlement()) + " takeable");
        }
        y = row(graphics, font, x, y, width, "Bank", trim(d.bank()));
        y = row(graphics, font, x, y, width, "Standard", colored(d.standardState()));
        if (d.trophies() > 0) {
            y = row(graphics, font, x, y, width, "Trophies", String.valueOf(d.trophies()));
        }
        if (d.raidsFought() > 0) {
            row(graphics, font, x, y, width, "Raids", d.raidsWon() + " of " + d.raidsFought());
        }
    }

    private static void relations(GuiGraphicsExtractor graphics, Font font, FactionPanelPayload d,
            int x, int y, int bottom, int width) {
        graphics.text(font, "Allies", x + PAD, y, LABEL);
        y += ROW;
        y = names(graphics, font, d.allies(), x, y, bottom, width, 0xFF77DD77);
        y += 4;
        if (y + ROW <= bottom) {
            graphics.text(font, "Enemies", x + PAD, y, LABEL);
            y += ROW;
            names(graphics, font, d.enemies(), x, y, bottom, width, 0xFFDD7777);
        }
    }

    private static int names(GuiGraphicsExtractor graphics, Font font, List<String> list,
            int x, int y, int bottom, int width, int colour) {
        if (list.isEmpty()) {
            graphics.text(font, "  none", x + PAD, y, DIM);
            return y + ROW;
        }
        for (String name : list) {
            if (y + ROW > bottom) {
                return y;
            }
            graphics.text(font, "  " + clip(font, name, width - PAD * 2 - 8), x + PAD, y,
                    colour);
            y += ROW;
        }
        return y;
    }

    /**
     * The members list, scrolled, with per-row buttons for whoever may use them.
     *
     * <p>The buttons are drawn only for a leader or officer, because a button that always answers
     * "you may not" teaches a player to ignore buttons. The server checks again regardless — this
     * decides what to <em>show</em>, exactly as the action bar's capability set does.</p>
     */
    private static void members(GuiGraphicsExtractor graphics, Font font, FactionPanelPayload d,
            int x, int y, int bottom, int width, int mouseX, int mouseY) {
        boolean mayManage = d.yourRank().equalsIgnoreCase("leader")
                || d.yourRank().equalsIgnoreCase("officer");
        List<FactionPanelPayload.Member> all = d.members();
        int top = y;
        int visible = Math.max(1, (bottom - y) / ROW);
        int maxScroll = Math.max(0, all.size() - visible);
        // Clamped here rather than at the scroll event, because the number of rows that fit is not
        // known until the pane has been laid out — and it changes with the window.
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        // ⚠ The bar's strip is reserved whether or not there is anything to scroll. Reserving it
        // only when needed would shift every row sideways the moment a faction outgrew the pane,
        // which is a layout that changes under you as people join.
        int barX = x + width - PAD - BAR_W;
        int rowRight = barX - 3;

        int marks = mayManage ? MARK * 3 + 2 : 0;
        int nameWidth = rowRight - (x + PAD) - marks - 26;
        for (int i = scroll; i < all.size() && y + ROW <= bottom; i++) {
            FactionPanelPayload.Member member = all.get(i);
            // Online state as colour rather than as a word: a column of "(online)" would cost more
            // width than it earns, and green reads without being read.
            graphics.text(font, clip(font, member.name(), nameWidth), x + PAD, y + 2,
                    member.online() ? ONLINE : DIM);
            // Only the ranks worth marking. "member" on eighteen of twenty rows is a column that
            // says nothing and costs the width a long name needed — and an initial (M/O/L) is
            // cheaper still and has to be learned. Blank means member.
            String rank = shortRank(member.rank());
            if (!rank.isEmpty()) {
                graphics.text(font, rank, x + PAD + nameWidth + 4, y + 2, LABEL);
            }
            if (mayManage) {
                final String who = member.name();
                int mx = rowRight - MARK * 3 - 2;
                mark(graphics, font, mx, y, mouseX, mouseY, "\u25b2", "Promote " + who,
                        () -> send("f promote " + who));
                mark(graphics, font, mx + MARK + 1, y, mouseX, mouseY, "\u25bc", "Demote " + who,
                        () -> send("f demote " + who));
                mark(graphics, font, mx + MARK * 2 + 2, y, mouseX, mouseY, "\u2715", "Kick " + who,
                        () -> send("f kick " + who));
            }
            y += ROW;
        }
        scrollbar(graphics, barX, top, bottom, all.size(), visible, maxScroll, mouseX, mouseY);
    }

    /**
     * A narrow bar down the right of the list.
     *
     * <p>It replaces a small {@code \u25bc 20} drawn in the bottom corner, which was the obvious
     * cheap thing and sat directly on top of the last row's kick button — a count nobody could read
     * over a button nobody could press. A bar has somewhere of its own to live, says how far down
     * you are and how much there is at a glance rather than as a number, and can be dragged.</p>
     *
     * <p>Drawn only when there is something to scroll, though its strip is always reserved: an
     * empty track is furniture that means nothing.</p>
     */
    private static void scrollbar(GuiGraphicsExtractor graphics, int barX, int top, int bottom,
            int total, int visible, int maxScroll, int mouseX, int mouseY) {
        // mouseX/mouseY are for the hover highlight only — the click and drag use their own.
        if (maxScroll <= 0) {
            return;
        }
        int height = bottom - top;
        graphics.fill(barX, top, barX + BAR_W, bottom, 0xFF1A1A22);

        // At least a few pixels tall however long the list gets: a thumb proportional all the way
        // down becomes one pixel at two hundred members, which is accurate and unusable.
        int thumbH = Math.max(8, height * visible / Math.max(1, total));
        int thumbY = top + (height - thumbH) * scroll / maxScroll;
        boolean hover = mouseX >= barX && mouseX < barX + BAR_W
                && mouseY >= top && mouseY < bottom;
        graphics.fill(barX, thumbY, barX + BAR_W, thumbY + thumbH,
                hover || dragging ? 0xFF9A7AD0 : 0xFF5A4A7A);

        // Remembered for the click and the drag, both of which arrive knowing only the cursor.
        trackX = barX;
        trackTop = top;
        trackHeight = height;
        trackThumb = thumbH;
        trackMax = maxScroll;

        // ⚠ Deliberately NOT a hotspot. A Hot carries a Runnable, so it would have to close over
        // this frame's mouse position — and a click handled next frame would jump to where the
        // cursor was when the bar was drawn rather than where it was clicked. Near enough to look
        // right and wrong every time. The track is hit-tested in mouseClicked against the real
        // click instead; the remembered geometry above is what lets it.
    }

    /** Put the thumb's middle where the cursor is, clamped to the track. */
    private static void scrollTo(double mouseY, int top, int height, int thumbH, int maxScroll) {
        int travel = Math.max(1, height - thumbH);
        double at = mouseY - top - thumbH / 2.0;
        scroll = (int) Math.round(Math.max(0, Math.min(1, at / travel)) * maxScroll);
    }

    /**
     * One square button on a member row, drawn at exactly the row's height.
     *
     * <p>It carries a tooltip naming what it does <em>and to whom</em>, because an unlabelled
     * {@code ✕} beside somebody's name is a button nobody should have to press to find out about.
     * Lit while hovered, so it is visibly a button before it is clicked.</p>
     */
    private static void mark(GuiGraphicsExtractor graphics, Font font, int x, int y,
            int mouseX, int mouseY, String glyph, String tip, Runnable action) {
        boolean hover = mouseX >= x && mouseX < x + MARK && mouseY >= y && mouseY < y + MARK;
        graphics.fill(x, y, x + MARK, y + MARK, hover ? 0xFF4A3A6A : 0xFF2A2A32);
        graphics.fill(x, y, x + MARK, y + 1, hover ? 0xFF9A7AD0 : 0xFF4A4A55);
        graphics.fill(x, y + MARK - 1, x + MARK, y + MARK, hover ? 0xFF9A7AD0 : 0xFF4A4A55);
        // 0xFFFFFFFF, not 0xFFFFFF. drawString takes ARGB, so a bare RGB has an alpha of zero and
        // renders as a dark smudge — the same trap the action bar's child labels fell into.
        graphics.text(font, glyph, x + (MARK - font.width(glyph)) / 2 + 1, y + 2,
                hover ? 0xFFFFFFFF : 0xFFDDDDDD);
        if (hover) {
            tooltip = tip;
        }
        HOTSPOTS.add(new Hot(x, y, x + MARK, y + MARK, action));
    }

    /** "member" is most of the list and says nothing; the other two are worth a word. */
    private static String shortRank(String rank) {
        return switch (rank.toLowerCase(java.util.Locale.ROOT)) {
            case "leader" -> "Ldr";
            case "officer" -> "Off";
            default -> "";
        };
    }

    private static int row(GuiGraphicsExtractor graphics, Font font, int x, int y, int width,
            String label, String value) {
        graphics.text(font, label, x + PAD, y, LABEL);
        graphics.text(font, clip(font, value, width - PAD - 72), x + PAD + 66, y, VALUE);
        return y + ROW;
    }

    /** Cut a name to the width it has, with an ellipsis, rather than letting it run into a button. */
    private static String clip(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && font.width(cut + "…") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    /**
     * Every mutation is a command, so the server's own rank checks apply unchanged.
     *
     * <p>Followed by a refresh rather than by editing the list here: the server decides whether the
     * promotion happened, and a pane that moved somebody up because it asked to would be lying the
     * moment a check failed.</p>
     */
    private static void send(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            mc.getConnection().sendCommand(command);
            mc.getConnection().sendCommand("f panel");
        }
    }

    /**
     * Turn an owner's {@code &} colour codes into the {@code §} the font understands.
     *
     * <p>⚠ Applied to the standard's state and <b>nothing else</b>, and that asymmetry is the whole
     * point. That string comes from the message catalogue, so it is text the server owner wrote and
     * may reasonably have coloured — and until somebody did, this code had only ever seen the
     * uncoloured defaults and would have drawn a literal {@code &a}. Faction names and player names
     * are on the other side of the line: those are typed by players, and converting them would let
     * anybody colour their own name in somebody else's panel. Standards learned this once already,
     * when the first player to type an ampersand in chat got "Tom § Jerry".</p>
     */
    private static String colored(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()
                    && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(text.charAt(i + 1)) >= 0) {
                out.append('\u00a7');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Whole numbers without a trailing .0, which reads as noise on a bank balance. */
    private static String trim(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value) : String.format("%.1f", value);
    }

    private FactionPanel() {}
}
