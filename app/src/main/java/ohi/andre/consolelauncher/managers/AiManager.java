package ohi.andre.consolelauncher.managers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.noties.markwon.Markwon;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Ai;
import ohi.andre.consolelauncher.tuils.Tuils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AiManager {

    private final Context context;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Markwon markwon;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AiCallback {
        void onResponse(CharSequence response);
        void onError(String error);
    }

    public AiManager(Context context) {
        this.context = context;
        this.markwon = Markwon.create(context);
    }

    public void generateResponse(String prompt, final AiCallback callback) {
        if (!XMLPrefsManager.getBoolean(Ai.ai_enabled)) {
            callback.onError("AI is disabled in config.");
            return;
        }

        String mode = XMLPrefsManager.get(Ai.ai_mode).toLowerCase();
        if (mode.equals("local")) {
            generateLocalResponse(prompt, callback);
        } else {
            generateGlobalResponse(prompt, callback);
        }
    }

    private void generateGlobalResponse(String prompt, final AiCallback callback) {
        String apiKey = XMLPrefsManager.get(Ai.google_api_key);
        String modelName = XMLPrefsManager.get(Ai.google_model);
        String systemPrompt = XMLPrefsManager.get(Ai.system_prompt);

        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("Google API Key is missing. Set it in ai.xml or with 'ai -key <key>'");
            return;
        }

        try {
            GenerativeModel gm = new GenerativeModel(modelName, apiKey);
            GenerativeModelFutures model = GenerativeModelFutures.from(gm);

            Content content = new Content.Builder()
                    .addText(systemPrompt + "\n\nUser: " + prompt)
                    .build();

            ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
            Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    String text = result.getText();
                    if (text != null) {
                        final CharSequence rendered = markwon.toMarkdown(text);
                        mainHandler.post(() -> callback.onResponse(rendered));
                    } else {
                        mainHandler.post(() -> callback.onError("Empty response from AI."));
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    mainHandler.post(() -> callback.onError(t.getMessage()));
                }
            }, executor);

        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    private void generateLocalResponse(String prompt, final AiCallback callback) {
        String url = XMLPrefsManager.get(Ai.local_url);
        String modelName = XMLPrefsManager.get(Ai.local_model);
        String systemPrompt = XMLPrefsManager.get(Ai.system_prompt);

        try {
            JSONObject json = new JSONObject();
            json.put("model", modelName);
            
            JSONArray messages = new JSONArray();
            
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.put(sysMsg);
            
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.put(userMsg);
            
            json.put("messages", messages);
            json.put("stream", false);

            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("Local AI Error: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onError("Local AI Server returned error: " + response.code()));
                        return;
                    }

                    try {
                        String responseData = response.body().string();
                        JSONObject resJson = new JSONObject(responseData);
                        JSONArray choices = resJson.getJSONArray("choices");
                        if (choices.length() > 0) {
                            String text = choices.getJSONObject(0).getJSONObject("message").getString("content");
                            final CharSequence rendered = markwon.toMarkdown(text);
                            mainHandler.post(() -> callback.onResponse(rendered));
                        } else {
                            mainHandler.post(() -> callback.onError("No choices returned from Local AI."));
                        }
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onError("Failed to parse Local AI response: " + e.getMessage()));
                    }
                }
            });

        } catch (Exception e) {
            callback.onError("Local AI Setup Error: " + e.getMessage());
        }
    }

    public void testApi(final AiCallback callback) {
        generateResponse("Hello, are you active?", new AiCallback() {
            @Override
            public void onResponse(CharSequence response) {
                callback.onResponse("AI is active and responding: " + response);
            }

            @Override
            public void onError(String error) {
                callback.onError("AI test failed: " + error);
            }
        });
    }
}
