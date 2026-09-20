-- AÇAÍ DA 7 — DISPOSITIVOS PARA NOTIFICAÇÕES DO PAINEL ANDROID
create table if not exists public.dispositivos_notificacao (
  id uuid primary key default gen_random_uuid(),
  usuario_id uuid references auth.users(id) on delete cascade not null,
  token text unique not null,
  plataforma text not null default 'android',
  ativo boolean not null default true,
  criado_em timestamptz not null default now(),
  atualizado_em timestamptz not null default now()
);

alter table public.dispositivos_notificacao enable row level security;

drop policy if exists "admin pode gerenciar seus dispositivos" on public.dispositivos_notificacao;
create policy "admin pode gerenciar seus dispositivos"
on public.dispositivos_notificacao
for all to authenticated
using (auth.uid() = usuario_id)
with check (auth.uid() = usuario_id);

create index if not exists dispositivos_notificacao_usuario_idx
on public.dispositivos_notificacao(usuario_id, ativo);

-- Depois, para envio automático, o Supabase deve chamar uma Edge Function
-- que usa o token FCM e credenciais de servidor do Firebase para enviar a mensagem.
