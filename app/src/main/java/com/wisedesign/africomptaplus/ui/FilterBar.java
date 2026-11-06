package com.wisedesign.africomptaplus.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import com.wisedesign.africomptaplus.R;

import java.util.Calendar;
import java.util.Locale;

/** Barre de recherche + filtre de période (layout view_filter_bar). Dates au format yyyy-MM-dd. */
public class FilterBar {
    public interface Listener { void onChange(String query, String from, String to); }

    private final Activity act;
    private final Listener listener;
    private final EditText et;
    private final TextView tvFrom, tvTo;
    private String from = "", to = "";

    public static void attach(Activity a, Listener l) { new FilterBar(a, l); }

    private FilterBar(Activity a, Listener l) {
        act = a; listener = l;
        et = a.findViewById(R.id.etSearch);
        tvFrom = a.findViewById(R.id.tvFrom);
        tvTo = a.findViewById(R.id.tvTo);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { fire(); }
        });
        tvFrom.setOnClickListener(v -> pick(from, d -> { from = d; fire(); }));
        tvTo.setOnClickListener(v -> pick(to, d -> { to = d; fire(); }));
        a.findViewById(R.id.tvClearFilter).setOnClickListener(v -> { et.setText(""); from = ""; to = ""; fire(); });
        UiKit.pressScale(tvFrom, tvTo);
        refreshLabels();
    }

    private interface Picked { void on(String date); }

    private void pick(String current, Picked cb) {
        Calendar c = Calendar.getInstance();
        if (current != null && current.length() >= 10) {
            try {
                c.set(Integer.parseInt(current.substring(0, 4)), Integer.parseInt(current.substring(5, 7)) - 1,
                        Integer.parseInt(current.substring(8, 10)));
            } catch (Exception ignored) {}
        }
        new DatePickerDialog(act, (v, y, m, d) -> cb.on(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void fire() {
        if (!from.isEmpty() && !to.isEmpty() && to.compareTo(from) < 0) { String t = from; from = to; to = t; }
        refreshLabels();
        listener.onChange(et.getText().toString(), from, to);
    }

    private static String show(String iso) {
        return iso.length() >= 10 ? iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4) : "—";
    }

    private void refreshLabels() {
        tvFrom.setText("Du : " + show(from));
        tvTo.setText("Au : " + show(to));
    }
}
