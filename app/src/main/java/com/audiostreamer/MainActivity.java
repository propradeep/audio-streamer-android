package com.audiostreamer;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends Activity {
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText urlInput = findViewById(R.id.url_input);
        Button playBtn   = findViewById(R.id.play_btn);
        TextView status  = findViewById(R.id.status);

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
        new Thread(() -> {
            try {
                MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(url);
                mp.setOnPreparedListener(p -> {
                    p.start();
                    isPlaying = true;
                    mediaPlayer = p;
                    handler.post(() -> {
                        playBtn.setText("⏹  Stop");
                        playBtn.setEnabled(true);
                        status.setText("Streaming from PC");
                    });
                });
                mp.setOnErrorListener((p, what, extra) -> {
                    handler.post(() -> {
                        playBtn.setText("▶  Play");
                        playBtn.setEnabled(true);
                        status.setText("Error — check PC IP and streamer");
                        isPlaying = false;
                    });
                    return true;
                });
                mp.prepareAsync();
            } catch (Exception e) {
                handler.post(() -> {
                    playBtn.setText("▶  Play");
                    playBtn.setEnabled(true);
                    status.setText("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void stopStream() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        isPlaying = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStream();
    }
}
