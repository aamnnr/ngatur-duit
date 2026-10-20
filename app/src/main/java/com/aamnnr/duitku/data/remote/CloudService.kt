package com.aamnnr.duitku.data.remote

import com.aamnnr.duitku.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Error dari cloud yang pesannya aman ditampilkan ke pengguna. */
class CloudException(val code: String, message: String) : Exception(message)

data class Account(val id: String, val email: String?)

// --- Kontrak Edge Functions (lihat supabase/functions) ---------------------

@Serializable
data class CategoryRef(val id: String, val name: String, val type: String)

@Serializable
data class WalletRef(val id: String, val name: String)

@Serializable
data class LedgerContext(val categories: List<CategoryRef>, val wallets: List<WalletRef>, val today: String)

@Serializable
data class ChatTurn(val role: String, val text: String)

@Serializable
data class AssistantRequest(
    val message: String,
    val context: LedgerContext,
    val summary: JsonObject? = null,
    val history: List<ChatTurn> = emptyList(),
)

@Serializable
data class AssistantResponse(val kind: String, val answer: String, val transactions: List<DraftPayload> = emptyList())

@Serializable
data class ScanRequest(
    @SerialName("image_base64") val imageBase64: String,
    @SerialName("mime_type") val mimeType: String,
    val context: LedgerContext,
)

@Serializable
data class ReceiptItem(val name: String, val amount: Long)

@Serializable
data class ScanResponse(
    @SerialName("is_receipt") val isReceipt: Boolean,
    val merchant: String? = null,
    val items: List<ReceiptItem> = emptyList(),
    val draft: DraftPayload? = null,
)

/**
 * Semua akses ke Supabase: login, tabel sinkron, inbox draf, Telegram, dan Edge Functions.
 * Bila URL/kunci belum diisi di local.properties, [isConfigured] = false dan fitur cloud disembunyikan.
 */
