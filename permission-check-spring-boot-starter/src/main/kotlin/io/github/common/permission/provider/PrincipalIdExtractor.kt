package io.github.common.permission.provider

import java.util.*

/**
 * Interface for user principals that can provide their identity for permission checking.
 *
 * Your UserDetails implementation should implement this interface to work with
 * the permission system.
 *
 * Example:
 * ```kotlin
 * class MyUser : UserDetails, PrincipalIdentity {
 *     override fun getPrincipalId(): UUID = this.id
 * }
 * ```
 */
interface PrincipalIdentity {
    /**
     * Get the unique identifier for this principal.
     * @return Principal's unique identifier (typically user ID)
     */
    fun getPrincipalId(): UUID
}

/**
 * Interface for extracting principal identity from the current security context.
 * This abstraction allows the permission system to remain completely decoupled
 * from specific user models and security implementations.
 *
 * The default implementation requires principals to implement PrincipalIdentity.
 * For custom authentication schemes, provide your own implementation.
 */
interface PrincipalIdExtractor {
    /**
     * Extract principal ID from the current security context.
     * @return Principal ID if authenticated
     * @throws IllegalStateException if no authenticated principal found
     */
    fun extractPrincipalId(): UUID
}