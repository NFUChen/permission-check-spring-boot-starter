package io.github.common.permission.provider

import java.util.*

/**
 * Interface for entities that have an owner.
 * Used with [@ResourceOwner][io.github.common.permission.annotation.ResourceOwner]
 * to extract the owner ID for self-scope permission checks.
 *
 * Example:
 * ```
 * data class Order(
 *     val id: UUID,
 *     val userId: UUID,
 *     val items: List<Item>
 * ) : OwnedResource {
 *     override fun getOwnerId(): UUID = userId
 * }
 * ```
 */
interface OwnedResource {
    fun getOwnerId(): UUID
}
