package com.bfr.gamescenario;

import static android.os.SystemClock.sleep;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bfr.buddy.ui.shared.FacialExpression;
import com.bfr.buddy.ui.shared.LabialExpression;
import com.bfr.buddy.speech.shared.ISTTCallback;
import com.bfr.buddy.speech.shared.STTResult;
import com.bfr.buddy.speech.shared.STTResultsData;
import com.bfr.buddysdk.BuddyActivity;
import com.bfr.buddysdk.BuddySDK;
import com.bfr.buddysdk.services.speech.STTTask;
import com.bfr.buddy.usb.shared.IUsbCommadRsp;
import com.bumptech.glide.Glide;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;
import com.microsoft.cognitiveservices.speech.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;

public class MainActivity extends BuddyActivity {
    private static final String SUBSCRIPTION_KEY = "522c4a2067f34864aaa6a35388cc4e1c";
    private static final String SERVICE_REGION = "westeurope";
    private static final int PERMISSIONS_REQUEST_CODE = 1;
    private ImageView fullScreenImage;


    private Handler uiHandler = new Handler(Looper.getMainLooper());

    private static final String TAG = "MainActivity";
    private ListView listViewFiles;
    private Button buttonBrowse;
    private TextView recognizedText;
    private ImageView imageView;
    private LinearLayout mainButtonsContainer;
    private Button buttonBack;
    private TextView sttState;
    private Handler handler = new Handler(Looper.getMainLooper());
    private String folderPath = "/storage/emulated/0/Movies";

    private STTTask sttTask;
    private boolean isSpeechServiceReady = false;
    private boolean awaitingGameResponse = false;
    private boolean isListening = false; // Flag to check if listening is active
    private String currentLanguage = "en-US"; // Default language