class CloudService {
    val isConfigured: Boolean = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val client: SupabaseClient by lazy {
        check(isConfigured) { "Supabase belum dikonfigurasi" }
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            install(Auth)
            install(Postgrest)
        }
    }

    // Panggilan AI melewati Edge Function (bisa cold start) lalu Gemini, sering > 10 detik.
    // socketTimeout wajib diatur: tanpa itu OkHttp memakai batas baca bawaan 10 detik.
    private val http by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 90_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 90_000
            }
        }
    }

    // --- Akun --------------------------------------------------------------

    /** Akun yang sedang masuk (null = belum masuk), mengikuti status sesi Supabase. */
    val account: Flow<Account?> =
        if (!isConfigured) flowOf(null)
        else client.auth.sessionStatus.map { status ->
            (status as? SessionStatus.Authenticated)?.session?.user?.let { Account(it.id, it.email) }
        }

    fun currentAccount(): Account? =
        if (!isConfigured) null else client.auth.currentUserOrNull()?.let { Account(it.id, it.email) }

    suspend fun sendLoginCode(email: String) {
        client.auth.signInWith(OTP) {
            this.email = email
            createUser = true
        }
    }

    suspend fun verifyLoginCode(email: String, code: String) {
        client.auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email, token = code)
    }

    suspend fun signOut() = client.auth.signOut()

    // --- Sinkron ----------------------------------------------------------

    suspend fun upsertProfiles(rows: List<RemoteProfile>) = upsert("fin_profiles", rows)
    suspend fun upsertWallets(rows: List<RemoteWallet>) = upsert("wallets", rows)
    suspend fun upsertCategories(rows: List<RemoteCategory>) = upsert("categories", rows)
    suspend fun upsertTransactions(rows: List<RemoteTransaction>) = upsert("transactions", rows)
    suspend fun upsertBudgets(rows: List<RemoteBudget>) = upsert("budgets", rows)
    suspend fun upsertGoals(rows: List<RemoteGoal>) = upsert("saving_goals", rows)
    suspend fun upsertLogs(rows: List<RemoteSavingLog>) = upsert("saving_logs", rows)

    private suspend inline fun <reified T : Any> upsert(table: String, rows: List<T>) {
        rows.chunked(500).forEach { chunk -> client.from(table).upsert(chunk) { onConflict = "id" } }
    }

    suspend fun profilesSince(cursor: String) = pageSince<RemoteProfile>("fin_profiles", cursor)
    suspend fun walletsSince(cursor: String) = pageSince<RemoteWallet>("wallets", cursor)
    suspend fun categoriesSince(cursor: String) = pageSince<RemoteCategory>("categories", cursor)
    suspend fun transactionsSince(cursor: String) = pageSince<RemoteTransaction>("transactions", cursor)
    suspend fun budgetsSince(cursor: String) = pageSince<RemoteBudget>("budgets", cursor)
    suspend fun goalsSince(cursor: String) = pageSince<RemoteGoal>("saving_goals", cursor)
    suspend fun logsSince(cursor: String) = pageSince<RemoteSavingLog>("saving_logs", cursor)

    /** Semua baris dengan updated_at > cursor, diambil per halaman 1000. */
    private suspend inline fun <reified T : Any> pageSince(table: String, cursor: String): List<T> {
        val out = mutableListOf<T>()
        var from = 0L
        while (true) {
            val page = client.from(table).select {
                filter { gt("updated_at", cursor) }
                order("updated_at", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(from, from + PAGE - 1)
            }.decodeList<T>()
            out += page
            if (page.size < PAGE) return out
            from += PAGE
        }
    }

    // --- Inbox draf -------------------------------------------------------

    suspend fun pendingDrafts(): List<RemoteDraft> =
        client.from("inbox_drafts").select {
            filter { eq("status", "PENDING") }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun markDraft(id: String, status: String) {
        client.from("inbox_drafts").update({ set("status", status) }) { filter { eq("id", id) } }
    }

    // --- Telegram ---------------------------------------------------------

    suspend fun telegramLink(): RemoteTelegramLink? =
        client.from("telegram_links").select().decodeSingleOrNull()

    suspend fun createPairingCode(profileId: String?): String =
        client.postgrest.rpc("create_telegram_pairing_code", buildJsonObject { put("p_profile_id", profileId) })
            .decodeAs<String>()

    suspend fun unlinkTelegram() {
        val uid = client.auth.currentUserOrNull()?.id ?: return
        client.from("telegram_links").delete { filter { eq("user_id", uid) } }
    }

    // --- Edge Functions ---------------------------------------------------

    suspend fun askAssistant(req: AssistantRequest): AssistantResponse =
        json.decodeFromString(callFunction("ai-assistant", json.encodeToString(AssistantRequest.serializer(), req)))

    suspend fun scanReceipt(req: ScanRequest): ScanResponse =
        json.decodeFromString(callFunction("scan-receipt", json.encodeToString(ScanRequest.serializer(), req)))

    private suspend fun callFunction(name: String, body: String): String {
        if (client.auth.currentAccessTokenOrNull() == null) {
            throw CloudException("unauthenticated", "Masuk ke akun Duitku dulu di Profil & Pengaturan → Akun dan sinkron.")
        }
        var res = post(name, body)
        // Token kedaluwarsa (mis. aplikasi lama di latar belakang): segarkan sesi sekali lalu ulangi.
        if (res.status.value == 401) {
            runCatching { client.auth.refreshCurrentSession() }
            res = post(name, body)
        }
        val text = res.bodyAsText()
        if (!res.status.isSuccess()) {
            val err = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonObject }.getOrNull()
            throw CloudException(
                err?.get("code")?.jsonPrimitive?.content ?: "http_${res.status.value}",
                err?.get("message")?.jsonPrimitive?.content ?: "Server sedang bermasalah (${res.status.value}). Coba lagi nanti.",
            )
        }
        return text
    }

    private suspend fun post(name: String, body: String): io.ktor.client.statement.HttpResponse {
        val token = client.auth.currentAccessTokenOrNull()
            ?: throw CloudException("unauthenticated", "Sesi berakhir. Masuk lagi di Profil & Pengaturan → Akun dan sinkron.")
        return try {
            http.post("${BuildConfig.SUPABASE_URL}/functions/v1/$name") {
                header("Authorization", "Bearer $token")
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                setBody(TextContent(body, ContentType.Application.Json))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("CloudService", "Panggilan $name gagal", e)
            throw when (e) {
                is io.ktor.client.plugins.HttpRequestTimeoutException,
                is java.net.SocketTimeoutException,
                is io.ktor.client.network.sockets.SocketTimeoutException,
                -> CloudException("timeout", "Server terlalu lama menjawab. Layanan AI mungkin sedang sibuk, coba lagi sebentar.")
                is java.net.UnknownHostException,
                is java.net.ConnectException,
                -> CloudException("network", "Tidak bisa terhubung ke server. Periksa koneksi internet.")
                else -> CloudException("network", "Gagal menghubungi server (${e.javaClass.simpleName}). Coba lagi.")
            }
        }
    }

    private companion object {
        const val PAGE = 1000L
    }
}
