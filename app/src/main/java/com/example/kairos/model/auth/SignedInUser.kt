package com.example.kairos.model.auth

/** UID do Firebase é String, não o SERIAL do PostgreSQL. */
data class SignedInUser(val uid: String, val email: String, val profile: UserProfile?,
                        val emailVerified: Boolean = true, val business: BusinessIdentity? = null,
                        val businessIssue: String? = null) {
    val name: String get() = profile?.name.orEmpty()
}

data class BusinessIdentity(val userId: Long?, val employeeId: Long?, val companyId: Long?,
                            val role: String, val companyName: String, val customerId: Long? = null,
                            val apiAuthenticated: Boolean = false) {
    val isCustomer get() = userId != null && userId > 0 && role == "CUSTOMER" &&
        (apiAuthenticated || companyId != null && companyId > 0 && customerId != null && customerId > 0)
    val canRestock get() = userId != null && userId > 0 &&
        (apiAuthenticated && role in setOf("EMPLOYEE", "MANAGER") ||
            employeeId != null && employeeId > 0 && companyId != null && companyId > 0 && role in setOf("STOCKER", "MANAGER"))
}
