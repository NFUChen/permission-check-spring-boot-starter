package io.github.common.permission.aop

import io.github.common.permission.annotation.Require
import io.github.common.permission.annotation.ResourceOwner
import io.github.common.permission.autoconfigure.PermissionCheckProperties
import io.github.common.permission.exception.PermissionDeniedException
import io.github.common.permission.provider.OwnedResource
import io.github.common.permission.provider.PrincipalIdExtractor
import io.github.common.permission.service.PermissionEvaluator
import io.github.common.permission.service.PermissionResult
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class PermissionAspectTest {

    private lateinit var permissionEvaluator: PermissionEvaluator
    private lateinit var principalIdExtractor: PrincipalIdExtractor
    private lateinit var aspect: PermissionAspect

    private val currentUserId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        permissionEvaluator = mock()
        principalIdExtractor = mock()
        val loggingProps = PermissionCheckProperties.LoggingProperties(
            debugEnabled = false,
            auditEnabled = false
        )
        aspect = PermissionAspect(permissionEvaluator, principalIdExtractor, loggingProps)
        whenever(principalIdExtractor.extractPrincipalId()).thenReturn(currentUserId)
    }

    @Test
    fun `GRANTED proceeds without ownership check`() {
        val joinPoint = mockJoinPoint("readOrders", NoResourceOwnerBean::class.java)
        val require = NoResourceOwnerBean::class.java.getMethod("readOrders")
            .getAnnotation(Require::class.java)

        whenever(permissionEvaluator.evaluatePermission(currentUserId, "orders:read"))
            .thenReturn(PermissionResult.GRANTED)
        whenever(joinPoint.proceed()).thenReturn("result")

        val result = aspect.around(joinPoint, require)

        assertEquals("result", result)
        verify(joinPoint).proceed()
    }

    @Test
    fun `DENIED throws PermissionDeniedException`() {
        val joinPoint = mockJoinPoint("readOrders", NoResourceOwnerBean::class.java)
        val require = NoResourceOwnerBean::class.java.getMethod("readOrders")
            .getAnnotation(Require::class.java)

        whenever(permissionEvaluator.evaluatePermission(currentUserId, "orders:read"))
            .thenReturn(PermissionResult.DENIED)

        assertThrows<PermissionDeniedException> {
            aspect.around(joinPoint, require)
        }
    }

    @Test
    fun `GRANTED_SELF_ONLY with matching UUID owner proceeds`() {
        val joinPoint = mockJoinPoint(
            "getOrder", UuidOwnerBean::class.java,
            args = arrayOf(currentUserId, UUID.randomUUID())
        )
        val require = UuidOwnerBean::class.java.getMethod("getOrder", UUID::class.java, UUID::class.java)
            .getAnnotation(Require::class.java)

        whenever(permissionEvaluator.evaluatePermission(currentUserId, "orders:read"))
            .thenReturn(PermissionResult.GRANTED_SELF_ONLY)
        whenever(joinPoint.proceed()).thenReturn("order")

        val result = aspect.around(joinPoint, require)

        assertEquals("order", result)
    }

    @Test
    fun `GRANTED_SELF_ONLY with non-matching UUID owner throws`() {
        val joinPoint = mockJoinPoint(
            "getOrder", UuidOwnerBean::class.java,
            args = arrayOf(otherUserId, UUID.randomUUID())
        )
        val require = UuidOwnerBean::class.java.getMethod("getOrder", UUID::class.java, UUID::class.java)
            .getAnnotation(Require::class.java)

        whenever(permissionEvaluator.evaluatePermission(currentUserId, "orders:read"))
            .thenReturn(PermissionResult.GRANTED_SELF_ONLY)

        assertThrows<PermissionDeniedException> {
            aspect.around(joinPoint, require)
        }
    }

    @Test
    fun `GRANTED_SELF_ONLY with OwnedResource matching owner proceeds`() {
        val resource = TestOwnedResource(currentUserId)
        val joinPoint = mockJoinPoint(
            "updateOrder", OwnedResourceBean::class.java,
            args = arrayOf(resource)
        )
        val require = OwnedResourceBean::class.java.getMethod("updateOrder", TestOwnedResource::class.java)
            .getAnnotation(Require::class.java)

        whenever(permissionEvaluator.evaluatePermission(currentUserId, "orders:update"))
            .thenReturn(PermissionResult.GRANTED_SELF_ONLY)
        whenever(joinPoint.proceed()).thenReturn("updated")

        val result = aspect.around(joinPoint, require)

        assertEquals("updated", result)
    }

    @Test
    fun `GRANTED_SELF_ONLY with OwnedResource non-matching owner throws`() {
        val resource = TestOwnedResource(otherUserId)
        val joinPoint = mockJoinPoint(
            "updateOrder", OwnedResourceBean::class.java,
            args = arrayOf(resource)
        )
        val require = OwnedResourceBean::class.java.getMethod("updateOrder", TestOwnedResource::class.java)
            .getAnnotation(Require::class.java)

        whenever(permissionEvaluator.evaluatePermission(currentUserId, "orders:update"))
            .thenReturn(PermissionResult.GRANTED_SELF_ONLY)

        assertThrows<PermissionDeniedException> {
            aspect.around(joinPoint, require)
        }
    }

    @Test
    fun `GRANTED_SELF_ONLY without ResourceOwner annotation throws`() {
        val joinPoint = mockJoinPoint("readOrders", NoResourceOwnerBean::class.java)
        val require = NoResourceOwnerBean::class.java.getMethod("readOrders")
            .getAnnotation(Require::class.java)

        whenever(permissionEvaluator.evaluatePermission(currentUserId, "orders:read"))
            .thenReturn(PermissionResult.GRANTED_SELF_ONLY)

        assertThrows<PermissionDeniedException> {
            aspect.around(joinPoint, require)
        }
    }

    // --- helpers ---

    private fun mockJoinPoint(
        methodName: String,
        targetClass: Class<*>,
        args: Array<Any> = emptyArray()
    ): ProceedingJoinPoint {
        val joinPoint: ProceedingJoinPoint = mock()
        val signature: MethodSignature = mock()
        val method = targetClass.methods.first { it.name == methodName }

        whenever(signature.method).thenReturn(method)
        whenever(joinPoint.signature).thenReturn(signature)
        whenever(joinPoint.target).thenReturn(targetClass.getDeclaredConstructor().newInstance())
        whenever(joinPoint.args).thenReturn(args)

        return joinPoint
    }

    // --- test fixtures ---

    open class NoResourceOwnerBean {
        @Require("orders:read")
        open fun readOrders() {}
    }

    open class UuidOwnerBean {
        @Require("orders:read")
        open fun getOrder(@ResourceOwner ownerId: UUID, orderId: UUID) {}
    }

    data class TestOwnedResource(private val ownerId: UUID) : OwnedResource {
        override fun getOwnerId(): UUID = ownerId
    }

    open class OwnedResourceBean {
        @Require("orders:update")
        open fun updateOrder(@ResourceOwner order: TestOwnedResource) {}
    }
}
