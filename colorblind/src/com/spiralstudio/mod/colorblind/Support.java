package com.spiralstudio.mod.colorblind;

import com.threerings.opengl.gui.Container;
import com.threerings.opengl.gui.Label;
import com.threerings.opengl.gui.ac;
import com.threerings.opengl.gui.b;
import com.threerings.opengl.gui.e;
import com.threerings.opengl.gui.event.ActionEvent;
import com.threerings.opengl.gui.event.ChangeEvent;
import com.threerings.opengl.gui.o;
import com.threerings.opengl.gui.config.StyleConfig;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Everything that touches game types. Kept out of {@link Main}: Main goes
 * through {@code Registers.add()}, and a game type in a registered class can
 * load a game class before {@code ClassPool.init()} patches it.
 *
 * <p>Injection strategy. Options > Video is a UserInterfaceConfig
 * ({@code ui/options/video.dat}) holding a 2-column table of
 * [label | control] rows; the game builds it by looking components up by tag.
 * {@code OptionsDialog.wasAdded()} runs every time the dialog opens, after the
 * tabs exist, so we hook there, find the table through a tag that has been
 * stable ({@code camera_shake}, falling back to {@code render_quality}) and
 * append rows of our own. No layout manager is touched: our rows are plain
 * 2-cell appends, so the table's own sizing/scroll behaviour applies. Labels
 * and buttons copy the style configs of their siblings so fonts, colours and
 * hover/press states are the game's. If the tags are gone (renamed in a
 * patch), nothing is injected and one line is logged; the filter and the yml
 * still work.
 */
public final class Support {
    private Support() {
    }

    private static final Set<Object> injected = Collections.newSetFromMap(new WeakHashMap<>());

    /** Render hook, after ProjectXApp.renderView(). */
    public static void frame(long window) {
        Filter.frame(window);
    }

    /** OptionsDialog.wasAdded() hook. */
    public static void inject(Object dialog) {
        try {
            if (injected.contains(dialog)) {
                return;
            }
            Object anchor = find(dialog, "camera_shake");
            if (anchor == null) {
                anchor = find(dialog, "render_quality");
            }
            if (!(anchor instanceof o)) {
                System.out.println("[colorblind] Video tab anchor not found; controls not added.");
                injected.add(dialog);
                return;
            }
            Container table = ((o) anchor).getParent();
            if (table == null) {
                System.out.println("[colorblind] Video tab anchor has no parent; controls not added.");
                injected.add(dialog);
                return;
            }
            Object button = find(dialog, "edit_advanced");
            build(table, (o) anchor, button instanceof o ? (o) button : null);
            try {
                flatten(find(dialog, "screen_mode"), (o) anchor, table);
                flatten(find(dialog, "ui_scale_panel"), (o) anchor, table);
            } catch (Throwable t) {
                System.out.println("[colorblind] could not flatten boxes: " + t);
            }
            injected.add(dialog);
        } catch (Throwable t) {
            System.out.println("[colorblind] Could not add Video controls: " + t);
        }
    }

    /**
     * Drops the framed "sub window" look (border + padding) of the Display Mode and
     * UI Scaling boxes by giving them the table container's plain style, which
     * frees the vertical space our rows need so everything fits on the grey panel.
     */
    private static void flatten(Object member, o anchor, Container table) {
        if (!(member instanceof o)) {
            return;
        }
        // The box is the child of the first ancestor shared with the anchor.
        o box = (o) member;
        while (box.getParent() != null && !isAncestor(box.getParent(), anchor)) {
            box = box.getParent();
        }
        if (box == member && box.getParent() == null) {
            return;
        }
        StyleConfig[] plain = table.getStyleConfigs();
        box.setStyleConfigs(plain != null ? plain : new StyleConfig[0]);
    }

    private static boolean isAncestor(Container c, o node) {
        for (o n = node; n != null; n = n.getParent()) {
            if (n == c) {
                return true;
            }
        }
        return false;
    }

