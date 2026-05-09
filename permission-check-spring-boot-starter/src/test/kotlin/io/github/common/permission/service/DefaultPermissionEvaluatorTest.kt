package io.github.common.permission.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class DefaultPermissionEvaluatorTest {

    private lateinit var permissionService: PermissionService
    private lateinit var evaluator: DefaultPermissionEvaluator
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        permissionService = mock()
        evaluator = DefaultPermissionEvaluator(permissionService)
    }

    @Test
    fun `direct match returns GRANTED`() {
        whenever(permissionService.getUserPermissions(userId))
            .thenReturn(setOf("orders:read"))

        val result = evaluator.evaluatePermission(userId, "orders:read")

        assertEquals(PermissionResult.GRANTED, result)
    }

    @Test
    fun `domain wildcard returns GRANTED`() {
        whenever(permissionService.getUserPermissions(userId))
            .thenReturn(setOf("orders:*"))

        val result = evaluator.evaluatePermission(userId, "orders:read")

        assertEquals(PermissionResult.GRANTED, result)
    }

    @Test
    fun `action wildcard returns GRANTED`() {
        whenever(permissionService.getUserPermissions(userId))
            .thenReturn(setOf("*:read"))

        val result = evaluator.evaluatePermission(userId, "orders:read")

        assertEquals(PermissionResult.GRANTED, result)
    }

    @Test
    fun `system-wide wildcard returns GRANTED`() {
        whenever(permissionService.getUserPermissions(userId))
            .thenReturn(setOf("*:*"))

        val result = evaluator.evaluatePermission(userId, "orders:read")

        assertEquals(PermissionResult.GRANTED, result)
    }

    @Test
    fun `no matching permission returns DENIED`() {
        whenever(permissionService.getUserPermissions(userId))
            .thenReturn(setOf("users:read"))

        val result = evaluator.evaluatePermission(userId, "orders:read")

        assertEquals(PermissionResult.DENIED, result)
    }

    @Test
    fun `self permission returns GRANTED_SELF_ONLY`() {
        whenever(permissionService.getUserPermissions(userId))
            .thenReturn(setOf("orders:read:self"))

        val result = evaluator.evaluatePermission(userId, "orders:read")

        assertEquals(PermissionResult.GRANTED_SELF_ONLY, result)
    }

    @Test
    fun `self domain wildcard returns GRANTED_SELF_ONLY`() {
        whenever(permissionService.getUserPermissions(userId))
            .thenReturn(setOf("orders:*:self"))

        val result = evaluator.evaluatePermission(userId, "orders:read")

        assertEquals(PermissionResult.GRANTED_SELF_ONLY, result)
    }

    @Test
    fun `self action wildcard returns GRANTED_SELF_ONLY`() {
        whenever(permissionService.getUserPermissions(userId))
            .thenReturn(setOf("*:read:self"))

        val result = evaluator.evaluatePermission(userId, "orders:read")

        assertEquals(PermissionResult.GRANTED_SELF_ONLY, result)
    }

    @Test
    fun `full permission wins over self permission returns GRANTED`() {
        whenever(permissionService.getUserPermissions(userId))
            .thenReturn(setOf("orders:read", "orders:read:self"))

        val result = evaluator.evaluatePermission(userId, "orders:read")

        assertEquals(PermissionResult.GRANTED, result)
    }

    @Test
    fun `malformed permission returns DENIED`() {
        whenever(permissionService.getUserPermissions(userId))
            .thenReturn(setOf("orders:read"))

        val result = evaluator.evaluatePermission(userId, "malformed")

        assertEquals(PermissionResult.DENIED, result)
    }

    @Test
    fun `hasPermission returns true for GRANTED`() {
        whenever(permissionService.getUserPermissions(userId))
            .thenReturn(setOf("orders:read"))

        assertTrue(evaluator.hasPermission(userId, "orders:read"))
    }

    @Test
    fun `hasPermission returns true for GRANTED_SELF_ONLY`() {
        whenever(permissionService.getUserPermissions(userId))
            .thenReturn(setOf("orders:read:self"))

        assertTrue(evaluator.hasPermission(userId, "orders:read"))
    }

    @Test
    fun `hasPermission returns false for DENIED`() {
        whenever(permissionService.getUserPermissions(userId))
            .thenReturn(setOf("users:read"))

        assertFalse(evaluator.hasPermission(userId, "orders:read"))
    }
}
