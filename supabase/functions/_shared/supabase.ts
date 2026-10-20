import { createClient, type SupabaseClient } from "jsr:@supabase/supabase-js@2";
import { AppError } from "./http.ts";

/** Klien dengan service role: melewati RLS, hanya dipakai di server. */
export function adminClient(): SupabaseClient {
  return createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });
}

/** Mengambil pengguna dari header Authorization (JWT Supabase dari aplikasi). */
export async function requireUser(req: Request): Promise<{ id: string }> {
  const auth = req.headers.get("Authorization");
  if (!auth) throw new AppError("unauthenticated", "Masuk ke akun Duitku dulu.", 401);
  const client = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: auth } },
    auth: { persistSession: false },
  });
  const { data, error } = await client.auth.getUser();
  if (error || !data.user) throw new AppError("unauthenticated", "Sesi berakhir. Masuk lagi ke akun Duitku.", 401);
  return { id: data.user.id };
}

/** Batas pemakaian AI per hari (secret AI_DAILY_LIMIT, bawaan 50) untuk menjaga kuota free tier. */
export async function consumeAiQuota(userId: string): Promise<void> {
  const limit = Number(Deno.env.get("AI_DAILY_LIMIT") ?? "50");
  const { error } = await adminClient().rpc("bump_ai_usage", { p_user: userId, p_limit: limit });
  if (error) {
    if (error.message.includes("ai_daily_limit_reached")) {
      throw new AppError(
        "ai_daily_limit",
        `Jatah ${limit} permintaan AI hari ini sudah habis. Tersedia lagi besok; pencatatan manual tetap bisa dipakai.`,
        429,
      );
    }
    throw error;
  }
}
