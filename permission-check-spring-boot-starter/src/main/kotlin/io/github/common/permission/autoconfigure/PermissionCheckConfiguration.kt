package io.github.common.permission.autoconfigure

import io.github.common.permission.aop.PermissionAspect
import io.github.common.permission.provider.PrincipalIdExtractor
import io.github.common.permission.provider.DefaultPrincipalIdExtractor
import io.github.common.permission.provider.DefaultPermissionRepository
import io.github.common.permission.provider.PermissionRepository
import io.github.common.permission.service.CachedPermissionService
import io.github.common.permission.service.DefaultPermissionEvaluator
import io.github.common.permission.service.PermissionEvaluator
import io.github.common.permission.service.PermissionRegistry
import io.github.common.permission.service.PermissionService
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy

/**
 * Auto-configuration for permission checking system.
 * This configuration is automatically applied when @EnablePermissionCheck is used.
 */
@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(PermissionCheckProperties::class)
class PermissionCheckConfiguration {

    /**
     * Create the permission evaluator bean.
     * Uses the default implementation unless a custom one is provided.
     */
    @Bean
    @ConditionalOnMissingBean
    fun permissionEvaluator(permissionService: PermissionService): PermissionEvaluator {
        return DefaultPermissionEvaluator(permissionService)
    }

    /**
     * Create the permission service bean.
     * Uses cached implementation by default unless a custom one is provided.
     */
    @Bean
    @ConditionalOnMissingBean
    fun permissionService(repository: PermissionRepository): PermissionService {
        return CachedPermissionService(repository)
    }

    /**
     * Create the AOP aspect for permission checking.
     * This is the core component that intercepts @Require annotated methods.
     */
    @Bean
    @ConditionalOnMissingBean
    fun permissionAspect(
        evaluator: PermissionEvaluator,
        principalIdExtractor: PrincipalIdExtractor,
        properties: PermissionCheckProperties
    ): PermissionAspect {
        return PermissionAspect(evaluator, principalIdExtractor, properties.logging)
    }

    /**
     * Create a cache manager for permission caching if none exists.
     * This is only created if caching is enabled in properties.
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager::class)
    fun permissionCacheManager(properties: PermissionCheckProperties): CacheManager? {
        return if (properties.cache.enabled) {
            ConcurrentMapCacheManager(properties.cache.cacheName)
        } else {
            null
        }
    }

    /**
     * Registry that collects all @Require permissions at startup.
     * Inject this bean to access the full set of defined permissions.
     */
    @Bean
    @ConditionalOnMissingBean
    fun permissionRegistry(applicationContext: ApplicationContext): PermissionRegistry {
        return PermissionRegistry(applicationContext)
    }

    /**
     * Provide default PrincipalIdExtractor if none is implemented by the application.
     * Requires principals to implement PrincipalIdentity interface.
     * Only available when Spring Security is on the classpath.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = ["org.springframework.security.core.context.SecurityContextHolder"])
    fun defaultPrincipalIdExtractor(): PrincipalIdExtractor {
        return DefaultPrincipalIdExtractor()
    }

    /**
     * Provide default PermissionRepository if none is implemented by the application.
     * This converts Spring Security roles to permissions using common patterns.
     * Only available when Spring Security is on the classpath.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = ["org.springframework.security.core.GrantedAuthority"])
    fun defaultPermissionRepository(): PermissionRepository {
        return DefaultPermissionRepository()
    }
}