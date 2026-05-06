package io.github.common.permission.system

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl

/**
 * Provides a way to execute code blocks with system-level permissions.
 *
 * Usage:
 * ```
 * systemAuthenticationContext.runAsSystem {
 *     subscriptionService.getAllUnexpiredSubscriptions()
 * }
 * ```
 */
class SystemAuthenticationContext {
    fun <T> runAsRoot(block: () -> T): T {
        val previousContext = SecurityContextHolder.getContext()
        try {
            val systemPrincipal = SystemPrincipal.INSTANCE
            val authentication = UsernamePasswordAuthenticationToken(
                systemPrincipal,
                null,
                listOf(SimpleGrantedAuthority("*:*") )
            )
            SecurityContextHolder.setContext(SecurityContextImpl(authentication))
            return block()
        } finally {
            SecurityContextHolder.setContext(previousContext)
        }
    }
}