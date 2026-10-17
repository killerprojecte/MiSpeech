package com.mispeech.hyperos.asr;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.mispeech.hyperos.asr.internal.SpeechAsrContract;

/**
 * 备用通道：直接拉起「小爱语音输入」浮窗。
 *
 * <p>这条路径完全不走 Binder、也不需要任何“标识”权限，它只是给语音助手发一个
 * {@code android.intent.action.ASSIST} 的 Service Intent，由语音助手自己弹出语音输入浮窗完成
 * 识别并把文字送进当前焦点输入框。
 *
 * <p>复原自输入法 {@code u9.d0#i(Context)}（日志 tag {@code VoiceAssistIntent}），
 * 输入法把它作为“AskXiaoAi”的兜底方案。
 *
 * <p><b>与 {@link SpeechAsrManager} 的区别</b>：
 * <ul>
 *   <li>{@code SpeechAsrManager}：进程内拿到识别文本，可自由上屏/处理，但需要 Binder 权限标识</li>
 *   <li>本类：拿不到文本（由语音助手直接输入），但几乎无门槛，适合做兜底</li>
 * </ul>
 */
public final class VoiceAssistLauncher {

    private static final String TAG = "MiSpeech";

    /** {@code android.intent.action.ASSIST}。 */
    public static final String ACTION_ASSIST = "android.intent.action.ASSIST";

    /** 功能键：拉起键盘（语音输入）。 */
    public static final String EXTRA_FUNCTION_KEY = "voice_assist_function_key";

    /** 来源键：标识请求来自输入法场景。 */
    public static final String EXTRA_START_FROM_KEY = "voice_assist_start_from_key";

    /** 功能值：拉起语音输入键盘。 */
    public static final String FUNCTION_PULL_UP_KEYBOARD = "pull_up_keyboard";

    /** 来源值：input_method。 */
    public static final String START_FROM_INPUT_METHOD = "input_method";

    private VoiceAssistLauncher() {
    }

    /**
     * 拉起小爱语音输入浮窗。
     *
     * @return 是否成功把 Intent 交给系统（true 表示语音助手已受理）
     */
    public static boolean launchFloatingInput(Context context) {
        try {
            Intent intent = new Intent(ACTION_ASSIST);
            intent.setPackage(SpeechAsrContract.VOICE_ASSIST_PACKAGE);
            intent.putExtra(EXTRA_FUNCTION_KEY, FUNCTION_PULL_UP_KEYBOARD);
            intent.putExtra(EXTRA_START_FROM_KEY, START_FROM_INPUT_METHOD);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            boolean accepted = context.startService(intent) != null;
            Log.i(TAG, "launchFloatingInput accepted=" + accepted);
            return accepted;
        } catch (Throwable t) {
            Log.w(TAG, "拉起小爱语音输入浮窗失败", t);
            return false;
        }
    }
}
