package com.kairos.models.repositor

/** inventoryId e productId formam uma chave única composta. */
data class ProductInventory(val id: Int, val inventoryId: Int?, val productId: Int?, val productQuantity: Int?)
