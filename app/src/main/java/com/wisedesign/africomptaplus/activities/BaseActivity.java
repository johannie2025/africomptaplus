package com.wisedesign.africomptaplus.activities;

import android.content.Context;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.africomptaplus.ui.I18n;

/** Base commune : applique la langue choisie (ou celle de l'appareil) et traduit l'écran. */
public abstract class BaseActivity extends AppCompatActivity {
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(I18n.wrap(base)); }

    @Override public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        I18n.watch(getWindow().getDecorView());
    }
    @Override public void setContentView(View view) {
        super.setContentView(view);
        I18n.watch(getWindow().getDecorView());
    }
}
