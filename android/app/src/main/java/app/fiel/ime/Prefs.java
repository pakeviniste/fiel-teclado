package app.fiel.ime;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONObject;

public final class Prefs {
    private static final String FILE = "fiel_ime";
    private static final String API = "api";
    private static final String TOKEN = "token";
    private static final String LANG = "lang";

    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String api(Context c) {
        return sp(c).getString(API, "").trim();
    }

    public static String token(Context c) {
        return sp(c).getString(TOKEN, "").trim();
    }

    public static String lang(Context c) {
        String v = sp(c).getString(LANG, "es");
        return v == null || v.isEmpty() ? "es" : v;
    }

    public static boolean ready(Context c) {
        return !api(c).isEmpty() && !token(c).isEmpty();
    }

    public static void save(Context c, String api, String token, String lang) {
        SharedPreferences.Editor e = sp(c).edit();
        if (api != null && !api.trim().isEmpty()) {
            e.putString(API, stripSlash(api.trim()));
        }
        if (token != null && !token.trim().isEmpty()) {
            e.putString(TOKEN, token.trim());
        }
        if (lang != null && !lang.trim().isEmpty()) {
            e.putString(LANG, normalizeLang(lang.trim()));
        }
        e.apply();
    }

    public static String applyPaste(Context c, String raw) {
        if (raw == null) return "Vacío.";
        String s = raw.trim();
        if (s.isEmpty()) return "Vacío.";

        if (s.startsWith("{")) {
            try {
                JSONObject o = new JSONObject(s);
                String api = o.optString("api", o.optString("url", ""));
                String token = o.optString("token", o.optString("key", ""));
                String lang = o.optString("language", o.optString("lang", "es"));
                if (token.isEmpty()) return "Ese texto no trae clave.";
                save(c, api, token, lang);
                return "Clave guardada.";
            } catch (Exception e) {
                return "No pude leer lo pegado.";
            }
        }

        if (s.startsWith("fiel://") || s.startsWith("https://") && s.contains("token=")) {
            try {
                Uri u = Uri.parse(s);
                String token = u.getQueryParameter("token");
                String api = u.getQueryParameter("api");
                String lang = u.getQueryParameter("lang");
                if (token == null || token.isEmpty()) token = u.getQueryParameter("t");
                if (token == null || token.isEmpty()) return "Ese enlace no trae token.";
                save(c, api, token, lang);
                return "Clave guardada.";
            } catch (Exception e) {
                return "Enlace ilegible.";
            }
        }

        if (s.startsWith("fiel_") || s.length() > 20) {
            save(c, null, s, null);
            return api(c).isEmpty()
                    ? "Token guardado. Falta la dirección de Fiel (pégala junta, en el JSON)."
                    : "Token guardado.";
        }
        return "No reconozco esa clave. Copia el recuadro entero desde Fiel.";
    }

    static String transcribeUrl(Context c) {
        return api(c) + "/api/keyboard-transcribe";
    }

    static String stripSlash(String api) {
        while (api.endsWith("/")) api = api.substring(0, api.length() - 1);
        return api;
    }

    static String normalizeLang(String lang) {
        String l = lang.toLowerCase();
        if (l.startsWith("ca")) return "ca";
        if (l.startsWith("en")) return "en";
        return "es";
    }
}
