package com.wisedesign.africomptaplus.services;

import java.util.ArrayList;
import java.util.List;

/** Tableau générique (affiché à l'écran et exporté en PDF). */
public class ReportTable {
    public String title = "";
    public String[] headers;
    public float[] weights;
    public boolean[] right;
    public final List<String[]> rows = new ArrayList<>();
    public final List<Boolean> bold = new ArrayList<>();
    public final List<Long> ids = new ArrayList<>();

    public ReportTable(String title, String[] headers, float[] weights, boolean[] right) {
        this.title = title; this.headers = headers; this.weights = weights; this.right = right;
    }
    public void add(boolean b, long id, String... cells) {
        String[] r = new String[headers.length];
        for (int i = 0; i < r.length; i++) r[i] = i < cells.length && cells[i] != null ? cells[i] : "";
        rows.add(r); bold.add(b); ids.add(id);
    }
    public void add(boolean b, String... cells) { add(b, -1, cells); }
}
