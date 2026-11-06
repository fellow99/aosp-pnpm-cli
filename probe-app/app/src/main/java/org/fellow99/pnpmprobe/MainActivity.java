package org.fellow99.pnpmprobe;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * pnpm-cli 探针载体（specs/003-cli/test-cases.md TC-C00…C16 的 App 内可执行子集）。
 *
 * 职责（镜像 docs/000-工程规划.md §3 消费方环境引导层 + §7 环境变量契约）：
 *  1) 物化：无——D8 纯二进制 AAR（pnpm 本体即命令入口，无 assets/壳脚本），
 *     libpnpm_cli.so 由容器 AAR 随 APK 落 nativeLibraryDir（D1 useLegacyPackaging）；
 *  2) 注入：HOME/TMPDIR/XDG_DATA_HOME/PATH/LD_LIBRARY_PATH（规划 §7 表；
 *     store 定案推荐案 b（config set store-dir，R5），案 a（XDG）作对照探针）；
 *  3) 执行：ProcessBuilder exec nativeLibraryDir/libpnpm_cli.so（非 PTY 子集；
 *     PTY 语义归终端 App，规划 §7.1 P1 完整形态留消费方联调）；
 *  4) 判据：TC-C00…C16 逐条 PASS/FAIL/BLOCKED + JSON 报告（filesDir/probe-report.json）。
 *
 * 对照 python-cli probe-app 的差异：无 assets 物化步骤；新增 sha256 前后比对
 * （TC-C10 P5 红线：self-update 本体不可被覆盖）；探测顺序按状态依赖编排
 * （案 a 先于 config set，案 b 后于 config set）。
 */
public class MainActivity extends Activity {

