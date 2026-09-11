package com.kairos.models.vendedor

import java.time.LocalDateTime

enum class PurchaseStatus { IN_PROGRESS, COMPLETED, CANCELLED }

data class Purchase(val id: Int, val customerId: Int, val date: LocalDateTime?, val status: PurchaseStatus)
