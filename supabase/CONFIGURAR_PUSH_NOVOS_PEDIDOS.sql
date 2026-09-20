-- AÇAÍ DA 7 — PUSH DE NOVOS PEDIDOS
--
-- Antes de executar:
-- 1) No Supabase > Edge Functions > Secrets, crie:
--    FIREBASE_PROJECT_ID = acaida7
--    FIREBASE_SERVICE_ACCOUNT_JSON = JSON da conta de serviço do Firebase
--    FCM_WEBHOOK_SECRET = um segredo forte escolhido por você
--
-- 2) NÃO coloque o JSON da conta de serviço neste arquivo nem no GitHub.
--
-- 3) Depois de criar os secrets, substitua SOMENTE o texto
--    COLOQUE_O_MESMO_SEGREDO_AQUI pelo mesmo valor usado em FCM_WEBHOOK_SECRET.
--
-- O webhook é assíncrono e dispara depois que um pedido é inserido.

create extension if not exists pg_net with schema extensions;

-- Evita duplicar o gatilho caso você execute o SQL novamente.
drop trigger if exists trigger_push_novo_pedido on public.pedidos;

create trigger trigger_push_novo_pedido
after insert on public.pedidos
for each row
execute function supabase_functions.http_request(
  'https://mkuekafoolfxppffjlyh.supabase.co/functions/v1/fcm-novo-pedido',
  'POST',
  '{"Content-Type":"application/json","x-webhook-secret":"COLOQUE_O_MESMO_SEGREDO_AQUI"}'::jsonb,
  '{}'::jsonb,
  '1000'
);

-- Teste: depois de instalar a nova versão do APK e permitir notificações,
-- crie um pedido real pelo site. O push deverá chegar mesmo com o APK fechado.
