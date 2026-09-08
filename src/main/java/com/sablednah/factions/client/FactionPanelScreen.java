package com.sablednah.factions.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.sablednah.factions.FactionPanelPayload;

/**
 * The faction at a glance: power, land, bank, standard, relations and who is in it.
 *
 * <h2>Everything here is also a command</h2>
 *
 * <p>{@code /f status}, {@code /f power}, {@code /f who}, {@code /f standard} and {@code /f money}
 * each print part of this, and {@code /f panel} prints the lot for a client that cannot draw. The
 * screen is a nicer surface for the same answers — it must never be the only way to learn
 * something, which is the rule the whole client half is built on.</p>
 *
 * <p>Every button sends a command too: kick, promote and demote are {@code /f kick} and friends. So
 * there is no serverbound payload, and every permission and rank check the commands already do
 * applies unchanged.</p>
 */
public final class FactionPanelScreen extends Screen {

    private static final int WIDTH = 260;
    private static final int ROW = 12;

    private final FactionPanelPayload data;
    private Tab tab = Tab.OVERVIEW;
    private int scroll;

    private enum Tab { OVERVIEW, RELATIONS, MEMBERS }

    private FactionPanelScreen(FactionPanelPayload data) {
        super(Component.literal(data.name()));
        this.data = data;
    }

    /** Called when the payload lands — the answer arriving is what opens the screen. */
    public static void openWith(FactionPanelPayload payload) {
        Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new FactionPanelScreen(payload)));
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int top = 30;
        addRenderableWidget(Button.builder(Component.literal("Overview"), b -> switchTo(Tab.OVERVIEW))
                .bounds(left, top, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Relations"), b -> switchTo(Tab.RELATIONS))
                .bounds(left + 86, top, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Members"), b -> switchTo(Tab.MEMBERS))
                .bounds(left + 172, top, 80, 18).build());

        if (tab == Tab.MEMBERS) {
            buildMemberButtons(left, top + 40);
        }
    }

    private void switchTo(Tab which) {
        tab = which;
        scroll = 0;
        rebuildWidgets();
    }

    /**
     * A row of buttons per member, for whoever may use them.
     *
     * <p>Drawn only for a leader or officer, because a button that always answers "you may not"
     * teaches a player to ignore buttons. The server checks again regardless — this decides what to
     * <em>show</em>, exactly as the action bar's capability set does.</p>
     */
    private void buildMemberButtons(int left, int top) {
        boolean mayManage = data.yourRank().equalsIgnoreCase("leader")
                || data.yourRank().equalsIgnoreCase("officer");
        if (!mayManage) {
            return;
        }
        int y = top;
        for (FactionPanelPayload.Member member : data.members()) {
            if (y > height - 60) {
                break;
            }
            final String who = member.name();
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> send("f promote " + who))
                    .bounds(left + WIDTH - 62, y - 3, 18, 16).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> send("f demote " + who))
                    .bounds(left + WIDTH - 42, y - 3, 18, 16).build());
            addRenderableWidget(Button.builder(Component.literal("✕"), b -> send("f kick " + who))
                    .bounds(left + WIDTH - 22, y - 3, 18, 16).build());
            y += ROW + 6;
        }
    }

    /** Every mutation is a command, so the server's own rank checks apply unchanged. */
    private void send(String command) {
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand(command);
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.render(graphics, mouseX, mouseY, partial);
        int left = (width - WIDTH) / 2;
        int y = 12;

        String title = data.name()
                + (data.tag().isEmpty() ? "" : " [" + data.tag() + "]")
                + (data.peaceful() ? " (peaceful)" : "");
        graphics.drawCenteredString(font, title, width / 2, y, 0xFFFFFF);

        y = 58;
        switch (tab) {
            case OVERVIEW -> {
                line(graphics, left, y, "Power", trim(data.power()) + " / " + trim(data.maxPower()));
                line(graphics, left, y += ROW, "Land",
                        data.claims() + " of " + data.entitlement() + " entitled");
                // Overreach is the number that decides whether they can be raided, so it is said
                // outright rather than left to be worked out from the two above.
                if (data.claims() > data.entitlement()) {
                    line(graphics, left, y += ROW, "Exposed",
                            (data.claims() - data.entitlement()) + " chunk(s) takeable");
                }
                line(graphics, left, y += ROW, "Bank", trim(data.bank()));
                line(graphics, left, y += ROW, "Standard", data.standardState());
                if (data.trophies() > 0) {
                    line(graphics, left, y += ROW, "Trophies", String.valueOf(data.trophies()));
                }
                if (data.raidsFought() > 0) {
                    line(graphics, left, y += ROW, "Raids",
                            data.raidsWon() + " won of " + data.raidsFought());
                }
            }
            case RELATIONS -> {
                line(graphics, left, y, "Allies", data.allies().isEmpty()
                        ? "none" : String.join(", ", data.allies()));
                line(graphics, left, y + ROW * 2, "Enemies", data.enemies().isEmpty()
                        ? "none" : String.join(", ", data.enemies()));
            }
            case MEMBERS -> {
                for (FactionPanelPayload.Member member : data.members()) {
                    if (y > height - 60) {
                        break;
                    }
                    // Online state as colour rather than as a word: a column of "(online)" would
                    // cost more width than it earns, and green reads without being read.
                    graphics.drawString(font, member.name(), left, y,
                            member.online() ? 0xFF55FF55 : 0xFF888888);
                    graphics.drawString(font, member.rank(), left + 110, y, 0xFFAAAAAA);
                    y += ROW + 6;
                }
            }
        }
    }

    private void line(GuiGraphics graphics, int left, int y, String label, String value) {
        graphics.drawString(font, label, left, y, 0xFFAAAAAA);
        graphics.drawString(font, value, left + 90, y, 0xFFFFFFFF);
    }

    /** Whole numbers without a trailing .0, which reads as noise on a bank balance. */
    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
