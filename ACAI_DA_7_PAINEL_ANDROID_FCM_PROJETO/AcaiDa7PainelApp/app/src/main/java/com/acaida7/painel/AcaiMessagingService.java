package com.acaida7.painel;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class AcaiMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = MainActivity.CHANNEL_ID;
    private static final String FCM_TOPIC = "novos_pedidos";

    @Override
    public void onNewToken(@NonNull String token) {
        getSharedPreferences("fcm", MODE_PRIVATE)
                .edit()
                .putString("token", token)
                .apply();

        // Se o token mudar, garante novamente a inscrição no tópico.
        FirebaseMessaging.getInstance().subscribeToTopic(FCM_TOPIC);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        createNotificationChannel();

        String title = message.getData().get("title");
        if (title == null || title.trim().isEmpty()) {
            title = message.getNotification() != null && message.getNotification().getTitle() != null
                    ? message.getNotification().getTitle()
                    : "🍧 Açaí da 7 — NOVO PEDIDO!";
        }

        String body = message.getData().get("body");
        if (body == null || body.trim().isEmpty()) {
            body = message.getNotification() != null && message.getNotification().getBody() != null
                    ? message.getNotification().getBody()
                    : "Você recebeu um novo pedido. Toque para abrir o painel.";
        }

        String orderId = message.getData().get("pedido_id");
        String numero = message.getData().get("numero");

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (orderId != null) intent.putExtra("pedido_id", orderId);
        if (numero != null) intent.putExtra("numero", numero);

        int requestCode = orderId == null ? 1001 : Math.abs(orderId.hashCode());
        PendingIntent pending = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
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
                .setVibrate(new long[]{0, 300, 150, 500, 150, 700})
                .setSound(android.net.Uri.parse(
                        "android.resource://" + getPackageName() + "/" + R.raw.novo_pedido));

        try {
            NotificationManagerCompat.from(this)
                    .notify(requestCode, builder.build());
        } catch (SecurityException ignored) {
            // Android 13+: se a permissão POST_NOTIFICATIONS estiver bloqueada,
            // o sistema impede a exibição da notificação.
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
            if (channel != null) return;

            channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Novos pedidos",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alertas de novos pedidos do Açaí da 7");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 300, 150, 500, 150, 700});
            channel.setSound(
                    android.net.Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.novo_pedido),
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
            nm.createNotificationChannel(channel);
        }
    }
}
