package com.acaida7.painel;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class AcaiMessagingService extends FirebaseMessagingService {
    private static final String FCM_TOPIC = "novos_pedidos";
    private static final String PREFS_NATIVE = "native_prefs";
    private static final String PREF_SOUND = "notification_sound";
    private static final String CHANNEL_PREFIX = "novos_pedidos_tone_";

    @Override
    public void onNewToken(@NonNull String token) {
        getSharedPreferences("fcm", MODE_PRIVATE).edit().putString("token", token).apply();
        FirebaseMessaging.getInstance().subscribeToTopic(FCM_TOPIC);
    }

    private int getSelectedSound() {
        int n = getSharedPreferences(PREFS_NATIVE, MODE_PRIVATE).getInt(PREF_SOUND, 1);
        return Math.max(1, Math.min(50, n));
    }

    private int getSoundFromMessage(RemoteMessage message) {
        String value = message.getData().get("sound_id");
        if (value == null) value = message.getData().get("tone_id");
        if (value != null) {
            try {
                int n = Integer.parseInt(value.trim());
                if (n >= 1 && n <= 50) {
                    // The server is the source of truth so computer and phone can share
                    // the same selected sound. Cache it locally for the next notification.
                    getSharedPreferences(PREFS_NATIVE, MODE_PRIVATE).edit().putInt(PREF_SOUND, n).apply();
                    return n;
                }
            } catch (Exception ignored) { }
        }
        return getSelectedSound();
    }

    private String channelId(int sound) {
        return CHANNEL_PREFIX + String.format(java.util.Locale.US, "%02d", sound);
    }

    private int soundResId(int number) {
        String name = String.format(java.util.Locale.US, "toque_%02d", Math.max(1, Math.min(50, number)));
        return getResources().getIdentifier(name, "raw", getPackageName());
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        int selectedSound = getSoundFromMessage(message);
        createNotificationChannel(selectedSound);

        String title = message.getData().get("title");
        if (title == null || title.trim().isEmpty()) title = "🍧 Açaí da 7 — NOVO PEDIDO!";

        String body = message.getData().get("body");
        if (body == null || body.trim().isEmpty()) {
            String numero = message.getData().get("numero");
            String total = message.getData().get("total");
            body = "Pedido #" + (numero == null ? "" : numero)
                    + (total == null || total.isEmpty() ? "" : " • R$ " + total)
                    + " recebido. Toque para abrir o painel.";
        }

        String orderId = message.getData().get("pedido_id");
        String numero = message.getData().get("numero");
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (orderId != null) intent.putExtra("pedido_id", orderId);
        if (numero != null) intent.putExtra("numero", numero);

        int requestCode = orderId == null ? (int) System.currentTimeMillis() : Math.abs(orderId.hashCode());
        PendingIntent pending = PendingIntent.getActivity(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId(selectedSound))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setOngoing(false)
                .setOnlyAlertOnce(false)
                .setContentIntent(pending)
                .setVibrate(new long[]{0, 300, 150, 500, 150, 700});

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            int resId = soundResId(selectedSound);
            if (resId != 0) builder.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + resId));
        }

        try { NotificationManagerCompat.from(this).notify(requestCode, builder.build()); }
        catch (SecurityException ignored) { }
    }

    private void createNotificationChannel(int soundNumber) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        String id = channelId(soundNumber);
        if (nm.getNotificationChannel(id) != null) return;

        int resId = soundResId(soundNumber);
        Uri soundUri = resId != 0 ? Uri.parse("android.resource://" + getPackageName() + "/" + resId) : null;
        NotificationChannel channel = new NotificationChannel(id,
                "Novos pedidos — toque " + String.format(java.util.Locale.US, "%02d", soundNumber),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Alertas de novos pedidos do Açaí da 7");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 300, 150, 500, 150, 700});
        if (soundUri != null) {
            channel.setSound(soundUri, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        }
        nm.createNotificationChannel(channel);
    }
}
