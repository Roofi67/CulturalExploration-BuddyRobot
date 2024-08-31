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
        buttonBrowse = findViewById(R.id.button_browse);
        Button buttonHello = findViewById(R.id.buttonHello);
        Button buttonListen = findViewById(R.id.button_listen);
        Button buttonAdvance = findViewById(R.id.button_advance);
        listViewFiles = findViewById(R.id.listView_files);
        recognizedText = findViewById(R.id.recognizedText);
        imageView = findViewById(R.id.imageView);
        buttonBack = findViewById(R.id.button_back);
        sttState = findViewById(R.id.sttState);

        buttonHello.setOnClickListener(view -> {
            BuddySDK.Speech.startSpeaking("Hello, I am Buddy");
        });
        buttonListen.setOnClickListener(v -> {
            mainButtonsContainer.setVisibility(View.GONE);
            sayText("Hi, What game are you playing guys?","it-IT-IsabellaMultilingualNeural");
            startContinuousRecognition();
            awaitingGameResponse = true; // Set flag to true when listening starts
        });
        buttonAdvance.setOnClickListener(view -> {
            mainButtonsContainer.setVisibility(View.GONE);
            recognizedText.setText("Advancing...");
            recognizedText.setVisibility(View.VISIBLE);
            showBackButton();
            AdvanceFunct();
        });
        buttonBack.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            }
            showMainButtons();
        });
    }

    private void configureListeners() {
        buttonBrowse.setOnClickListener(v -> {
            mainButtonsContainer.setVisibility(View.GONE);
            listViewFiles.setVisibility(View.VISIBLE);
            showBackButton();
            checkPermissionsAndLoadFiles();
        });
        listViewFiles.setOnItemClickListener(this::onVideoSelected);
    }

    private void checkPermissionsAndLoadFiles() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);
        } else {
            loadVideoFiles();
        }
    }

    private void loadVideoFiles() {
        File directory = new File(folderPath);
        ArrayList<String> videoNames = new ArrayList<>();
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            for (File file : files) {
                if (file.getName().endsWith(".mp4")) {
                    videoNames.add(file.getName());
                }
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, videoNames);
            listViewFiles.setAdapter(adapter);
        } else {
            Toast.makeText(this, "No videos found.", Toast.LENGTH_LONG).show();
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

    private void AdvanceFunct() {
        float initialSpeed = 0.3F;
        float initialDistance = 1.2F;
        float angle = -110;
        float rotspeed = 90;
        float secondSpeed = 0.3F;
        float secondDistance = 0.8F;

        long moveDuration = calculateMoveDuration(initialSpeed, initialDistance);
        long rotationDuration = calculateRotationDuration(angle, rotspeed);

        moveBuddy(initialSpeed, initialDistance, () -> {
            handler.postDelayed(() -> rotateBuddy(rotspeed, angle, () -> {
                handler.postDelayed(() -> moveBuddy(secondSpeed, secondDistance, null), rotationDuration);
            }), moveDuration);
        });
    }

    private long calculateMoveDuration(float speed, float distance) {
        return (long) (distance / speed * 1000);
    }

    private long calculateRotationDuration(float angle, float rotspeed) {
        return (long) (Math.abs(angle) / rotspeed * 1000);
    }

    private void moveBuddy(float speed, float distance, Runnable onSuccess) {
        Log.i(TAG, "Sending moveBuddy command: speed=" + speed + ", distance=" + distance);
        BuddySDK.USB.moveBuddy(speed, distance, new IUsbCommadRsp.Stub() {
            @Override
            public void onSuccess(String s) throws RemoteException {
                Log.i(TAG, "moveBuddy success: " + s);
                runOnUiThread(() -> recognizedText.setText("Move successful"));
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }

            @Override
            public void onFailed(String s) throws RemoteException {
                Log.i(TAG, "moveBuddy failed: " + s);
                runOnUiThread(() -> recognizedText.setText("Fail to advance"));
            }
        });
    }

    private void rotateBuddy(float rotspeed, float angle, Runnable onSuccess) {
        Log.i(TAG, "Sending rotateBuddy command: rotspeed=" + rotspeed + ", angle=" + angle);
        BuddySDK.USB.rotateBuddy(rotspeed, angle, new IUsbCommadRsp.Stub() {
            @Override
            public void onSuccess(String s) throws RemoteException {
                Log.i(TAG, "rotateBuddy success: " + s);
                runOnUiThread(() -> recognizedText.setText("Rotate successful"));
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }

            @Override
            public void onFailed(String s) throws RemoteException {
                Log.i(TAG, "rotateBuddy failed: " + s);
                runOnUiThread(() -> recognizedText.setText("Fail to rotate"));
            }
        });
    }

    private void initializeRecognizer() {
        try {
            SpeechConfig config = SpeechConfig.fromSubscription(SUBSCRIPTION_KEY, SERVICE_REGION);
            List<String> languages = Arrays.asList("en-US", "it-IT", "fr-FR");
            AutoDetectSourceLanguageConfig autoDetectSourceLanguageConfig = AutoDetectSourceLanguageConfig.fromLanguages(languages);

            recognizer = new SpeechRecognizer(config, autoDetectSourceLanguageConfig);

            recognizer.recognized.addEventListener((s, e) -> {
                if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                    Log.i(TAG, "Recognized: " + e.getResult().getText());

                    // Update currentLanguage based on detected language
                    AutoDetectSourceLanguageResult languageResult = AutoDetectSourceLanguageResult.fromResult(e.getResult());
                    currentLanguage = languageResult.getLanguage();
                    Log.i(TAG, "Detected language: " + currentLanguage);

                    runOnUiThread(() -> {
                        recognizedText.setText("Recognized: " + e.getResult().getText());
                        processRecognizedText(e.getResult().getText());
                    });
                } else if (e.getResult().getReason() == ResultReason.NoMatch) {
                    Log.i(TAG, "No speech could be recognized.");
                    runOnUiThread(() -> recognizedText.setText("No speech could be recognized."));
                }
            });

            recognizer.canceled.addEventListener((s, e) -> {
                Log.i(TAG, "Canceled: Reason=" + e.getReason() + "\nErrorDetails: " + e.getErrorDetails());
                isListening = false;
                runOnUiThread(() -> recognizedText.setText("Recognition canceled."));
            });

            recognizer.sessionStarted.addEventListener((s, e) -> Log.i(TAG, "Session started."));
            recognizer.sessionStopped.addEventListener((s, e) -> Log.i(TAG, "Session stopped."));
        } catch (Exception e) {
            Log.e(TAG, "Error initializing recognizer: " + e.getMessage(), e);
        }
    }

    private void processRecognizedText(String text) {
        Log.d(TAG, "Processing recognized text: " + text);
        Log.d(TAG, "Current language: " + currentLanguage);
        Log.d(TAG, "Awaiting game response: " + awaitingGameResponse);

        String speechText = text.toLowerCase();

        if (awaitingGameResponse) {
            Log.d(TAG, "Responding to game query");
            awaitingGameResponse = false; // Reset the flag
            if (speechText.contains("nothing") || speechText.contains("not playing")) {
                sayText("oh okay", "it-IT-IsabellaMultilingualNeural");

                }  else {
                sayText("Can I play with you", "it-IT-IsabellaMultilingualNeural");
                }
            }
        handleSpeechInteraction(speechText);

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
                recognizedText.setVisibility(View.VISIBLE); // Ensure the recognized text view is visible
            });
        } catch (Exception e) {
            Log.e(TAG, "Error starting continuous recognition: " + e.getMessage());
        }
    }

    private void updateImageViewBasedonSpeech(String speechText) {
        int drawableId = -1;
        boolean isGif = false;

        if (speechText.contains("cat")) {
            //drawableId = R.drawable.cat_image;
        } else if (speechText.contains("dog")) {
            //drawableId = R.drawable.dog_image;
        } else if (speechText.contains("goodbye") || speechText.contains("arrivederci") || speechText.contains("au revoir")) {
            isGif = true;
        }

        if (!isGif && drawableId != -1) {
            Drawable image = ContextCompat.getDrawable(getApplicationContext(), drawableId);
            if (image != null) {
                runOnUiThread(() -> {
                    imageView.setImageDrawable(image);
                    imageView.setVisibility(View.VISIBLE);
                });
            } else {
                Log.e(TAG, "Drawable not found");
                runOnUiThread(() -> imageView.setVisibility(View.GONE));
            }
        } else if (isGif) {
            runOnUiThread(() -> {
                //Glide.with(this).asGif().load(R.drawable.hello_gif).into(imageView);
                imageView.setVisibility(View.VISIBLE);
            });
        } else {
            runOnUiThread(() -> imageView.setVisibility(View.GONE));
        }

        runOnUiThread(() -> {
            sttState.setVisibility(View.GONE);
            buttonBack.setVisibility(View.VISIBLE);
        });
    }

    private synchronized void handleSpeechInteraction(String speechText) {
        if (isProcessing) {
            return; // Skip processing if we're already processing a speech input
        }

        isProcessing = true; // Set the flag to indicate we're processing

        // Check if the speech text indicates an offer of food or drink
        switch (currentLanguage) {
            case "fr-FR":
                handleSpeechInteractionFR(speechText);
                break;
            case "it-IT":
                handleSpeechInteractionIT(speechText);
                break;
            default:
                handleSpeechInteractionEN(speechText);
                break;
        }

        isProcessing = false; // Reset the flag after processing is complete
    }

    private void handleSpeechInteractionEN(String speechText) {
        if (speechText.contains("do you want some") || speechText.contains("would you like")) {
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
            } else if (speechText.contains("nourriture") ) {
                handleFoodOfferFR(extractFoodType(speechText));
            }
        }
    }

    private void handleSpeechInteractionIT(String speechText) {
        if (speechText.contains("vuoi") || speechText.contains("vorresti")) {
            if (speechText.contains("bevanda")) {
                handleDrinkOfferIT(extractDrinkType(speechText));
            } else if (speechText.contains("cibo") || speechText.contains("mangiare")) {
                handleFoodOfferIT(extractFoodType(speechText));
            }
        }
    }

    private void handleDrinkOfferEN(String drinkType) {
        sayText("Thank you! I would love some " + drinkType + ".");
        delayedResponse(() -> playSound(R.raw.drinking_sound, () -> {
            showDrinkOnScreen(drinkType);
            suggestCulturalDrinksEN();
        }));
    }

    private void handleFoodOfferEN(String foodType) {
        sayText("Thank you! I would love some " + foodType + ".");
        delayedResponse(() -> playSound(R.raw.eating_sound, this::talkAboutCulturalFoodsEN));
    }

    private void handleDrinkOfferFR(String drinkType) {
        sayText("Merci! J'aimerais beaucoup " + drinkType + ".","it-IT-IsabellaMultilingualNeural");
        delayedResponse(() -> playSound(R.raw.drinking_sound, () -> {
            showDrinkOnScreen(drinkType);
            suggestCulturalDrinksFR();
        }));
    }

    private void handleFoodOfferFR(String foodType) {
        sayText("Merci! J'aimerais beaucoup " + foodType + ".", "it-IT-IsabellaMultilingualNeural");
        delayedResponse(() -> playSound(R.raw.eating_sound, this::talkAboutCulturalFoodsFR));
    }

    private void handleDrinkOfferIT(String drinkType) {
        sayText("Grazie! Mi piacerebbe molto " + drinkType + ".");
        delayedResponse(() -> playSound(R.raw.drinking_sound, () -> {
            showDrinkOnScreen(drinkType);
            suggestCulturalDrinksIT();
        }));
    }

    private void handleFoodOfferIT(String foodType) {
        sayText("Grazie! Mi piacerebbe molto " + foodType + ".");
        delayedResponse(() -> playSound(R.raw.eating_sound, this::talkAboutCulturalFoodsIT));
    }

    private void delayedResponse(Runnable action) {
        handler.postDelayed(action, 3000);
    }

    private String extractDrinkType(String speechText) {
        // Implement logic to extract drink type
        return "tea"; // Simplified placeholder
    }

    private String extractFoodType(String speechText) {
        // Implement logic to extract food type
        return "pizza"; // Simplified placeholder
    }

    private void suggestCulturalDrinksEN() {
        String suggestion = "Did you know? In many cultures, tea is a popular drink. Here's what tea looks like!";
        sayText(suggestion, "en-US-JennyNeural");
        showDrinkOnScreen("tea");
    }

    private void talkAboutCulturalFoodsEN() {
        String foodTalk = "On my planet, we enjoy Glorp, a glowing beverage that bubbles. It's quite refreshing!";
        sayText(foodTalk, "en-US-JennyNeural");
    }

    private void suggestCulturalDrinksFR() {
        String suggestion = "Saviez-vous? Dans de nombreuses cultures, le thé est une boisson populaire. Voici à quoi ressemble le thé!";
        sayText(suggestion, "it-IT-IsabellaMultilingualNeural");
        showDrinkOnScreen("tea");
    }

    private void talkAboutCulturalFoodsFR() {
        String foodTalk = "Sur ma planète, nous apprécions le Glorp, une boisson lumineuse qui pétille. C'est très rafraîchissant!";
        sayText(foodTalk, "it-IT-IsabellaMultilingualNeural");
    }

    private void suggestCulturalDrinksIT() {
        String suggestion = "Lo sapevi? In molte culture, il tè è una bevanda popolare. Ecco com'è il tè!";
        sayText(suggestion, "it-IT-IsabellaNeural");
        showDrinkOnScreen("tea");
    }

    private void talkAboutCulturalFoodsIT() {
        String foodTalk = "Sul mio pianeta, godiamo del Glorp, una bevanda luminosa che fa le bolle. È molto rinfrescante!";
        sayText(foodTalk, "it-IT-IsabellaNeural");
    }

    private void sayText(String text) {
        String voiceName = getVoiceForCurrentLanguage();  // This method will determine the voice based on currentLanguage
        sayText(text, voiceName);
    }
    private void playSound(int resourceId, Runnable onCompletion) {
        MediaPlayer mediaPlayer = MediaPlayer.create(this, resourceId);
        if (mediaPlayer != null) {
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                handler.post(onCompletion);
            });
            mediaPlayer.start();
        } else {
            Log.e(TAG, "Failed to create MediaPlayer");
            handler.post(onCompletion);
        }
    }
    private void showDrinkOnScreen(String drinkType) {
        int drawableId = getDrawableForDrink(drinkType);
        runOnUiThread(() -> {
            Drawable drinkImage = ContextCompat.getDrawable(this, drawableId);
            if (drinkImage != null) {
                imageView.setImageDrawable(drinkImage);
                imageView.setVisibility(View.VISIBLE);
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
                return R.drawable.default_drink_image; // Ensure this drawable exists or handle the case
        }
    }

    private String getVoiceForCurrentLanguage() {
        switch (currentLanguage) {
            case "fr-FR":
                return "fr-FR-JulieNeural"; // Sample voice for French
            case "it-IT":
                return "it-IT-IsabellaNeural"; // Sample voice for Italian
            default:
                return "en-US-JennyNeural"; // Default voice for English
        }
    }

    private void sayText(String text, String voiceName) {
        handler.post(() -> {
            try {
                SpeechConfig config = SpeechConfig.fromSubscription(SUBSCRIPTION_KEY, SERVICE_REGION);
                config.setSpeechSynthesisVoiceName(voiceName);
                SpeechSynthesizer synthesizer = new SpeechSynthesizer(config);

                new Thread(() -> {
                    try {
                        Thread.sleep(500); // Sleep for 0.5 seconds
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
            } finally {
                isProcessing = false; // Reset the flag after processing is complete
            }
        });
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
                initializeRecognizer();
            } else {
                Log.i("info", "Permissions not granted!");
                Toast.makeText(this, "Permissions are required for this app to function", Toast.LENGTH_SHORT).show();
            }
        }
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
