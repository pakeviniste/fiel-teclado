package app.fiel.ime;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 16 kHz / 16-bit / mono PCM wrapped as WAV. The STT backend reads this
 * better than AMR or 3GP, and we keep the whole utterance — not a live caption.
 */
final class WavRecorder {
    static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord record;
    private Thread worker;
    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private volatile boolean running;

    synchronized boolean start() {
        if (running) return true;
        pcm.reset();
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, ENCODING);
        if (min <= 0) min = SAMPLE_RATE;
        try {
            record = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE, CHANNELS, ENCODING, Math.max(min, SAMPLE_RATE * 2));
        } catch (SecurityException e) {
            return false;
        }
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            record.release();
            record = null;
            return false;
        }
        running = true;
        record.startRecording();
        worker = new Thread(this::pump, "fiel-rec");
        worker.start();
        return true;
    }

    private void pump() {
        byte[] buf = new byte[4096];
        while (running) {
            AudioRecord r = record;
            if (r == null) break;
            int n = r.read(buf, 0, buf.length);
            if (n > 0) {
                synchronized (pcm) {
                    pcm.write(buf, 0, n);
                }
            }
        }
    }

    synchronized byte[] stopWav() {
        running = false;
        AudioRecord r = record;
        record = null;
        if (r != null) {
            try { r.stop(); } catch (Exception ignored) {}
            r.release();
        }
        Thread t = worker;
        worker = null;
        if (t != null) {
            try { t.join(400); } catch (InterruptedException ignored) {}
        }
        byte[] data;
        synchronized (pcm) {
            data = pcm.toByteArray();
            pcm.reset();
        }
        if (data.length < SAMPLE_RATE) return new byte[0]; // < ~0.5s of 16-bit
        try {
            return wrapWav(data);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    boolean isRunning() {
        return running;
    }

    private static byte[] wrapWav(byte[] pcm) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
        int byteRate = SAMPLE_RATE * 2;
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes());
        h.putInt(36 + pcm.length);
        h.put("WAVE".getBytes());
        h.put("fmt ".getBytes());
        h.putInt(16);
        h.putShort((short) 1);
        h.putShort((short) 1);
        h.putInt(SAMPLE_RATE);
        h.putInt(byteRate);
        h.putShort((short) 2);
        h.putShort((short) 16);
        h.put("data".getBytes());
        h.putInt(pcm.length);
        out.write(h.array());
        out.write(pcm);
        return out.toByteArray();
    }
}
