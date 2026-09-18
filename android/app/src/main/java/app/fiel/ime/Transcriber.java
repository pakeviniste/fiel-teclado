package app.fiel.ime;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class Transcriber {
    static final class Result {
        final boolean ok;
        final String text;
        final String error;
        Result(boolean ok, String text, String error) {
            this.ok = ok;
            this.text = text;
            this.error = error;
        }
    }

    static Result ping(Context c) {
        if (!Prefs.ready(c)) return new Result(false, null, "Falta la clave.");
        try {
            HttpURLConnection conn = open(Prefs.transcribeUrl(c), Prefs.token(c), "GET", 8000);
            int code = conn.getResponseCode();
            String body = read(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            conn.disconnect();
            if (code >= 200 && code < 300) return new Result(true, "ok", null);
            return new Result(false, null, message(body, code));
        } catch (Exception e) {
            return new Result(false, null, "Sin red: " + e.getMessage());
        }
    }

    static Result transcribe(Context c, byte[] wav) {
        if (!Prefs.ready(c)) return new Result(false, null, "Abre Fiel y pega la clave.");
        if (wav == null || wav.length < 100) return new Result(false, null, "Audio demasiado corto.");
        String boundary = "----Fiel" + System.currentTimeMillis();
        try {
            HttpURLConnection conn = open(Prefs.transcribeUrl(c), Prefs.token(c), "POST", 90000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setDoOutput(true);
            byte[] payload = multipart(boundary, wav, Prefs.lang(c));
            conn.setFixedLengthStreamingMode(payload.length);
            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            int code = conn.getResponseCode();
            String body = read(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            conn.disconnect();
            if (code >= 200 && code < 300) {
                JSONObject o = new JSONObject(body);
                if (o.optBoolean("ok", false)) {
                    String text = o.optString("text", "").trim();
                    if (text.isEmpty()) return new Result(false, null, "El audio no devolvió texto.");
                    return new Result(true, text, null);
                }
                return new Result(false, null, o.optString("error", "Error al transcribir."));
            }
            return new Result(false, null, message(body, code));
        } catch (Exception e) {
            return new Result(false, null, "No se pudo enviar el audio.");
        }
    }

    private static HttpURLConnection open(String url, String token, String method, int timeout)
            throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(timeout);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "FielTeclado/1.0");
        return conn;
    }

    private static byte[] multipart(String boundary, byte[] wav, String lang) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String dash = "--" + boundary + "\r\n";
        out.write(dash.getBytes(StandardCharsets.UTF_8));
        out.write("Content-Disposition: form-data; name=\"language\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(lang.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(dash.getBytes(StandardCharsets.UTF_8));
        out.write("Content-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n".getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: audio/wav\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(wav);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private static String message(String body, int code) {
        try {
            JSONObject o = new JSONObject(body);
            String err = o.optString("error", "");
            if (!err.isEmpty()) return err;
        } catch (Exception ignored) {}
        if (code == 401) return "Clave no válida. Genera otra en Fiel.";
        return "El servidor respondió " + code + ".";
    }
}
