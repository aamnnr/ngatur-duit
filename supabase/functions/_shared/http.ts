export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

export function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

/**
 * Error yang aman ditampilkan ke pengguna. `code` dipakai aplikasi untuk memilih pesan,
 * `message` sudah dalam bahasa Indonesia.
 */
export class AppError extends Error {
  constructor(public code: string, message: string, public status = 400) {
    super(message);
  }
}

export function errorResponse(err: unknown): Response {
  if (err instanceof AppError) {
    return json({ error: { code: err.code, message: err.message } }, err.status);
  }
  console.error(err);
  return json({ error: { code: "internal", message: "Terjadi kesalahan di server. Coba lagi sebentar." } }, 500);
}

/** Pembungkus handler: tangani preflight CORS, hanya izinkan POST, ubah error jadi JSON. */
export function serve(handler: (req: Request) => Promise<Response>) {
  Deno.serve(async (req) => {
    if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
    if (req.method !== "POST") return json({ error: { code: "method", message: "Gunakan POST." } }, 405);
    try {
      return await handler(req);
    } catch (err) {
      return errorResponse(err);
    }
  });
}
