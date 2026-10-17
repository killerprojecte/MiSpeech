package com.mispeech.hyperos.asr.internal;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * {@code com.xiaomi.speech.framework.common.asr.IAsrListener} 的客户端实现。
 *
 * <p>这是一个跨进程回调：语音助手进程会调用本对象的事务方法把识别结果送回来，
 * 因此 {@link #onTransact} 运行在 Binder 线程（不是主线程），上层需自行切线程。
 *
 * <p>事务表（复原自 {@code r8.d#onTransact}）：
 * <pre>
 *   1  Bundle{text}         -> onPartialResult  中间结果
 *   2  Bundle{text,index}   -> onSentenceResult 分句结果
 *   3  Bundle{text,extra}   -> onFinalResult    最终结果
 *   4  float                -> onAudioLevel     音量 0f~1f
 *   5  Bundle{code,message} -> onError          错误
 *   6  (无参)               -> onReady          会话就绪
 * </pre>
 */
public final class AsrListenerBinder extends Binder implements IInterface {

    /** 回调分派接口，由上层（{@code SpeechAsrManager}）实现并负责切回主线程。 */
    public interface Callback {
        void onReady(int session);

        void onPartialResult(int session, String text);

        void onSentenceResult(int session, String text, int index);

        void onFinalResult(int session, String text, String extraJson);

        void onAudioLevel(int session, float level);

        void onError(int session, int code, String message);
    }

    private final int session;
    private final Callback callback;

    public AsrListenerBinder(int session, Callback callback) {
        this.session = session;
        this.callback = callback;
        attachInterface(this, SpeechAsrContract.DESC_ASR_LISTENER);
    }

    /** 本次回调所属的会话号。 */
    public int getSession() {
        return session;
    }

    @Override
    public IBinder asBinder() {
        return this;
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code >= 1 && code <= SpeechAsrContract.TX_MAX) {
            data.enforceInterface(SpeechAsrContract.DESC_ASR_LISTENER);
        }
        if (code == SpeechAsrContract.TX_INTERFACE_DESCRIPTOR) {
            reply.writeString(SpeechAsrContract.DESC_ASR_LISTENER);
            return true;
        }
        switch (code) {
            case SpeechAsrContract.TX_LISTENER_ON_PARTIAL_RESULT: {
                Bundle bundle = readBundle(data);
                callback.onPartialResult(session, string(bundle, SpeechAsrContract.KEY_TEXT));
                return true;
            }
            case SpeechAsrContract.TX_LISTENER_ON_SENTENCE_RESULT: {
                Bundle bundle = readBundle(data);
                callback.onSentenceResult(
                        session,
                        string(bundle, SpeechAsrContract.KEY_TEXT),
                        bundle != null ? bundle.getInt(SpeechAsrContract.KEY_INDEX, -1) : -1);
                return true;
            }
            case SpeechAsrContract.TX_LISTENER_ON_FINAL_RESULT: {
                Bundle bundle = readBundle(data);
                callback.onFinalResult(
                        session,
                        string(bundle, SpeechAsrContract.KEY_TEXT),
                        bundle != null ? bundle.getString(SpeechAsrContract.KEY_EXTRA) : null);
                return true;
            }
            case SpeechAsrContract.TX_LISTENER_ON_AUDIO_LEVEL: {
                callback.onAudioLevel(session, data.readFloat());
                return true;
            }
            case SpeechAsrContract.TX_LISTENER_ON_ERROR: {
                Bundle bundle = readBundle(data);
                callback.onError(
                        session,
                        bundle != null ? bundle.getInt(SpeechAsrContract.KEY_CODE, -1) : -1,
                        string(bundle, SpeechAsrContract.KEY_MESSAGE));
                return true;
            }
            case SpeechAsrContract.TX_LISTENER_ON_READY: {
                callback.onReady(session);
                return true;
            }
            default:
                return super.onTransact(code, data, reply, flags);
        }
    }

    private static Bundle readBundle(Parcel parcel) {
        return parcel.readInt() != 0 ? Bundle.CREATOR.createFromParcel(parcel) : null;
    }

    private static String string(Bundle bundle, String key) {
        if (bundle == null) {
            return "";
        }
        String value = bundle.getString(key);
        return value != null ? value : "";
    }
}
