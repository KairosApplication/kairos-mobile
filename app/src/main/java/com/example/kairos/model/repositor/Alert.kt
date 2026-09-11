package com.kairos.models.repositor

import java.time.LocalDate
import java.time.LocalDateTime

data class Alert(val id: Int, val stockoutDate: LocalDate?, val employeeId: Int?, val shelfId: Int, val productId: Int?, val description: String, val status: String, val resolutionDate: LocalDateTime?)
