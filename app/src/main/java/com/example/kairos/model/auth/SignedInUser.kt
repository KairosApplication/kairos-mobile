package com.example.kairos.model.auth

/** UID do Firebase é String, não o SERIAL do PostgreSQL. */
data class SignedInUser(val uid: String, val email: String, val profile: UserProfile?,
                        val emailVerified: Boolean = true, val business: BusinessIdentity? = null,
                        val businessIssue: String? = null) {
    val name: String get() = profile?.name.orEmpty()
}

data class BusinessIdentity(val userId: Long?, val employeeId: Long?, val companyId: Long?,
                            val role: String, val companyName: String) {
    val canRestock get() = userId != null && userId > 0 && employeeId != null && employeeId > 0 && companyId != null && companyId > 0 && role in setOf("STOCKER", "MANAGER")
}
