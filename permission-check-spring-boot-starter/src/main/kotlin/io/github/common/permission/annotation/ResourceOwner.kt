package io.github.common.permission.annotation

/**
 * Marks a method parameter as the resource owner identifier for self-scope permission checks.
 *
 * The annotated parameter must be either:
 * - A [java.util.UUID] representing the resource owner's ID directly
 * - An object implementing [io.github.common.permission.provider.OwnedResource]
 *
 * When a user only holds a `domain:action:self` permission, the framework uses this
 * annotation to locate the resource owner and verify it matches the current principal.
 *
 * Example:
 * ```
 * @Require("orders:read")
 * fun getOrder(@ResourceOwner ownerId: UUID, orderId: UUID): Order
 *
 * @Require("orders:update")
 * fun updateOrder(@ResourceOwner order: Order): Order  // Order implements OwnedResource
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ResourceOwner
