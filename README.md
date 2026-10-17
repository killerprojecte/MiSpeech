# MiSpeech

**HyperOS 语音转文字（ASR）桥接库** —— 把「超级小爱」语音助手的语音识别能力，抽成一个可直接复用的 Android Library。

调用的是 HyperOS 语音助手（`com.miui.voiceassist`）导出的 **OSbotSpeechService**：
一句话概括就是 **绑定系统语音服务 → 新建 ASR 会话 → 收文本回调**，录音与识别全部在语音助手进程里完成，
你的 App 只负责说"开始 / 停止 / 取消"并把结果文本上屏。

> 📌 本库的接口契约（Binder 描述符、事务码、Bundle 键名与默认取值）全部由
> **「超级小爱输入法」`com.xiaomi.type`（versionName `0.2.1053.06f65b7c`）** 反编译提取，
> 与该输入法线上行为一致。详见 [逆向来源](#逆向来源)。

---

## 目录

- [工作原理](#工作原理)
- [快速开始](#快速开始)
  - [1. 加依赖](#1-加依赖)
  - [2. 必须的 Manifest 标识（重要）](#2-必须的-manifest-标识重要)
  - [3. 写代码](#3-写代码)
- [API 参考](#api-参考)
- [完整接口协议](#完整接口协议)
- [错误码](#错误码)
- [示例 App（example 模块）](#示例-appexample-模块)
- [限制与排错](#限制与排错)
- [逆向来源](#逆向来源)
- [构建与发布到 JitPack](#构建与发布到-jitpack)
- [目录结构](#目录结构)

---

## 工作原理

```
你的 App                      Android 系统              HyperOS 语音助手
   │                              │                    (com.miui.voiceassist)
   │ ① bindService               │                          │
   │  Intent("com.xiaomi."       │                          │
   │         "speech.action."    │                          │
   │         "SPEECH")           │                          │
   │  Component = voiceassist/   │                          │
   │    ...OSbotSpeechService    │                          │
   │ ───────────────────────────►│ ──► 绑定 ───────────────►│
   │                             │                          │
   │ ② ISpeechService.transact(1) │  ← 返回 IAsrClient       │
   │ ③ ISpeechService.transact(3) │  ← 返回能力表(Bundle)    │
   │                             │                          │
   │ ④ IAsrClient.transact(1, 回调, 参数Bundle)              │
   │ ─────────────────────────────────────────────────────► │ 🎙 开始录音
   │                             │                          │
   │ ⑤ IAsrListener 回调（服务端反向调用你的进程）             │
   │ ◄────── ready / 音量 / 中间结果 / 分句 / 最终结果 / 错误 ─┤
   │                             │                          │
   │ ⑥ IAsrClient.transact(2) 停止，或 transact(3) 取消       │
```

两个关键点，和"自己调 `SpeechRecognizer`"很不一样：

1. **服务端录音**：客户端接口里**没有任何写入音频的方法**，也不用麦克风权限，录音在语音助手进程里做。
2. **文本回调**：识别结果通过一个**反向 Binder 回调**（`IAsrListener`）送回你的进程，因此你可以拿到文本自行处理。

---

## 快速开始

### 1. 加依赖

**方式 A：JitPack（推荐，先按[此节](#构建与发布到-jitpack)把仓库推上去）**

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")   // ← 加上
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.<你的GitHub用户名>:MiSpeech:1.0.0")
}
```

> `groupId` 里的用户名要和 GitHub 账号一致。本仓库当前写的是 `com.github.AnserJim`
> —— 如果你的用户名不同，改 [library/build.gradle.kts](library/build.gradle.kts) 里的 `groupId` 再推送。

**方式 B：源码依赖**

```kotlin
// settings.gradle.kts
include(":library")          // 或把你的 app 放在本仓库里，直接用下面的 project 依赖

// app/build.gradle.kts
dependencies {
    implementation(project(":library"))
}
```

### 2. 必须的 Manifest 标识（重要）

这是最容易踩坑的地方。**光写对 ComponentName 是不够的**，宿主 App 必须持有下面这些"标识"，
否则 `bindService` 会直接失败（系统只会回一个含糊的 `SecurityException` 或返回 `false`）。

这些条目已经写在 [library/src/main/AndroidManifest.xml](library/src/main/AndroidManifest.xml) 里，
**Library 模块的 manifest 会在构建时自动合并进你的 App，你不需要再手写**。
但你必须知道它们为什么存在：

| # | 标识 | 类型 | 作用 |
|---|---|---|---|
| ① | `com.xiaomi.speech.permission.BIND_SPEECH_SERVICE` | `uses-permission` | 目标服务 `OSbotSpeechService` 由该权限保护，**调用方必须持有**才能绑定 |
| ② | `com.aios.osbot.permission.EXTERNAL_AGENT` | `uses-permission` | OSbot 框架（`com.aios.osbot.*`）用它校验调用方是否为受信任的外部代理 |
| ③ | `com.miui.voiceassist.QUERY_ENABLE` | `uses-permission` | 查询语音助手开关 / 状态的权限 |
| ④ | `<queries><package android:name="com.miui.voiceassist"/></queries>` | `queries` | **Android 11+ 包可见性**：不声明的话，即使写死了 ComponentName，系统也"看不见"语音助手，`bindService` 必然失败 |

以上四项，就是从「超级小爱输入法」`AndroidManifest.xml` 里实测出来的全部相关声明。

另外两个**不需要**的东西，也顺便说清楚：

- ❌ **不需要 `android.permission.RECORD_AUDIO`**：录音是语音助手做的。输入法本身的 manifest 里
  也确实没有声明它（我们逐条核对过），代码里也搜不到 `AudioRecord` / `MediaRecorder`。
- ❌ **不需要 `com.xiaomi.aiservice.permission.AI_ABILITY`**：那是走小米 AI SDK（`com.xiaomi.aiservice`）
  做 AI 写作/润色等能力的权限，和语音转文字不是同一条链路。

> ⚠️ **关于权限级别**：①②这类权限通常是 `signature` / 特权级别。
> 如果 `com.miui.voiceassist` 侧确实用 `android:permission` 强制校验，那么普通第三方 App
> 即便声明了也拿不到授权，绑定会被系统拒绝。本库无法从输入法 APK 侧证明服务端的校验策略，
> 但在你的设备上一跑就知道 —— 见 [SpeechAsrDiagnostics](#speechasrdiagnostics) 与
> [限制与排错](#限制与排错)。

### 3. 写代码

```java
public class MyActivity extends Activity implements SpeechAsrListener {

    private SpeechAsrManager asr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        asr = new SpeechAsrManager(this);

        // 建议：先打一份环境自检，出问题一眼看出卡在哪一步
        Log.i("MiSpeech", SpeechAsrDiagnostics.describe(this));

        // 开始识别（内部会自动 bindService，连上后再发起识别）
        asr.start(new SpeechAsrConfig(), this);
    }

    @Override public void onPartialResult(String text) {
        // 实时上屏：文本可能被不断修正
        inputConnection.setComposingText(text, 1);
    }

    @Override public void onFinalResult(String text, String extraJson) {
        // 最终结果：正式上屏
        inputConnection.commitText(text, 1);
    }

    @Override public void onError(int code, String message, String token) {
        Log.w("MiSpeech", "识别失败 " + token + "(" + code + "): " + message);
    }

    @Override protected void onDestroy() {
        asr.release();   // 取消会话 + 解绑服务 + 关闭内部线程
        super.onDestroy();
    }
}
```

说完话后调用 `asr.stop()` 结束（服务端随后回调 `onFinalResult`）；
不想等结果就 `asr.cancel()`。**所有方法都要在主线程调用，回调也全部回到主线程。**

---

## API 参考

### SpeechAsrManager

| 方法 | 说明 |
|---|---|
| `new SpeechAsrManager(Context)` | 内部持有 `applicationContext`，不会泄漏 Activity |
| `start(SpeechAsrConfig, SpeechAsrListener)` | 开始识别；未连接时会自动绑定，连上后再发起 |
| `stop()` | 正常结束，服务端会回调最终结果 |
| `cancel()` | 放弃本次识别，不再产出结果 |
| `connect()` | 只预热连接，不开始识别 |
| `isConnected()` | 是否已绑定上服务 |
| `getCapabilities()` | 服务能力表 `Bundle` |
| `getSupportedAsrEngines()` | 服务端支持的引擎数组（来自能力表 `supported_asr_engines`） |
| `release()` | 释放全部资源，之后不可再用 |

### SpeechAsrConfig

默认值与输入法**完全一致**，可按需覆盖：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `engineProvider` | `volc` | 识别引擎。可选 `aliyun`，但服务端能力表不支持时会自动回退 `volc` |
| `language` | `zh-CN` | 语种 |
| `enableCloudVad` | `false` | 云端 VAD，输入法默认关闭 |
| `enableAudioLevel` | `true` | 是否回传音量（做波形动画用） |
| `businessTag` | `mi_ime` | 业务标识 |
| `featureId` | `voice_asr` | 功能标识 |
| `contentModeration` | `false` | 内容审核 |
| `fromApp` | 空 | 来源包名，不填则不写入该字段 |
| `hotwords` | 空 | 热词，序列化为 `hotwordsJson`，权重默认 `4` |

```java
SpeechAsrConfig config = new SpeechAsrConfig()
        .setFromApp(getPackageName())
        .setEnableCloudVad(true)
        .addHotword("超级小爱")            // 权重默认 4
        .addHotword("HyperOS", 6);
```

### SpeechAsrListener

全部是 `default` 方法，按需覆写即可。典型时序：

```
onConnected → onReady → (onAudioLevel / onPartialResult)* → (onSentenceResult)* → onFinalResult → onFinished
```

| 回调 | 说明 |
|---|---|
| `onConnected(Bundle capabilities)` | 已绑定并拿到能力表 |
| `onReady()` | 会话就绪，可以提示用户说话 |
| `onPartialResult(String)` | 中间结果，实时上屏用 |
| `onSentenceResult(String, int index)` | 分句结果：一句话识别完成 |
| `onFinalResult(String text, String extraJson)` | 最终结果，`extraJson` 含 `effectiveProvider` 等 |
| `onAudioLevel(float level)` | 音量 `0f~1f` |
| `onError(int code, String message, String token)` | 出错 |
| `onFinished()` | 会话结束（收到最终结果 / 被取消 / 出错收尾） |
| `onDisconnected(String reason)` | 与服务断连 |

<a id="speechasrdiagnostics"></a>
### SpeechAsrDiagnostics

环境自检。**强烈建议在 `start()` 之前调用一次**，把结论打进日志：

```java
Log.i("MiSpeech", SpeechAsrDiagnostics.describe(context));
```

输出形如：

```
MiSpeech 环境自检
  语音助手已安装        : true  (com.miui.voiceassist)
  语音服务可见(queries) : true  (...OSbotSpeechService)
  com.xiaomi.speech.permission.BIND_SPEECH_SERVICE: 已授予
  com.aios.osbot.permission.EXTERNAL_AGENT        : 未授予
  com.miui.voiceassist.QUERY_ENABLE               : 已授予
```

逐项含义：装没装语音助手 →`<queries>` 有没有写对 → 三个"标识"权限拿没拿到。
调试试配不上时，这份报告能直接指出是哪一环的问题。

### VoiceAssistLauncher

**备用通道（免权限兜底）**：直接拉起小爱的语音输入浮窗，由它自己完成识别并把文字送进当前输入框。

```java
VoiceAssistLauncher.launchFloatingInput(context);
```

它只是给语音助手发了一个 `android.intent.action.ASSIST` 的 Service Intent
（`voice_assist_function_key=pull_up_keyboard`、`voice_assist_start_from_key=input_method`），
**不走 Binder、不需要上面那些权限标识**。代价是拿不到文本，无法自定义上屏逻辑。

> 输入法自己也是这么兜底的：`SpeechAsrManager` 那条路失败时，它会调用这个 Intent 拉起语音助手。

---

## 完整接口协议

如果你不用本库、想自己实现，或者要移植到别的语言，这里是全部细节。
描述符与事务码均已核对，`Bundle` 键名照抄即可。

### 绑定服务

```java
Intent intent = new Intent("com.xiaomi.speech.action.SPEECH");   // action
intent.setComponent(new ComponentName(
        "com.miui.voiceassist",
        "com.aios.osbot.external.OSbotSpeechService"));          // 组件
context.bindService(intent, conn, 4097);                          // 4097 = 见下
```

`4097` = `BIND_AUTO_CREATE(0x1) | BIND_INCLUDE_CAPABILITIES(0x1000)`。
注意是**三参数版 `bindService`（不传权限参数）**，因此权限校验发生在服务侧。

### ISpeechService

> descriptor: `com.xiaomi.speech.framework.common.ISpeechService`

| 事务 | 签名 | 返回 |
|---|---|---|
| `1` | `IAsrClient newAsrClient()` | ASR 客户端 Binder |
| `3` | `Bundle getCapabilities()` | 能力表，含 `supported_asr_engines`(`String[]`) |

> 事务 `2` 未在本 App 中被使用，推测是另一类客户端（如 TTS）；本库不涉及。

### IAsrClient

> descriptor: `com.xiaomi.speech.framework.common.asr.IAsrClient`

| 事务 | 签名 | 说明 |
|---|---|---|
| `1` | `void start(IAsrListener listener, in Bundle params)` | 开始识别 |
| `2` | `void stop()` | 正常结束，会产出最终结果 |
| `3` | `void cancel()` | 取消，不产出结果 |

`start` 的参数编码顺序：`writeInterfaceToken` → `writeStrongInterface(listener)` →
`writeInt(1)` + `bundle.writeToParcel()`。（`writeInt(1)` 是 AIDL 对可空 `Bundle` 的固定写法。）

### IAsrListener（服务端反调你的进程）

> descriptor: `com.xiaomi.speech.framework.common.asr.IAsrListener`

| 事务 | 入参 | 语义 |
|---|---|---|
| `6` | – | 会话就绪 |
| `1` | `Bundle{text}` | 中间结果 |
| `2` | `Bundle{text, index}` | 分句结果 |
| `3` | `Bundle{text, extra}` | 最终结果（`extra` 是 JSON，含 `effectiveProvider`） |
| `4` | `float` | 音量 `0f~1f` |
| `5` | `Bundle{code, message}` | 错误 |

实现要点（和你平时写 AIDL 略有不同，因为这是**手工 Binder**）：

```java
public final class MyListener extends Binder implements IInterface {
    public MyListener() { attachInterface(this, "com.xiaomi.speech.framework.common.asr.IAsrListener"); }

    @Override public IBinder asBinder() { return this; }

    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code >= 1 && code <= 16777215) data.enforceInterface(DESC);   // 描述符校验
        if (code == 1598968902) { reply.writeString(DESC); return true; } // 取描述符事务
        switch (code) {
            case 3: {  // 最终结果
                Bundle b = data.readInt() != 0 ? Bundle.CREATOR.createFromParcel(data) : null;
                String text = b != null ? b.getString("text") : "";
                return true;
            }
            // ...
        }
        return super.onTransact(code, data, reply, flags);
    }
}
```

`onTransact` 运行在 **Binder 线程**，要自己切回主线程。

### start 的参数 Bundle

键名与默认值（与输入法一致）：

| 键 | 类型 | 输入法默认值 |
|---|---|---|
| `engineProvider` | String | `volc`（可选 `aliyun`） |
| `language` | String | `zh-CN` |
| `enableCloudVad` | boolean | `false` |
| `enableAudioLevel` | boolean | `true` |
| `businessTag` | String | `mi_ime` |
| `featureId` | String | `voice_asr` |
| `contentModeration` | boolean | `false` |
| `fromApp` | String | 可选，为空则不写 |
| `hotwordsJson` | String | 可选，`{"items":[{"text":"…","weight":4}]}` |

---

## 错误码

服务端通过 `IAsrListener` 事务 `5` 的 `Bundle{code, message}` 回传。token 与中文说明见
[SpeechAsrError](library/src/main/java/com/mispeech/hyperos/asr/SpeechAsrError.java)：

| code | token | 含义 |
|---|---|---|
| `-1` | `UNKNOWN` | 未知 |
| `1` | `NETWORK` | 网络异常 |
| `2` | `AUDIO` | 音频采集异常 |
| `4` | `TIMEOUT` | 识别超时 |
| `5` | `NO_SPEECH` | 没检测到语音 |
| `6` | `SERVER` | 服务端错误 |
| `7` | `AUTH` | 鉴权失败 |
| `8` | `INVALID_STATE` | 状态不合法 |
| `100` | `INVALID_REQUEST` | 请求参数不合法 |
| `101` | `RECORDER_BUSY` | 录音被占用（别的 App 正在录音） |
| `102` | `SERVICE_UNAVAILABLE` | 服务不可用（绑定失败 / 未就绪）——**权限没配好通常是这个** |
| `103` | `CTA_NOT_ACCEPTED` | 用户还没同意语音助手的隐私协议 |
| `104` | `UNSUPPORTED_AUDIO_CONFIG` | 不支持的音频参数 |
| `105` | `AUDIO_PERMISSION` | 缺录音权限（**由语音助手侧判定**） |
| `30002` | `CONTENT_MODERATION` | 内容被审核拦截 |

---

## 示例 App（example 模块）

[example/](example) 是一个极简的测试 App，用来在真机上把整条链路跑通并看到每一步结果。

```bash
./gradlew :example:installDebug
```

界面顶部是一个输入框 —— **语音文字会实时写进这里**，下面还有一个**识别引擎下拉框**
（连上服务后会用能力表 `supported_asr_engines` 自动填充可选项）。再往下是七个按钮：

| 控件 | 作用 |
|---|---|
| 识别引擎 | 选择 `engineProvider`；「自动」即交给库解析（默认 `volc`，配置了 `aliyun` 而服务端不支持时自动回退） |
| ① 开始识别 | 发起一次识别，边说边把文字实时上屏 |
| ② 停止 | 正常结束，等最终结果 |
| ③ 取消 | 放弃本次识别 |
| ④ 拉起小爱语音输入浮窗 | 走免权限兜底通道，验证语音助手本身是否可用 |
| ⑤ 申请 RECORD_AUDIO（可选） | 仅当遇到 `105 AUDIO_PERMISSION` 时才需要 |
| ⑥ 打印环境自检 | 重新输出 `SpeechAsrDiagnostics.describe()` |
| ⑦ 清空输入框 | 清掉输入框内容 |

### 实时上屏是怎么做的

这是本库最实用的部分，姿势和「超级小爱输入法」完全一致：

| 服务端回调 | 事务 | 上屏动作 |
|---|---|---|
| `onPartialResult` | `IAsrListener` 1 | `setComposingText(text, 1)` —— 预编辑态（带下划线），每来一次整体替换，文字就"长"出来了 |
| `onFinalResult` | `IAsrListener` 3 | `commitText(text, 1)` —— 定稿，把预编辑内容替换为最终文本 |

示例里用 `new BaseInputConnection(editText, true)` 直接驱动输入框；**真正的输入法里换成 IME 自己的
`getCurrentInputConnection()` 即可，代码一模一样**：

```java
@Override public void onPartialResult(String text) {
    getCurrentInputConnection().setComposingText(text, 1);   // 边说边出字
}

@Override public void onFinalResult(String text, String extra) {
    getCurrentInputConnection().commitText(text, 1);         // 定稿上屏
}
```

> 分句回调 `onSentenceResult`（事务 2）里的文本通常已包含在最终结果中，示例只做记录不做上屏，
> 以免内容重复。

页面底部会滚动显示完整事件日志，同时也会打到 Logcat：

```bash
adb logcat -s MiSpeech MiSpeechExample
```

> 示例只用了框架 API，没有引入 AppCompat / Material 依赖。

---

## 限制与排错

**先说结论**：这条链路依赖 HyperOS 的语音助手，并且要吃系统级权限标识。
能不能在普通第三方 App 里跑通，取决于你设备上 `com.miui.voiceassist` 的校验策略。
本库把它做成了"如实复刻 + 明确报错"，跑不通时会告诉你卡在哪一步，而不是静默失败。

| 现象 | 可能原因 | 怎么办 |
|---|---|---|
| `onError(102)` 且日志有 `bindService 被拒绝` | `<queries>` 没生效 / 权限没拿到 | 跑 `SpeechAsrDiagnostics.describe()` 逐项对照 |
| `SecurityException: not allowed to bind to service` | ②③类权限是 signature 级别，你的 App 拿不到 | 需要系统签名 / 特权应用，或用下面④的兜底通道 |
| `onNullBinding` / `onServiceDisconnected` | 语音助手版本不匹配或被杀 | 确认 HyperOS 版本与"超级小爱"已启用；重试 |
| `onError(103)` | 语音助手隐私协议未同意 | 先手动打开一次小爱同学并同意协议 |
| `onError(101)` | 录音被占用 | 关掉其他正在录音的 App |
| `onError(105)` | 录音权限被服务端判定缺失 | 在 Manifset 声明并在运行时申请 `RECORD_AUDIO`（示例 App 已提供该按钮） |
| 一直卡在未连接 | 服务端需要更长时间 | 本库有超时保护，服务端不可用时会给 102 |

**其它已知限制**

- **minSdk 29**，但功能上要求设备是 HyperOS / MIUI 且装有语音助手，否则一律 102。
- **`engineProvider=aliyun` 不一定可用**：会先查服务端能力表 `supported_asr_engines`，不支持则回退 `volc`。
- **不具备"喂音频"能力**：接口设计上就是服务端录音。想识别一个现成音频文件，这个接口做不到。
- 事务 `2`（`ISpeechService`）未使用，语义未知；若你依赖它，需要自行验证。

---

## 逆向来源

提取对象：**「超级小爱输入法」`com.xiaomi.type`**，`versionName 0.2.1053.06f65b7c`，
`versionCode 21053`，compileSdk 35。

| 混淆类 | 在链路中的角色 |
|---|---|
| `r8.h` | 目标服务 ComponentName：`com.miui.voiceassist` / `com.aios.osbot.external.OSbotSpeechService` |
| `cc.h` | `bindService` 的 Intent、action、flags，以及连接状态机（DISCONNECTED/CONNECTING/CONNECTED/DIED） |
| `cc.e` | `ServiceConnection` 实现 |
| `cc.d` | 连接建立后取 `ISpeechService` 代理 + 拉能力表 |
| `cc.f` | `ISpeechService` 事务 1（`newAsrClient`） |
| `ac.a` | `ISpeechService` 事务 3（`getCapabilities`） |
| `bc.a` | `IAsrClient` 事务 1/2/3（start/stop/cancel） |
| `r8.d` | `IAsrListener#onTransact`：6 个回调事务的完整分派与 Bundle 键名 |
| `r8.f` | ASR 管理器：启动参数 Bundle 全部键名与默认值、错误码 → token 映射表、引擎回退逻辑 |
| `u9.d0#i` | 免权限兜底：`ACTION_ASSIST` 拉起语音输入浮窗的 Intent 参数 |
| `com.xiaomi.onetrack.g.a#d` | 错误码 Bundle 的键名常量（`= "code"`） |

其它已核实的事实：

- 全 App 内**搜不到** `AudioRecord` / `MediaRecorder` 调用 → 录音确实在服务端。
- `AndroidManifest.xml` 中**没有** `RECORD_AUDIO`，但保留了 `PermissionRequestActivity`（默认申请它，可选路径）。
- `com.xiaomi.aiservice` / `AI_ABILITY` 属于另一条链路（小米 AI SDK），与 ASR 无关。

---

## 构建与发布到 JitPack

### 本地构建

```bash
./gradlew :library:assembleRelease      # 产出 library/build/outputs/aar/library-release.aar
./gradlew :example:assembleDebug        # 产出 example/build/outputs/apk/debug/example-debug.apk
./gradlew :library:publishToMavenLocal  # 验证发布配置
```

本库是**零依赖**的纯 Java 库（AGP 9 默认会给 Android Library 挂 `kotlin-stdlib`，
已在 `gradle.properties` 里用 `kotlin.stdlib.default.dependency=false` 关掉）。

### 推到 JitPack

1. 改 [library/build.gradle.kts](library/build.gradle.kts) 里的 `groupId` 为 `com.github.<你的GitHub用户名>`，
   并按需改 `version`。
   （当前是 `com.github.AnserJim` / `1.0.0`。）
2. 初始化 Git 仓库并推到 GitHub（**必须打 tag**，JitPack 是按 tag / release 出包的）：

   ```bash
   git init
   git add .
   git commit -m "MiSpeech: HyperOS ASR bridge library"
   git remote add origin https://github.com/<你的用户名>/MiSpeech.git
   git push -u origin main
   git tag 1.0.0
   git push origin 1.0.0
   ```

3. 打开 `https://jitpack.io/#<你的用户名>/MiSpeech`，点 **Look up** → **Get it**。
4. 按 [1. 加依赖](#1-加依赖) 里的坐标引入即可。

`jitpack.yml` 已经配好只构建发布 `:library`（示例 App 不参与发布）：

```yaml
jdk:
  - openjdk21
install:
  - ./gradlew :library:publishToMavenLocal -x test --no-daemon
```

> 提示：首次构建时 JitPack 需要联网拉 AGP 与 Android SDK，耐心等几分钟。

---

## 目录结构

```
MiSpeech/
├── library/                                # ★ 对外发布的库（零依赖纯 Java）
│   ├── build.gradle.kts                    #   maven-publish，groupId/version 在这里改
│   ├── consumer-rules.pro                  #   消费方混淆规则（保留 Binder 回调）
│   └── src/main/
│       ├── AndroidManifest.xml             #   ★ 必须的“标识”：3 个权限 + queries
│       └── java/com/mispeech/hyperos/asr/
│           ├── SpeechAsrManager.java       #   高层 API：连接 / 开始 / 停止 / 取消
│           ├── SpeechAsrConfig.java        #   启动参数（默认值与输入法一致）
│           ├── SpeechAsrListener.java      #   回调接口（全 default 方法）
│           ├── SpeechAsrError.java         #   错误码 → token / 中文说明
│           ├── SpeechAsrDiagnostics.java   #   环境自检报告
│           ├── VoiceAssistLauncher.java    #   免权限兜底：拉起小爱语音输入浮窗
│           └── internal/
│               ├── SpeechAsrContract.java  #   全部协议常量（描述符/事务码/Bundle 键）
│               ├── SpeechServiceProxy.java #   ISpeechService 代理
│               ├── AsrClientProxy.java     #   IAsrClient 代理
│               └── AsrListenerBinder.java  #   IAsrListener 实现（服务端回调入口）
├── example/                                # 真机测试用示例 App（不发布）
├── jitpack.yml
└── settings.gradle.kts                     # include(":library") / include(":example")
```

---

## 用 root 手动授予权限（进阶）

> 仅当你确认了「目标服务确实校验权限」、且希望走 Binder 通路时才需要折腾这些。
> 如果自检报告里写的是 `无 —— 服务不校验调用方权限`，那**不需要 root**，直接调就行。

### 第 0 步：先查清保护级别（一切的前提）

```bash
# 看权限由谁定义、什么级别
adb shell su -c 'pm list permissions -f | grep -i -A5 -E "speech\.permission|osbot|voiceassist\.QUERY"'

# 或直接看目标服务自己写了什么
adb shell su -c 'dumpsys package com.miui.voiceassist | grep -i -B3 -A8 OSbotSpeechService'
```

关注输出里的 `protectionLevel:`。**级别决定了有没有"授予"这回事**：

| protectionLevel | 能否手动授予 | 做法 |
|---|---|---|
| `normal` | 装完就有 | 无需操作（仍显示未授予 → 是包可见性问题，去查 `<queries>`） |
| `dangerous` | ✅ 可以 | `adb shell pm grant <你的包名> <权限>` |
| `privileged` / `signature\|privileged` / 旧式 `signatureOrSystem` | ✅ 可以 | 特权 App + 白名单，见下 |
| **`signature`** | ❌ **无法授予** | 只能"绕过"，见文末 |

### ⚠️ 关于 `pm grant`：对签名级权限一定失败

```bash
adb shell pm grant com.mispeech.hyperos.asr.example com.xiaomi.speech.permission.BIND_SPEECH_SERVICE
# SecurityException: Permission ... is not a changeable permission type
```

`pm grant` 只处理 `dangerous`（运行时）权限，这是硬编码行为，不是配置问题。
签名级权限在**安装扫描时**就由系统比对签名决定，装完之后没有"补授予"的官方入口。
所以别在这一步反复试。

### ✅ priv-app + 白名单（对 `privileged` / `signature|privileged` 有效）

核心思路：把 App 变成**特权系统应用**。用 Magisk 模块最干净 —— 不动 `/system`、可随时卸载、
不需要重新刷机。模块结构：

```
MiSpeechPerm/
├── module.prop
└── system/
    ├── etc/permissions/privapp_permissions_mispeech.xml
    └── priv-app/MiSpeechExample/MiSpeechExample.apk
```

`module.prop`：

```properties
id=mispeech_perm
name=MiSpeech Privileged Permissions
version=1.0
versionCode=1
author=you
description=Make MiSpeech example a privileged app so it can hold the HyperOS ASR permissions
```

`system/etc/permissions/privapp_permissions_mispeech.xml`（包名换成你的）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.mispeech.hyperos.asr.example">
        <permission name="com.xiaomi.speech.permission.BIND_SPEECH_SERVICE" />
        <permission name="com.aios.osbot.permission.EXTERNAL_AGENT" />
        <permission name="com.miui.voiceassist.QUERY_ENABLE" />
    </privapp-permissions>
</permissions>
```

步骤：

```bash
# 1. 先卸掉普通方式装的同名 App，避免包名冲突（重要）
adb uninstall com.mispeech.hyperos.asr.example

# 2. 把模块目录放到手机上
adb push MiSpeechPerm /data/local/tmp/
adb shell su -c 'cp -r /data/local/tmp/MiSpeechPerm /data/adb/modules/'

# 3. 重启
adb shell su -c 'reboot'
```

重启后打开示例 App，点 **⑥ 打印环境自检**：原来"未授予"的三项应该变成"已授予"。

**注意事项**

- 不同机型分区布局不同：有的设备需要把路径放在 `system/system_ext/...` 而不是 `system/...`，
  以你机器上 `/system/etc/permissions/` 里原有 `privapp-permissions-*.xml` 的实际位置为准。
- 白名单里列出的权限，**保护级别必须真的是 privileged 系**。若是纯 `signature`，
  系统会忽略白名单条目（并在 logcat 打 warning），你不会拿到权限。
- 换成特权应用后，用 `adb install -r` 覆盖安装可能失败（系统应用签名限制），
  更新时要改模块里的 APK 再重启。
- 拿到权限也不代表一定能识别成功：还要过语音助手自己的隐私协议（`103 CTA_NOT_ACCEPTED`）等。

### ❌ 不要做的事：手改 `packages.xml`

网上流传"改 `/data/system/packages.xml` 里的 `<perms>` 加一行就能授予签名权限"。
这在 Android 10+ 基本不生效：开机时 `PermissionManagerService.restorePermissionState()`
会重新校验签名级权限（`grantSignaturePermission`），手改的条目会被判定为不合法而不授予。
折腾它有把 `packages.xml` 改坏导致开不了机的风险，收益却是零。

### 纯 `signature` 级的出路

如果查出是纯 `signature`（例如由 `com.miui.voiceassist` 用自己的证书定义），那么就**只能绕过**，
而且都不是"授予权限"：

1. **改目标 APK，去掉校验**：把语音助手 APK 里 `OSbotSpeechService` 上的 `android:permission`
   删掉，重签后用 Magisk 模块覆盖。副作用很大：重签会破坏该应用自身基于签名的权限，
   还可能触发它的完整性校验，属于"能跑但很脏"。
2. **LSPosed hook**：作用域挂 `System Framework`（system_server）劫持权限判定
   （`PermissionManagerServiceImpl` / `checkComponentPermission`），或直接 hook 语音助手进程。
   版本相关性强、易碎，属于研究手段。
3. **最省事的选择**：接受现实，用免权限兜底通道
   [`VoiceAssistLauncher`](library/src/main/java/com/mispeech/hyperos/asr/VoiceAssistLauncher.java)
   —— 让语音助手自己弹窗识别。拿不到文本，但零门槛、零风险。

---

## 免责声明

本项目仅用于**技术研究与个人设备上的互操作**。接口来自公开可获取的 APK 逆向分析，
未使用任何小米私有源码或密钥。语音识别服务、其可用性、以及权限策略均由小米控制，
可能随系统版本变化而失效。请遵守你所在地法律法规与相关服务条款；
不要用它做大规模调用、绕过付费或侵害他人隐私的事情。
