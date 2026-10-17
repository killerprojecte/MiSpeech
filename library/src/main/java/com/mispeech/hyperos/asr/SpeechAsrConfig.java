package com.mispeech.hyperos.asr;

import android.os.Bundle;

import com.mispeech.hyperos.asr.internal.SpeechAsrContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次识别会话的启动参数。
 *
 * <p>默认值与「超级小爱输入法」完全一致（{@code r8.f#b(...)} 中构造的 Bundle）：
 * <pre>
 *   engineProvider   = volc（可切 aliyun，但需服务端能力表支持）
 *   language         = zh-CN
 *   enableCloudVad   = false
 *   enableAudioLevel = true
 *   businessTag      = mi_ime
 *   featureId        = voice_asr
 *   contentModeration= false
 *   fromApp          = 宿主包名（可选）
 *   hotwordsJson     = {"items":[{"text":"...","weight":4}]}（可选）
 * </pre>
 */
public final class SpeechAsrConfig {

    /** 一个热词及其权重。 */
    public static final class Hotword {
        public final String text;
        public final int weight;

        public Hotword(String text, int weight) {
            this.text = text;
            this.weight = weight;
        }
    }

    private String engineProvider = SpeechAsrContract.ENGINE_VOLC;
    private String language = SpeechAsrContract.DEFAULT_LANGUAGE;
    private boolean enableCloudVad;
    private boolean enableAudioLevel = true;
    private String businessTag = SpeechAsrContract.DEFAULT_BUSINESS_TAG;
    private String featureId = SpeechAsrContract.DEFAULT_FEATURE_ID;
    private boolean contentModeration;
    private String fromApp;
    private final List<Hotword> hotwords = new ArrayList<>();

    /** 引擎；传 {@code null} 表示交给本库按服务端能力表自动选择。 */
    public SpeechAsrConfig setEngineProvider(String engineProvider) {
        this.engineProvider = engineProvider;
        return this;
    }

    public SpeechAsrConfig setLanguage(String language) {
        this.language = language;
        return this;
    }

    /** 云端 VAD：输入法默认关闭（false）。 */
    public SpeechAsrConfig setEnableCloudVad(boolean enableCloudVad) {
        this.enableCloudVad = enableCloudVad;
        return this;
    }

    /** 是否回传音量（用于波形动画），默认 true。 */
    public SpeechAsrConfig setEnableAudioLevel(boolean enableAudioLevel) {
        this.enableAudioLevel = enableAudioLevel;
        return this;
    }

    public SpeechAsrConfig setBusinessTag(String businessTag) {
        this.businessTag = businessTag;
        return this;
    }

    public SpeechAsrConfig setFeatureId(String featureId) {
        this.featureId = featureId;
        return this;
    }

    public SpeechAsrConfig setContentModeration(boolean contentModeration) {
        this.contentModeration = contentModeration;
        return this;
    }

    /** 来源 App，用于服务端统计；留空则不写入该字段。 */
    public SpeechAsrConfig setFromApp(String fromApp) {
        this.fromApp = fromApp;
        return this;
    }

    /** 追加一个热词（权重取输入法同款 4）。 */
    public SpeechAsrConfig addHotword(String text) {
        if (text != null && !text.trim().isEmpty()) {
            hotwords.add(new Hotword(text.trim(), SpeechAsrContract.DEFAULT_HOTWORD_WEIGHT));
        }
        return this;
    }

    public SpeechAsrConfig addHotword(String text, int weight) {
        if (text != null && !text.trim().isEmpty()) {
            hotwords.add(new Hotword(text.trim(), weight));
        }
        return this;
    }

    public List<Hotword> getHotwords() {
        return Collections.unmodifiableList(hotwords);
    }

    public String getEngineProvider() {
        return engineProvider;
    }

    /** 按线上协议构造参数 Bundle。 */
    Bundle toBundle(String resolvedEngineProvider) {
        Bundle bundle = new Bundle();
        bundle.putString(SpeechAsrContract.PARAM_ENGINE_PROVIDER, resolvedEngineProvider);
        bundle.putString(SpeechAsrContract.PARAM_LANGUAGE, language);
        bundle.putBoolean(SpeechAsrContract.PARAM_ENABLE_CLOUD_VAD, enableCloudVad);
        bundle.putBoolean(SpeechAsrContract.PARAM_ENABLE_AUDIO_LEVEL, enableAudioLevel);
        bundle.putString(SpeechAsrContract.PARAM_BUSINESS_TAG, businessTag);
        bundle.putString(SpeechAsrContract.PARAM_FEATURE_ID, featureId);
        bundle.putBoolean(SpeechAsrContract.PARAM_CONTENT_MODERATION, contentModeration);
        if (fromApp != null && !fromApp.trim().isEmpty()) {
            bundle.putString(SpeechAsrContract.PARAM_FROM_APP, fromApp.trim());
        }
        String hotwordsJson = buildHotwordsJson();
        if (hotwordsJson != null) {
            bundle.putString(SpeechAsrContract.PARAM_HOTWORDS_JSON, hotwordsJson);
        }
        return bundle;
    }

    private String buildHotwordsJson() {
        if (hotwords.isEmpty()) {
            return null;
        }
        try {
            JSONArray items = new JSONArray();
            for (Hotword hotword : hotwords) {
                items.put(new JSONObject()
                        .put(SpeechAsrContract.KEY_TEXT, hotword.text)
                        .put("weight", hotword.weight));
            }
            return new JSONObject().put("items", items).toString();
        } catch (JSONException e) {
            return null;
        }
    }
}
