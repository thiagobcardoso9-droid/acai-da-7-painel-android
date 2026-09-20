const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-webhook-secret',
};

function base64Url(bytes: Uint8Array): string {
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function base64UrlText(value: string): string {
  return base64Url(new TextEncoder().encode(value));
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const base64 = pem
    .replace('-----BEGIN PRIVATE KEY-----', '')
    .replace('-----END PRIVATE KEY-----', '')
    .replace(/\s/g, '');
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

async function getGoogleAccessToken(serviceAccount: Record<string, string>): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64UrlText(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claim = base64UrlText(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  }));

  const key = await crypto.subtle.importKey(
    'pkcs8',
    pemToArrayBuffer(serviceAccount.private_key),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  );

  const unsigned = `${header}.${claim}`;
  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(unsigned),
  );

  const assertion = `${unsigned}.${base64Url(new Uint8Array(signature))}`;
  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    }),
  });

  if (!response.ok) {
    throw new Error(`Google OAuth falhou: ${await response.text()}`);
  }

  const data = await response.json();
  return data.access_token;
}

function orderText(record: Record<string, unknown>): { title: string; body: string } {
  const number = record.numero_dia ?? record.numero ?? record.id ?? '';
  const total = Number(record.total ?? record.valor_total ?? 0);
  const totalText = total > 0
    ? ` • ${total.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}`
    : '';
  return {
    title: '🍧 Açaí da 7 — NOVO PEDIDO!',
    body: `Pedido #${number}${totalText} recebido. Toque para abrir o painel.`,
  };
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });

  try {
    const expectedSecret = Deno.env.get('FCM_WEBHOOK_SECRET');
    if (!expectedSecret || req.headers.get('x-webhook-secret') !== expectedSecret) {
      return new Response(JSON.stringify({ error: 'unauthorized' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const payload = await req.json();
    if (payload?.table && payload.table !== 'pedidos') {
      return new Response(JSON.stringify({ ok: true, ignored: true }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const record = payload?.record ?? payload?.data ?? payload ?? {};
    const serviceAccountRaw = Deno.env.get('FIREBASE_SERVICE_ACCOUNT_JSON');
    const projectId = Deno.env.get('FIREBASE_PROJECT_ID') || 'acaida7';
    if (!serviceAccountRaw) throw new Error('FIREBASE_SERVICE_ACCOUNT_JSON não configurado');

    const serviceAccount = JSON.parse(serviceAccountRaw);
    const accessToken = await getGoogleAccessToken(serviceAccount);
    const notification = orderText(record);

    const response = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        message: {
          topic: 'novos_pedidos',
          notification,
          data: {
            pedido_id: String(record.id ?? ''),
            numero: String(record.numero_dia ?? record.numero ?? ''),
            tipo: 'novo_pedido',
          },
          android: {
            priority: 'HIGH',
            notification: {
              channel_id: 'novos_pedidos',
              sound: 'novo_pedido',
              default_vibrate_timings: false,
              vibrate_timings: ['0.25s', '0.15s', '0.35s', '0.15s', '0.5s'],
            },
          },
        },
      }),
    });

    const result = await response.text();
    if (!response.ok) throw new Error(`FCM ${response.status}: ${result}`);

    return new Response(JSON.stringify({ ok: true, fcm: JSON.parse(result) }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error(error);
    return new Response(JSON.stringify({ error: String(error?.message ?? error) }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
