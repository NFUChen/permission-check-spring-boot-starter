package io.github.common.permission.service

import io.github.common.permission.annotation.Require
import io.github.common.permission.annotation.ResourceOwner
import io.github.common.permission.provider.OwnedResource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationContext
import org.mockito.kotlin.*
import java.util.*

class PermissionRegistryTest {

    private lateinit var applicationContext: ApplicationContext
    private lateinit var registry: PermissionRegistry

    @BeforeEach
    fun setUp() {
        applicationContext = mock()
        registry = PermissionRegistry(applicationContext)
    }

    @Test
    fun `should collect single permission from annotated method`() {
        val bean = SinglePermissionBean()
        whenever(applicationContext.beanDefinitionNames).thenReturn(arrayOf("singleBean"))
        whenever(applicationContext.getBean("singleBean")).thenReturn(bean)
        whenever(applicationContext.getType("singleBean")).thenReturn(SinglePermissionBean::class.java)

        val result = registry.getAllPermissions()

        assertEquals(setOf("orders:read"), result)
    }

    @Test
    fun `should collect multiple permissions from annotation with permissions array`() {
        val bean = MultiplePermissionsBean()
        whenever(applicationContext.beanDefinitionNames).thenReturn(arrayOf("multiBean"))
        whenever(applicationContext.getBean("multiBean")).thenReturn(bean)
        whenever(applicationContext.getType("multiBean")).thenReturn(MultiplePermissionsBean::class.java)

        val result = registry.getAllPermissions()

        assertEquals(setOf("users:create", "roles:assign"), result)
    }

    @Test
    fun `should collect permissions from multiple beans`() {
        val bean1 = SinglePermissionBean()
        val bean2 = AnotherPermissionBean()
        whenever(applicationContext.beanDefinitionNames).thenReturn(arrayOf("bean1", "bean2"))
        whenever(applicationContext.getBean("bean1")).thenReturn(bean1)
        whenever(applicationContext.getBean("bean2")).thenReturn(bean2)
        whenever(applicationContext.getType("bean1")).thenReturn(SinglePermissionBean::class.java)
        whenever(applicationContext.getType("bean2")).thenReturn(AnotherPermissionBean::class.java)

        val result = registry.getAllPermissions()

        assertEquals(setOf("orders:read", "billing:manage"), result)
    }

    @Test
    fun `should deduplicate permissions across methods and beans`() {
        val bean1 = SinglePermissionBean()
        val bean2 = DuplicatePermissionBean()
        whenever(applicationContext.beanDefinitionNames).thenReturn(arrayOf("bean1", "bean2"))
        whenever(applicationContext.getBean("bean1")).thenReturn(bean1)
        whenever(applicationContext.getBean("bean2")).thenReturn(bean2)
        whenever(applicationContext.getType("bean1")).thenReturn(SinglePermissionBean::class.java)
        whenever(applicationContext.getType("bean2")).thenReturn(DuplicatePermissionBean::class.java)

        val result = registry.getAllPermissions()

        assertEquals(setOf("orders:read"), result)
    }

    @Test
    fun `should collect permissions from multiple methods on same bean`() {
        val bean = MultiMethodBean()
        whenever(applicationContext.beanDefinitionNames).thenReturn(arrayOf("multiMethod"))
        whenever(applicationContext.getBean("multiMethod")).thenReturn(bean)
        whenever(applicationContext.getType("multiMethod")).thenReturn(MultiMethodBean::class.java)

        val result = registry.getAllPermissions()

        assertEquals(setOf("orders:read", "orders:create", "orders:delete"), result)
    }

