package com.aamnnr.duitku.data.local

import androidx.room.TypeConverter
import com.aamnnr.duitku.domain.ProfileType
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.TransferType

class Converters {
    @TypeConverter fun profileTypeToString(v: ProfileType): String = v.name
    @TypeConverter fun stringToProfileType(v: String): ProfileType = ProfileType.valueOf(v)

    @TypeConverter fun txTypeToString(v: TransactionType): String = v.name
    @TypeConverter fun stringToTxType(v: String): TransactionType = TransactionType.valueOf(v)

    @TypeConverter fun transferTypeToString(v: TransferType?): String? = v?.name
    @TypeConverter fun stringToTransferType(v: String?): TransferType? = v?.let(TransferType::valueOf)
}
