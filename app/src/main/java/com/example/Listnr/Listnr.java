// Copyright 2019 Alpha Cephei Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.example.Listnr;

import static android.media.AudioManager.STREAM_MUSIC;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class Listnr extends Activity implements
        RecognitionListener {

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private Model model;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private TextView resultView;
    private AudioManager audioManager;
    private Button btn;
    private int btnFlag = 0;
    private boolean flag2 = true;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);

        resultView = findViewById(R.id.resultView);
        btn = findViewById(R.id.btn_start);
        btn.setOnClickListener(view -> onClick());

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        LibVosk.setLogLevel(LogLevel.INFO);

        // Check if user has given permission to record audio, init the model after
        // permission is granted
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.RECORD_AUDIO },
                    PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            initModel();
        }
    }

    private void initModel() {
        StorageService.unpack(this, "model-en-us", "model",
                (model) -> {
                    this.model = model;
                },
                (exception) -> Log.w("MyActivity", "Failed to unpack the model" + exception.getMessage()));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                initModel();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }

        if (speechStreamService != null) {
            speechStreamService.stop();
        }
    }

    @Override
    public void onResult(String hypothesis) {
        try {
            JSONObject obj = new JSONObject(hypothesis);
            hypothesis = obj.getString("text");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        resultView.append(hypothesis + "\n");
        if (hypothesis.matches(".*stop.*|.*pause.*")) {
            resultView.append("Pause detected\n");
            if (audioManager.isMusicActive()) {
                resultView.append("Music is active");
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }
        } else if (hypothesis.matches(".*less.*|.*decrease.*")) {
            audioManager.setStreamVolume(STREAM_MUSIC, audioManager.getStreamVolume(STREAM_MUSIC) - 5,
                    AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        }
        if ((hypothesis.matches(".*play.*|.*start.*"))) {
            audioManager.abandonAudioFocus(null);
        }
        if ((hypothesis.matches(".*increase*|.*more.*"))) {
            audioManager.setStreamVolume(STREAM_MUSIC, audioManager.getStreamVolume(STREAM_MUSIC) + 5,
                    AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        }

    }

    @Override
    public void onFinalResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
    }

    @Override
    public void onPartialResult(String hypothesis) {
        // resultView.append(hypothesis + "\n");
    }

    @Override
    public void onError(Exception e) {
        Log.e("MyActivity", e.getMessage());
    }

    @Override
    public void onTimeout() {
        Log.w("MyActivity", "onTimeOut");
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            speechService.stop();
            speechService = null;
        } else {
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    private void pause(boolean checked) {
        if (speechService != null) {
            speechService.setPause(checked);
        }
    }

    private void setErrorState(String message) {
        resultView.setText(message);
    }

    private void onClick() {
        if (btnFlag == 0) {
            recognizeMicrophone();
            btnFlag = 1;
            btn.setText("Stop");
        } else if (btnFlag == 1) {
            if (flag2) {
                pause(flag2);
                flag2 = !flag2;
                btn.setText("Resume");
            } else {
                pause(flag2);
                flag2 = !flag2;
                btn.setText("Stop");
            }
        }
    }

}
