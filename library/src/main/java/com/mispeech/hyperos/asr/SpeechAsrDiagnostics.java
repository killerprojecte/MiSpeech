package com.mispeech.hyperos.asr;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ServiceInfo;

import com.mispeech.hyperos.asr.internal.SpeechAsrContract;

/**
 * 环境自检 —— 用来回答一个问题：<b>这套接口在「本机 + 本 App」上到底能不能调？</b>
 *
 * <p>能不能用取决于四件事，任何一件不满足都会让 {@code bindService} 失败
 * （而系统只会给一个很含糊的 SecurityException 或直接返回 false）：
 * <ol>
 *   <li>设备是 HyperOS / MIUI，且装有语音助手 {@code com.miui.voiceassist}</li>
 *   <li>声明了包可见性 {@code <queries>}，否则系统"看不见"语音助手</li>
 *   <li>目标服务是否用 {@code android:permission} 校验调用方</li>
 *   <li>若校验，本 App 是否真的持有那个权限</li>
 * </ol>
 *
 * <p>请注意一个容易误判的点：<b>「权限未授予」不等于「一定失败」</b>。
 * 只有第 3 条成立（服务确实校验）时，第 4 条才有意义。
 * 本类会把第 3 条的结论直接打出来 —— 它读的是 {@link ServiceInfo#permission}，
 * 也就是语音助手 APK 里写在服务上的那个 {@code android:permission}，属于设备上的权威事实。
 *
 * <p>用法：{@code Log.i("MiSpeech", SpeechAsrDiagnostics.describe(context));}
 */
public final class SpeechAsrDiagnostics {

    /** 权限保护级别的基础掩码（{@code PermissionInfo.PROTECTION_MASK_BASE}）。 */
    private static final int PROTECTION_MASK_BASE = 0x0000000f;

    private SpeechAsrDiagnostics() {
    }

    // ------------------------------------------------------------ 单项查询

    /** 目标服务是否可见（能拿到 ServiceInfo 说明 queries 声明正确）。 */
    public static boolean isSpeechServiceVisible(Context context) {
        return getSpeechServiceInfo(context) != null;
    }

