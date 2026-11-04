package com.wisedesign.africomptaplus.services;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Google Drive REST v3 — dossier caché "appDataFolder" (visible uniquement par l'app, scope drive.appdata).
 * Toutes les méthodes sont bloquantes : à appeler hors du thread principal.
 */
public class DriveBackup {
    public static final String SCOPE = "https://www.googleapis.com/auth/drive.appdata";
    private static final String API = "https://www.googleapis.com/drive/v3/files";
    private static final String UPLOAD = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart";
    private static final int KEEP = 3;

    /** Envoie la sauvegarde et ne garde que les 3 dernières. */
    public static void upload(String token, String name, byte[] data) throws Exception {
        String b = "acp" + System.currentTimeMillis();
        JSONObject meta = new JSONObject();
        meta.put("name", name);
        meta.put("parents", new JSONArray().put("appDataFolder"));
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + b + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + meta + "\r\n--" + b
                + "\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(data);
        body.write(("\r\n--" + b + "--").getBytes(StandardCharsets.UTF_8));

        HttpURLConnection c = (HttpURLConnection) new URL(UPLOAD).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestProperty("Content-Type", "multipart/related; boundary=" + b);
            c.setDoOutput(true);
            c.setConnectTimeout(15000); c.setReadTimeout(60000);
            try (OutputStream os = c.getOutputStream()) { os.write(body.toByteArray()); }
            if (c.getResponseCode() >= 300) throw new Exception("Drive : erreur " + c.getResponseCode());
        } finally { c.disconnect(); }

        JSONArray files = list(token);
        for (int i = KEEP; i < files.length(); i++) delete(token, files.getJSONObject(i).getString("id"));
    }

    /** Fichiers du dossier caché, du plus récent au plus ancien : [{id,name,modifiedTime,size}]. */
    public static JSONArray list(String token) throws Exception {
        String url = API + "?spaces=appDataFolder&orderBy=" + URLEncoder.encode("modifiedTime desc", "UTF-8")
                + "&pageSize=20&fields=" + URLEncoder.encode("files(id,name,modifiedTime,size)", "UTF-8");
        return new JSONObject(new String(get(token, url), StandardCharsets.UTF_8)).getJSONArray("files");
    }

    public static byte[] download(String token, String fileId) throws Exception {
        return get(token, API + "/" + fileId + "?alt=media");
    }

    private static void delete(String token, String id) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(API + "/" + id).openConnection();
            c.setRequestMethod("DELETE");
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.getResponseCode();
            c.disconnect();
        } catch (Exception ignored) {}
    }

    private static byte[] get(String token, String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setConnectTimeout(15000); c.setReadTimeout(60000);
            int code = c.getResponseCode();
            if (code >= 300) throw new Exception("Drive : erreur " + code);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            }
            return bo.toByteArray();
        } finally { c.disconnect(); }
    }
}
