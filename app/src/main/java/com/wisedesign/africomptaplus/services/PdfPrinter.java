package com.wisedesign.africomptaplus.services;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Impression d'un PDF via le service d'impression Android (imprimante, Bluetooth, « Enregistrer en PDF »). */
public final class PdfPrinter {
    private PdfPrinter() {}

    public static void print(Activity a, Uri pdf, String jobName) {
        PrintManager pm = (PrintManager) a.getSystemService(Context.PRINT_SERVICE);
        if (pm == null) return;
        final Context app = a.getApplicationContext();
        pm.print(jobName, new PrintDocumentAdapter() {
            @Override public void onLayout(PrintAttributes o, PrintAttributes n, CancellationSignal c,
                                           LayoutResultCallback cb, Bundle extras) {
                if (c.isCanceled()) { cb.onLayoutCancelled(); return; }
                cb.onLayoutFinished(new PrintDocumentInfo.Builder(jobName)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true);
            }
            @Override public void onWrite(PageRange[] pages, ParcelFileDescriptor dest, CancellationSignal c, WriteResultCallback cb) {
                try (InputStream in = app.getContentResolver().openInputStream(pdf);
                     OutputStream out = new FileOutputStream(dest.getFileDescriptor())) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                } catch (Exception e) { cb.onWriteFailed(e.getMessage()); }
            }
        }, null);
    }
}
