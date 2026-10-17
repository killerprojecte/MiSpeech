package com.mispeech.hyperos.asr.internal;

/**
 * HyperOS 语音助手语音识别（ASR）接口契约常量。
 *
 * <p>全部取自「超级小爱输入法」{@code com.xiaomi.type}（versionName 0.2.1053.06f65b7c）
 * 的反编译结果，对应原始类（混淆名）如下：
 * <ul>
 *   <li>{@code r8.h}  —— 目标服务的 ComponentName</li>
 *   <li>{@code cc.h}  —— bindService 的 Intent / action 与 flags</li>
 *   <li>{@code cc.f} / {@code ac.a} —— ISpeechService 的两个事务</li>
 *   <li>{@code bc.a}  —— IAsrClient 的事务</li>
 *   <li>{@code r8.d}  —— IAsrListener 的 onTransact（回调分派）</li>
 *   <li>{@code r8.f}  —— 发起识别时写入的参数 Bundle 键名与取值</li>
 * </ul>
 *
 * <p>该接口是“服务端录音”模型：客户端只负责 start / stop / cancel，
 * 不接受也不发送任何音频数据，识别结果通过 IAsrListener 回传。
 */
public final class SpeechAsrContract {

    private SpeechAsrContract() {
    }

    // ---------------------------------------------------------------- 目标服务

    /** 语音助手包名。 */
    public static final String VOICE_ASSIST_PACKAGE = "com.miui.voiceassist";

    /** 语音助手导出的 OSbot 语音服务（Binder 入口）。 */
    public static final String OSBOT_SPEECH_SERVICE =
            "com.aios.osbot.external.OSbotSpeechService";

    /** 绑定该服务使用的 Intent action。 */
    public static final String ACTION_SPEECH = "com.xiaomi.speech.action.SPEECH";

    /** {@code Context.BIND_AUTO_CREATE} = 0x00000001。 */
    public static final int BIND_FLAG_AUTO_CREATE = 0x00000001;

    /** {@code Context.BIND_INCLUDE_CAPABILITIES} = 0x00001000。 */
    public static final int BIND_FLAG_INCLUDE_CAPABILITIES = 0x00001000;

    /**
     * bindService 实际使用的 flags。
     * 输入法原值 4097 == BIND_AUTO_CREATE | BIND_INCLUDE_CAPABILITIES。
     */
    public static final int BIND_FLAGS = BIND_FLAG_AUTO_CREATE | BIND_FLAG_INCLUDE_CAPABILITIES;

    // ---------------------------------------------------------------- 三个 Binder 描述符

    /** 语音服务接口：从服务拿 ASR 客户端与能力表。 */
    public static final String DESC_SPEECH_SERVICE =
            "com.xiaomi.speech.framework.common.ISpeechService";

    /** ASR 客户端接口：控制一次识别会话。 */
    public static final String DESC_ASR_CLIENT =
            "com.xiaomi.speech.framework.common.asr.IAsrClient";

    /** ASR 回调接口：服务端反向调用本进程。 */
    public static final String DESC_ASR_LISTENER =
            "com.xiaomi.speech.framework.common.asr.IAsrListener";

    // ---------------------------------------------------------------- ISpeechService 事务码

    /** {@code IAsrClient newAsrClient()}；无入参，返回 IAsrClient 的 Binder。 */
    public static final int TX_SPEECH_NEW_ASR_CLIENT = 1;

    /** {@code Bundle getCapabilities()}；无入参，返回能力表（含 supported_asr_engines）。 */
    public static final int TX_SPEECH_GET_CAPABILITIES = 3;

    // ---------------------------------------------------------------- IAsrClient 事务码

    /** {@code void start(IAsrListener listener, in Bundle params)}。 */
    public static final int TX_ASR_START = 1;

    /** {@code void stop()}。 */
    public static final int TX_ASR_STOP = 2;

    /** {@code void cancel()}。 */
    public static final int TX_ASR_CANCEL = 3;

    // ---------------------------------------------------------------- IAsrListener 事务码