    private TextView logView;
    private File homeDir, tmpDir, xdgDir, workDir, storeDir, initDir;
    private String nativeDir;   // applicationInfo.nativeLibraryDir
    private String pnpmExe;     // nativeDir/libpnpm_cli.so
    private String soShaBefore; // TC-C10 前后比对基准
    private final StringBuilder uiLog = new StringBuilder();
    private final List<JSONObject> report = new ArrayList<>();
    private boolean ran = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nativeDir = getApplicationInfo().nativeLibraryDir;
        pnpmExe = nativeDir + "/libpnpm_cli.so";
        homeDir = new File(getFilesDir(), "home");
        tmpDir = new File(getFilesDir(), "tmp");
        xdgDir = new File(getFilesDir(), "xdg");
        workDir = new File(getFilesDir(), "work");
        storeDir = new File(getFilesDir(), "store");
        initDir = new File(getFilesDir(), "init-probe");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Button run = new Button(this);
        run.setText("RUN ALL PROBES");
        run.setOnClickListener(v -> runAll());
        ScrollView scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setTextSize(11f);
        int pad = (int) (getResources().getDisplayMetrics().density * 8);
        logView.setPadding(pad, pad, pad, pad);
        scroll.addView(logView);
        root.addView(run, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        log("probe-app ready\nnativeDir=" + nativeDir
                + "\n被测 pnpm 12.16.1（D8 纯二进制：无 assets 物化步骤）\n"
                + "启动 1s 后自动执行全部探针（--ez autorun true 无头触发，亦可点按钮）");
        // 无头触发（统一测试队列，对齐 aosp-git-cli）：adb shell am start ... --ez autorun true
        // 缺省行为保留：启动 1s 后自动执行全部探针（am start 即可无人值守跑完，pull 报告即可）
        Intent in = getIntent();
        boolean autorun = in != null && in.getBooleanExtra("autorun", false);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!ran) runAll();
        }, autorun ? 0 : 1000);
    }

    private void log(String s) {
        runOnUiThread(() -> {
            uiLog.append(s).append('\n');
            logView.setText(uiLog.toString());
        });
    }

    // ── 环境注入（规划 §7）与执行器 ─────────────────────────────────────────────

    private Map<String, String> baseEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("HOME", homeDir.getAbsolutePath());
        env.put("TMPDIR", tmpDir.getAbsolutePath());
        env.put("XDG_DATA_HOME", xdgDir.getAbsolutePath()); // 案 a 兜底（案 b 由 config set 覆盖，R5）
        env.put("PATH", nativeDir + ":" + new File(homeDir, "bin") + ":/system/bin");
        return env;
    }

    static class Out {
        String stdout = "", stderr = "";
        int exit = -1;
        long ms;
        boolean timedOut;
        String failReason; // 启动失败等
    }

    /** 递归删除（探针可重复性：清上一轮 run 落盘的残留状态）。 */
    private void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] c = f.listFiles();
            if (c != null) for (File x : c) deleteRecursive(x);
        }
        f.delete();
    }

    private Out exec(List<String> argv, Map<String, String> envIn, File cwd, long timeoutMs) {
        Out o = new Out();
        long t0 = System.currentTimeMillis();
        try {
            homeDir.mkdirs(); tmpDir.mkdirs(); xdgDir.mkdirs(); workDir.mkdirs();
            ProcessBuilder pb = new ProcessBuilder(argv);
            pb.directory(cwd != null ? cwd : workDir);
            pb.redirectErrorStream(false);
            Map<String, String> env = pb.environment();
            env.clear();
            env.putAll(envIn);
            env.put("ANDROID_ROOT", "/system");
            env.put("ANDROID_DATA", "/data");
            env.put("LD_LIBRARY_PATH", nativeDir); // exec 子进程默认 linker namespace（规划 §7）
            Process p = pb.start();
            p.getOutputStream().close();
            StringBuilder so = new StringBuilder(), se = new StringBuilder();
            Thread t1 = pump(p.getInputStream(), so), t2 = pump(p.getErrorStream(), se);
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                o.timedOut = true;
            }
            t1.join(3000);
            t2.join(3000);
            o.stdout = so.toString();
            o.stderr = se.toString();
            o.exit = p.exitValue();
        } catch (Exception e) {
            o.failReason = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        o.ms = System.currentTimeMillis() - t0;
        return o;
    }

    private Thread pump(java.io.InputStream is, StringBuilder sb) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            } catch (Exception ignored) {
            }
        });
        t.start();
        return t;
    }

    // ── 工具 ───────────────────────────────────────────────────────────────────

    private List<String> pn(String... args) {
        List<String> a = new ArrayList<>();
        a.add(pnpmExe);
        for (String s : args) a.add(s);
        return a;
    }

    private static Map<String, String> envKv(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @SafeVarargs
    private static <T> List<T> arr(T... items) {
        List<T> l = new ArrayList<>();
        for (T t : items) l.add(t);
        return l;
    }

    private static String head(String s) { return head(s, 160); }

    private static String head(String s, int n) {
        if (s == null) return "";
        s = s.replace("\n", "\\n");
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    private String sha256(File f) {
        try (FileInputStream is = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = new byte[65536];
            int r;
            while ((r = is.read(b)) > 0) md.update(b, 0, r);
            StringBuilder sb = new StringBuilder();
            for (byte x : md.digest()) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return "SHA-ERR:" + e.getMessage();
        }
    }

    // ── 探针定义（对照 specs/003-cli/test-cases.md）────────────────────────────

    interface Check {
        String check(Out o); // null=PASS；否则失败原因；"BLOCKED:"/"PASS-"/"RECORD:" 开头有特判
    }

    static class Probe {
        final String id, title;
        final List<String> argv;
        final Map<String, String> envOverride; // 覆盖/删除基线（value null/""=删除）
        final long timeoutMs;
        final Check check;
        File cwd; // null=workDir
        Probe(String id, String title, List<String> argv, Map<String, String> envOverride,
              long timeoutMs, Check check) {
            this.id = id; this.title = title; this.argv = argv;
            this.envOverride = envOverride; this.timeoutMs = timeoutMs; this.check = check;
        }
        Probe cwd(File d) { this.cwd = d; return this; }
    }

    private List<Probe> probes() {
        List<Probe> l = new ArrayList<>();
        File so = new File(pnpmExe);

        // ── 载体与环境 ──
        l.add(new Probe("TC-C00", "载体就绪（NLDIR 物理落盘 + 唯一原生件）",
                null, null, 5_000,
                o -> {
                    // argv=null：纯文件面检查（不入 exec）
                    if (!so.isFile()) return "本体缺失: " + pnpmExe;
                    if (so.length() != 47317088L) return "size=" + so.length() + "（期望 47317088=lock 落锁值）";
                    String[] nl = new File(nativeDir).list();
                    int soCount = 0;
                    if (nl != null) for (String f : nl) if (f.endsWith(".so")) soCount++;
                    if (soCount != 1) return "NLDIR .so 数=" + soCount + "（期望 1：兄弟文件唯一）";
                    return null;
                }));
        l.add(new Probe("TC-C00b", "exec 权限语义（裸环境直启，无 HOME/TMPDIR 注入）",
                pn("--version"),
                envKv("HOME", "", "TMPDIR", "", "XDG_DATA_HOME", ""),
                30_000,
                o -> o.exit == 0 && o.stdout.contains("12.16.1") ? null
                        : "exit=" + o.exit + " out=" + head(o.stdout) + " err=" + head(o.stderr)));

        // ── 基础行为（无网络）──
        l.add(new Probe("TC-C01", "版本回程（P1）：--version 与 -v",
                pn("--version"), null, 30_000,
                o -> o.exit == 0 && o.stdout.trim().equals("12.16.1") ? null
                        : "exit=" + o.exit + " out=" + head(o.stdout)));
        l.add(new Probe("TC-C01b", "-v 短旗标等价",
                pn("-v"), null, 30_000,
                o -> o.exit == 0 && o.stdout.trim().equals("12.16.1") ? null
                        : "exit=" + o.exit + " out=" + head(o.stdout)));
        l.add(new Probe("TC-C02", "--help 用法输出",
                pn("--help"), null, 30_000,
                o -> o.exit == 0 && o.stdout.contains("Usage") ? null
                        : "exit=" + o.exit + " out=" + head(o.stdout)));
        l.add(new Probe("TC-C03", "未知子命令：exit≠0 且诊断走 stderr",
                pn("definitely-not-a-command"), null, 30_000,
                o -> o.exit != 0 && o.stderr.length() > 0 ? null
                        : "exit=" + o.exit + " err=" + head(o.stderr)));
        l.add(new Probe("TC-C04", "UTF-8 回程（sh 脚本 echo 中文/emoji）",
                pn("run", "utf8"), null, 60_000,
                o -> o.exit == 0 && o.stdout.contains("你好世界🦆") ? null
                        : "exit=" + o.exit + " out=" + head(o.stdout) + " err=" + head(o.stderr)));
        l.add(new Probe("TC-C07", "init 生成合法 package.json（独立目录）",
                pn("init"), null, 60_000,
                o -> {
                    if (o.exit != 0) return "exit=" + o.exit + " err=" + head(o.stderr);
                    File pj = new File(initDir, "package.json");
                    if (!pj.isFile()) return "package.json 未生成: " + pj;
                    try {
                        String s = new String(java.nio.file.Files.readAllBytes(pj.toPath()),
                                StandardCharsets.UTF_8);
                        if (!s.contains("\"name\"")) return "package.json 缺 name 字段: " + head(s);
                    } catch (Exception e) { return "读 package.json 失败: " + e.getMessage(); }
                    return null;
                }).cwd(initDir));

        // ── store 与目录契约（P3；顺序依赖：案 a 先于 config set）──
        l.add(new Probe("TC-C08a", "store 落点案 a（XDG_DATA_HOME 兜底）",
                pn("store", "path"), null, 30_000,
                o -> o.exit == 0 && o.stdout.trim().startsWith(xdgDir.getAbsolutePath())
                        ? null : "exit=" + o.exit + " out=" + head(o.stdout)));
        l.add(new Probe("TC-C09", "HOME 基座：config set store-dir（案 b）+ config.yaml 落点",
                pn("config", "set", "store-dir", storeDir.getAbsolutePath()),
                null, 30_000,
                o -> {
                    if (o.exit != 0) return "exit=" + o.exit + " err=" + head(o.stderr);
                    File cfg = new File(homeDir, ".config/pnpm/config.yaml");
                    if (!cfg.isFile()) return "config.yaml 未落 HOME 基座: " + cfg;
                    return null;
                }));
        l.add(new Probe("TC-C08b", "store 落点案 b（store-dir 持久化 + store path 回读）",
                pn("store", "path"), null, 30_000,
                o -> o.exit == 0 && o.stdout.trim().startsWith(storeDir.getAbsolutePath())
                        ? null : "exit=" + o.exit + " out=" + head(o.stdout)));

        // ── 网络与安装（P7 / P2）──
        l.add(new Probe("TC-C05", "TLS install 纯 JS 包（P7）：pnpm add left-pad",
                pn("add", "left-pad"), null, 300_000,
                o -> {
                    if (o.exit != 0) return "exit=" + o.exit + " err=" + head(o.stderr);
                    if (!new File(workDir, "node_modules/left-pad").isDirectory())
                        return "node_modules/left-pad 缺失（TLS/store 链路不完整）";
                    return null;
                }));
        l.add(new Probe("TC-C06a", "lifecycle 完整回程（P2①）：需 PATH 上 node",
                null, null, 5_000,
                o -> "BLOCKED:node 缺席（aosp-node-cli 未首产，libnode_cli.so 为占位残品）——完整回程待 node 产物"));
        l.add(new Probe("TC-C06b", "lifecycle 无预授权拦截（P2②/L1 增强）：core-js 默认拦截",
                pn("add", "core-js"), null, 300_000,
                o -> o.exit != 0 && (o.stderr.contains("IGNORED_BUILDS") || o.stdout.contains("IGNORED_BUILDS"))
                        ? null // 预期拦截：配置层阻断（非静默）＝通过
                        : "意外形态：exit=" + o.exit + " out=" + head(o.stdout) + " err=" + head(o.stderr)));
        l.add(new Probe("TC-C14", "平台二进制包行为（L3）：esbuild postinstall 被默认拦截＝非静默",
                pn("add", "esbuild"), null, 300_000,
                o -> (o.stderr.contains("IGNORED_BUILDS") || o.stdout.contains("IGNORED_BUILDS"))
                        ? null // 拦截生效＝C5 红线未被静默突破
                        : o.exit == 0 ? "FAIL-静默装成功且构建放行（C5 红线！）out=" + head(o.stdout)
                        : "exit=" + o.exit + " err=" + head(o.stderr)));

        // ── 红线与边界 ──
        l.add(new Probe("TC-C10", "self-update 红线（P5）：本体不可被覆盖（sha256 前后一致）",
                pn("self-update"), null, 180_000,
                o -> {
                    String after = sha256(so);
                    boolean unchanged = after.equals(soShaBefore);
                    // P5 实测口径：工具不物理失败（可能 exit 0 下载/已最新），判据＝本体 sha256 不变
                    return unchanged ? null : "本体被改写！before=" + soShaBefore + " after=" + after;
                }));
        l.add(new Probe("TC-C11", "dlx 行为记录（P4）：cowsay 需 PATH 上 node",
                pn("dlx", "cowsay", "hi"), null, 180_000,
                o -> "RECORD:" + (o.timedOut ? "TIMEOUT" : "exit=" + o.exit)
                        + " out=" + head(o.stdout, 120) + " err=" + head(o.stderr, 120)));
        l.add(new Probe("TC-C15", "16KB 页设备 exec（P6）",
                null, null, 15_000,
                o -> "BLOCKED:无 16KB 页设备/镜像（产物 min LOAD align=0x4000 已由门禁⑦保证）"));
        l.add(new Probe("TC-C16", "翻译层事实记录（x86_64 跑 arm64）",
                arr("/system/bin/getprop", "ro.product.cpu.abilist"), null, 15_000,
                o -> "RECORD:abilist=" + head(o.stdout.trim(), 80)));

        return l;
    }

    // ── 主流程 ─────────────────────────────────────────────────────────────────

    private void runAll() {
        if (ran) { log("已在运行/已运行"); return; }
        ran = true;
        new Thread(() -> {
            try {
                report.clear();
                File so = new File(pnpmExe);
                soShaBefore = sha256(so);
                log("── 前置准备 ──");
                log("本体 sha256=" + soShaBefore);
                // 状态清理（探针可重复性）：adb install -r 不清 App 数据，上一轮 run 的落盘状态会
                // 残留——TC-C07 的 init 目录 package.json（ERR_PNPM_PACKAGE_JSON_EXISTS）、TC-C09
                // 持久化的 store-dir 配置（污染 TC-C08a 案 a 的 XDG 兜底判据）、TC-C05/C06b/C14 的
                // node_modules 等。每题开头清空 ⇒ 每轮从确定性状态起跑。
                for (File d : new File[]{homeDir, tmpDir, xdgDir, storeDir, initDir, workDir}) {
                    deleteRecursive(d);
                }
                // TC-C04 前置：work/package.json（中文 description + utf8 脚本）
                workDir.mkdirs();
                try (java.io.OutputStream os = new java.io.FileOutputStream(new File(workDir, "package.json"))) {
                    os.write(("{\"name\":\"pnpm-probe\",\"version\":\"1.0.0\","
                            + "\"description\":\"探针中文描述🦆\","
                            + "\"scripts\":{\"utf8\":\"echo 你好世界🦆\"}}")
                            .getBytes(StandardCharsets.UTF_8));
                }
                // TC-C07 前置：独立 init 目录（避免覆盖 work/package.json）
                initDir.mkdirs();

                int pass = 0, fail = 0, blocked = 0;
                for (Probe p : probes()) {
                    log("── " + p.id + " " + p.title);
                    String verdict;
                    long ms = 0;
                    Out o = new Out();
                    if (p.argv == null) {
                        // 纯检查型探针（TC-C00/06a/15）：不 exec
                        verdict = p.check.check(o);
                    } else {
                        Map<String, String> env = baseEnv();
                        if (p.envOverride != null) {
                            for (Map.Entry<String, String> e : p.envOverride.entrySet()) {
                                if (e.getValue() == null || e.getValue().isEmpty()) env.remove(e.getKey());
                                else env.put(e.getKey(), e.getValue());
                            }
                        }
                        o = exec(p.argv, env, p.cwd, p.timeoutMs);
                        ms = o.ms;
                        // 统一交 check 判定；预期失败类不得在 runner 层抢先判 FAIL
                        verdict = p.check.check(o);
                    }
                    String tag = verdict == null ? "PASS"
                            : verdict.startsWith("BLOCKED") ? "BLOCKED"
                            : verdict.startsWith("PASS-") || verdict.startsWith("RECORD:") ? "PASS"
                            : verdict.startsWith("FAIL-") ? "FAIL"
                            : "FAIL";
                    if ("PASS".equals(tag)) pass++;
                    else if ("BLOCKED".equals(tag)) blocked++;
                    else fail++;
                    JSONObject j = new JSONObject();
                    try {
                        j.put("id", p.id).put("title", p.title).put("verdict", tag)
                                .put("detail", verdict == null ? "" : verdict)
                                .put("exit", o.exit).put("ms", ms).put("timedOut", o.timedOut)
                                .put("stdout", head(o.stdout, 800)).put("stderr", head(o.stderr, 800));
                    } catch (org.json.JSONException ignored) {
                    }
                    report.add(j);
                    log(String.format("%-9s %-8s %s", p.id, tag, p.title)
                            + (verdict == null ? "" : "  | " + head(verdict, 220)));
                }
                log(String.format("── 汇总：PASS=%d FAIL=%d BLOCKED=%d ──", pass, fail, blocked));
                JSONObject out = new JSONObject();
                out.put("runAt", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",
                        java.util.Locale.ROOT).format(new java.util.Date()));
                out.put("device", android.os.Build.MODEL + " " + android.os.Build.VERSION.RELEASE
                        + " sdk=" + android.os.Build.VERSION.SDK_INT
                        + " abi=" + android.os.Build.SUPPORTED_ABIS[0]);
                out.put("nativeDir", nativeDir);
                out.put("soSha256", soShaBefore);
                out.put("probes", new JSONArray(report));
                File f = new File(getFilesDir(), "probe-report.json");
                try (java.io.FileOutputStream os = new java.io.FileOutputStream(f)) {
                    os.write(out.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                log("报告：" + f + "（adb pull 取回）");
            } catch (Exception e) {
                log("FATAL " + e);
            }
        }).start();
    }
}
