package com.aamnnr.duitku.data.local

import com.aamnnr.duitku.domain.ProfileType
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.TransactionType.EXPENSE
import com.aamnnr.duitku.domain.TransactionType.INCOME
import java.util.UUID

/** Kategori & dompet bawaan untuk profil baru. Ikon memakai kunci dari `ui/Icons.kt`. */
object DefaultData {

    private data class Seed(val name: String, val type: TransactionType, val icon: String, val color: Long)

    private val personal = listOf(
        Seed("Makan & Minum", EXPENSE, "restaurant", 0xFFB8653F),
        Seed("Belanja", EXPENSE, "shopping_cart", 0xFFA3626F),
        Seed("Transportasi", EXPENSE, "car", 0xFF5B7085),
        Seed("Tagihan", EXPENSE, "receipt", 0xFF7A5C7A),
        Seed("Rumah", EXPENSE, "home", 0xFF4F7F7A),
        Seed("Pulsa & Internet", EXPENSE, "wifi", 0xFF4F7F7A),
        Seed("Kesehatan", EXPENSE, "health", 0xFF9E4F45),
        Seed("Hiburan", EXPENSE, "movie", 0xFF7A5C7A),
        Seed("Pendidikan", EXPENSE, "school", 0xFF5B7085),
        Seed("Sedekah & Hadiah", EXPENSE, "gift", 0xFFC08A5B),
        Seed("Lainnya", EXPENSE, "more", 0xFF857A72),
        Seed("Gaji", INCOME, "payments", 0xFF3F7D58),
        Seed("Bonus", INCOME, "star", 0xFF8C7A4B),
        Seed("Usaha Sampingan", INCOME, "store", 0xFF4F7F7A),
        Seed("Investasi", INCOME, "trending_up", 0xFF4E6E8E),
        Seed("Hadiah", INCOME, "gift", 0xFFC08A5B),
        Seed("Lainnya", INCOME, "more", 0xFF857A72),
    )

    private val business = listOf(
        Seed("Bahan Baku", EXPENSE, "inventory", 0xFFB8653F),
        Seed("Gaji Karyawan", EXPENSE, "groups", 0xFF7A5C7A),
        Seed("Sewa Tempat", EXPENSE, "home", 0xFF4F7F7A),
        Seed("Listrik & Air", EXPENSE, "bolt", 0xFFC08A5B),
        Seed("Operasional", EXPENSE, "build", 0xFF857A72),
        Seed("Marketing", EXPENSE, "campaign", 0xFFA3626F),
        Seed("Transportasi", EXPENSE, "car", 0xFF5B7085),
        Seed("Peralatan", EXPENSE, "handyman", 0xFF5B7085),
        Seed("Pajak & Retribusi", EXPENSE, "bank", 0xFF9E4F45),
        Seed("Lainnya", EXPENSE, "more", 0xFF857A72),
        Seed("Penjualan", INCOME, "store", 0xFF3F7D58),
        Seed("Jasa", INCOME, "handshake", 0xFF4F7F7A),
        Seed("Tambahan Modal", INCOME, "savings", 0xFF4E6E8E),
        Seed("Lainnya", INCOME, "more", 0xFF857A72),
    )

    fun categoriesFor(profileId: String, type: ProfileType, now: Long = System.currentTimeMillis()): List<CategoryEntity> =
        (if (type == ProfileType.BUSINESS) business else personal).map {
            CategoryEntity(
                id = UUID.randomUUID().toString(),
                profileId = profileId,
                name = it.name,
                type = it.type,
                icon = it.icon,
                color = it.color,
                isDefault = true,
                updatedAt = now,
            )
        }

    fun mainWallet(profileId: String, now: Long) = WalletEntity(
        id = UUID.randomUUID().toString(),
        profileId = profileId,
        name = "Dompet Utama",
        icon = "wallet",
        color = 0xFF3B2F2A,
        isDefault = true,
        createdAt = now,
        updatedAt = now,
    )
}
