package com.mispeech.hyperos.asr;

/**
 * ASR 错误码 → 语义 token 映射。
 *
 * <p>token 表复原自输入法 {@code r8.f#m(int, String)}。
 * 服务端通过 {@code IAsrListener} 事务 5 的 {@code Bundle{code,message}} 回传错误码。
 */
public final class SpeechAsrError {

    private SpeechAsrError() {
    }

    public static final int UNKNOWN = -1;
    public static final int NETWORK = 1;
    public static final int AUDIO = 2;
    public static final int TIMEOUT = 4;
    public static final int NO_SPEECH = 5;
    public static final int SERVER = 6;
    public static final int AUTH = 7;
    public static final int INVALID_STATE = 8;
    public static final int INVALID_REQUEST = 100;
    public static final int RECORDER_BUSY = 101;
    public static final int SERVICE_UNAVAILABLE = 102;
    public static final int CTA_NOT_ACCEPTED = 103;
    public static final int UNSUPPORTED_AUDIO_CONFIG = 104;
    public static final int AUDIO_PERMISSION = 105;
    public static final int CONTENT_MODERATION = 30002;

    /** 错误码 → token，未知码返回 {@code ERR_<code>}。 */
    public static String tokenOf(int code) {
        switch (code) {
            case UNKNOWN:
                return "UNKNOWN";
            case CONTENT_MODERATION:
                return "CONTENT_MODERATION";
            case NETWORK:
                return "NETWORK";
            case AUDIO:
                return "AUDIO";
            case TIMEOUT:
                return "TIMEOUT";
            case NO_SPEECH:
                return "NO_SPEECH";
            case SERVER:
                return "SERVER";
            case AUTH:
                return "AUTH";
            case INVALID_STATE:
                return "INVALID_STATE";
            case INVALID_REQUEST:
                return "INVALID_REQUEST";
            case RECORDER_BUSY:
                return "RECORDER_BUSY";
            case SERVICE_UNAVAILABLE:
                return "SERVICE_UNAVAILABLE";
            case CTA_NOT_ACCEPTED:
                return "CTA_NOT_ACCEPTED";
            case UNSUPPORTED_AUDIO_CONFIG:
                return "UNSUPPORTED_AUDIO_CONFIG";
            case AUDIO_PERMISSION:
                return "AUDIO_PERMISSION";
            default:
                return "ERR_" + code;
        }
    }

    /** 直接给出中文说明，便于展示给用户。 */
    public static String describe(int code) {
        switch (code) {
            case NETWORK:
                return "网络异常";
            case AUDIO:
                return "音频采集异常";
            case TIMEOUT:
                return "识别超时";
            case NO_SPEECH:
                return "没有检测到语音";
            case SERVER:
                return "服务端错误";
            case AUTH:
                return "鉴权失败";
            case INVALID_STATE:
                return "状态不合法";
            case INVALID_REQUEST:
                return "请求参数不合法";
            case RECORDER_BUSY:
                return "录音被占用（其他应用正在录音）";
            case SERVICE_UNAVAILABLE:
                return "语音服务不可用（绑定失败或服务未就绪）";
            case CTA_NOT_ACCEPTED:
                return "用户尚未同意语音助手隐私协议";
            case UNSUPPORTED_AUDIO_CONFIG:
                return "不支持的音频参数";
            case AUDIO_PERMISSION:
                return "缺少录音权限（由语音助手侧判定）";
            case CONTENT_MODERATION:
                return "内容被审核拦截";
            default:
                return "未知错误(" + code + ")";
        }
    }
}
