package com.samuschat.calltest;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

/** Independent UID produces real Android media, including an opt-out capture test. */
public class ToneActivity extends Activity {
    private AudioTrack tone;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int frame;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        boolean blocked = getIntent().getBooleanExtra("blocked", false);
        short[] pcm = new short[48000];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short)(2500 * Math.sin(2 * Math.PI * 440 * i / 48000));
        tone = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setAllowedCapturePolicy(blocked ? AudioAttributes.ALLOW_CAPTURE_BY_NONE : AudioAttributes.ALLOW_CAPTURE_BY_ALL).build())
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.length * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
        tone.write(pcm, 0, pcm.length); tone.setLoopPoints(0, pcm.length, -1); tone.play();
        TextView view = new TextView(this); view.setTextSize(28); view.setPadding(30, 80, 30, 30); setContentView(view);
        handler.post(new Runnable() {
            @Override public void run() {
                view.setText("440 Hz / capture " + (blocked ? "blocked" : "allowed") + "\nFrame " + frame++);
                view.setBackgroundColor(frame % 2 == 0 ? 0xff92b5ff : 0xff99ddbc);
                handler.postDelayed(this, 500);
            }
        });
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (tone != null) { tone.stop(); tone.release(); }
        super.onDestroy();
    }
}
