package com.vangpt.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

// VanGPT — aplikasi web-to-app: membungkus situs VanGPT dalam WebView.
// V1.1: jembatan WebView dilengkapi dua hal yang sebelumnya hilang —
// (1) onShowFileChooser agar <input type=file> (lampiran) benar-benar membuka
// pemilih file, dan (2) onPermissionRequest + izin RECORD_AUDIO agar halaman
// boleh memakai mikrofon (input suara). Tanpa keduanya fitur mati di aplikasi
// walau di browser jalan.
public class MainActivity extends Activity {
    private WebView webView;
    private boolean showingError;

    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_MIC_PERMISSION = 1002;

    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingPermissionRequest;

    private static final String ERROR_HTML =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'>" +
            "<style>body{background:#050505;color:#e0e0e0;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center}" +
            "a{display:inline-block;margin-top:18px;background:#b30000;color:#fff;padding:14px 28px;border-radius:12px;text-decoration:none;font-weight:bold}</style></head>" +
            "<body><div><div style='font-size:44px'>&#128225;</div><h2>Koneksi terputus</h2>" +
            "<p>VanGPT butuh internet untuk menyala.</p>" +
            "<a href='" + BuildConfig.WEB_APP_URL + "'>COBA LAGI</a></div></body></html>";

    private boolean micIzinAda() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.rgb(5, 5, 5));
            getWindow().setNavigationBarColor(Color.rgb(5, 5, 5));

            webView = new WebView(this);
            webView.setBackgroundColor(Color.rgb(5, 5, 5));
            setContentView(webView);

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(true);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setUserAgentString(settings.getUserAgentString() + " VanGPTAndroid/1.1");

            webView.setWebChromeClient(new WebChromeClient() {
                // Lampiran: buka pemilih file sistem untuk <input type=file>.
                @Override
                public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                    if (fileCallback != null) fileCallback.onReceiveValue(null);
                    fileCallback = callback;
                    try {
                        Intent intent;
                        try {
                            intent = params.createIntent();
                        } catch (Exception e) {
                            intent = new Intent(Intent.ACTION_GET_CONTENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("*/*");
                        }
                        startActivityForResult(intent, REQ_FILE_CHOOSER);
                        return true;
                    } catch (Exception e) {
                        fileCallback = null;
                        return false;
                    }
                }

                // Mikrofon: teruskan izin Android ke halaman web.
                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    boolean mintaAudio = false;
                    for (String res : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) mintaAudio = true;
                    }
                    if (!mintaAudio) {
                        request.deny();
                        return;
                    }
                    if (micIzinAda()) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    } else {
                        pendingPermissionRequest = request;
                        if (Build.VERSION.SDK_INT >= 23) {
                            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC_PERMISSION);
                        } else {
                            request.deny();
                            pendingPermissionRequest = null;
                        }
                    }
                }

                @Override
                public void onPermissionRequestCanceled(PermissionRequest request) {
                    if (pendingPermissionRequest == request) pendingPermissionRequest = null;
                }
            });

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    Uri uri = request.getUrl();
                    String host = uri.getHost();
                    if (host != null && host.equals("irvan1406.github.io")) return false;
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        return true;
                    } catch (Exception ignored) {
                        return false;
                    }
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (url.startsWith("https://")) showingError = false;
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame() && !showingError) {
                        showingError = true;
                        view.loadDataWithBaseURL(null, ERROR_HTML, "text/html", "UTF-8", null);
                    }
                }
            });

            // Minta izin mikrofon sekali di awal (boleh ditolak; halaman tetap jalan).
            if (!micIzinAda() && Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC_PERMISSION);
            }

            webView.loadUrl(BuildConfig.WEB_APP_URL);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC_PERMISSION && pendingPermissionRequest != null) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            try {
                if (granted) {
                    pendingPermissionRequest.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    pendingPermissionRequest.deny();
                }
            } catch (Exception ignored) {}
            pendingPermissionRequest = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER) {
            if (fileCallback == null) return;
            Uri[] hasil = null;
            if (resultCode == RESULT_OK && data != null) {
                ClipData clip = data.getClipData();
                if (clip != null && clip.getItemCount() > 0) {
                    hasil = new Uri[clip.getItemCount()];
                    for (int i = 0; i < clip.getItemCount(); i++) hasil[i] = clip.getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    hasil = new Uri[]{data.getData()};
                }
            }
            try { fileCallback.onReceiveValue(hasil); } catch (Exception ignored) {}
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        try {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
            } else {
                super.onBackPressed();
            }
        } catch (Exception e) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (webView != null) webView.destroy();
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