    @Test
    fun `should return empty set when no beans have Require annotations`() {
        val bean = NoAnnotationBean()
        whenever(applicationContext.beanDefinitionNames).thenReturn(arrayOf("plainBean"))
        whenever(applicationContext.getBean("plainBean")).thenReturn(bean)
        whenever(applicationContext.getType("plainBean")).thenReturn(NoAnnotationBean::class.java)

        val result = registry.getAllPermissions()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should return empty set when no beans exist`() {
        whenever(applicationContext.beanDefinitionNames).thenReturn(emptyArray())

        val result = registry.getAllPermissions()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should skip beans that fail to resolve type`() {
        val bean = SinglePermissionBean()
        whenever(applicationContext.beanDefinitionNames).thenReturn(arrayOf("goodBean", "badBean"))
        whenever(applicationContext.getType("goodBean")).thenReturn(SinglePermissionBean::class.java)
        whenever(applicationContext.getBean("goodBean")).thenReturn(bean)
        whenever(applicationContext.getType("badBean")).thenReturn(null)

        val result = registry.getAllPermissions()

        assertEquals(setOf("orders:read"), result)
    }

    @Test
    fun `should collect permissions from interface methods`() {
        val bean = InterfaceImplBean()
        whenever(applicationContext.beanDefinitionNames).thenReturn(arrayOf("interfaceBean"))
        whenever(applicationContext.getBean("interfaceBean")).thenReturn(bean)
        whenever(applicationContext.getType("interfaceBean")).thenReturn(InterfaceImplBean::class.java)

        val result = registry.getAllPermissions()

        assertEquals(setOf("reports:view"), result)
    }

    // --- Test fixtures ---

    open class SinglePermissionBean {
        @Require("orders:read")
        open fun readOrders() {}
    }

    open class MultiplePermissionsBean {
        @Require(permissions = ["users:create", "roles:assign"])
        open fun createUserWithRole() {}
    }

    open class AnotherPermissionBean {
        @Require("billing:manage")
        open fun manageBilling() {}
    }

    open class DuplicatePermissionBean {
        @Require("orders:read")
        open fun alsoReadOrders() {}
    }

    open class MultiMethodBean {
        @Require("orders:read")
        open fun readOrders() {}

        @Require("orders:create")
        open fun createOrder() {}

        @Require("orders:delete")
        open fun deleteOrder() {}
    }

    open class NoAnnotationBean {
        open fun doSomething() {}
    }

    interface AnnotatedInterface {
        @Require("reports:view")
        fun viewReports()
    }

    open class InterfaceImplBean : AnnotatedInterface {
        override fun viewReports() {}
    }

    // --- ResourceOwner test fixtures ---

    open class ResourceOwnerBean {
        @Require("orders:read")
        open fun getOrder(@ResourceOwner ownerId: UUID, orderId: UUID) {}
    }

    open class MultiPermResourceOwnerBean {
        @Require(permissions = ["orders:read", "orders:update"])
        open fun manageOrder(@ResourceOwner ownerId: UUID, orderId: UUID) {}
    }

    data class TestOwnedResource(private val ownerId: UUID) : OwnedResource {
        override fun getOwnerId(): UUID = ownerId
    }

    open class OwnedResourceParamBean {
        @Require("orders:update")
        open fun updateOrder(@ResourceOwner order: TestOwnedResource) {}
    }

    // --- ResourceOwner tests ---

    @Test
    fun `should generate self variant for method with ResourceOwner parameter`() {
        val bean = ResourceOwnerBean()
        whenever(applicationContext.beanDefinitionNames).thenReturn(arrayOf("roBean"))
        whenever(applicationContext.getBean("roBean")).thenReturn(bean)
        whenever(applicationContext.getType("roBean")).thenReturn(ResourceOwnerBean::class.java)

        val result = registry.getAllPermissions()

        assertEquals(setOf("orders:read", "orders:read:self"), result)
    }

    @Test
    fun `should not generate self variant for method without ResourceOwner parameter`() {
        val bean = SinglePermissionBean()
        whenever(applicationContext.beanDefinitionNames).thenReturn(arrayOf("singleBean"))
        whenever(applicationContext.getBean("singleBean")).thenReturn(bean)
        whenever(applicationContext.getType("singleBean")).thenReturn(SinglePermissionBean::class.java)

        val result = registry.getAllPermissions()

        assertEquals(setOf("orders:read"), result)
        assertFalse(result.contains("orders:read:self"))
    }

    @Test
    fun `should generate self variants for multiple permissions on ResourceOwner method`() {
        val bean = MultiPermResourceOwnerBean()
        whenever(applicationContext.beanDefinitionNames).thenReturn(arrayOf("mpBean"))
        whenever(applicationContext.getBean("mpBean")).thenReturn(bean)
        whenever(applicationContext.getType("mpBean")).thenReturn(MultiPermResourceOwnerBean::class.java)

        val result = registry.getAllPermissions()

        assertEquals(
            setOf("orders:read", "orders:read:self", "orders:update", "orders:update:self"),
            result
        )
    }

    @Test
    fun `should generate self variant for OwnedResource parameter`() {
        val bean = OwnedResourceParamBean()
        whenever(applicationContext.beanDefinitionNames).thenReturn(arrayOf("orpBean"))
        whenever(applicationContext.getBean("orpBean")).thenReturn(bean)
        whenever(applicationContext.getType("orpBean")).thenReturn(OwnedResourceParamBean::class.java)

        val result = registry.getAllPermissions()

        assertEquals(setOf("orders:update", "orders:update:self"), result)
    }
}
