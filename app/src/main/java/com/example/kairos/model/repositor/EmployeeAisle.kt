package com.kairos.models.repositor

/** employeeId e aisleId formam uma chave única composta. */
data class EmployeeAisle(val id: Int, val aisleId: Int?, val employeeId: Int?)
