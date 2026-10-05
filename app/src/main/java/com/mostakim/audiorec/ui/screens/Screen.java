package com.mostakim.audiorec.ui.screens;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mostakim.audiorec.R;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.kit.Theme;
import com.mostakim.audiorec.ui.kit.Ui;

/**
 * One page of the workstation.
 *
 * Screens build themselves into a vertical column; the base class handles the
 * chrome (page header, scrolling, padding, empty states, section helpers) so
 * every page stays visually consistent.
 */
public abstract class Screen {

    protected final MainActivity act;
    protected final Theme th;
    protected final LinearLayout col;
    private final ScrollView scroller;
    private final boolean scrollable;
    private final String title, subtitle;
    private boolean built;

    protected Screen(MainActivity a, String title, String subtitle, boolean scrollable) {
        this.act = a;
        this.th = a.theme();
        this.title = title;
        this.subtitle = subtitle;
        this.scrollable = scrollable;
        col = Ui.column(a);
        if (scrollable) {
            scroller = new ScrollView(a);
            scroller.setFillViewport(true);
            scroller.setClipToPadding(false);
            scroller.setVerticalScrollBarEnabled(true);
            scroller.setScrollbarFadingEnabled(false);
            int pad = Ui.dp(a, 16);
            scroller.setPadding(pad, Ui.dp(a, 12), pad, Ui.dp(a, 28));
            scroller.addView(col, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            scroller.setBackgroundColor(th.bgRoot);
        } else {
            scroller = null;
            col.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 16));
        }
        // build() is deliberately NOT called here.  A constructor must not run
        // subclass code that reads subclass fields - they are still null at this
        // point (PlaylistScreen's queue is the one that bit us) - so the page is
        // built on first use instead.
    }

    protected abstract void build(LinearLayout col);

    /** builds the page the first time something asks for it */
    private void ensureBuilt() {
        if (!built) {
            built = true;
            build(col);
        }
    }

    public View view() {
        ensureBuilt();
        return scrollable ? scroller : col;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    /** rebuild when data may have changed elsewhere */
    public void refresh() {
        if (!built) {
            ensureBuilt();
            return;
        }
        if (scroller != null) {
            int y = scroller.getScrollY();
            col.removeAllViews();
            build(col);
            scroller.post(() -> scroller.scrollTo(0, Math.min(y, scroller.getChildAt(0).getHeight())));
        } else {
            col.removeAllViews();
            build(col);
        }
    }

    public void onResume() {
    }

    public void onPause() {
    }

    // --------------------------------------------------------------- helpers
    protected void toast(String msg) {
        act.toast(msg);
    }

    protected void navigate(int page) {
        act.navigate(page);
    }

    protected TextView section(String text) {
        return Ui.section(act, text);
    }

    protected LinearLayout card() {
        LinearLayout c = Ui.card(act);
        Ui.addWide(col, c);
        return c;
    }

    protected LinearLayout card(String heading, String trailing) {
        LinearLayout c = Ui.card(act, heading, trailing);
        Ui.addWide(col, c);
        return c;
    }

    protected LinearLayout cardStyled(int bgRes) {
        LinearLayout c = Ui.card(act);
        c.setBackgroundResource(bgRes);
        Ui.addWide(col, c);
        return c;
    }

    /**
     * Centred empty-state message with a call to action.
     *
     * It adds the card to the page and returns it: a caller that builds the card
     * and forgets to add it leaves a hole in the page instead of a message.
     */
    protected LinearLayout empty(String message, String actionLabel, View.OnClickListener action) {
        LinearLayout c = emptyCard(message, actionLabel, action);
        Ui.addWide(col, c);
        return c;
    }

    /** the empty-state card without a parent - for a page that owns its list */
    protected LinearLayout emptyCard(String message, String actionLabel,
                                     View.OnClickListener action) {
        LinearLayout c = Ui.card(act);
        TextView t = Ui.dim(act, message);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(act, 18), 0, Ui.dp(act, 12));
        Ui.addWide(c, t);
        if (actionLabel != null) {
            LinearLayout row = Ui.row(act);
            row.setGravity(Gravity.CENTER);
            row.addView(Ui.button(act, actionLabel, R.style.Btn_Primary, action));
            Ui.addWide(c, row);
        }
        return c;
    }

    /** big number + caption, used across the dashboard */
    protected LinearLayout stat(String value, String label, String hint, int valueColor) {
        LinearLayout c = Ui.column(act);
        TextView v = Ui.text(act, value, R.style.T_Display);
        v.setTextColor(valueColor);
        v.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        Ui.addWide(c, v);
        TextView l = Ui.caption(act, label);
        l.setTextColor(act.getColor(com.mostakim.audiorec.R.color.text_secondary));
        Ui.addWide(c, l);
        if (hint != null) {
            TextView h = Ui.text(act, hint, R.style.T_Caption);
            h.setTextColor(th.textTertiary);
            Ui.addWide(c, h);
        }
        return c;
    }

    protected void hairline() {
        col.addView(Ui.divider(act));
    }

    protected View spacer(int dp) {
        return Ui.spacer(act, dp);
    }
}
