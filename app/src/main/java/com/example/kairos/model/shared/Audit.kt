package com.kairos.models.shared

import java.time.LocalDateTime

enum class AuditOperation { INSERT, UPDATE, DELETE }

data class Audit(val id: Int, val tableName: String?, val operation: AuditOperation, val timestamp: LocalDateTime, val oldData: String?, val newData: String?, val recordId: Int?, val users: String?)