    /** 语音助手是否安装（同样受包可见性影响）。 */
    public static boolean isVoiceAssistantInstalled(Context context) {
        try {
            context.getPackageManager().getApplicationInfo(
                    SpeechAsrContract.VOICE_ASSIST_PACKAGE, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 宿主是否持有某个“标识”权限。 */
    public static boolean hasPermission(Context context, String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 目标服务要求的权限（即服务上的 {@code android:permission}）。
     *
     * @return 权限名；{@code null} 表示服务不校验调用方权限，或服务不可见
     */
    public static String getRequiredServicePermission(Context context) {
        ServiceInfo info = getSpeechServiceInfo(context);
        return info != null ? info.permission : null;
    }

    private static ServiceInfo getSpeechServiceInfo(Context context) {
        try {
            ComponentName component = new ComponentName(
                    SpeechAsrContract.VOICE_ASSIST_PACKAGE,
                    SpeechAsrContract.OSBOT_SPEECH_SERVICE);
            return context.getPackageManager().getServiceInfo(component, 0);
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------ 报告

    /** 生成一份可读的自检报告，并给出结论。 */
    public static String describe(Context context) {
        StringBuilder sb = new StringBuilder("MiSpeech 环境自检\n");

        sb.append("  语音助手已安装        : ")
                .append(isVoiceAssistantInstalled(context))
                .append("  (").append(SpeechAsrContract.VOICE_ASSIST_PACKAGE).append(")\n");

        ServiceInfo service = getSpeechServiceInfo(context);
        sb.append("  语音服务可见(queries) : ").append(service != null)
                .append("  (").append(SpeechAsrContract.OSBOT_SPEECH_SERVICE).append(")\n");

        String required = service != null ? service.permission : null;
        sb.append("  目标服务要求的权限    : ")
                .append(service == null ? "未知（服务不可见）"
                        : required == null ? "无 —— 服务不校验调用方权限" : required)
                .append('\n');

        sb.append("  —— 本 App 持有的情况 ——\n");
        appendPermission(sb, context, SpeechAsrContract.PERMISSION_BIND_SPEECH_SERVICE);
        appendPermission(sb, context, SpeechAsrContract.PERMISSION_EXTERNAL_AGENT);
        appendPermission(sb, context, SpeechAsrContract.PERMISSION_QUERY_ENABLE);

        appendVoiceAssistDefinedPermissions(sb, context);
        sb.append(verdict(context, service, required));
        return sb.toString();
    }

    private static void appendPermission(StringBuilder sb, Context context, String permission) {
        boolean granted = hasPermission(context, permission);
        sb.append("  ").append(pad(permission)).append(": ")
                .append(granted ? "已授予" : "未授予")
                .append("  [").append(protectionOf(context, permission)).append("]\n");
    }

    /** 查该权限在本机的保护级别；未定义 / 不可见时说明原因。 */
    private static String protectionOf(Context context, String permission) {
        try {
            PermissionInfo info = context.getPackageManager().getPermissionInfo(permission, 0);
            return protectionName(info.protectionLevel);
        } catch (PackageManager.NameNotFoundException e) {
            // Android 11+ 的包可见性也会导致这里查不到，需与「真的没定义」区分开
            return "本机未定义或不可见";
        } catch (Throwable t) {
            return "查询失败";
        }
    }

    /** 语音助手自己声明了哪些相关权限（判断权限由谁定义、什么级别）。 */
    private static void appendVoiceAssistDefinedPermissions(StringBuilder sb, Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    SpeechAsrContract.VOICE_ASSIST_PACKAGE, PackageManager.GET_PERMISSIONS);
            PermissionInfo[] declared = info.permissions;
            if (declared == null || declared.length == 0) {
                return;
            }
            boolean headerWritten = false;
            for (PermissionInfo permission : declared) {
                if (permission == null || permission.name == null || !isRelevant(permission.name)) {
                    continue;
                }
                if (!headerWritten) {
                    sb.append("  —— 由语音助手定义的权限 ——\n");
                    headerWritten = true;
                }
                sb.append("  ").append(pad(permission.name)).append(": ")
                        .append(protectionName(permission.protectionLevel)).append('\n');
            }
        } catch (Throwable t) {
            // 拿不到就略过，不影响主结论
        }
    }

    private static boolean isRelevant(String name) {
        String lower = name.toLowerCase();
        return lower.contains("speech")
                || lower.contains("osbot")
                || lower.contains("voiceassist")
                || lower.contains("voice")
                || lower.contains("aiasst");
    }

    private static String verdict(Context context, ServiceInfo service, String required) {
        if (service == null) {
            return "结论：语音服务不可见 —— <queries> 未生效或语音助手不存在，"
                    + "bindService 必然失败。请检查 manifest 的 <queries> 声明。\n";
        }
        if (required == null) {
            return "结论：服务未要求任何权限。权限项显示“未授予”不影响调用，"
                    + "可以直接 start() 实测。\n";
        }
        if (hasPermission(context, required)) {
            return "结论：静态条件满足，可以调用。若仍失败，请看 onError 与 logcat。\n";
        }
        return "结论：服务要求权限 " + required + "，而本 App 未持有 —— 绑定会被系统拒绝。\n"
                + "      若该权限为 signature / signatureOrSystem 级，普通第三方 App 无法获得，"
                + "只能使用免权限兜底通道 VoiceAssistLauncher。\n";
    }

    private static String protectionName(int protectionLevel) {
        switch (protectionLevel & PROTECTION_MASK_BASE) {
            case PermissionInfo.PROTECTION_NORMAL:
                return "normal";
            case PermissionInfo.PROTECTION_DANGEROUS:
                return "dangerous";
            case PermissionInfo.PROTECTION_SIGNATURE:
                return "signature";
            case PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM:
                return "signatureOrSystem";
            default:
                return "0x" + Integer.toHexString(protectionLevel);
        }
    }

    private static String pad(String value) {
        if (value.length() >= 40) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value);
        while (sb.length() < 40) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
