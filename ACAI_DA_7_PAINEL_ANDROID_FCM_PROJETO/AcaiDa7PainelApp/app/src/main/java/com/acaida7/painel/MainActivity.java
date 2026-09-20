package com.acaida7.painel;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

public class MainActivity extends ComponentActivity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    // Canal V2: usar um ID novo garante que aparelhos que já criaram o canal antigo
    // não mantenham configurações antigas de som/silêncio.
    public static final String CHANNEL_ID = "novos_pedidos_v2";
    private static final String FCM_TOPIC = "novos_pedidos";

    // Painel oficial online. Alterações de HTML/CSS/JS publicadas no Netlify
    // passam a aparecer no APK sem precisar gerar/reinstalar outro APK.
    private static final String PANEL_URL = "https://acaida7-painel.netlify.app/";

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
    }

    private void loadPanel() {
        if (webView == null) return;
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.loadUrl(PANEL_URL);
    }

    private void requestNotificationPermission() {
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        subscribeToNewOrders();
                    }
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
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }
        });
        wv.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
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

    private boolean handleUrl(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();

        // Mantém a navegação do próprio painel dentro do APK.
        if (url.startsWith(PANEL_URL) ||
                (host != null && host.equals("acaida7-painel.netlify.app"))) {
            return false;
        }

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
