package com.mispeech.hyperos.asr;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.mispeech.hyperos.asr.internal.AsrClientProxy;
import com.mispeech.hyperos.asr.internal.AsrListenerBinder;
import com.mispeech.hyperos.asr.internal.SpeechAsrContract;
import com.mispeech.hyperos.asr.internal.SpeechServiceProxy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HyperOS 语音助手语音转文字（ASR）客户端。
 *
 * <p><b>完整调用链</b>（与「超级小爱输入法」{@code com.xiaomi.type} 完全一致）：
 * <pre>
 * 1. bindService(new Intent("com.xiaomi.speech.action.SPEECH")
 *        .setComponent(com.miui.voiceassist / com.aios.osbot.external.OSbotSpeechService),
 *        BIND_AUTO_CREATE | BIND_INCLUDE_CAPABILITIES)
 * 2. ISpeechService.transact(1)                       -> IAsrClient
 * 3. ISpeechService.transact(3)                       -> Bundle 能力表(supported_asr_engines)
 * 4. IAsrClient.transact(1, listener, params)         -> 服务端开始录音识别
 * 5. IAsrListener 事务 1/2/3/4/5/6                    -> 中间结果/分句/最终结果/音量/错误/就绪
 * 6. IAsrClient.transact(2) 正常结束 / transact(3) 取消
 * </pre>
 *
 * <p><b>录音权限</b>：本接口是服务端录音模型，客户端<b>不需要</b> RECORD_AUDIO，
 * 也不需要向服务端发送任何音频数据。
 *
 * <p>典型用法：
 * <pre>
 * SpeechAsrManager asr = new SpeechAsrManager(context);
 * asr.start(new SpeechAsrConfig(), new SpeechAsrListener() {
 *     &#64;Override public void onPartialResult(String text) { ... }
 *     &#64;Override public void onFinalResult(String text, String extra) { ... }
 * });
 * asr.stop();   // 说完后结束，等待 onFinalResult
 * asr.release();// 页面销毁时释放
 * </pre>
 *
 * <p>本类方法需在主线程调用；回调也全部回到主线程。
 */
public final class SpeechAsrManager {

