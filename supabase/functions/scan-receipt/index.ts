// Membaca foto struk menjadi draf pengeluaran.
//
// POST body:
// {
//   "image_base64": "...",          // JPG/PNG, sebaiknya diperkecil di HP (sisi terpanjang ~1600px)
//   "mime_type": "image/jpeg",
//   "ocr_text": "...",              // opsional: hasil ML Kit di HP, membantu akurasi
//   "context": { "categories": [...], "wallets": [...], "today": "2026-10-19" }
// }
// Respons: { "is_receipt": true, "merchant": "...", "items": [{name, amount}], "draft": TransactionDraft | null }

import { AppError, json, serve } from "../_shared/http.ts";
import { consumeAiQuota, requireUser } from "../_shared/supabase.ts";
import { parseContext } from "../_shared/drafts.ts";
import { readReceipt } from "../_shared/receipt.ts";

serve(async (req) => {
  const user = await requireUser(req);
  const body = await req.json().catch(() => ({}));

  const image = typeof body.image_base64 === "string" ? body.image_base64.replace(/^data:[^,]+,/, "") : "";
  if (!image) throw new AppError("no_image", "Foto struk belum dikirim.");
  const mime = typeof body.mime_type === "string" ? body.mime_type : "image/jpeg";
  const ocr = typeof body.ocr_text === "string" ? body.ocr_text : undefined;

  await consumeAiQuota(user.id);
  const result = await readReceipt(image, mime, parseContext(body.context), ocr);
  return json(result);
});
