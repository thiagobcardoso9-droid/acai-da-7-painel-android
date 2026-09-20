package com.acaida7.painel;

import android.app.PendingIntent;
import android.content.Intent;
import android.media.RingtoneManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class AcaiMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "novos_pedidos";

    @Override
    public void onNewToken(@NonNull String token) {
        getSharedPreferences("fcm", MODE_PRIVATE).edit().putString("token", token).apply();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        String title = message.getNotification() != null && message.getNotification().getTitle() != null
                ? message.getNotification().getTitle() : "🍧 Açaí da 7 — NOVO PEDIDO!";
        String body = message.getNotification() != null && message.getNotification().getBody() != null
                ? message.getNotification().getBody() : message.getData().getOrDefault("body", "Você recebeu um novo pedido.");
        String orderId = message.getData().get("pedido_id");

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (orderId != null) intent.putExtra("pedido_id", orderId);
        PendingIntent pending = PendingIntent.getActivity(this, orderId == null ? 1 : orderId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setVibrate(new long[]{0, 250, 150, 350, 150, 500})
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        try {
            NotificationManagerCompat.from(this).notify(orderId == null ? 1001 : Math.abs(orderId.hashCode()), b.build());
        } catch (SecurityException ignored) {}
    }
}
