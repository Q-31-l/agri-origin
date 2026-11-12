package com.agri.trace;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 农安链溯源 App 外壳：以全屏 WebView 承载已部署的 H5 站点，
 * 补齐相机、定位、文件上传、下载、返回键、断网重试等原生能力。
 */
public class MainActivity extends Activity {

    /**
     * 启动地址：直接进 H5 应用的启动页，而不是站点根目录的门户宣传页。
     * 门户页是给电脑浏览器看的介绍页，App 打开它会有很强的"网页感"，
     * 启动页则会在识别到 UA 标识后跳到 App 专属首页。
     */
    private static final String APP_URL = "https://8-130-105-150.sslip.io/pages/launch/index";
    /** 版本号同时用于 UA 标识与页面展示，发版时与 build.sh 的 VERSION_NAME 保持一致 */
    private static final String APP_VERSION = "1.0.0";
    /** UA 尾巴：前端 isAppShell() 靠它识别"运行在壳里"，浏览器与小程序的 UA 不含这段 */
    private static final String UA_TAG = "AgriTraceApp/" + APP_VERSION;
    private static final String APP_HOST = "8-130-105-150.sslip.io";
    private static final String BRAND_DEEP = "#0B3D26";
    private static final String PAGE_BG = "#F4F7F5";

    private static final int REQ_FILE = 1001;
    private static final int REQ_PERM = 1002;

    private WebView web;
    private ProgressBar bar;
    private View errorPanel;
    private ValueCallback<Uri[]> fileCallback;
    private long lastBackTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window win = getWindow();
        win.setStatusBarColor(Color.parseColor(BRAND_DEEP));
        win.setNavigationBarColor(Color.parseColor(BRAND_DEEP));
        buildUi();
        requestRuntimePermissions();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor(BRAND_DEEP));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor(PAGE_BG));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setVisibility(View.GONE);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        blp.gravity = Gravity.TOP;
        root.addView(bar, blp);

        errorPanel = buildErrorPanel();
        root.addView(errorPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        configureWebView();
        web.loadUrl(APP_URL);
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        /*
         * 在默认 UA 后追加标识。uni-app 的 H5 产物与浏览器版是同一份包，
         * 编译期无法区分，前端只能靠 UA（以及下面的 JS 桥）判断是否在 App 里，
         * 从而决定进入 App 专属首页/登录页。
         */
        s.setUserAgentString(s.getUserAgentString() + " " + UA_TAG);
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        if (Build.VERSION.SDK_INT >= 26) {
            s.setSafeBrowsingEnabled(true);
        }
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) {
            cm.setAcceptThirdPartyCookies(web, true);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                bar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    showError();
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progress < 100) {
                    bar.setVisibility(View.VISIBLE);
                    bar.setProgress(progress);
                } else {
                    bar.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    if (params != null && params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                    startActivityForResult(Intent.createChooser(intent, "选择文件"), REQ_FILE);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (request == null) return;
                        if (Build.VERSION.SDK_INT < 23
                                || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            request.grant(request.getResources());
                        } else {
                            request.deny();
                            toast("请允许相机权限后再扫码");
                        }
                    }
                });
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                           GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });

        /*
         * JS 桥：UA 被某些定制 ROM 的 WebView 设置覆盖时的兜底。
         * 前端只会读 isApp / version 两个字段，不通过它做任何写操作，
         * 所以即使页面被注入脚本也拿不到额外能力。
         */
        web.addJavascriptInterface(new JsBridge(), "AgriTraceNative");

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                if (url == null || !url.startsWith("http")) {
                    return;
                }
                try {
                    String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    req.setMimeType(mimeType);
                    req.addRequestHeader("User-Agent", userAgent);
                    req.setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    dm.enqueue(req);
                    toast("已开始下载：" + fileName);
                } catch (Exception e) {
                    openExternal(Uri.parse(url));
                }
            }
        });
    }

    private boolean handleUrl(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if ("http".equals(scheme) || "https".equals(scheme)) {
            if (host != null && (host.equals(APP_HOST) || host.endsWith(".sslip.io"))) {
                return false;
            }
            openExternal(uri);
            return true;
        }
        if ("tel".equals(scheme) || "mailto".equals(scheme) || "sms".equals(scheme) || "geo".equals(scheme)) {
            openExternal(uri);
            return true;
        }
        return false;
    }

    private void openExternal(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private View buildErrorPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor(PAGE_BG));
        box.setVisibility(View.GONE);

        TextView title = new TextView(this);
        title.setText("网络连接不可用");
        title.setTextSize(17f);
        title.setTextColor(Color.parseColor("#1F2D26"));
        title.setGravity(Gravity.CENTER);

        TextView desc = new TextView(this);
        desc.setText("请检查手机网络后重试");
        desc.setTextSize(13f);
        desc.setTextColor(Color.parseColor("#6B7B72"));
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(8), 0, dp(20));

        Button retry = new Button(this);
        retry.setText("重新加载");
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                errorPanel.setVisibility(View.GONE);
                web.loadUrl(APP_URL);
            }
        });

        box.addView(title);
        box.addView(desc);
        box.addView(retry);
        return box;
    }

    private void showError() {
        bar.setVisibility(View.GONE);
        errorPanel.setVisibility(View.VISIBLE);
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        java.util.ArrayList<String> need = new java.util.ArrayList<String>();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.READ_MEDIA_IMAGES") != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.READ_MEDIA_IMAGES");
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[need.size()]), REQ_PERM);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (fileCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(results);
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (web != null && web.canGoBack()) {
                web.goBack();
                return true;
            }
            long now = System.currentTimeMillis();
            if (now - lastBackTime < 2000L) {
                finish();
                return true;
            }
            lastBackTime = now;
            toast("再按一次退出");
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** 暴露给页面脚本的只读标识，命名直白，便于前端一眼看懂 */
    public class JsBridge {
        @android.webkit.JavascriptInterface
        public boolean isApp() {
            return true;
        }

        @android.webkit.JavascriptInterface
        public String version() {
            return APP_VERSION;
        }
    }
}
