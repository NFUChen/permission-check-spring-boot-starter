package io.github.common.permission.service

import io.github.common.permission.annotation.Require
import io.github.common.permission.annotation.extractPermissions
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.ApplicationContext
import java.util.Collections

/**
 * Collects all permissions defined via @Require annotations across all Spring beans at startup.
 *
 * After application context initialization, [definedPermissions] contains the complete set
 * of unique permission strings found in the codebase. Useful for:
 * - Generating permission documentation
 * - Building admin UIs for permission assignment
 * - Validating database permission data against code
 * - Exposing via Actuator endpoints
 */
class PermissionRegistry(
    private val applicationContext: ApplicationContext
) : SmartInitializingSingleton {

    private val _permissions = mutableSetOf<String>()

    /**
     * All unique permission strings found in @Require annotations across the application.
     * Populated after all singletons are instantiated.
     */
    val definedPermissions: Set<String>
        get() = Collections.unmodifiableSet(_permissions)

    override fun afterSingletonsInstantiated() {
        for (beanName in applicationContext.beanDefinitionNames) {
            applicationContext.getType(beanName) ?: continue
            val bean = try {
                applicationContext.getBean(beanName)
            } catch (_: Exception) {
                continue
            }

            for (method in bean.javaClass.methods) {
                val require = method.getAnnotation(Require::class.java) ?: continue
                _permissions.addAll(require.extractPermissions())
            }
        }
    }
}