    public static final String TAG = "MiSpeech";

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ipc = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mispeech-ipc");
        thread.setDaemon(true);
        return thread;
    });

    private volatile SpeechServiceProxy serviceProxy;
    private volatile Bundle capabilities;
    private volatile boolean bound;
    private volatile boolean binding;
    private volatile boolean released;

    private int sessionCounter;
    private volatile int activeSession;
    private volatile AsrClientProxy activeClient;
    private volatile SpeechAsrListener listener;

    private SpeechAsrConfig pendingConfig;
    private int pendingSession;

    public SpeechAsrManager(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }
        this.appContext = context.getApplicationContext();
    }

    // ------------------------------------------------------------ 连接管理

    /** 只建立绑定（预热），不开始识别。 */
    public void connect() {
        binderConnect();
    }

    /** 是否已绑定上语音助手服务。 */
    public boolean isConnected() {
        return bound;
    }

    /** 服务能力表，未连接时为 null。 */
    public Bundle getCapabilities() {
        return capabilities;
    }

    /** 服务端支持的 ASR 引擎（能力表 {@code supported_asr_engines}）。 */
    public String[] getSupportedAsrEngines() {
        Bundle caps = capabilities;
        if (caps == null) {
            return null;
        }
        try {
            return caps.getStringArray(SpeechAsrContract.KEY_SUPPORTED_ASR_ENGINES);
        } catch (Throwable t) {
            return null;
        }
    }

    private void binderConnect() {
        if (released || bound || binding) {
            return;
        }
        binding = true;
        Intent intent = new Intent(SpeechAsrContract.ACTION_SPEECH);
        intent.setComponent(new ComponentName(
                SpeechAsrContract.VOICE_ASSIST_PACKAGE,
                SpeechAsrContract.OSBOT_SPEECH_SERVICE));
        boolean accepted;
        try {
            accepted = appContext.bindService(intent, connection, SpeechAsrContract.BIND_FLAGS);
        } catch (Throwable t) {
            Log.e(TAG, "bindService threw (通常是缺少 manifest 标识/权限)", t);
            accepted = false;
        }
        if (!accepted) {
            binding = false;
            reportUnavailable("bindService 被拒绝：请检查 manifest 标识（"
                    + SpeechAsrContract.PERMISSION_BIND_SPEECH_SERVICE + " 等）、<queries> 声明"
                    + "以及语音助手是否存在。可先调用 SpeechAsrDiagnostics.describe()。");
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.i(TAG, "onServiceConnected: " + name);
            ipc.execute(() -> {
                SpeechServiceProxy proxy = new SpeechServiceProxy(binder);
                Bundle caps = null;
                try {
                    caps = proxy.getCapabilities();
                } catch (Throwable t) {
                    Log.w(TAG, "getCapabilities 失败", t);
                }
                final Bundle resultCaps = caps;
                mainHandler.post(() -> handleConnected(proxy, resultCaps));
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "onServiceDisconnected: " + name);
            mainHandler.post(() -> handleDisconnected("onServiceDisconnected: " + name));
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.w(TAG, "onBindingDied: " + name);
            mainHandler.post(() -> handleDisconnected("onBindingDied: " + name));
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.w(TAG, "onNullBinding: " + name);
            mainHandler.post(() -> handleDisconnected("onNullBinding: " + name));
        }
    };

    private void handleConnected(SpeechServiceProxy proxy, Bundle caps) {
        if (released) {
            return;
        }
        binding = false;
        bound = true;
        serviceProxy = proxy;
        capabilities = caps;
        Log.i(TAG, "已连接语音助手，能力项=" + (caps == null ? 0 : caps.size()));
        SpeechAsrListener current = listener;
        if (current != null) {
            current.onConnected(caps);
        }
        if (pendingConfig != null) {
            SpeechAsrConfig config = pendingConfig;
            int session = pendingSession;
            pendingConfig = null;
            dispatchStart(session, config);
        }
    }

    private void handleDisconnected(String reason) {
        binding = false;
        bound = false;
        serviceProxy = null;
        capabilities = null;
        int session = activeSession;
        if (session != 0) {
            deliverError(session, SpeechAsrError.SERVICE_UNAVAILABLE, reason);
        }
        SpeechAsrListener current = listener;
        if (current != null) {
            current.onDisconnected(reason);
        }
    }

    // ------------------------------------------------------------ 识别控制

    /**
     * 开始一次识别。若尚未绑定，会先自动绑定，连上后再发起识别。
     *
     * @param config   参数；传 null 使用默认值（与输入法一致）
     * @param listener 回调；可为 null（此时只想预热连接）
     */
    public void start(SpeechAsrConfig config, SpeechAsrListener listener) {
        if (released) {
            throw new IllegalStateException("SpeechAsrManager 已 release()");
        }
        this.listener = listener;
        SpeechAsrConfig effective = config != null ? config : new SpeechAsrConfig();

        int session = ++sessionCounter;
        activeSession = session;

        AsrClientProxy previous = activeClient;
        activeClient = null;
        if (previous != null) {
            cancelQuietly(previous);
        }

        if (bound && serviceProxy != null) {
            dispatchStart(session, effective);
        } else {
            pendingSession = session;
            pendingConfig = effective;
            binderConnect();
        }
    }

    /** 正常结束识别（服务端会回调最终结果）。 */
    public void stop() {
        AsrClientProxy client = activeClient;
        if (client == null) {
            return;
        }
        ipc.execute(() -> {
            try {
                client.stop();
                Log.i(TAG, "ASR stop 已下发");
            } catch (Throwable t) {
                Log.w(TAG, "stop 失败", t);
            }
        });
    }

    /** 放弃本次识别（不再产出结果）。 */
    public void cancel() {
        AsrClientProxy client = activeClient;
        int session = activeSession;
        activeClient = null;
        activeSession = 0;
        if (client != null) {
            cancelQuietly(client);
        }
        if (session != 0) {
            SpeechAsrListener current = listener;
            if (current != null) {
                current.onFinished();
            }
        }
    }

    /** 释放资源：取消会话、解绑服务、关闭 IPC 线程。之后不可再使用。 */
    public void release() {
        if (released) {
            return;
        }
        released = true;
        activeSession = 0;
        AsrClientProxy client = activeClient;
        activeClient = null;
        if (client != null) {
            cancelQuietly(client);
        }
        listener = null;
        unbind();
        ipc.shutdown();
    }

    // ------------------------------------------------------------ 内部实现

    private void dispatchStart(int session, SpeechAsrConfig config) {
        ipc.execute(() -> {
            try {
                SpeechServiceProxy proxy = serviceProxy;
                if (proxy == null) {
                    throw new IllegalStateException("语音服务尚未连接");
                }
                AsrClientProxy client = proxy.newAsrClient();
                String engine = resolveEngine(config.getEngineProvider());
                Bundle params = config.toBundle(engine);
                AsrListenerBinder callback = new AsrListenerBinder(session, dispatcher);
                client.start(callback, params);
                if (activeSession == session) {
                    activeClient = client;
                } else {
                    cancelQuietly(client);
                }
                Log.i(TAG, "ASR start: session=" + session + ", engine=" + engine);
            } catch (Throwable t) {
                Log.e(TAG, "ASR start 失败", t);
                String message = t.getMessage() != null ? t.getMessage() : t.toString();
                deliverError(session, SpeechAsrError.SERVICE_UNAVAILABLE, message);
            }
        });
    }

    /**
     * 引擎选择：优先用配置值；若指定 aliyun 但服务端能力表不支持，则回退 volc。
     * 复刻输入法 {@code AsrEngineSelector} 的行为。
     */
    private String resolveEngine(String preferred) {
        String wanted = preferred != null ? preferred : SpeechAsrContract.ENGINE_VOLC;
        if (SpeechAsrContract.ENGINE_ALIYUN.equals(wanted)) {
            String[] supported = getSupportedAsrEngines();
            if (supported == null || !contains(supported, SpeechAsrContract.ENGINE_ALIYUN)) {
                Log.w(TAG, "服务端能力表不支持 aliyun，回退 " + SpeechAsrContract.ENGINE_VOLC);
                return SpeechAsrContract.ENGINE_VOLC;
            }
        }
        return wanted;
    }

    private static boolean contains(String[] values, String target) {
        for (String value : values) {
            if (target.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private final AsrListenerBinder.Callback dispatcher = new AsrListenerBinder.Callback() {
        @Override
        public void onReady(int session) {
            post(session, () -> {
                SpeechAsrListener current = listener;
                if (current != null) {
                    current.onReady();
                }
            });
        }

        @Override
        public void onPartialResult(int session, String text) {
            post(session, () -> {
                SpeechAsrListener current = listener;
                if (current != null) {
                    current.onPartialResult(text);
                }
            });
        }

        @Override
        public void onSentenceResult(int session, String text, int index) {
            post(session, () -> {
                SpeechAsrListener current = listener;
                if (current != null) {
                    current.onSentenceResult(text, index);
                }
            });
        }

        @Override
        public void onFinalResult(int session, String text, String extraJson) {
            post(session, () -> {
                SpeechAsrListener current = listener;
                if (current != null) {
                    current.onFinalResult(text, extraJson);
                }
                finishSession(session);
            });
        }

        @Override
        public void onAudioLevel(int session, float level) {
            post(session, () -> {
                SpeechAsrListener current = listener;
                if (current != null) {
                    current.onAudioLevel(level);
                }
            });
        }

        @Override
        public void onError(int session, int code, String message) {
            post(session, () -> {
                SpeechAsrListener current = listener;
                if (current != null) {
                    current.onError(code, message, SpeechAsrError.tokenOf(code));
                }
                finishSession(session);
            });
        }
    };

    private void post(int session, Runnable action) {
        mainHandler.post(() -> {
            if (released || session != activeSession) {
                return;
            }
            action.run();
        });
    }

    private void finishSession(int session) {
        if (session != activeSession) {
            return;
        }
        AsrClientProxy client = activeClient;
        activeClient = null;
        activeSession = 0;
        if (client != null) {
            cancelQuietly(client);
        }
        SpeechAsrListener current = listener;
        if (current != null) {
            current.onFinished();
        }
    }

    private void deliverError(int session, int code, String message) {
        pendingConfig = null;
        pendingSession = 0;
        mainHandler.post(() -> {
            if (released) {
                return;
            }
            SpeechAsrListener current = listener;
            if (current != null) {
                current.onError(code, message, SpeechAsrError.tokenOf(code));
            }
            if (session != 0) {
                finishSession(session);
            }
        });
    }

    private void reportUnavailable(String message) {
        Log.w(TAG, message);
        deliverError(activeSession != 0 ? activeSession : pendingSession,
                SpeechAsrError.SERVICE_UNAVAILABLE, message);
    }

    private void cancelQuietly(AsrClientProxy client) {
        ipc.execute(() -> {
            try {
                client.cancel();
            } catch (Throwable t) {
                Log.w(TAG, "cancel 失败", t);
            }
        });
    }

    private void unbind() {
        if (!bound && !binding) {
            return;
        }
        binding = false;
        bound = false;
        try {
            appContext.unbindService(connection);
        } catch (Throwable t) {
            Log.w(TAG, "unbindService 失败", t);
        }
        serviceProxy = null;
        capabilities = null;
    }
}
