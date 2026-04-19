package io.github.common.permission.provider

import org.springframework.security.core.context.SecurityContextHolder
import java.util.*

/**
 * Default implementation of PrincipalIdExtractor that requires principals to implement PrincipalIdentity.
 *
 * Your UserDetails (or custom principal) must implement PrincipalIdentity interface:
 * ```kotlin
 * class MyUser : UserDetails, PrincipalIdentity {
 *     override fun getPrincipalId(): UUID = this.id
 * }
 * ```
 *
 * For custom authentication schemes (API keys, custom tokens, etc.),
 * provide your own PrincipalIdExtractor implementation.
 */
class DefaultPrincipalIdExtractor : PrincipalIdExtractor {
    override fun extractPrincipalId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw IllegalStateException("No authentication found in SecurityContext")

        val principal = authentication.principal

        require(principal is PrincipalIdentity) {
            "Principal must implement PrincipalIdentity interface. " +
            "Your UserDetails should implement PrincipalIdentity to provide getPrincipalId()."
        }

        return principal.getPrincipalId()
    }
}