package com.mispeech.hyperos.asr.example;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputConnection;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.mispeech.hyperos.asr.SpeechAsrConfig;
import com.mispeech.hyperos.asr.SpeechAsrDiagnostics;
import com.mispeech.hyperos.asr.SpeechAsrListener;
import com.mispeech.hyperos.asr.SpeechAsrManager;
import com.mispeech.hyperos.asr.VoiceAssistLauncher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 语音输入测试页 —— 重点演示<b>实时上屏</b>。
 *
 * <p>上屏用的就是输入法那套姿势：
 * <ul>
 *   <li>中间结果（{@code IAsrListener} 事务 1）→ {@code setComposingText}，带下划线的预编辑态，
 *       每来一次就整体替换，所以文字会边说着边"长"出来</li>
 *   <li>最终结果（事务 3）→ {@code commitText}，把预编辑内容定稿</li>
 * </ul>
 *
 * <p>注意：这是用 {@link BaseInputConnection} 直接驱动输入框的演示。
 * 真正的输入法里应当用 IME 自己的 {@code getCurrentInputConnection()}，做法完全一样。
 */
public class MainActivity extends Activity implements SpeechAsrListener {

    private static final String TAG = "MiSpeechExample";
    private static final int REQ_RECORD_AUDIO = 1001;

    /** 引擎下拉框的「自动」项：交给库按能力表与默认值解析（默认 volc）。 */
    private static final String ENGINE_AUTO = "自动（默认 volc）";

    private SpeechAsrManager asr;

    private EditText inputField;
    private InputConnection inputConnection;

    private Spinner engineSpinner;
    private ArrayAdapter<String> engineAdapter;
    private final List<String> engineOptions =
            new ArrayList<>(Arrays.asList(ENGINE_AUTO, "volc", "aliyun", "mimo"));

    private TextView statusView;
    private TextView partialView;
    private TextView finalView;
    private ProgressBar levelBar;
    private TextView logView;

