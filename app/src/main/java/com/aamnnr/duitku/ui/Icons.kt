package com.aamnnr.duitku.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Ikon disimpan di database sebagai kunci string agar tidak bergantung pada library ikon. */
object AppIcons {
    private val all: Map<String, ImageVector> = linkedMapOf(
        "restaurant" to Icons.Filled.Restaurant,
        "cafe" to Icons.Filled.LocalCafe,
        "grocery" to Icons.Filled.LocalGroceryStore,
        "shopping_cart" to Icons.Filled.ShoppingCart,
        "clothes" to Icons.Filled.Checkroom,
        "car" to Icons.Filled.DirectionsCar,
        "fuel" to Icons.Filled.LocalGasStation,
        "flight" to Icons.Filled.Flight,
        "receipt" to Icons.Filled.Receipt,
        "home" to Icons.Filled.Home,
        "wifi" to Icons.Filled.Wifi,
        "bolt" to Icons.Filled.Bolt,
        "health" to Icons.Filled.LocalHospital,
        "fitness" to Icons.Filled.FitnessCenter,
        "spa" to Icons.Filled.Spa,
        "movie" to Icons.Filled.Movie,
        "game" to Icons.Filled.SportsEsports,
        "school" to Icons.Filled.School,
        "child" to Icons.Filled.ChildCare,
        "pets" to Icons.Filled.Pets,
        "gift" to Icons.Filled.CardGiftcard,
        "charity" to Icons.Filled.VolunteerActivism,
        "payments" to Icons.Filled.Payments,
        "work" to Icons.Filled.Work,
        "star" to Icons.Filled.Star,
        "store" to Icons.Filled.Storefront,
        "trending_up" to Icons.AutoMirrored.Filled.TrendingUp,
        "inventory" to Icons.Filled.Inventory,
        "groups" to Icons.Filled.Groups,
        "build" to Icons.Filled.Build,
        "campaign" to Icons.Filled.Campaign,
        "handyman" to Icons.Filled.Handyman,
        "handshake" to Icons.Filled.Handshake,
        "savings" to Icons.Filled.Savings,
        "bank" to Icons.Filled.AccountBalance,
        "wallet" to Icons.Filled.AccountBalanceWallet,
        "card" to Icons.Filled.CreditCard,
        "phone" to Icons.Filled.PhoneAndroid,
        "qr" to Icons.Filled.QrCode2,
        "more" to Icons.Filled.MoreHoriz,
    )

    val categoryKeys: List<String> = all.keys.filterNot { it in setOf("wallet", "card", "phone", "qr") }
    val walletKeys: List<String> = listOf("wallet", "bank", "card", "phone", "qr", "payments", "savings", "store")

    fun get(key: String?): ImageVector = all[key] ?: Icons.Filled.Category
}

val PaletteColors: List<Long> = listOf(
    0xFF3B2F2A, 0xFFC08A5B, 0xFFB8653F, 0xFF9E4F45, 0xFFA3626F, 0xFF7A5C7A,
    0xFF5B7085, 0xFF4E6E8E, 0xFF4F7F7A, 0xFF6E8B6B, 0xFF3F7D58, 0xFF8C7A4B,
    0xFF857A72, 0xFF5E4B3C,
)
