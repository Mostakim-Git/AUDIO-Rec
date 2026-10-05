package com.mostakim.audiorec.ui.kit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.mostakim.audiorec.R;

/** View factory + small interaction helpers used by every screen. */
public final class Ui {

    private Ui() {
    }

    // ------------------------------------------------------------- metrics --
    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static int sp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().scaledDensity);
    }

    // --------------------------------------------------------------- text ---
    public static TextView text(Context c, String s, int styleRes) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextAppearance(c, styleRes);
        return t;
    }

    public static TextView body(Context c, String s) {
        return text(c, s, R.style.T_Body);
    }

    public static TextView dim(Context c, String s) {
        return text(c, s, R.style.T_Dim);
    }

    public static TextView caption(Context c, String s) {
        return text(c, s, R.style.T_Caption);
    }

    public static TextView head(Context c, String s) {
        return text(c, s, R.style.T_Head);
    }

    public static TextView title(Context c, String s) {
        return text(c, s, R.style.T_Title);
    }

    public static TextView section(Context c, String s) {
        TextView t = text(c, s, R.style.T_Section);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 18);
        lp.bottomMargin = dp(c, 8);
        t.setLayoutParams(lp);
        return t;
    }

    public static TextView mono(Context c, String s) {
        return text(c, s, R.style.T_Mono);
    }

    public static TextView keyValue(Context c, String key, String value) {
        TextView t = new TextView(c);
        t.setText(key + "  " + value);
        t.setTextAppearance(c, R.style.T_Caption);
        return t;
    }

    // -------------------------------------------------------------- layout --
    /**
     * A vertical container.
     *
     * The default layout params matter: a container added to a card without
     * explicit params would otherwise be measured wrap-content, and a row of
     * weighted children inside a wrap-content parent collapses to nothing - which
     * is exactly what "the alignment is wrong" looks like on a device.  Rows and
     * columns default to the full width of whatever holds them.
     */
    /**
     * A vertical container.
     *
     * A container is measured wrap-content until something says otherwise.  That
     * matters: a row of weighted children - 0 dp wide, sharing the leftover - has
     * no natural width at all, so a wrap-content parent collapses it to nothing
     * and the operator sees a card with nothing in it.  Anything that is meant to
     * span its card has to say so, which is what {@link #wide} is for.
     */
    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /**
     * The full width of whatever holds it - for a card or a column, not a row.
     *
     * Only the width is touched.  A view that already asked for a fixed height -
     * a 190 dp level meter, say - keeps it, along with its margins.
     */
    public static <T extends View> T wide(T v) {
        ViewGroup.LayoutParams existing = v.getLayoutParams();
        LinearLayout.LayoutParams lp;
        if (existing instanceof LinearLayout.LayoutParams) {
            lp = new LinearLayout.LayoutParams(existing);
            if (existing instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) existing;
                lp.setMargins(m.leftMargin, m.topMargin, m.rightMargin, m.bottomMargin);
            }
        } else {
            int h = existing == null ? ViewGroup.LayoutParams.WRAP_CONTENT : existing.height;
            lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
        }
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        v.setLayoutParams(lp);
        return v;
    }

    /** a row that spans its parent */
    public static LinearLayout wideRow(Context c) {
        return wide(row(c));
    }

    /**
     * Adds a view to a vertical container at full width and returns it.
     *
     * Every card in this app is built from these two helpers, so a container can
     * never silently collapse to zero width again.
     */
    public static <T extends View> T addWide(LinearLayout parent, T child) {
        wide(child);
        parent.addView(child);
        return child;
    }

    public static View spacer(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, heightDp)));
        return v;
    }

    /**
     * A filler that pushes what follows it to the far edge of a row.
     *
     * It is tagged because it is allowed to measure to nothing: when the text
     * beside it already fills the row, a zero-width filler is exactly right and
     * there is nothing to see or tap.
     */
    public static View flex(Context c) {
        View v = new View(c);
        v.setTag(FILLER);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    /** marks a view that carries no content, so tests may skip it when looking for squeezes */
    public static final String FILLER = "ui:filler";

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(c.getColor(R.color.stroke_soft));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
        return v;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackgroundResource(R.drawable.bg_card);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 12);
        l.setLayoutParams(lp);
        return l;
    }

    public static LinearLayout card(Context c, String title, String subtitle) {
        LinearLayout l = card(c);
        LinearLayout head = row(c);
        head.addView(head(c, title));
        head.addView(flex(c));
        if (subtitle != null) {
            TextView s = caption(c, subtitle);
            s.setGravity(Gravity.END);
            head.addView(s);
        }
        addWide(l, head);
        return l;
    }

    /** label on the left, value/trailing view on the right */
    public static LinearLayout settingRow(Context c, String label, View trailing, String hint) {
        LinearLayout r = row(c);
        LinearLayout texts = column(c);
        texts.addView(body(c, label));
        if (hint != null) texts.addView(caption(c, hint));
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (trailing != null) {
            // a long value - a device name, say - must not eat the whole row: the
            // label beside it is the part an operator reads first
            if (trailing instanceof TextView) {
                ((TextView) trailing).setMaxWidth(dp(c, 130));
            }
            r.addView(trailing);
        }
        int pad = dp(c, 10);
        r.setPadding(0, pad, 0, pad);
        wide(r);
        return r;
    }

    public static TextView button(Context c, String label, int styleRes, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextAppearance(c, styleRes);
        t.setClickable(true);
        t.setFocusable(true);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(backgroundFor(styleRes));
        int ph = dp(c, 14), pv = dp(c, 10);
        t.setPadding(ph, pv, ph, pv);
        if (l != null) t.setOnClickListener(l);
        return t;
    }

    private static int backgroundFor(int styleRes) {
        if (styleRes == R.style.Btn_Primary) return R.drawable.bg_btn_primary;
        if (styleRes == R.style.Btn_Danger) return R.drawable.bg_btn_danger;
        if (styleRes == R.style.Btn_Rec) return R.drawable.bg_btn_rec;
        return R.drawable.bg_btn;
    }

    public static TextView pill(Context c, String label, int bgRes, int textColor) {
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimension(R.dimen.text_small));
        t.setTextColor(textColor);
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        t.setBackgroundResource(bgRes);
        t.setSingleLine(true);
        return t;
    }

    public static TextView badge(Context c, String label) {
        TextView t = pill(c, label, R.drawable.bg_badge, c.getColor(R.color.text_secondary));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimension(R.dimen.text_tiny));
        return t;
    }

    public static EditText input(Context c, String hint, String value, int inputType) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setTextAppearance(c, R.style.T_Body);
        e.setBackgroundResource(R.drawable.bg_input);
        e.setHintTextColor(c.getColor(R.color.text_tertiary));
        e.setInputType(inputType);
        int p = dp(c, 12);
        e.setPadding(p, p, p, p);
        return e;
    }

    public static EditText textInput(Context c, String hint, String value) {
        EditText e = input(c, hint, value, InputType.TYPE_CLASS_TEXT);
        e.setSingleLine(true);
        return e;
    }

    public static EditText area(Context c, String hint, String value) {
        EditText e = input(c, hint, value,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setMinLines(3);
        e.setGravity(Gravity.TOP);
        return e;
    }

    /** WRAP_CONTENT child that grows inside a column */
    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    // ------------------------------------------------------------- dialogs --
    public interface OnText {
        void onText(String value);
    }

    public interface OnConfirm {
        void onConfirm();
    }

    /** themed single-field prompt (rename, etc.) */
    public static void prompt(Activity a, String title, String hint, String initial,
                              boolean multiline, boolean number, final OnText cb) {
        Theme th = new Theme(a);
        LinearLayout col = column(a);
        int pad = dp(a, 20);
        col.setPadding(pad, dp(a, 8), pad, dp(a, 4));
        final EditText field = multiline
                ? area(a, hint, initial)
                : input(a, hint, initial, number
                        ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        : InputType.TYPE_CLASS_TEXT);
        if (initial != null) field.setSelection(field.getText().length());
        Ui.addWide(col, field);

        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(col)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String s = field.getText().toString().trim();
            d.dismiss();
            cb.onText(s);
        });
    }

    public static void confirm(Activity a, String title, String message,
                               String positive, final OnConfirm onYes) {
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positive, (dlg, w) -> onYes.onConfirm())
                .setNegativeButton("Cancel", null)
                .create();
        d.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(a.getColor(R.color.rec));
    }

    public static AlertDialog.Builder dialog(Activity a, String title) {
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(title);
        return b;
    }

    public static AlertDialog show(AlertDialog.Builder b, boolean wrapScroll) {
        AlertDialog d = b.setNegativeButton("Close", null).create();
        d.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        d.show();
        return d;
    }

    public static View scrollWrap(Context c, View content, int maxHeightDp) {
        ScrollView s = new ScrollView(c);
        s.addView(content);
        s.setVerticalScrollBarEnabled(true);
        return s;
    }

    // ------------------------------------------------------------ feedback --
    public static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static void longToast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
    }

    public static void monospace(TextView t) {
        t.setTypeface(Typeface.MONOSPACE);
    }

    /** tinted square used as a section marker */
    public static View dot(Context c, int color) {
        View v = new View(c);
        int s = dp(c, 8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
        lp.rightMargin = dp(c, 10);
        v.setLayoutParams(lp);
        v.setBackgroundResource(R.drawable.bg_dot);
        v.getBackground().setTint(color);
        return v;
    }
}
