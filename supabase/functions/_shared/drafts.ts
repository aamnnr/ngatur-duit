// Bentuk data bersama untuk asisten, scan struk, dan bot Telegram.

export interface CategoryRef {
  id: string;
  name: string;
  type: "INCOME" | "EXPENSE";
}

export interface WalletRef {
  id: string;
  name: string;
}

/** Konteks yang dikirim aplikasi (atau diambil bot dari database) agar AI memakai ID yang benar. */
export interface LedgerContext {
  categories: CategoryRef[];
  wallets: WalletRef[];
  /** Tanggal hari ini "YYYY-MM-DD" di zona pengguna. */
  today: string;
}

/** Draf transaksi yang sudah divalidasi. Aplikasi tetap menampilkannya untuk ditinjau sebelum disimpan. */
export interface TransactionDraft {
  type: "INCOME" | "EXPENSE";
  amount: number;
  category_id: string | null;
  wallet_id: string | null;
  note: string | null;
  /** "YYYY-MM-DD" */
  date: string;
  /** 0..1 — seberapa yakin AI. Di bawah 0.6 ditandai "Perlu cek". */
  confidence: number;
}

export const MAX_DRAFTS = 10;

/** Skema output Gemini untuk satu draf transaksi. */
export const draftSchema = {
  type: "OBJECT",
  properties: {
    type: { type: "STRING", enum: ["INCOME", "EXPENSE"] },
    amount: { type: "INTEGER", description: "Nominal dalam rupiah penuh, tanpa desimal" },
    category_id: { type: "STRING", nullable: true },
    wallet_id: { type: "STRING", nullable: true },
    note: { type: "STRING", nullable: true },
    date: { type: "STRING", description: "YYYY-MM-DD" },
    confidence: { type: "NUMBER" },
  },
  required: ["type", "amount", "date", "confidence"],
};

export function todayIn(timeZone = Deno.env.get("APP_TIMEZONE") ?? "Asia/Jakarta"): string {
  // en-CA menghasilkan format YYYY-MM-DD.
  return new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" })
    .format(new Date());
}

/** Teks konteks untuk prompt: daftar kategori & dompet beserta ID-nya. */
export function describeContext(ctx: LedgerContext): string {
  const cats = ctx.categories.map((c) => `- ${c.id} | ${c.name} | ${c.type}`).join("\n");
  const wallets = ctx.wallets.map((w) => `- ${w.id} | ${w.name}`).join("\n");
  return `Hari ini: ${ctx.today}\n\nKategori (id | nama | jenis):\n${cats || "-"}\n\nDompet (id | nama):\n${wallets || "-"}`;
}

export const PARSING_RULES = `
Aturan membaca transaksi:
- Nominal selalu rupiah bulat. "25rb"/"25k" = 25000, "1,5jt"/"1.5 juta" = 1500000, "2 jt" = 2000000, "goceng" = 5000, "ceban" = 10000.
- Pilih category_id HANYA dari daftar kategori dengan jenis yang sesuai; bila tidak ada yang cocok, null.
- Pilih wallet_id HANYA dari daftar dompet bila disebut (mis. "pakai GoPay", "dari BCA"); bila tidak disebut, null.
- "kemarin" = hari ini dikurangi 1, "tadi"/tanpa keterangan = hari ini. Tulis tanggal sebagai YYYY-MM-DD.
- note: ringkas, maksimal 60 karakter, tanpa nominal (mis. "Bakso", "Gaji Oktober").
- Gaji, bonus, terima transfer, dijual = INCOME. Beli, bayar, makan, isi bensin = EXPENSE.
- confidence: 0.9+ bila nominal dan kategori jelas, 0.5-0.8 bila ada tebakan, di bawah 0.5 bila ragu.
`.trim();

/** Validasi & rapikan draf dari AI: buang yang tidak masuk akal, pastikan ID memang milik pengguna. */
export function normalizeDrafts(raw: unknown, ctx: LedgerContext): TransactionDraft[] {
  if (!Array.isArray(raw)) return [];
  const categoryType = new Map(ctx.categories.map((c) => [c.id, c.type]));
  const walletIds = new Set(ctx.wallets.map((w) => w.id));
  const out: TransactionDraft[] = [];
  for (const item of raw.slice(0, MAX_DRAFTS)) {
    if (typeof item !== "object" || item === null) continue;
    const r = item as Record<string, unknown>;
    const type = r.type === "INCOME" ? "INCOME" : r.type === "EXPENSE" ? "EXPENSE" : null;
    const amount = Math.round(Number(r.amount));
    if (!type || !Number.isFinite(amount) || amount <= 0 || amount > 1_000_000_000_000) continue;
    const categoryId = typeof r.category_id === "string" && categoryType.get(r.category_id) === type ? r.category_id : null;
    const walletId = typeof r.wallet_id === "string" && walletIds.has(r.wallet_id) ? r.wallet_id : null;
    const date = typeof r.date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(r.date) ? r.date : ctx.today;
    const note = typeof r.note === "string" && r.note.trim() ? r.note.trim().slice(0, 60) : null;
    const confidence = Math.min(1, Math.max(0, Number(r.confidence) || 0));
    // Tanpa kategori, draf selalu dianggap perlu dicek.
    out.push({ type, amount, category_id: categoryId, wallet_id: walletId, note, date, confidence: categoryId ? confidence : Math.min(confidence, 0.5) });
  }
  return out;
}

/** Validasi konteks kiriman aplikasi. */
export function parseContext(input: unknown): LedgerContext {
  const r = (input ?? {}) as Record<string, unknown>;
  const categories = Array.isArray(r.categories)
    ? r.categories
      .filter((c): c is CategoryRef =>
        typeof c?.id === "string" && typeof c?.name === "string" && (c?.type === "INCOME" || c?.type === "EXPENSE")
      )
      .slice(0, 200)
    : [];
  const wallets = Array.isArray(r.wallets)
    ? r.wallets.filter((w): w is WalletRef => typeof w?.id === "string" && typeof w?.name === "string").slice(0, 50)
    : [];
  const today = typeof r.today === "string" && /^\d{4}-\d{2}-\d{2}$/.test(r.today) ? r.today : todayIn();
  return { categories, wallets, today };
}
