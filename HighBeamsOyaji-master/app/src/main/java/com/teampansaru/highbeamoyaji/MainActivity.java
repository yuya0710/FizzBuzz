package com.teampansaru.highbeamoyaji;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.teampansaru.highbeamoyaji.BuildConfig;
import com.teampansaru.highbeamoyaji.R;

public class MainActivity extends AppCompatActivity {
    /**
     * 今車のハイビームがONになっているかどうかのフラグ
     */
    private boolean isHighBeam = false;
    private VideoView videoView;
    private ImageView imageView;
    public static final String ISTURNEDONHIGHBEAM = "isTurnedOnHighBeam";
    public static final String LOG = "ろぐ";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        registerReceiver(broadcastReceiver, new IntentFilter("HIGH_BEAM"));
        //If we are connected to a module we want to start our SdlService
        if (BuildConfig.TRANSPORT.equals("MULTI") || BuildConfig.TRANSPORT.equals("MULTI_HB")) {
            SdlReceiver.queryForConnectedService(this);
        } else if (BuildConfig.TRANSPORT.equals("TCP")) {
            Intent proxyIntent = new Intent(this, SdlService.class);
            startService(proxyIntent);
        }

        videoView = findViewById(R.id.videoView);
        imageView = findViewById(R.id.image_view);

        Uri videoPath = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.hibi_mu);
        videoView.setVideoPath(String.valueOf(videoPath));
        videoView.requestFocus();
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if (isHighBeam) {
                    hideImage();
                    videoView.seekTo(0);
                    videoView.start();
                } else {
                    showImage();
                }
            }
        });
    }

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(LOG, "onReceive()");
            final VideoView videoView = findViewById(R.id.videoView);
            Bundle extras = intent.getExtras();
            boolean isTurnedOnHighBeam = extras.getBoolean(ISTURNEDONHIGHBEAM);
            Handler uiThreadHandler = new Handler(Looper.getMainLooper());
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (isHighBeam) {
                        // ハイビームがONになった瞬間、動画をスタート
                        Log.d(LOG, "動画すたーと");
                        videoView.start();
                        hideImage();
                    } else {
                        // ハイビームがOFFになった瞬間、動画をストップ
                        Log.d(LOG, "動画ストップ");
                        videoView.seekTo(0);
                        showImage();
                    }
                }
            });

            // 今ONになったかOFFになったかをメンバ変数に保持
            isHighBeam = isTurnedOnHighBeam;
        }
    };

    private void showImage() {
        imageView.setVisibility(View.VISIBLE);
        videoView.setVisibility(View.GONE);
    }

    private void hideImage() {
        imageView.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }
}