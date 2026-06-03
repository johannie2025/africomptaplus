package com.wisedesign.africomptaplus.services;

import android.content.Context;
import android.database.Cursor;

import com.wisedesign.africomptaplus.db.DatabaseHelper;

import android.content.ContentValues;

/**
 * SecurityManager — Système de verrouillage 14 jours avec anti-retour d'horloge.
 *
 * Logique :
 *  1. Premier lancement → enregistre first_launch_date = now.
 *  2. Chaque lancement → vérifie que now >= last_known_date (anti-cheat horloge).
 *  3. Chaque lancement → vérifie que now - first_launch_date <= 14 jours.
 *  4. Met à jour last_known_date = now à chaque lancement valide.
 */
public class SecurityManager {

    // 14 jours en millisecondes
    private static final long TRIAL_DURATION_MS = 14L * 24 * 60 * 60 * 1000; // 1 209 600 000 ms

    public enum LockReason {
        NONE,           // OK, pas verrouillé
        CLOCK_FRAUD,    // Horloge retournée en arrière
        TRIAL_EXPIRED,  // 14 jours dépassés
        ALREADY_LOCKED  // is_activated = -1 (verrouillé manuellement)
    }

    private final DatabaseHelper db;

    public SecurityManager(Context context) {
        this.db = DatabaseHelper.getInstance(context);
    }

    /**
     * Point d'entrée principal — à appeler dans SplashActivity.
     * @return LockReason.NONE si l'app peut démarrer, sinon la raison du verrouillage.
     */
    public LockReason checkAndUpdate() {
        long now = System.currentTimeMillis();

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT " + DatabaseHelper.COL_FIRST_LAUNCH_DATE
                    + ", " + DatabaseHelper.COL_LAST_KNOWN_DATE
                    + ", " + DatabaseHelper.COL_IS_ACTIVATED
                    + " FROM " + DatabaseHelper.T_APP_SECURITY
                    + " LIMIT 1", null);

            if (!cursor.moveToFirst()) {
                // ── Premier lancement ──────────────────────────────────────
                insertSecurityRow(now);
                return LockReason.NONE;
            }

            long firstLaunch  = cursor.getLong(0);
            long lastKnown    = cursor.getLong(1);
            int  isActivated  = cursor.getInt(2);

            // ── Déjà activé (code premium valide) ─────────────────────────
            if (isActivated == 1) {
                updateLastKnown(now);
                return LockReason.NONE;
            }

            // ── Verrouillé manuellement ────────────────────────────────────
            if (isActivated == -1) {
                return LockReason.ALREADY_LOCKED;
            }

            // ── Anti-cheat : horloge retournée en arrière ──────────────────
            if (now < lastKnown) {
                hardLock();
                return LockReason.CLOCK_FRAUD;
            }

            // ── Trial expiré (14 jours) ────────────────────────────────────
            if (now - firstLaunch > TRIAL_DURATION_MS) {
                hardLock();
                return LockReason.TRIAL_EXPIRED;
            }

            // ── Tout OK : mise à jour last_known_date ──────────────────────
            updateLastKnown(now);
            return LockReason.NONE;

        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * Retourne le nombre de jours restants dans la période d'essai (0 si expiré).
     */
    public int getRemainingDays() {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT " + DatabaseHelper.COL_FIRST_LAUNCH_DATE
                    + " FROM " + DatabaseHelper.T_APP_SECURITY + " LIMIT 1", null);

            if (!cursor.moveToFirst()) return 14;

            long firstLaunch = cursor.getLong(0);
            long elapsed     = System.currentTimeMillis() - firstLaunch;
            long remaining   = TRIAL_DURATION_MS - elapsed;
            int  days        = (int) (remaining / (24L * 60 * 60 * 1000));
            return Math.max(0, days);

        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * Active l'app définitivement (après validation du code d'activation).
     * Appelé depuis ActivationActivity.
     */
    public void activate() {
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_IS_ACTIVATED, 1);
        db.update(DatabaseHelper.T_APP_SECURITY, cv, null, null);
    }

    // ── Privés ───────────────────────────────────────────────────────────────

    private void insertSecurityRow(long now) {
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_FIRST_LAUNCH_DATE, now);
        cv.put(DatabaseHelper.COL_LAST_KNOWN_DATE,   now);
        cv.put(DatabaseHelper.COL_IS_ACTIVATED,      0);
        db.insert(DatabaseHelper.T_APP_SECURITY, cv);
    }

    private void updateLastKnown(long now) {
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_LAST_KNOWN_DATE, now);
        db.update(DatabaseHelper.T_APP_SECURITY, cv, null, null);
    }

    /**
     * Pose un verrou permanent (is_activated = -1).
     * Seul un code d'activation valide peut lever ce verrou.
     */
    private void hardLock() {
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_IS_ACTIVATED, -1);
        db.update(DatabaseHelper.T_APP_SECURITY, cv, null, null);
    }
}
