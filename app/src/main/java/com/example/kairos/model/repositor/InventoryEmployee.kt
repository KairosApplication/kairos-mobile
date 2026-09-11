package com.kairos.models.repositor

/** inventoryId e employeeId formam uma chave única composta. */
data class InventoryEmployee(val id: Int, val inventoryId: Int, val employeeId: Int)