    private int partialCount;
    private final StringBuilder logBuffer = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());

        asr = new SpeechAsrManager(this);

        String report = SpeechAsrDiagnostics.describe(this);
        Log.i(TAG, report);
        log("—— 环境自检 ——\n" + report);

        asr.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (asr != null) {
            asr.release();
        }
    }

    // ------------------------------------------------------------------ UI

    private View buildUi() {
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        // 实时上屏的目标输入框
        inputField = new EditText(this);
        inputField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        inputField.setHint("语音内容会实时写进这里…");
        inputField.setMinLines(2);
        inputField.setGravity(Gravity.TOP | Gravity.START);
        // 不弹系统键盘，避免和演示互相干扰
        inputField.setShowSoftInputOnFocus(false);
        root.addView(inputField, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(this);
        statusView.setText("状态：未连接");
        statusView.setTextSize(15f);
        statusView.setPadding(0, dp(8), 0, 0);
        root.addView(statusView);

        partialView = new TextView(this);
        partialView.setText("中间结果：");
        partialView.setTextSize(16f);
        root.addView(partialView);

        finalView = new TextView(this);
        finalView.setText("最终结果：");
        finalView.setTextSize(16f);
        root.addView(finalView);

        levelBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        levelBar.setMax(100);
        root.addView(levelBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 可配置引擎：连上服务后会按能力表 supported_asr_engines 动态填充
        LinearLayout engineRow = new LinearLayout(this);
        engineRow.setOrientation(LinearLayout.HORIZONTAL);
        engineRow.setGravity(Gravity.CENTER_VERTICAL);
        engineRow.setPadding(0, dp(8), 0, 0);

        TextView engineLabel = new TextView(this);
        engineLabel.setText("识别引擎：");
        engineRow.addView(engineLabel);

        engineSpinner = new Spinner(this);
        engineAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                engineOptions);
        engineAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        engineSpinner.setAdapter(engineAdapter);
        engineRow.addView(engineSpinner, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(engineRow);

        root.addView(button("① 开始识别", view -> startRecognition()));
        root.addView(button("② 停止（等最终结果）", view -> asr.stop()));
        root.addView(button("③ 取消", view -> asr.cancel()));
        root.addView(button("④ 拉起小爱语音输入浮窗（免权限兜底）",
                view -> VoiceAssistLauncher.launchFloatingInput(this)));
        root.addView(button("⑤ 申请 RECORD_AUDIO（可选）", view -> requestRecordAudio()));
        root.addView(button("⑥ 打印环境自检", view -> {
            String report = SpeechAsrDiagnostics.describe(this);
            Log.i(TAG, report);
            log("—— 环境自检 ——\n" + report);
        }));
        root.addView(button("⑦ 清空输入框", view -> inputField.setText("")));

        logView = new TextView(this);
        logView.setTextSize(12f);
        logView.setPadding(0, dp(12), 0, 0);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setOnClickListener(listener);
        return button;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    /** 当前选中的引擎；返回 null 表示「自动」。 */
    private String selectedEngine() {
        if (engineSpinner == null) {
            return null;
        }
        Object selected = engineSpinner.getSelectedItem();
        if (selected == null) {
            return null;
        }
        String value = selected.toString();
        return ENGINE_AUTO.equals(value) ? null : value;
    }

    /** 用服务端能力表刷新可选项；拿不到能力表时退回 volc / aliyun 两个常见值。 */
    private void updateEngineOptions(String[] supported) {
        String keep = selectedEngine();
        engineOptions.clear();
        engineOptions.add(ENGINE_AUTO);
        if (supported != null && supported.length > 0) {
            for (String engine : supported) {
                if (engine != null && !engine.isEmpty() && !engineOptions.contains(engine)) {
                    engineOptions.add(engine);
                }
            }
        } else {
            engineOptions.add("volc");
            engineOptions.add("aliyun");
            engineOptions.add("mimo");
        }
        if (engineAdapter != null) {
            engineAdapter.notifyDataSetChanged();
        }
        if (keep != null && engineSpinner != null) {
            int index = engineOptions.indexOf(keep);
            if (index >= 0) {
                engineSpinner.setSelection(index);
            }
        }
        log("可选引擎：" + engineOptions);
    }

    private void requestRecordAudio() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            log("RECORD_AUDIO 已授予");
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_RECORD_AUDIO) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            log("RECORD_AUDIO 申请结果：" + (granted ? "已授予" : "被拒绝"));
        }
    }

    // ------------------------------------------------------------------ 识别

    private void startRecognition() {
        partialCount = 0;
        partialView.setText("中间结果：");
        finalView.setText("最终结果：");

        // 演示实时上屏：整个会话复用同一个 InputConnection
        inputConnection = new BaseInputConnection(inputField, true);

        SpeechAsrConfig config = new SpeechAsrConfig()
                .setEngineProvider(selectedEngine())
                .setFromApp(getPackageName())
                .addHotword("超级小爱")
                .addHotword("HyperOS");
        String engine = selectedEngine();
        log("开始识别，引擎=" + (engine == null ? "自动" : engine)
                + "，热词=" + config.getHotwords().size() + " 个");
        asr.start(config, this);
    }

    // ------------------------------------------------------------------ 回调（都在主线程）

    @Override
    public void onConnected(Bundle capabilities) {
        statusView.setText("状态：已连接语音助手，能力项="
                + (capabilities == null ? 0 : capabilities.size()));
        String[] engines = asr.getSupportedAsrEngines();
        log("已连接。服务端支持的引擎=" + (engines == null ? "未知" : String.join(",", engines)));
        updateEngineOptions(engines);
    }

    @Override
    public void onReady() {
        statusView.setText("状态：正在聆听…");
        log("onReady —— 可以说话了");
    }

    @Override
    public void onPartialResult(String text) {
        partialCount++;
        statusView.setText("状态：正在聆听…（中间结果 " + partialCount + " 次）");
        partialView.setText("中间结果(" + partialCount + ")：" + text);
        // 实时上屏：预编辑态，每次整体替换
        if (inputConnection != null && text != null && !text.isEmpty()) {
            inputConnection.setComposingText(text, 1);
        }
    }

    @Override
    public void onSentenceResult(String text, int index) {
        // 分句结果：最终结果里通常已包含，这里只记录，避免重复上屏
        log("分句[" + index + "]：" + text);
    }

    @Override
    public void onFinalResult(String text, String extraJson) {
        finalView.setText("最终结果：" + text);
        log("最终结果：" + text + (extraJson == null ? "" : "  extra=" + extraJson));
        // 定稿：把预编辑内容替换成最终文本
        if (inputConnection != null && text != null && !text.isEmpty()) {
            inputConnection.commitText(text, 1);
        }
    }

    @Override
    public void onAudioLevel(float level) {
        levelBar.setProgress(Math.round(level * 100f));
    }

    @Override
    public void onError(int code, String message, String token) {
        statusView.setText("状态：出错 " + token + "(" + code + ")");
        log("onError code=" + code + " token=" + token + " message=" + message);
    }

    @Override
    public void onFinished() {
        statusView.setText("状态：会话结束");
        levelBar.setProgress(0);
        if (inputConnection != null) {
            inputConnection.finishComposingText();
            inputConnection = null;
        }
        log("onFinished");
    }

    @Override
    public void onDisconnected(String reason) {
        statusView.setText("状态：已断开");
        log("onDisconnected：" + reason);
    }

    // ------------------------------------------------------------------ 日志

    private void log(String message) {
        Log.i(TAG, message);
        logBuffer.append(message).append('\n');
        if (logView != null) {
            logView.setText(logBuffer.toString());
        }
    }
}