    private SpeechRecognizer recognizer;
    private boolean isProcessing = false; // To prevent duplicate processing of the same speech input

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        configureListeners();
        checkPermissions();
    }

    private void initViews() {
        mainButtonsContainer = findViewById(R.id.mainButtonsContainer);
       // buttonBrowse = findViewById(R.id.button_browse);
        fullScreenImage = findViewById(R.id.welcomeImage);

        Button buttonListen = findViewById(R.id.button_listen);
        listViewFiles = findViewById(R.id.listView_files);
        recognizedText = findViewById(R.id.recognizedText);
        imageView = findViewById(R.id.imageView);
        buttonBack = findViewById(R.id.button_back);
        sttState = findViewById(R.id.sttState);

        buttonListen.setOnClickListener(v -> {
            mainButtonsContainer.setVisibility(View.GONE);
            fullScreenImage.setVisibility(View.GONE);
            startContinuousRecognition();
        });

        buttonBack.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            }
            showMainButtons();
            fullScreenImage.setVisibility(View.VISIBLE);

        });
    }

    private void configureListeners() {
        listViewFiles.setOnItemClickListener(this::onVideoSelected);
    }

    private void checkPermissionsAndLoadFiles() {
        Log.i(TAG, "checkPermissionsAndLoadFiles: Checking permissions for READ_EXTERNAL_STORAGE");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "checkPermissionsAndLoadFiles: Permission not granted. Requesting permission.");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);
        } else {
            Log.i(TAG, "checkPermissionsAndLoadFiles: Permission granted. Loading video files.");
            loadVideoFiles();
        }
    }

    private void loadVideoFiles() {
        Log.i(TAG, "loadVideoFiles: Loading video files from directory.");
        File directory = new File(folderPath);
        ArrayList<String> videoNames = new ArrayList<>();
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".mp4")) {
                        videoNames.add(file.getName());
                        Log.d(TAG, "Loaded video file: " + file.getName());
                    }
                }
            }
            runOnUiThread(() -> {
                if (!videoNames.isEmpty()) {
                    Log.i(TAG, "loadVideoFiles: Videos found. Updating ListView.");
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, videoNames);
                    listViewFiles.setAdapter(adapter);
                    listViewFiles.setVisibility(View.VISIBLE);
                    mainButtonsContainer.setVisibility(View.GONE);
                    showBackButton();
                } else {
                    Log.i(TAG, "loadVideoFiles: No videos found.");
                    Toast.makeText(this, "No videos found.", Toast.LENGTH_LONG).show();
                }
            });
        } else {
            runOnUiThread(() -> {
                Log.i(TAG, "loadVideoFiles: Directory not found.");
                Toast.makeText(this, "Directory not found.", Toast.LENGTH_LONG).show();
            });
        }
    }

    private void onVideoSelected(AdapterView<?> parent, View view, int position, long id) {
        String filePath = folderPath + "/" + parent.getItemAtPosition(position).toString();
        Log.d(TAG, "onVideoSelected: FilePath: " + filePath);
        playVideo(filePath);
    }

    private void playVideo(String filePath) {
        try {
            File videoFile = new File(filePath);
            Uri videoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", videoFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(videoUri, "video/mp4");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "playVideo: Exception: " + e.getMessage(), e);
            Toast.makeText(this, "Error playing video", Toast.LENGTH_SHORT).show();
        }
    }

    private void moveBuddy(float speed, float distance, Runnable onSuccess) {
        Log.i(TAG, "Sending moveBuddy command: speed=" + speed + ", distance=" + distance);

        runOnUiThread(() -> {
            recognizedText.setText("Moving...");
            recognizedText.setVisibility(View.VISIBLE);
        });

        BuddySDK.USB.moveBuddy(speed, distance, new IUsbCommadRsp.Stub() {
            @Override
            public void onSuccess(String s) throws RemoteException {
                Log.i(TAG, "moveBuddy success: " + s);
                runOnUiThread(() -> {
                    recognizedText.setText("Move successful");
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                });
            }

            @Override
            public void onFailed(String s) throws RemoteException {
                Log.i(TAG, "moveBuddy failed: " + s);
                runOnUiThread(() -> recognizedText.setText("Failed to move"));
            }
        });
    }

    private void rotateBuddy(float rotspeed, float angle, Runnable onSuccess) {
        Log.i(TAG, "Sending rotateBuddy command: rotspeed=" + rotspeed + ", angle=" + angle);

        runOnUiThread(() -> {
            recognizedText.setText("Rotating...");
            recognizedText.setVisibility(View.VISIBLE);
        });

        BuddySDK.USB.rotateBuddy(rotspeed, angle, new IUsbCommadRsp.Stub() {
            @Override
            public void onSuccess(String s) throws RemoteException {
                Log.i(TAG, "rotateBuddy success: " + s);
                runOnUiThread(() -> {
                    recognizedText.setText("Rotate successful");
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                });
            }

            @Override
            public void onFailed(String s) throws RemoteException {
                Log.i(TAG, "rotateBuddy failed: " + s);
                runOnUiThread(() -> recognizedText.setText("Failed to rotate"));
            }
        });
    }

    private void initializeRecognizer() {
        SpeechConfig config = SpeechConfig.fromSubscription(SUBSCRIPTION_KEY, SERVICE_REGION);
        List<String> languages = Arrays.asList("en-US", "fr-FR", "it-IT");
        AutoDetectSourceLanguageConfig autoDetectSourceLanguageConfig = AutoDetectSourceLanguageConfig.fromLanguages(languages);

        recognizer = new SpeechRecognizer(config, autoDetectSourceLanguageConfig);

        recognizer.recognized.addEventListener((s, e) -> {
            if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                Log.i(TAG, "Recognized: " + e.getResult().getText());

                AutoDetectSourceLanguageResult languageResult = AutoDetectSourceLanguageResult.fromResult(e.getResult());
                String detectedLanguage = languageResult.getLanguage();
                Log.i(TAG, "Detected language: " + detectedLanguage);

                // Update currentLanguage
                runOnUiThread(() -> {
                    currentLanguage = detectedLanguage;
                    recognizedText.setText("Recognized: " + e.getResult().getText());
                    handleSpeechInteraction(e.getResult().getText().toLowerCase());
                });
            } else if (e.getResult().getReason() == ResultReason.NoMatch) {
                Log.i(TAG, "No speech could be recognized.");
                runOnUiThread(() -> recognizedText.setText("No speech could be recognized."));
            }
        });

        recognizer.canceled.addEventListener((s, e) -> {
            Log.i(TAG, "Canceled: Reason=" + e.getReason() + "\nErrorDetails: " + e.getErrorDetails());
            runOnUiThread(() -> recognizedText.setText("Recognition canceled."));
        });

        recognizer.sessionStarted.addEventListener((s, e) -> Log.i(TAG, "Session started."));
        recognizer.sessionStopped.addEventListener((s, e) -> Log.i(TAG, "Session stopped."));
    }

    private void startContinuousRecognition() {
        try {
            if (recognizer == null) {
                initializeRecognizer();
            }
            recognizer.startContinuousRecognitionAsync();
            isListening = true;
            runOnUiThread(() -> {
                sttState.setText("Listening...");
                sttState.setVisibility(View.VISIBLE);
                buttonBack.setVisibility(View.VISIBLE);
                recognizedText.setVisibility(View.VISIBLE);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error starting continuous recognition: " + e.getMessage());
        }
    }

    private boolean waitingForGameResponse = false; // Flag to check if waiting for a game response

    private synchronized void handleSpeechInteraction(String speechText) {
        if (isProcessing) {
            return; // Skip processing if we're already processing a speech input
        }

        isProcessing = true; // Set the flag to indicate we're processing

        // Movement commands
        if (speechText.contains("move forward")) {
            Log.i(TAG, "Command to move forward recognized.");
            runOnUiThread(() -> moveBuddy(0.3F, 0.2F, null));
        } else if (speechText.contains("turn left")) {
            Log.i(TAG, "Command to rotate left recognized.");
            runOnUiThread(() -> rotateBuddy(90, 110, null));
        } else if (speechText.contains("turn right")) {
            Log.i(TAG, "Command to rotate right recognized.");
            runOnUiThread(() -> rotateBuddy(90, -110, null));
        } else if (speechText.contains("game room")) {
            askWhatAreTheyPlaying();
        } else if (waitingForGameResponse) {
            // Enhanced check for the context in which "playing" is used
            if (isActuallyPlaying(speechText)) {
                askToJoinPlay();
            } else {
                sayOk();
            }
            waitingForGameResponse = false; // Reset flag after handling the response
        } else {
            // Check for culture related phrases and respond accordingly
            switch (currentLanguage) {
                case "fr-FR":
                    if (speechText.contains("culture") || speechText.contains("cultura")) {
                        talkAboutCulturalFoodsFR();
                    } else {
                        handleSpeechInteractionFR(speechText);
                    }
                    break;
                case "it-IT":
                    if (speechText.contains("cultura")) {
                        talkAboutCulturalFoodsIT();
                    } else {
                        handleSpeechInteractionIT(speechText);
                    }
                    break;
                default: // Default to English
                    if (speechText.contains("culture")) {
                        talkAboutCulturalFoodsEN();
                    } else {
                        handleSpeechInteractionEN(speechText);
                    }
                    break;
            }
        }

        isProcessing = false; // Reset the flag after processing is complete
    }

    private boolean isActuallyPlaying(String speechText) {
        String lowerCaseText = speechText.toLowerCase();
        // Check if "not playing" or "no playing" appears
        if (lowerCaseText.contains("not playing") || lowerCaseText.contains("no playing")) {
            return false;
        }
        // More sophisticated checks can be added here
        return lowerCaseText.contains("playing");
    }

    private void askWhatAreTheyPlaying() {
        sayText("Hello! What are you playing?", currentVoiceSetting());
        setMoodTemporarily(FacialExpression.HAPPY);
        waitingForGameResponse = true; // Set the flag to true to wait for the response
    }

    private void askToJoinPlay() {
        sayText("Can I play with you?", currentVoiceSetting());
        setMoodTemporarily(FacialExpression.HAPPY);
    }

    private void sayOk() {
        sayText("Oh, ok.", currentVoiceSetting());
    }

    private String currentVoiceSetting() {
        switch (currentLanguage) {
            case "fr-FR":
                return "fr-FR-IsabellaMultilingualNeural";
            case "it-IT":
                return "it-IT-IsabellaMultilingualNeural";
            default:
                return "en-US-JennyNeural";
        }
    }

    private void handleSpeechInteractionEN(String speechText) {
        if (speechText.contains("want") || speechText.contains("would you like")) {
            if (speechText.contains("drink")) {
                handleDrinkOfferEN(extractDrinkType(speechText));
            } else if (speechText.contains("food")) {
                handleFoodOfferEN(extractFoodType(speechText));
            }
        }
    }

    private void handleSpeechInteractionFR(String speechText) {
        if (speechText.contains("veux") || speechText.contains("aimerais-tu")) {
            if (speechText.contains("boisson") || speechText.contains("verre")) {
                handleDrinkOfferFR(extractDrinkType(speechText));
            } else if (speechText.contains("nourriture")) {
                handleFoodOfferFR(extractFoodType(speechText));
            }
        }
    }

    private void handleSpeechInteractionIT(String speechText) {
        if (speechText.contains("vuoi") || speechText.contains("vorresti")) {
            if (speechText.contains("bevanda") || speechText.contains("bere")) {
                handleDrinkOfferIT(extractDrinkType(speechText));
            } else if (speechText.contains("cibo") || speechText.contains("mangiare")) {
                handleFoodOfferIT(extractFoodType(speechText));
            }
        }
    }

    private void handleDrinkOfferEN(String drinkType) {
        sayText("Thank you! I would love some " + drinkType + ".","en-US-JennyNeural");
        setMoodTemporarily(FacialExpression.HAPPY);
        delayedResponse(() -> playSound(R.raw.drinking_sound, () -> {
            showDrinkOnScreen(drinkType);
            suggestCulturalDrinksEN();
        }));
    }

    private void handleFoodOfferEN(String foodType) {
        sayText("Thank you! I would love some " + foodType + ".","en-US-JennyNeural");
        setMoodTemporarily(FacialExpression.HAPPY);
        delayedResponse(() -> playSound(R.raw.eating_sound, this::talkAboutCulturalFoodsEN));
    }

    private void handleDrinkOfferFR(String drinkType) {
        sayText("Merci! J'aimerais beaucoup " + drinkType + ".", "it-IT-IsabellaMultilingualNeural");
        setMoodTemporarily(FacialExpression.HAPPY);
        delayedResponse(() -> playSound(R.raw.drinking_sound, () -> {
            showDrinkOnScreen(drinkType);
            suggestCulturalDrinksFR();
        }));
    }

    private void handleFoodOfferFR(String foodType) {
        sayText("Merci! J'aimerais beaucoup " + foodType + ".", "it-IT-IsabellaMultilingualNeural");
        setMoodTemporarily(FacialExpression.HAPPY);
        delayedResponse(() -> playSound(R.raw.eating_sound, this::talkAboutCulturalFoodsFR));
    }

    private void handleDrinkOfferIT(String drinkType) {
        sayText("Grazie! Mi piacerebbe molto " + drinkType + ".", "it-IT-IsabellaMultilingualNeural");
        setMoodTemporarily(FacialExpression.HAPPY);
        delayedResponse(() -> playSound(R.raw.drinking_sound, () -> {
            showDrinkOnScreen(drinkType);
            suggestCulturalDrinksIT();
        }));
    }

    private void handleFoodOfferIT(String foodType) {
        sayText("Grazie! Mi piacerebbe molto " + foodType + ".", "it-IT-IsabellaMultilingualNeural");
        setMoodTemporarily(FacialExpression.HAPPY);
        delayedResponse(() -> playSound(R.raw.eating_sound, this::talkAboutCulturalFoodsIT));
    }

    private void delayedResponse(Runnable action) {
        handler.postDelayed(action, 3000);
    }

    private String extractDrinkType(String speechText) {
        return "tea"; // Simplified placeholder
    }

    private String extractFoodType(String speechText) {
        return "pizza"; // Simplified placeholder
    }

    private void suggestCulturalDrinksEN() {
        String suggestion = "Did you know? In many cultures, tea is a popular drink. Here's what tea looks like! You should try to prepare it some time.";
        sayText(suggestion, "en-US-JennyNeural");
        setMoodTemporarily(FacialExpression.HAPPY);
        showDrinkOnScreen("tea");
    }

    private void talkAboutCulturalFoodsEN() {
        String foodTalk = "On my planet, we enjoy Glorp, a glowing beverage that bubbles. It's quite refreshing!";
        sayText(foodTalk, "en-US-JennyNeural");
        setMoodTemporarily(FacialExpression.HAPPY);
    }

    private void suggestCulturalDrinksFR() {
        String suggestion = "Saviez-vous? Dans de nombreuses cultures, le thé est une boisson populaire. Voici à quoi ressemble le thé! Tu devrais essayer de le préparer un de ces jours.";
        sayText(suggestion, "it-IT-IsabellaMultilingualNeural");
        setMoodTemporarily(FacialExpression.HAPPY);
        showDrinkOnScreen("tea");
    }

    private void talkAboutCulturalFoodsFR() {
        String foodTalk = "Sur ma planète, nous apprécions le Glorp, une boisson lumineuse qui pétille. C'est très rafraîchissant!";
        sayText(foodTalk, "it-IT-IsabellaMultilingualNeural");
        setMoodTemporarily(FacialExpression.HAPPY);
    }

    private void suggestCulturalDrinksIT() {
        String suggestion = "Lo sapevi? In molte culture, il tè è una bevanda popolare. Ecco com'è il tè! Dovresti provare a prepararlo qualche volta.";
        sayText(suggestion, "it-IT-IsabellaMultilingualNeural");
        showDrinkOnScreen("tea");
    }

    private void talkAboutCulturalFoodsIT() {
        String foodTalk = "Sul mio pianeta, godiamo del Glorp, una bevanda luminosa che fa le bolle. È molto rinfrescante!";
        sayText(foodTalk, "it-IT-IsabellaMultilingualNeural");
    }

    private void sayText(String text, String voiceName) {
        handler.post(() -> {
            try {
                SpeechConfig config = SpeechConfig.fromSubscription(SUBSCRIPTION_KEY, SERVICE_REGION);
                config.setSpeechSynthesisVoiceName(voiceName);
                SpeechSynthesizer synthesizer = new SpeechSynthesizer(config);

                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                        BuddySDK.UI.setLabialExpression(LabialExpression.SPEAK_HAPPY);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();

                SpeechSynthesisResult result = synthesizer.SpeakText(text);
                BuddySDK.UI.setLabialExpression(LabialExpression.NO_EXPRESSION);

                result.close();
                synthesizer.close();
            } catch (Exception e) {
                Log.i("info", "Error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void playSound(int resourceId, Runnable onCompletion) {
        new Thread(() -> {
            MediaPlayer mediaPlayer = MediaPlayer.create(MainActivity.this, resourceId);
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                    handler.post(onCompletion);
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    mp.release();
                    Log.e(TAG, "MediaPlayer error on playback: " + what + " , " + extra);
                    handler.post(onCompletion);
                    return true;
                });
                mediaPlayer.start();
            } else {
                Log.e(TAG, "Failed to create MediaPlayer");
                handler.post(onCompletion);
            }
        }).start();
    }


    private Runnable hideImageRunnable = new Runnable() {
        @Override
        public void run() {
            imageView.setVisibility(View.GONE);
        }
    };

    private void showDrinkOnScreen(String drinkType) {
        int drawableId = getDrawableForDrink(drinkType);
        runOnUiThread(() -> {
            Drawable drinkImage = ContextCompat.getDrawable(this, drawableId);
            if (drinkImage != null) {
                imageView.setImageDrawable(drinkImage);
                imageView.setVisibility(View.VISIBLE);

                // Cancel any pending hide actions and post a new one
                uiHandler.removeCallbacks(hideImageRunnable);
                uiHandler.postDelayed(hideImageRunnable, 5000);
            } else {
                Log.e(TAG, "Image resource not found for " + drinkType);
                imageView.setVisibility(View.GONE);
            }
        });
    }

    private int getDrawableForDrink(String drinkType) {
        switch (drinkType.toLowerCase()) {
            case "tea":
                return R.drawable.tea_image;
            case "coffee":
                return R.drawable.coffee_image;
            default:
                return R.drawable.default_drink_image;
        }
    }

    private void stopListening() {
        if (recognizer != null) {
            recognizer.stopContinuousRecognitionAsync();
            isListening = false;
            runOnUiThread(() -> {
                sttState.setText("Stopped listening.");
                sttState.setVisibility(View.GONE);
                showMainButtons();
            });
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_CODE);
            } else {
                initializeRecognizer();
            }
        } else {
            initializeRecognizer();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                Log.i(TAG, "onRequestPermissionsResult: All permissions granted. Initializing recognizer and loading videos.");
                initializeRecognizer();
                loadVideoFiles();
            } else {
                Log.i(TAG, "onRequestPermissionsResult: Permissions not granted.");
                Toast.makeText(this, "Permissions are required for this app to function", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setMoodTemporarily(FacialExpression expression) {
        BuddySDK.UI.setMood(expression);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            BuddySDK.UI.setMood(FacialExpression.NEUTRAL); // Change NEUTRAL to your default mood if different
        }, 3000);
    }

    private void showMainButtons() {
        mainButtonsContainer.setVisibility(View.VISIBLE);
        listViewFiles.setVisibility(View.GONE);
        recognizedText.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        buttonBack.setVisibility(View.GONE);
        sttState.setVisibility(View.GONE);
    }

    private void showBackButton() {
        buttonBack.setVisibility(View.VISIBLE);
    }
}