    /** onReady()：会话就绪，可以开始说话。 */
    public static final int TX_LISTENER_ON_READY = 6;

    /** onPartialResult(Bundle{text})：中间结果（实时上屏用）。 */
    public static final int TX_LISTENER_ON_PARTIAL_RESULT = 1;

    /** onSentenceResult(Bundle{text,index})：分句结果。 */
    public static final int TX_LISTENER_ON_SENTENCE_RESULT = 2;

    /** onFinalResult(Bundle{text,extra})：最终结果。 */
    public static final int TX_LISTENER_ON_FINAL_RESULT = 3;

    /** onAudioLevel(float)：音量（0f~1f），用于波形动画。 */
    public static final int TX_LISTENER_ON_AUDIO_LEVEL = 4;

    /** onError(Bundle{code,message})：错误。 */
    public static final int TX_LISTENER_ON_ERROR = 5;

    /** Android 规定的「取接口描述符」事务码：0x5f4e5446。 */
    public static final int TX_INTERFACE_DESCRIPTOR = 1598968902;

    /** Binder 事务码上限（enforceInterface 的范围判断用）。 */
    public static final int TX_MAX = 16777215;

    // ---------------------------------------------------------------- 回调 / 能力表 Bundle 键

    /** 识别文本。 */
    public static final String KEY_TEXT = "text";
    /** 分句下标。 */
    public static final String KEY_INDEX = "index";
    /** 附加信息，JSON 字符串（含 effectiveProvider）。 */
    public static final String KEY_EXTRA = "extra";
    /** 错误码键（原常量 {@code com.xiaomi.onetrack.g.a.d} == "code"）。 */
    public static final String KEY_CODE = "code";
    /** 错误信息。 */
    public static final String KEY_MESSAGE = "message";
    /** 最终结果里 JSON 字段：实际生效的引擎。 */
    public static final String KEY_EFFECTIVE_PROVIDER = "effectiveProvider";
    /** 能力表键：服务端支持的 ASR 引擎列表（String[]）。 */
    public static final String KEY_SUPPORTED_ASR_ENGINES = "supported_asr_engines";

    // ---------------------------------------------------------------- 启动参数 Bundle 键

    public static final String PARAM_ENGINE_PROVIDER = "engineProvider";
    public static final String PARAM_LANGUAGE = "language";
    public static final String PARAM_ENABLE_CLOUD_VAD = "enableCloudVad";
    public static final String PARAM_ENABLE_AUDIO_LEVEL = "enableAudioLevel";
    public static final String PARAM_BUSINESS_TAG = "businessTag";
    public static final String PARAM_FEATURE_ID = "featureId";
    public static final String PARAM_CONTENT_MODERATION = "contentModeration";
    public static final String PARAM_FROM_APP = "fromApp";
    public static final String PARAM_HOTWORDS_JSON = "hotwordsJson";

    // ---------------------------------------------------------------- 默认值

    /** 输入法使用的来源标识。 */
    public static final String DEFAULT_BUSINESS_TAG = "mi_ime";
    /** 输入法使用的功能标识。 */
    public static final String DEFAULT_FEATURE_ID = "voice_asr";
    /** 输入法固定使用中文普通话。 */
    public static final String DEFAULT_LANGUAGE = "zh-CN";

    /** 火山引擎（默认）。 */
    public static final String ENGINE_VOLC = "volc";
    /** 阿里云引擎（需要服务端能力表支持）。 */
    public static final String ENGINE_ALIYUN = "aliyun";

    /** 热词权重：输入法注入热词时固定使用 4。 */
    public static final int DEFAULT_HOTWORD_WEIGHT = 4;

    // ---------------------------------------------------------------- 权限（“标识”）

    public static final String PERMISSION_BIND_SPEECH_SERVICE =
            "com.xiaomi.speech.permission.BIND_SPEECH_SERVICE";
    public static final String PERMISSION_EXTERNAL_AGENT =
            "com.aios.osbot.permission.EXTERNAL_AGENT";
    public static final String PERMISSION_QUERY_ENABLE =
            "com.miui.voiceassist.QUERY_ENABLE";
}
