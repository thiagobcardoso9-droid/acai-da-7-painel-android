# Açaí da 7 — Painel Android

Projeto Android do painel administrativo, baseado no painel web atual e registrado no Firebase como `com.acaida7.painel`.

## O que já está preparado
- WebView com o painel Açaí da 7 atual.
- Login/Supabase preservado.
- WhatsApp manual preservado.
- Tempo de entrega preservado.
- Ícone Açaí da 7.
- Firebase Cloud Messaging (FCM).
- Permissão de notificações Android 13+.
- Canal de alta importância para "Novos pedidos" com som/vibração.
- Token FCM disponibilizado ao painel por `NativeBridge.getFcmToken()`.
- Registro do token no Supabase pela tabela `dispositivos_notificacao`.

## Importante sobre o alerta automático
O APK está preparado para **receber** notificações FCM mesmo fora do app. Para o alerta automático acontecer quando um novo pedido entrar no Supabase, ainda é necessário criar o disparador no backend (Edge Function/Webhook) que envia o FCM. Isso exige credenciais de servidor do Firebase; o `google-services.json` do app não é suficiente para enviar mensagens do servidor.

O FCM documenta que notificações podem ser entregues pela bandeja do sistema quando o app está em segundo plano e que Android 13+ exige permissão de notificação em tempo de execução.

## Firebase
Projeto: `acaida7`
Pacote: `com.acaida7.painel`

O arquivo `app/google-services.json` veio do Firebase Console e contém identificadores de configuração do app.

## Supabase
Execute `SUPABASE_NOTIFICACOES.sql` no SQL Editor do projeto do Açaí da 7.

## Compilação
Abra esta pasta no Android Studio atual, sincronize o Gradle e gere um APK debug em:
`app/build/outputs/apk/debug/app-debug.apk`

O ambiente de execução usado para preparar este pacote não possui Android SDK/build-tools, então o APK binário não foi compilado aqui.
