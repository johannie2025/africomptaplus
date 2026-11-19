package com.wisedesign.africomptaplus;

import android.app.Application;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.ui.UiKit;

public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        com.wisedesign.africomptaplus.ui.I18n.init(this);   // langue de l'appareil par défaut, modifiable dans Paramètres
        try {
            String c = DatabaseHelper.getInstance(this).getConfig("shop_currency");
            if (c != null && !c.isEmpty()) UiKit.currency = c;
        } catch (Exception ignored) {}
    }
}
