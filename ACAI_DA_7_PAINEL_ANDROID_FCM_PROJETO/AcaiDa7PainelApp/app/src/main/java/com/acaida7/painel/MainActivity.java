package com.acaida7.painel;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends ComponentActivity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private boolean triedBundledFallback = false;
    private long updateDownloadId = -1L;
    private final Handler updateHandler = new Handler();

    public static final String CHANNEL_ID = "novos_pedidos_v2";
    private static final String FCM_TOPIC = "novos_pedidos";

    // Painel oficial online: mudanças de HTML/CSS/JS aparecem sem reinstalar o APK.
    private static final String PANEL_URL = "https://acaida7-painel.netlify.app/";

    // Release "latest" do GitHub contém o APK nativo mais recente.
    private static final String UPDATE_API_URL =
            "https://api.github.com/repos/thiagobcardoso9-droid/acai-da-7-painel-android/releases/latest";
    private static final String UPDATE_ASSET_NAME = "AcaiDa7-Painel-APK.apk";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        requestNotificationPermission();

        webView = new WebView(this);
        configureWebView(webView);
        setContentView(webView, new ViewGroup.LayoutParams(-1, -1));
        loadPanel();

        subscribeToNewOrders();
        handleIntent(getIntent());

        // Verifica silenciosamente se existe uma nova versão nativa.
        checkForNativeUpdate();
    }

    private void loadPanel() {
        if (webView == null) return;
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        triedBundledFallback = false;
        webView.loadUrl(PANEL_URL + "?app=android");
    }

    private void requestNotificationPermission() {
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) subscribeToNewOrders();
                });

        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void subscribeToNewOrders() {
        FirebaseMessaging.getInstance().subscribeToTopic(FCM_TOPIC)
                .addOnSuccessListener(unused -> getPreferences(MODE_PRIVATE)
                        .edit().putBoolean("fcm_topic_ok", true).apply())
                .addOnFailureListener(e -> getPreferences(MODE_PRIVATE)
                        .edit().putBoolean("fcm_topic_ok", false).apply());

        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token ->
                getPreferences(MODE_PRIVATE).edit().putString("fcm_token", token).apply());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
            subscribeToNewOrders();
        }
    }

    private void configureWebView(WebView wv) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        wv.addJavascriptInterface(new NativeBridge(this), "NativeBridge");
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) loadBundledFallback();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                if (request.isForMainFrame() && errorResponse.getStatusCode() >= 400) {
                    loadBundledFallback();
                }
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), 1001);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
    }

    private void loadBundledFallback() {
        if (webView == null || triedBundledFallback) return;
        triedBundledFallback = true;
        webView.postDelayed(() -> {
            if (webView != null) {
                webView.loadUrl("file:///android_asset/index.html");
                Toast.makeText(this, "Painel online indisponível. Usando painel local.", Toast.LENGTH_LONG).show();
            }
        }, 250);
    }

    private boolean handleUrl(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();

        if (host != null && host.equals("acaida7-painel.netlify.app")) return false;
        if (url.startsWith("file://")) return false;
        return openExternal(url);
    }

    private boolean openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir o link", Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private void checkForNativeUpdate() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(UPDATE_API_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "AcaiDa7-Painel-Android");

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return;

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject release = new JSONObject(response.toString());
                String body = release.optString("body", "");
                Matcher versionMatcher = Pattern.compile("VERSION_NAME=([^\\s\\n]+)").matcher(body);
                if (!versionMatcher.find()) return;

                String latestVersion = versionMatcher.group(1).trim();
                String currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                if (!isNewerVersion(latestVersion, currentVersion)) return;

                JSONArray assets = release.optJSONArray("assets");
                if (assets == null) return;

                String apkUrl = null;
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if (UPDATE_ASSET_NAME.equals(asset.optString("name"))) {
                        apkUrl = asset.optString("browser_download_url", null);
                        break;
                    }
                }
                if (apkUrl == null || apkUrl.isEmpty()) return;

                final String downloadUrl = apkUrl;
                runOnUiThread(() -> showUpdateDialog(latestVersion, downloadUrl));
            } catch (Exception ignored) {
                // Falha na verificação não impede o painel de funcionar.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private boolean isNewerVersion(String latest, String current) {
        try {
            String[] a = latest.replaceAll("[^0-9.]", "").split("\\.");
            String[] b = current.replaceAll("[^0-9.]", "").split("\\.");
            int max = Math.max(a.length, b.length);
            for (int i = 0; i < max; i++) {
                int av = i < a.length && !a[i].isEmpty() ? Integer.parseInt(a[i]) : 0;
                int bv = i < b.length && !b[i].isEmpty() ? Integer.parseInt(b[i]) : 0;
                if (av != bv) return av > bv;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void showUpdateDialog(String latestVersion, String downloadUrl) {
        new AlertDialog.Builder(this)
                .setTitle("🔄 Nova atualização disponível")
                .setMessage("Açaí da 7\n\nNova versão: " + latestVersion + "\n\nAtualize para receber melhorias e correções.")
                .setNegativeButton("Depois", null)
                .setPositiveButton("ATUALIZAR AGORA", (dialog, which) -> startApkDownload(downloadUrl))
                .setCancelable(true)
                .show();
    }

    private void startApkDownload(String downloadUrl) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("Permitir atualização")
                    .setMessage("Para atualizar o Açaí da 7, permita que este aplicativo instale atualizações. Depois volte para o app e toque em Atualizar novamente.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("ABRIR CONFIGURAÇÃO", (d, w) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                        }
                    })
                    .show();
            return;
        }

        try {
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("Atualização Açaí da 7");
            request.setDescription("Baixando a nova versão do painel...");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, UPDATE_ASSET_NAME);

            updateDownloadId = manager.enqueue(request);
            Toast.makeText(this, "Baixando atualização...", Toast.LENGTH_LONG).show();
            waitForDownload(manager, updateDownloadId);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível baixar a atualização.", Toast.LENGTH_LONG).show();
        }
    }

    private void waitForDownload(DownloadManager manager, long id) {
        updateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
                android.database.Cursor cursor = manager.query(query);
                if (cursor == null) return;
                try {
                    if (cursor.moveToFirst()) {
                        int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            Uri uri = manager.getUriForDownloadedFile(id);
                            if (uri != null) installApk(uri);
                            return;
                        }
                        if (status == DownloadManager.STATUS_FAILED) {
                            Toast.makeText(MainActivity.this, "Falha ao baixar a atualização.", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                } finally {
                    cursor.close();
                }
                updateHandler.postDelayed(this, 1000);
            }
        }, 700);
    }

    private void installApk(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir o instalador.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || webView == null) return;
        if (intent.hasExtra("pedido_id")) {
            webView.postDelayed(() -> webView.evaluateJavascript(
                    "try{if(typeof showOrders==='function'){showOrders();}}catch(e){}", null), 1000);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID);
            if (existing != null) return;

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Novos pedidos",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alertas de novos pedidos do Açaí da 7");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 300, 150, 500, 150, 700});
            channel.setSound(
                    Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.novo_pedido),
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
            nm.createNotificationChannel(channel);
        }
    }

    public String getFcmToken() {
        return getPreferences(MODE_PRIVATE).getString("fcm_token", "");
    }

    public static class NativeBridge {
        private final MainActivity activity;
        NativeBridge(MainActivity activity) { this.activity = activity; }
        @android.webkit.JavascriptInterface public String getFcmToken() { return activity.getFcmToken(); }
        @android.webkit.JavascriptInterface public String isNativeApp() { return "true"; }
        @android.webkit.JavascriptInterface public void showToast(String text) {
            activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
        }
    }
}
