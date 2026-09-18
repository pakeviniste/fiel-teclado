package app.fiel.ime;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;

/**
 * System IME. Appears in the keyboard switcher (the globe / emoji slot).
 * Hold the mic → we record WAV → POST to Fiel → commitText into whatever
 * app has the cursor (WhatsApp, Notes, Gmail, …).
 */
public class FielImeService extends InputMethodService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final WavRecorder recorder = new WavRecorder();
    private View root;
    private TextView status;
    private TextView langLabel;
    private View mic;
    private View wave;
    private boolean busy;
    private long downAt;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!recorder.isRunning()) return;
            long s = (SystemClock.uptimeMillis() - downAt) / 1000;
            setStatus("Escuchando  " + s + "s");
            if (s >= 55) {
                finishHold();
                return;
            }
            main.postDelayed(this, 400);
        }
    };

    @Override
    public View onCreateInputView() {
        root = getLayoutInflater().inflate(R.layout.input_view, null);
        status = root.findViewById(R.id.status);
        langLabel = root.findViewById(R.id.lang);
        mic = root.findViewById(R.id.mic);
        wave = root.findViewById(R.id.wave);
        bind(R.id.globe, v -> switchToNextInputMethod(false));
        bind(R.id.gear, v -> {
            EditorInfo info = getCurrentInputEditorInfo();
            android.content.Intent i = new android.content.Intent(this, SetupActivity.class);
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });
        bind(R.id.key_comma, v -> type(","));
        bind(R.id.key_dot, v -> type("."));
        bind(R.id.key_q, v -> type("?"));
        bind(R.id.key_e, v -> type("!"));
        bind(R.id.key_nl, v -> sendDownUp(KeyEvent.KEYCODE_ENTER));
        bind(R.id.key_space, v -> type(" "));
        View del = root.findViewById(R.id.key_del);
        del.setOnClickListener(v -> deleteOne());
        del.setOnLongClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.deleteSurroundingText(40, 0);
            return true;
        });
        mic.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    startHold();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    finishHold();
                    return true;
                default:
                    return true;
            }
        });
        refreshIdle();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (!busy) refreshIdle();
        if (langLabel != null) {
            String lang = Prefs.lang(this);
            langLabel.setText(lang.equals("ca") ? "CA" : lang.equals("en") ? "EN" : "ES");
        }
    }

    private void bind(int id, View.OnClickListener l) {
        View v = root.findViewById(id);
        if (v != null) v.setOnClickListener(l);
    }

    private void startHold() {
        if (busy) return;
        if (!Prefs.ready(this)) {
            setStatus("Abre Fiel · Ajustes y pega la clave");
            return;
        }
        if (!recorder.start()) {
            setStatus("Sin micrófono. Dáselo en Ajustes.");
            return;
        }
        downAt = SystemClock.uptimeMillis();
        buzz(20);
        if (wave != null) wave.setVisibility(View.VISIBLE);
        mic.setSelected(true);
        setStatus("Escuchando…");
        main.removeCallbacks(tick);
        main.post(tick);
    }

    private void finishHold() {
        main.removeCallbacks(tick);
        if (!recorder.isRunning()) return;
        byte[] wav = recorder.stopWav();
        mic.setSelected(false);
        if (wave != null) wave.setVisibility(View.GONE);
        if (wav.length == 0) {
            setStatus("Mantén un poco más");
            main.postDelayed(this::refreshIdle, 1200);
            return;
        }
        busy = true;
        setStatus("Pasando a texto…");
        new Thread(() -> {
            Transcriber.Result r = Transcriber.transcribe(this, wav);
            main.post(() -> {
                busy = false;
                if (r.ok) {
                    commitSpoken(r.text);
                    setStatus("Listo");
                    buzz(12);
                } else {
                    setStatus(r.error);
                }
                main.postDelayed(this::refreshIdle, 1600);
            });
        }, "fiel-stt").start();
    }

    private void commitSpoken(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String t = text.trim();
        if (t.isEmpty()) return;
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        boolean needSpace = before != null && before.length() > 0
                && !Character.isWhitespace(before.charAt(0))
                && !isPunct(t.charAt(0));
        ic.beginBatchEdit();
        ic.commitText(needSpace ? " " + t : t, 1);
        ic.endBatchEdit();
    }

    private static boolean isPunct(char c) {
        return c == ',' || c == '.' || c == '?' || c == '!' || c == '…';
    }

    private void type(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
    }

    private void deleteOne() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) {
            ic.commitText("", 1);
            return;
        }
        ic.deleteSurroundingText(1, 0);
    }

    private void sendDownUp(int code) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long t = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }

    private void refreshIdle() {
        if (busy) return;
        if (!Prefs.ready(this)) {
            setStatus("Pega la clave de Fiel en Ajustes");
        } else {
            setStatus("Mantén pulsado y habla");
        }
    }

    private void setStatus(String s) {
        if (status != null) status.setText(s);
    }

    private void buzz(int ms) {
        try {
            Vibrator v = getSystemService(Vibrator.class);
            if (v != null) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {}
    }
}
