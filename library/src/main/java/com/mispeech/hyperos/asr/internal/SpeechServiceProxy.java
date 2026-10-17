package com.mispeech.hyperos.asr.internal;

import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * {@code com.xiaomi.speech.framework.common.ISpeechService} 的客户端代理。
 *
 * <p>这是语言助手侧 {@code OSbotSpeechService} 暴露的主接口。
 * 手工实现 Parcel 编解码（而不是用 AIDL 生成），是为了让事务码与线上协议严格一致。
 *
 * <p>对应反编译来源：{@code cc.f}（事务 1）与 {@code ac.a}（事务 3）。
 */
public final class SpeechServiceProxy implements IInterface {

    private final IBinder remote;

    public SpeechServiceProxy(IBinder remote) {
        this.remote = remote;
    }

    @Override
    public IBinder asBinder() {
        return remote;
    }

    /**
     * {@code IAsrClient newAsrClient()} —— 事务 1。
     *
     * <p>无入参，返回一个会话级的 ASR 客户端 Binder。
     *
     * @return ASR 客户端代理；服务返回 null 时抛出 {@link RemoteException}
     */
    public AsrClientProxy newAsrClient() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SpeechAsrContract.DESC_SPEECH_SERVICE);
            remote.transact(SpeechAsrContract.TX_SPEECH_NEW_ASR_CLIENT, data, reply, 0);
            reply.readException();
            IBinder binder = reply.readStrongBinder();
            if (binder == null) {
                throw new RemoteException("ISpeechService.newAsrClient() returned null binder");
            }
            return new AsrClientProxy(binder);
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    /**
     * {@code Bundle getCapabilities()} —— 事务 3。
     *
     * <p>返回能力表，其中 {@code supported_asr_engines} 为服务端支持的引擎数组（String[]）。
     *
     * @return 能力表；服务端异常或无数据时可能为 {@code null}
     */
    public Bundle getCapabilities() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SpeechAsrContract.DESC_SPEECH_SERVICE);
            remote.transact(SpeechAsrContract.TX_SPEECH_GET_CAPABILITIES, data, reply, 0);
            reply.readException();
            if (reply.readInt() != 0) {
                return Bundle.CREATOR.createFromParcel(reply);
            }
            return null;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
