import { AppError } from "./http.ts";

const API = "https://generativelanguage.googleapis.com/v1beta/models";

export type GeminiPart =
  | { text: string }
  | { inline_data: { mime_type: string; data: string } };

export interface GeminiRequest {
  system: string;
  parts: GeminiPart[];
  /** Skema JSON (subset OpenAPI) untuk output terstruktur. */
  schema: Record<string, unknown>;
  temperature?: number;
}

/**
 * Memanggil Gemini dan mengembalikan JSON hasil parse.
 * Model dibaca dari secret GEMINI_MODEL agar bisa diganti tanpa deploy ulang aplikasi.
 */
export async function generateJson<T>(req: GeminiRequest): Promise<T> {
  const key = Deno.env.get("GEMINI_API_KEY");
  if (!key) throw new AppError("ai_not_configured", "Fitur AI belum dikonfigurasi di server.", 503);
  const model = Deno.env.get("GEMINI_MODEL") ?? "gemini-flash-latest";

  const res = await fetch(`${API}/${model}:generateContent`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": key },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: req.system }] },
      contents: [{ role: "user", parts: req.parts }],
      generationConfig: {
        temperature: req.temperature ?? 0.2,
        responseMimeType: "application/json",
        responseSchema: req.schema,
      },
    }),
  });

  if (res.status === 429) {
    throw new AppError("ai_quota", "Kuota gratis Gemini sedang habis atau terlalu sering dipakai. Coba lagi nanti.", 429);
  }
  if (!res.ok) {
    console.error("Gemini error", res.status, await res.text());
    throw new AppError("ai_failed", "Layanan AI sedang bermasalah. Coba lagi sebentar.", 502);
  }

  const body = await res.json();
  const text: string | undefined = body?.candidates?.[0]?.content?.parts
    ?.map((p: { text?: string }) => p.text ?? "")
    .join("");
  if (!text) {
    const reason = body?.promptFeedback?.blockReason ?? body?.candidates?.[0]?.finishReason;
    console.error("Gemini empty response", reason);
    throw new AppError("ai_empty", "AI tidak memberi jawaban. Coba ubah kalimatnya.", 502);
  }
  try {
    return JSON.parse(text) as T;
  } catch {
    console.error("Gemini invalid JSON", text);
    throw new AppError("ai_invalid", "Jawaban AI tidak bisa dibaca. Coba lagi.", 502);
  }
}
