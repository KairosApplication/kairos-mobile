package com.kairos.models.repositor

/** shelfId e employeeId formam uma chave única composta. */
data class ShelfEmployee(val id: Int, val shelfId: Int?, val employeeId: Int?)
