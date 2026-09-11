package com.kairos.models.cliente

import java.math.BigDecimal

data class Product(val id: Int, val brand: String?, val price: BigDecimal?, val name: String, val categoryId: Int?)
