package com.mispeech.hyperos.asr;

import android.os.Bundle;

/**
 * 识别回调。所有方法都在主线程回调，未实现的方法默认什么都不做。
 *
 * <p>一次正常会话的顺序大致为：
 * {@link #onConnected} → {@link #onReady} → ({@link #onAudioLevel} / {@link #onPartialResult})*
 * → {@link #onSentenceResult}* → {@link #onFinalResult} → {@link #onFinished}。
 */
public interface SpeechAsrListener {

    /** 已绑定上语音助手服务，并拿到能力表（可能为 null）。 */
    default void onConnected(Bundle capabilities) {
    }

    /** 会话就绪，服务端已开始录音，可以提示用户说话。 */
    default void onReady() {
    }

    /** 中间结果（实时上屏）。文本可能不断被修正。 */
    default void onPartialResult(String text) {
    }

    /** 分句结果：一句话识别完成。 */
    default void onSentenceResult(String text, int index) {
    }

    /**
     * 最终结果。
     *
     * @param text      识别文本
     * @param extraJson 附加信息 JSON（含 {@code effectiveProvider} 等），可能为 null
     */
    default void onFinalResult(String text, String extraJson) {
    }

    /** 音量 0f~1f，用于波形动画（需要 enableAudioLevel=true）。 */
    default void onAudioLevel(float level) {
    }

    /**
     * 错误。
     *
     * @param code    错误码，见 {@link SpeechAsrError}
     * @param message 服务端原文
     * @param token   语义 token，见 {@link SpeechAsrError#tokenOf(int)}
     */
    default void onError(int code, String message, String token) {
    }

    /** 本次会话结束（已收到最终结果、被取消，或出错收尾）。 */
    default void onFinished() {
    }

    /** 与语音助手服务的连接断开。{@code reason} 为断开原因（日志用）。 */
    default void onDisconnected(String reason) {
    }
}
