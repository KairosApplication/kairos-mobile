package com.kairos.models.vendedor

import java.math.BigDecimal

/** purchaseId e productId formam uma chave única composta. */
data class PurchaseProduct(val id: Int, val productId: Int, val purchaseId: Int, val quantity: Int, val promotionId: Int?, val amount: BigDecimal)
