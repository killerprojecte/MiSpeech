# MiSpeech — HyperOS 语音转文字（ASR）桥接库
# 通过 Binder 跨进程调用 HyperOS 语音助手（com.miui.voiceassist）的
# OSbotSpeechService，完成"服务端录音 + 识别 + 文本回调"。

# 保留对外暴露的 Binder 回调（服务端会反向调用本进程的 Binder）
-keep class com.mispeech.hyperos.asr.internal.AsrListenerBinder { *; }
-keep class com.mispeech.hyperos.asr.internal.AsrClientProxy { *; }
-keep class com.mispeech.hyperos.asr.internal.SpeechServiceProxy { *; }

# 公开 API 保留（便于使用方按名称反射/调用）
-keep class com.mispeech.hyperos.asr.** { *; }
