package com.kairos.models.repositor

/** productId e shelfId formam uma chave única composta. */
data class ProductShelf(val id: Int, val productId: Int?, val shelfId: Int?, val productQuantity: Int)
