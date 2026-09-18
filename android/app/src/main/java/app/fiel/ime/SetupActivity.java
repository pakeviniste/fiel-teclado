package app.fiel.ime;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class SetupActivity extends Activity {
    private EditText field;
    private TextView state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        field = findViewById(R.id.field);
        state = findViewById(R.id.state);

        findViewById(R.id.btn_paste).setOnClickListener(v -> paste());
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_mic).setOnClickListener(v -> askMic());
        findViewById(R.id.btn_enable).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        findViewById(R.id.btn_pick).setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        findViewById(R.id.btn_test).setOnClickListener(v -> test());

        applyIntent(getIntent());
        refresh();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        applyIntent(intent);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void applyIntent(Intent intent) {
        if (intent == null) return;
        Uri u = intent.getData();
        if (u != null) {
            String msg = Prefs.applyPaste(this, u.toString());
            toast(msg);
        }
    }

    private void paste() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) {
            toast("El portapapeles está vacío.");
            return;
        }
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence t = clip.getItemAt(0).coerceToText(this);
        field.setText(t);
        field.setSelection(field.getText().length());
        String msg = Prefs.applyPaste(this, t.toString());
        toast(msg);
        refresh();
    }

    private void save() {
        String msg = Prefs.applyPaste(this, field.getText().toString());
        toast(msg);
        refresh();
    }

    private void askMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            toast("Micrófono ya concedido.");
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
    }

    private void test() {
        state.setText("Comprobando…");
        new Thread(() -> {
            Transcriber.Result r = Transcriber.ping(this);
            runOnUiThread(() -> {
                toast(r.ok ? "Conexión buena. Ya puedes activar el teclado." : r.error);
                refresh();
            });
        }).start();
    }

    private void refresh() {
        boolean key = Prefs.ready(this);
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        String api = Prefs.api(this);
        StringBuilder b = new StringBuilder();
        b.append(key ? "Clave: guardada" : "Clave: falta").append("\n");
        b.append(mic ? "Micrófono: sí" : "Micrófono: no").append("\n");
        if (!api.isEmpty()) b.append("Servidor: ").append(api);
        state.setText(b.toString());
        findViewById(R.id.step_key).setAlpha(key ? 1f : 0.55f);
        findViewById(R.id.step_mic).setAlpha(mic ? 1f : 0.55f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
