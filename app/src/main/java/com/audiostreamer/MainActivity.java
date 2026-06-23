package com.audiostreamer;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MainActivity extends Activity {
    private Thread streamThread;
    private volatile boolean isPlaying = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText urlInput = findViewById(R.id.url_input);
        Button playBtn = findViewById(R.id.play_btn);
        TextView status = findViewById(R.id.status);

        playBtn.setOnClickListener(v -> {
            if (isPlaying) {
                stopStream();
                playBtn.setText("▶  Play");
                status.setText("Stopped");
            } else {
                String url = urlInput.getText().toString().trim();
                if (url.isEmpty()) {
                    status.setText("Enter a URL first");
                    return;
                }
                status.setText("Connecting...");
                playBtn.setEnabled(false);
                startStream(url, playBtn, status);
            }
        });
    }

    private void startStream(String url, Button playBtn, TextView status) {
        isPlaying = true;
        streamThread = new Thread(() -> {
            AudioTrack audioTrack = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.connect();

                InputStream in = conn.getInputStream();

                // Read 44-byte WAV header
                byte[] header = new byte[44];
                int headerRead = 0;
                while (headerRead < 44) {
                    int r = in.read(header, headerRead, 44 - headerRead);
                    if (r < 0) throw new Exception("Stream ended during header");
                    headerRead += r;
                }

                // Parse format from WAV header
                int channels    = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                int sampleRate  = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                int bitsPerSample = ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

                int channelConfig = (channels == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
                int audioFormat   = (bitsPerSample == 16) ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;

                int minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);

                audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                    .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build())
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();

                audioTrack.play();

                final String info = sampleRate / 1000 + "kHz " + channels + "ch";
                handler.post(() -> {
                    playBtn.setText("⏹  Stop");
                    playBtn.setEnabled(true);
                    status.setText("Streaming • " + info);
                });

                byte[] buf = new byte[minBuf];
                while (isPlaying) {
                    int n = in.read(buf, 0, buf.length);
                    if (n < 0) break;
                    audioTrack.write(buf, 0, n);
                }

            } catch (Exception e) {
                if (isPlaying) {
                    handler.post(() -> {
                        playBtn.setText("▶  Play");
                        playBtn.setEnabled(true);
                        status.setText("Error: " + e.getMessage());
                    });
                }
            } finally {
                if (audioTrack != null) { audioTrack.stop(); audioTrack.release(); }
                if (conn != null) conn.disconnect();
                isPlaying = false;
            }
        });
        streamThread.start();
    }

    private void stopStream() {
        isPlaying = false;
        if (streamThread != null) streamThread.interrupt();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStream();
    }
}
