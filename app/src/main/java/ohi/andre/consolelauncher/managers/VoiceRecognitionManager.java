package ohi.andre.consolelauncher.managers;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.HapticFeedbackConstants;
import android.view.View;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

import ohi.andre.consolelauncher.tuils.Tuils;

public class VoiceRecognitionManager {

    public interface VoiceCallback {
        void onSpeechStart();
        void onPartialResult(String partialText);
        void onFinalResult(String recognizedCommand);
        void onError(String errorMessage);
    }

    private static VoiceRecognitionManager instance;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    public static synchronized VoiceRecognitionManager getInstance() {
        if (instance == null) {
            instance = new VoiceRecognitionManager();
        }
        return instance;
    }

    public boolean isListening() {
        return isListening;
    }

    public void startListening(final Context context, final View feedbackView, final VoiceCallback callback) {
        if (context == null) return;

        // Check if device supports speech recognition
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            if (callback != null) callback.onError("Speech recognition is not available on this device.");
            return;
        }

        // Check RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (context instanceof Activity) {
                ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.RECORD_AUDIO}, 1042);
            }
            if (callback != null) callback.onError("Microphone permission required for voice-to-text. Please grant permission and try again.");
            return;
        }

        stopListening();

        try {
            // Haptic touch confirmation on activation
            if (feedbackView != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    feedbackView.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                } else {
                    feedbackView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.getApplicationContext());
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a T-UI command...");

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening = true;
                    if (callback != null) callback.onSpeechStart();
                }

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    isListening = false;
                }

                @Override
                public void onError(int error) {
                    isListening = false;
                    String message;
                    switch (error) {
                        case SpeechRecognizer.ERROR_AUDIO:
                            message = "Audio recording error";
                            break;
                        case SpeechRecognizer.ERROR_CLIENT:
                            message = "Voice client error";
                            break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            message = "Insufficient microphone permissions";
                            break;
                        case SpeechRecognizer.ERROR_NETWORK:
                        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                            message = "Network error during speech recognition";
                            break;
                        case SpeechRecognizer.ERROR_NO_MATCH:
                            message = "No speech detected or recognized";
                            break;
                        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                            message = "Speech recognizer is busy";
                            break;
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            message = "No speech detected (timeout)";
                            break;
                        default:
                            message = "Recognition error code: " + error;
                            break;
                    }
                    if (callback != null) callback.onError(message);
                    cleanup();
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    ArrayList<String> matches = results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                    if (matches != null && !matches.isEmpty()) {
                        String recognized = matches.get(0).trim();
                        if (feedbackView != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                feedbackView.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                            } else {
                                feedbackView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                            }
                        }
                        if (callback != null) callback.onFinalResult(recognized);
                    } else {
                        if (callback != null) callback.onError("No speech recognized.");
                    }
                    cleanup();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults != null ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                    if (matches != null && !matches.isEmpty()) {
                        String partial = matches.get(0).trim();
                        if (callback != null) callback.onPartialResult(partial);
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });

            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            isListening = false;
            Tuils.log(e);
            if (callback != null) callback.onError("Failed to start speech recognizer: " + e.getLocalizedMessage());
            cleanup();
        }
    }

    public void stopListening() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
            } catch (Exception ignored) {}
            cleanup();
        }
        isListening = false;
    }

    private void cleanup() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        isListening = false;
    }
}
