package com.mostakim.audiorec.ui.kit;

import android.content.Context;
import android.graphics.Color;

import com.mostakim.audiorec.R;

/** Resolved palette + type scale. One place to restyle the whole app. */
public final class Theme {

    public final int bgRoot, bgPanel, bgCard, bgCardHi, bgSunken, bgSidebar;
    public final int stroke, strokeSoft;
    public final int textPrimary, textSecondary, textTertiary, textDisabled;
    public final int accent, accentDim, accentPress, brand, rec, recDim;
    public final int ok, warn, busy;
    public final int meterBg, meterLow, meterMid, meterHigh, meterPeak, meterRms;
    public final int scopeTrace, scopeGrid;
    public final int scrim;

    public final float dp;
    public final float textDisplay, textTitle, textHead, textBody, textSmall, textTiny, textMonoL;

    public Theme(Context c) {
        dp = c.getResources().getDisplayMetrics().density;
        bgRoot = c.getColor(R.color.bg_root);
        bgPanel = c.getColor(R.color.bg_panel);
        bgCard = c.getColor(R.color.bg_card);
        bgCardHi = c.getColor(R.color.bg_card_hi);
        bgSunken = c.getColor(R.color.bg_sunken);
        bgSidebar = c.getColor(R.color.bg_sidebar);
        stroke = c.getColor(R.color.stroke);
        strokeSoft = c.getColor(R.color.stroke_soft);
        textPrimary = c.getColor(R.color.text_primary);
        textSecondary = c.getColor(R.color.text_secondary);
        textTertiary = c.getColor(R.color.text_tertiary);
        textDisabled = c.getColor(R.color.text_disabled);
        accent = c.getColor(R.color.accent);
        accentDim = c.getColor(R.color.accent_dim);
        accentPress = c.getColor(R.color.accent_press);
        brand = c.getColor(R.color.brand);
        rec = c.getColor(R.color.rec);
        recDim = c.getColor(R.color.rec_dim);
        ok = c.getColor(R.color.ok);
        warn = c.getColor(R.color.warn);
        busy = c.getColor(R.color.busy);
        meterBg = c.getColor(R.color.meter_bg);
        meterLow = c.getColor(R.color.meter_low);
        meterMid = c.getColor(R.color.meter_mid);
        meterHigh = c.getColor(R.color.meter_high);
        meterPeak = c.getColor(R.color.meter_peak);
        meterRms = c.getColor(R.color.meter_rms);
        scopeTrace = c.getColor(R.color.scope_trace);
        scopeGrid = c.getColor(R.color.scope_grid);
        scrim = c.getColor(R.color.scrim);

        textDisplay = c.getResources().getDimension(R.dimen.text_display);
        textTitle = c.getResources().getDimension(R.dimen.text_title);
        textHead = c.getResources().getDimension(R.dimen.text_head);
        textBody = c.getResources().getDimension(R.dimen.text_body);
        textSmall = c.getResources().getDimension(R.dimen.text_small);
        textTiny = c.getResources().getDimension(R.dimen.text_tiny);
        textMonoL = c.getResources().getDimension(R.dimen.text_mono_l);
    }

    public int dp(float v) {
        return Math.round(v * dp);
    }

    /** meter colour for a dBFS value (green / amber / red zones) */
    public int meterColor(float db) {
        if (db >= -0.5f) return meterHigh;
        if (db >= -6f) return meterMid;
        return meterLow;
    }

    /** colour with an alpha multiplier (0..1) */
    public static int alpha(int color, float a) {
        int al = Math.round(Color.alpha(color) * Math.max(0f, Math.min(1f, a)));
        return (color & 0x00FFFFFF) | (al << 24);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
