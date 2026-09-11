package com.kairos.models.cliente

import java.math.BigDecimal
import java.time.LocalDateTime

data class Promotion(val id: Int, val shelfId: Int?, val productId: Int?, val promotionStartDate: LocalDateTime, val promotionEndDate: LocalDateTime, val description: String?, val discountPercentage: BigDecimal?)
