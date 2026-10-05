package com.mostakim.audiorec.ui.screens;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.R;
import com.mostakim.audiorec.db.Models.Session;
import com.mostakim.audiorec.ui.Dialogs;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.kit.Ui;
import com.mostakim.audiorec.util.Fmt;

import java.util.List;

/** Sessions: create, open, edit, archive and delete production sessions. */
public class SessionsScreen extends Screen {

    public SessionsScreen(MainActivity a) {
        super(a, "Sessions", "Production sessions and their takes", true);
    }

    @Override
    protected void build(LinearLayout col) {
        List<Session> sessions = act.store().sessions(null);
        long activeId = App.get().prefs().lastSessionId();

        LinearLayout actions = Ui.row(act);
        actions.addView(Ui.button(act, "+  New session", R.style.Btn_Primary,
                        v -> Dialogs.sessionEditor(act, act.store(), null, () -> {
                            refresh();
                            act.refreshHeader();
                        })),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(Ui.spacer(act, 8));
        actions.addView(Ui.button(act, "Record", R.style.Btn, v -> navigate(MainActivity.PAGE_RECORDER)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(actions);
        col.addView(Ui.spacer(act, 14));

        if (sessions.isEmpty()) {
            empty("No sessions yet.\nA session groups takes, keeps the artist and venue, and "
                            + "carries its own default capture format.",
                    "Create the first session",
                    v -> Dialogs.sessionEditor(act, act.store(), null, () -> refresh()));
            return;
        }

        col.addView(section(sessions.size() + (sessions.size() == 1 ? " SESSION" : " SESSIONS")));
        for (final Session s : sessions) {
            boolean active = s.id == activeId;
            LinearLayout card = Ui.card(act);
            card.setBackgroundResource(active ? R.drawable.bg_selected : R.drawable.bg_card);
            col.addView(card);

            LinearLayout head = Ui.row(act);
            head.addView(Ui.head(act, s.name),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (active) head.addView(Ui.pill(act, "ACTIVE", R.drawable.bg_pill, th.accent));
            else if ("archived".equals(s.status)) {
                head.addView(Ui.pill(act, "ARCHIVED", R.drawable.bg_badge, th.textTertiary));
            }
            Ui.addWide(card, head);

            if (!s.artist.isEmpty() || !s.venue.isEmpty()) {
                card.addView(Ui.caption(act, (s.artist.isEmpty() ? "" : s.artist)
                        + (s.venue.isEmpty() ? "" : (s.artist.isEmpty() ? "" : "  \u00b7  ") + s.venue)));
            }
            card.addView(Ui.caption(act, s.formatSummary()));
            card.addView(Ui.caption(act, s.trackCount + (s.trackCount == 1 ? " take" : " takes")
                    + "  \u00b7  " + Fmt.size(s.totalBytes)
                    + "  \u00b7  created " + Fmt.stampShort(s.createdAt)));

            card.addView(Ui.spacer(act, 10));
            LinearLayout buttons = Ui.row(act);
            buttons.addView(Ui.button(act, active ? "Open takes" : "Set active", R.style.Btn_Small,
                    v -> {
                        App.get().prefs().setLastSessionId(s.id);
                        if (!active) {
                            toast("Active session: " + s.name);
                            refresh();
                        } else {
                            navigate(MainActivity.PAGE_LIBRARY);
                        }
                    }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            buttons.addView(Ui.spacer(act, 6));
            buttons.addView(Ui.button(act, "Edit", R.style.Btn_Small,
                    v -> Dialogs.sessionEditor(act, act.store(), s, () -> refresh())),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            buttons.addView(Ui.spacer(act, 6));
            buttons.addView(Ui.button(act, "Delete", R.style.Btn_Small,
                            v -> confirmDelete(s)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Ui.addWide(card, buttons);
        }
    }

    private void confirmDelete(final Session s) {
        int takes = s.trackCount;
        Ui.confirm(act, "Delete session?",
                "\"" + s.name + "\" will be removed" + (takes > 0
                        ? " together with its " + takes + " take" + (takes == 1 ? "" : "s")
                        + " and their audio files" : "")
                        + ". This cannot be undone.",
                "Delete", () -> {
                    act.store().delete(s.id, takes > 0);
                    if (App.get().prefs().lastSessionId() == s.id) {
                        App.get().prefs().setLastSessionId(-1);
                    }
                    refresh();
                    act.refreshHeader();
                    toast("Session deleted");
                });
    }
}
