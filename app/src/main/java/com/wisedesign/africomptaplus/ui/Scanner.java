package com.wisedesign.africomptaplus.ui;

import com.journeyapps.barcodescanner.ScanOptions;
import com.wisedesign.africomptaplus.activities.PortraitCaptureActivity;

/** Options communes du scan code-barres (ZXing, 100 % hors-ligne). */
public final class Scanner {
    private Scanner() {}

    public static ScanOptions options(String prompt) {
        ScanOptions o = new ScanOptions();
        o.setPrompt(prompt);
        o.setBeepEnabled(true);
        o.setOrientationLocked(true);
        o.setCaptureActivity(PortraitCaptureActivity.class);
        return o;
    }
}
