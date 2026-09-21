package com.kairos.models.repositor

import java.time.LocalDateTime

data class Restocking(
    val id: Int,
    val inventoryId: Int?,
    val employeeId: Int?,
    val shelfId: Int?,
    val restockingDate: LocalDateTime?,
    val restockedQuantity: Int,
    val productId: Int?,
    /** Corresponde a qnt_avarias no banco. */
    val damagedQuantity: Int = 0
)
