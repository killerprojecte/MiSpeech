package com.mispeech.hyperos.asr.internal;

import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * {@code com.xiaomi.speech.framework.common.asr.IAsrClient} 的客户端代理。
 *
 * <p>一次识别会话 = 一个 IAsrClient。它只提供三个动作：
 * <ol>
 *   <li>{@link #start(IInterface, Bundle)} —— 事务 1，传入回调 + 参数，服务端开始录音识别</li>
 *   <li>{@link #stop()} —— 事务 2，正常结束（会产出最终结果）</li>
 *   <li>{@link #cancel()} —— 事务 3，放弃本次识别</li>
 * </ol>
 *
 * <p>注意：这里<b>没有</b>任何写入音频数据的接口 —— 录音完全发生在语音助手进程内。
 *
 * <p>对应反编译来源：{@code bc.a#c(d, Bundle)}、{@code a0.b#q()}（stop）、{@code a0.b#i()}（cancel）。
 */
public final class AsrClientProxy implements IInterface {

    private final IBinder remote;

    public AsrClientProxy(IBinder remote) {
        this.remote = remote;
    }

    @Override
    public IBinder asBinder() {
        return remote;
    }

    /**
     * {@code void start(IAsrListener listener, in Bundle params)} —— 事务 1。
     *
     * @param listener 本进程的回调 Binder（{@link AsrListenerBinder}）
     * @param params   识别参数，见 {@link SpeechAsrContract} 的 PARAM_* 常量
     */
    public void start(IInterface listener, Bundle params) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SpeechAsrContract.DESC_ASR_CLIENT);
            data.writeStrongInterface(listener);
            if (params != null) {
                data.writeInt(1);
                params.writeToParcel(data, 0);
            } else {
                data.writeInt(0);
            }
            remote.transact(SpeechAsrContract.TX_ASR_START, data, reply, 0);
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    /** {@code void stop()} —— 事务 2：正常结束识别，等待最终结果回调。 */
    public void stop() throws RemoteException {
        transactVoid(SpeechAsrContract.TX_ASR_STOP);
    }

    /** {@code void cancel()} —— 事务 3：取消本次识别，不再产出结果。 */
    public void cancel() throws RemoteException {
        transactVoid(SpeechAsrContract.TX_ASR_CANCEL);
    }

    private void transactVoid(int code) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SpeechAsrContract.DESC_ASR_CLIENT);
            remote.transact(code, data, reply, 0);
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