    /** getComponent(String) is on a game superclass; reach it without naming its type. */
    private static Object find(Object dialog, String tag) throws Exception {
        for (Class<?> c = dialog.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod("getComponent", String.class);
                m.setAccessible(true);
                return m.invoke(dialog, tag);
            } catch (NoSuchMethodException ignored) {
                // keep climbing
            }
        }
        return null;
    }

    private static void build(Container table, o anchor, o buttonModel) {
        com.threerings.opengl.util.d ctx = anchor.getContext();
        // The anchor's left neighbour is its row label: borrow its style.
        int at = table.getComponentIndex(anchor);
        StyleConfig[] labelStyle = null;
        if (at > 0) {
            labelStyle = table.getComponent(at - 1).getStyleConfigs();
        }
        StyleConfig[] buttonStyle = buttonModel == null ? null : buttonModel.getStyleConfigs();
        // Keep the 2-column grid aligned if the table has an odd number of cells.
        if (table.getComponentCount() % 2 != 0) {
            table.add(new Label(ctx, ""));
        }

        Label status = new Label(ctx, "");
        applyStyle(status, labelStyle);
        b model = new b(0, Settings.strength, 1, 101);
        ac slider = new ac(ctx, 0, model);
        slider.setPreferredSize(170, 20);

        // The buttons' text is refreshed by a Runnable that must see the buttons
        // they belong to, so it reads them out of this holder once built.
        e[] btn = new e[3];
        Runnable refresh = () -> {
            btn[0].setText(Settings.type.label);
            btn[1].setText(Settings.mode.label);
            if (model.getValue() != Settings.strength) {
                model.setValue(Settings.strength);
            }
            status.setText(strengthText());
        };
        e typeBtn = button(ctx, "", () -> {
            Cvd.Type[] all = Cvd.Type.values();
            Settings.type = all[(Settings.type.ordinal() + 1) % all.length];
            Settings.changed();
        }, refresh, buttonStyle);
        e modeBtn = button(ctx, "", () -> {
            Cvd.Mode[] all = Cvd.Mode.values();
            Settings.mode = all[(Settings.mode.ordinal() + 1) % all.length];
            Settings.changed();
        }, refresh, buttonStyle);
        e reset = button(ctx, "Reset to default", Settings::resetToDefaults, refresh, buttonStyle);
        btn[0] = typeBtn;
        btn[1] = modeBtn;
        btn[2] = reset;
        model.addChangeListener((ChangeEvent ev) -> {
            if (Settings.strength != model.getValue()) {
                Settings.strength = Settings.clamp(model.getValue());
                Settings.changed();
                status.setText(strengthText());
            }
        });
        // b is (min, value, extent, max); extent 1 keeps the thumb on the track at 100.
        // Three rows only: Options > Video has little spare height and the UI-scale
        // box sits just under the table. Mode and Reset share a row; the strength
        // label doubles as the live preview ("Strength 60%").
        addRow(table, ctx, labelStyle, "Colorblind filter", typeBtn);
        Container pair = row(ctx, 6);
        if (pair != null) {
            pair.add(modeBtn);
            pair.add(reset);
            addRow(table, ctx, labelStyle, "Filter mode", pair);
        } else {
            addRow(table, ctx, labelStyle, "Filter mode", modeBtn);
            addRow(table, ctx, labelStyle, "", reset);
        }
        table.add(status);
        table.add(slider);
        refresh.run();
        typeBtn.setTooltipText("Off, Deuteranopia (red-green), Protanopia (red-green), Tritanopia (blue-yellow).");
        modeBtn.setTooltipText("Correct: make confusable colours easier to tell apart. Simulate: show what this type sees.");
        slider.setTooltipText("0% leaves the picture alone, 100% is the full effect. Applies immediately.");
        reset.setTooltipText("Turn the filter off and restore the default strength.");
    }

    private static e button(com.threerings.opengl.util.d ctx, String text, Runnable act, Runnable refresh,
                            StyleConfig[] style) {
        e btn = new e(ctx, text, (com.threerings.opengl.gui.event.a) (ActionEvent ev) -> {
            try {
                act.run();
                refresh.run();
            } catch (Throwable t) {
                System.out.println("[colorblind] button failed: " + t);
            }
        }, "colorblind");
        applyStyle(btn, style);
        return btn;
    }

    private static void applyStyle(o c, StyleConfig[] style) {
        if (style != null && style.length > 0) {
            try {
                c.setStyleConfigs(style);
            } catch (Throwable t) {
                // keep the default look rather than fail the whole panel
            }
        }
    }

    private static void addRow(Container table, com.threerings.opengl.util.d ctx, StyleConfig[] labelStyle,
                               String label, o control) {
        Label l = new Label(ctx, label);
        applyStyle(l, labelStyle);
        table.add(l);
        table.add(control);
    }

    static String strengthText() {
        return "Strength " + Settings.strength + "%";
    }

    /**
     * A left-to-right row. The layout managers live in {@code opengl.gui.d},
     * which is also a class name, so they can't be written in source (see
     * modconfig's Layouts); build one reflectively and return null on any
     * failure so the caller falls back to plain rows.
     */
    private static Container row(com.threerings.opengl.util.d ctx, int gap) {
        try {
            String pkg = "com.threerings.opengl.gui.d.";
            Class<?> e = Class.forName(pkg + "e");
            Class<?> policy = Class.forName(pkg + "e$c");
            Class<?> justify = Class.forName(pkg + "e$b");
            Object layout = e.getMethod("makeHoriz", policy, justify, policy).invoke(null,
                e.getField("NONE").get(null), e.getField("LEFT").get(null), e.getField("STRETCH").get(null));
            layout = e.getMethod("setGap", int.class).invoke(layout, gap);
            Container c = new Container(ctx, null);
            Container.class.getMethod("setLayoutManager", Class.forName(pkg + "g")).invoke(c, layout);
            return c;
        } catch (Throwable t) {
            System.out.println("[colorblind] row layout unavailable, using plain rows: " + t);
            return null;
        }
    }

    static String statusText() {
        if (Settings.type == Cvd.Type.OFF) {
            return "Filter off";
        }
        return Settings.type.label + " - " + Settings.mode.label.toLowerCase() + " - " + Settings.strength + "%";
    }
}
