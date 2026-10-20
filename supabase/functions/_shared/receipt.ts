// Membaca foto struk dengan Gemini (dipakai scan-receipt dan bot Telegram).

import { AppError } from "./http.ts";
import { generateJson } from "./gemini.ts";
import { describeContext, type LedgerContext, type TransactionDraft } from "./drafts.ts";

export interface ReceiptResult {
  is_receipt: boolean;
  merchant: string | null;
  items: { name: string; amount: number }[];
  /** Draf pengeluaran dari total struk; null bila bukan struk. */
  draft: TransactionDraft | null;
}

const SYSTEM = `
Kamu membaca foto struk/nota belanja Indonesia untuk aplikasi pencatat keuangan.
- is_receipt = false bila gambar bukan struk, nota, atau bukti pembayaran.
- total: jumlah akhir yang dibayar (setelah diskon, termasuk pajak/servis), rupiah bulat. Titik di struk Indonesia adalah pemisah ribuan.
- date: tanggal di struk (YYYY-MM-DD); bila tidak terbaca, pakai tanggal hari ini.
- merchant: nama toko bila terbaca.
- items: maksimal 15 baris barang (nama singkat + harga bulat).
- category_id: pilih dari kategori PENGELUARAN yang paling cocok; null bila tidak ada.
- Jangan menebak angka yang tidak terlihat.
`.trim();

const schema = {
  type: "OBJECT",
  properties: {
    is_receipt: { type: "BOOLEAN" },
    merchant: { type: "STRING", nullable: true },
    total: { type: "INTEGER", nullable: true },
    date: { type: "STRING", nullable: true },
    category_id: { type: "STRING", nullable: true },
    items: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: { name: { type: "STRING" }, amount: { type: "INTEGER" } },
        required: ["name", "amount"],
      },
    },
    confidence: { type: "NUMBER" },
  },
  required: ["is_receipt", "items", "confidence"],
};

interface RawReceipt {
  is_receipt: boolean;
  merchant?: string | null;
  total?: number | null;
  date?: string | null;
  category_id?: string | null;
  items?: { name?: string; amount?: number }[];
  confidence?: number;
}

const MAX_IMAGE_BYTES = 4 * 1024 * 1024;
const ALLOWED_MIME = new Set(["image/jpeg", "image/png", "image/webp", "image/heic"]);

export async function readReceipt(
  imageBase64: string,
  mimeType: string,
  ctx: LedgerContext,
  ocrText?: string,
): Promise<ReceiptResult> {
  if (!ALLOWED_MIME.has(mimeType)) throw new AppError("bad_image", "Format gambar tidak didukung. Pakai JPG atau PNG.");
  if (imageBase64.length * 0.75 > MAX_IMAGE_BYTES) {
    throw new AppError("image_too_large", "Foto terlalu besar. Maksimal sekitar 4 MB.");
  }

  const raw = await generateJson<RawReceipt>({
    system: SYSTEM,
    schema,
    parts: [
      { text: describeContext(ctx) + (ocrText ? `\n\nTeks hasil OCR di HP (bisa tidak akurat):\n${ocrText.slice(0, 4000)}` : "") },
      { inline_data: { mime_type: mimeType, data: imageBase64 } },
    ],
  });

  const items = (raw.items ?? [])
    .filter((i) => typeof i?.name === "string" && Number.isFinite(Number(i.amount)))
    .slice(0, 15)
    .map((i) => ({ name: String(i.name).slice(0, 60), amount: Math.round(Number(i.amount)) }));
  const merchant = typeof raw.merchant === "string" && raw.merchant.trim() ? raw.merchant.trim().slice(0, 60) : null;
  const total = Math.round(Number(raw.total));
  if (!raw.is_receipt || !Number.isFinite(total) || total <= 0) {
    return { is_receipt: Boolean(raw.is_receipt), merchant, items, draft: null };
  }

  const expenseCategory = ctx.categories.find((c) => c.id === raw.category_id && c.type === "EXPENSE");
  const date = typeof raw.date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(raw.date) && raw.date <= ctx.today ? raw.date : ctx.today;
  const confidence = Math.min(1, Math.max(0, Number(raw.confidence) || 0));
  return {
    is_receipt: true,
    merchant,
    items,
    draft: {
      type: "EXPENSE",
      amount: total,
      category_id: expenseCategory?.id ?? null,
      wallet_id: null,
      note: merchant ?? "Belanja",
      date,
      confidence: expenseCategory ? confidence : Math.min(confidence, 0.5),
    },
  };
}
