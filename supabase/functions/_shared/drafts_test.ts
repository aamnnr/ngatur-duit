import { assertEquals } from "jsr:@std/assert@1";
import { type LedgerContext, MAX_DRAFTS, normalizeDrafts, parseContext } from "./drafts.ts";

const ctx: LedgerContext = {
  categories: [
    { id: "cat-food", name: "Makan", type: "EXPENSE" },
    { id: "cat-salary", name: "Gaji", type: "INCOME" },
  ],
  wallets: [{ id: "w-gopay", name: "GoPay" }],
  today: "2026-10-19",
};

Deno.test("draf valid dipertahankan dan dirapikan", () => {
  const [d] = normalizeDrafts(
    [{ type: "EXPENSE", amount: 25000.4, category_id: "cat-food", wallet_id: "w-gopay", note: "  Bakso  ", date: "2026-10-18", confidence: 0.95 }],
    ctx,
  );
  assertEquals(d, {
    type: "EXPENSE", amount: 25000, category_id: "cat-food", wallet_id: "w-gopay", note: "Bakso", date: "2026-10-18", confidence: 0.95,
  });
});

Deno.test("ID asing dan kategori beda jenis dibuang, keyakinan diturunkan", () => {
  const [d] = normalizeDrafts(
    [{ type: "EXPENSE", amount: 10000, category_id: "cat-salary", wallet_id: "w-asing", date: "kemarin", confidence: 0.9 }],
    ctx,
  );
  assertEquals(d.category_id, null);
  assertEquals(d.wallet_id, null);
  assertEquals(d.date, "2026-10-19");
  assertEquals(d.confidence, 0.5);
});

Deno.test("nominal tidak masuk akal dan jenis salah ditolak", () => {
  const drafts = normalizeDrafts(
    [
      { type: "EXPENSE", amount: 0, date: "2026-10-19", confidence: 1 },
      { type: "EXPENSE", amount: -5000, date: "2026-10-19", confidence: 1 },
      { type: "TRANSFER", amount: 5000, date: "2026-10-19", confidence: 1 },
      "bukan objek",
    ],
    ctx,
  );
  assertEquals(drafts.length, 0);
});

Deno.test("maksimal MAX_DRAFTS draf", () => {
  const many = Array.from({ length: 15 }, () => ({ type: "EXPENSE", amount: 1000, date: "2026-10-19", confidence: 1 }));
  assertEquals(normalizeDrafts(many, ctx).length, MAX_DRAFTS);
});

Deno.test("konteks kiriman aplikasi divalidasi", () => {
  const parsed = parseContext({
    categories: [{ id: "a", name: "A", type: "EXPENSE" }, { id: 1, name: "rusak" }],
    wallets: [{ id: "w", name: "W" }, null],
    today: "19/10/2026",
  });
  assertEquals(parsed.categories.length, 1);
  assertEquals(parsed.wallets.length, 1);
  assertEquals(/^\d{4}-\d{2}-\d{2}$/.test(parsed.today), true);
});
