---
name: permission-check-starter
description: Expert guidance for developing and integrating the permission-check-spring-boot-starter library — a Kotlin Spring Boot starter for annotation-based permission checking with AOP. Use this skill whenever working on the permission-check-spring-boot-starter codebase, integrating it into a Spring Boot application, designing permission schemes, implementing @Require annotations, creating PermissionRepository or PrincipalIdExtractor implementations, configuring permission caching, or troubleshooting permission-related issues. Also use when the user mentions permission checking, RBAC, access control annotations, or AOP-based authorization in the context of Spring Boot / Kotlin projects.
---

# Permission Check Spring Boot Starter — Development & Integration Guide

This skill covers two modes of work:

1. **Library development** — contributing code, adding features, fixing bugs, writing tests for the starter itself
2. **Application integration** — helping users integrate and configure the starter in their Spring Boot applications

## Architecture Overview

The starter uses AOP to intercept methods annotated with `@Require`, extract the current user's identity, fetch their permissions (with caching), and evaluate whether access should be granted using wildcard matching.

```
Method Call -> @Require -> PermissionAspect (AOP Around)
                               |
                    PrincipalIdExtractor.extractPrincipalId()
                               |
                    PermissionEvaluator.hasPermission(uuid, "domain:action")
                               |
                    PermissionService.getUserPermissions(uuid)
                               |                      |
                          [Cache Hit]           [Cache Miss]
                                                     |
                                   PermissionRepository.getUserPermissions(uuid)
                               |
                    Wildcard Matching & Validation
                               |
              [GRANTED] -> Proceed | [DENIED] -> PermissionDeniedException
```

### Core Components

| Component | Interface | Default Impl | Package |
|-----------|-----------|-------------|---------|
| Permission annotation | `@Require` | -- | `annotation` |
| AOP interceptor | -- | `PermissionAspect` | `aop` |
| Identity extraction | `PrincipalIdExtractor` | `DefaultPrincipalIdExtractor` | `provider` |
| Principal identity | `PrincipalIdentity` | -- (user implements) | `provider` |
| Permission source | `PermissionRepository` | `DefaultPermissionRepository` | `provider` |
| Direct permissions | `PermissionAware` | -- (user implements) | `provider` |
| Permission evaluation | `PermissionEvaluator` | `DefaultPermissionEvaluator` | `service` |
| Caching layer | `PermissionService` | `CachedPermissionService` | `service` |
| Auto-configuration | -- | `PermissionCheckAutoConfiguration` | `autoconfigure` |
| Config properties | `PermissionCheckProperties` | -- | `autoconfigure` |
| Enable annotation | `@EnablePermissionCheck` | -- | `autoconfigure` |
| Exception | `PermissionDeniedException` | -- | `exception` |

### Source Layout

All source lives under `permission-check-spring-boot-starter/src/main/kotlin/io/github/common/permission/`:

```
annotation/   @Require annotation + extractPermissions() extension
aop/          PermissionAspect (AOP Around advice)
autoconfigure/ Auto-configuration, @EnablePermissionCheck, properties
exception/    PermissionDeniedException
provider/     PrincipalIdExtractor, PrincipalIdentity, PermissionRepository, PermissionAware
service/      PermissionEvaluator, PermissionService, cached/default impls
```

## Permission Format

Two-part `domain:action` format:

- **Direct match**: `"orders:read"` matches `"orders:read"`
- **Domain wildcard**: `"orders:*"` matches `"orders:read"`, `"orders:create"`, etc.
- **Action wildcard**: `"*:read"` matches `"orders:read"`, `"users:read"`, etc.
- **System-wide**: `"*:*"` matches everything

Multi-level hierarchies like `domain:subdomain:action` are not supported — `split(":", limit = 2)` in `DefaultPermissionEvaluator` treats everything after the first colon as the action part.

## Development Mode — Contributing to the Starter

### Tech Stack

- **Language**: Kotlin 1.9.25, Java 17
- **Framework**: Spring Boot 3.2.5
- **Build**: Gradle Kotlin DSL, multi-module project
- **Publishing**: Maven publishing via JitPack
- **Testing**: JUnit 5, Mockito (mockito-kotlin 4.1.0)

### Key Design Decisions

These decisions are intentional and should be preserved when adding features:

1. **`@ConditionalOnMissingBean` on every bean** in `PermissionCheckConfiguration` — users can override any component by defining their own bean. Never remove this pattern.

2. **`@ConditionalOnClass` for Spring Security** — the defaults (`DefaultPrincipalIdExtractor`, `DefaultPermissionRepository`) only activate when Spring Security is on the classpath. The core permission system works without Spring Security if the user provides custom implementations.

3. **`compileOnly` for Spring Security** — keeps the starter lightweight. Don't change this to `implementation`.

4. **`open class` on `CachedPermissionService`** — Kotlin classes are final by default, but Spring's `@Cacheable` proxy requires subclassing.

5. **Extension function `Require.extractPermissions()`** — a top-level extension function in the annotation package, not a method on the annotation class.

6. **`@EnablePermissionCheck` uses `@Import`** — it imports `PermissionCheckConfiguration` directly. The `PermissionCheckAutoConfiguration` class handles auto-configuration via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### Adding New Features

- Place new interfaces in the appropriate package (`provider/` for user-facing contracts, `service/` for internal services)
- Provide a default implementation wired with `@ConditionalOnMissingBean` in `PermissionCheckConfiguration`
- Maintain the `compileOnly` boundary for Spring Security
- Make defaults useful out-of-the-box but overridable

### Writing Tests

The project currently has no tests. When adding tests:

- Use JUnit 5 with `@ExtendWith(MockitoExtension::class)` for unit tests
- Use `mockito-kotlin` for idiomatic Kotlin mocking
- Priority areas for test coverage:
  - `DefaultPermissionEvaluator` wildcard matching (direct, domain wildcard, action wildcard, system-wide, non-matching, malformed formats)
  - `PermissionAspect` with mocked dependencies (AND logic, OR logic, single permission)
  - `@Require.extractPermissions()` for single vs multiple permissions
  - `DefaultPermissionRepository` priority resolution (PermissionAware vs fallback)
- For integration tests, use `@SpringBootTest` with test configurations

### Build Commands

```bash
# Build the starter module
./gradlew :permission-check-spring-boot-starter:build

# Run tests
./gradlew :permission-check-spring-boot-starter:test

# Publish to local Maven
./gradlew :permission-check-spring-boot-starter:publishToMavenLocal
```

## Integration Mode — Using the Starter in an Application

### Minimal Setup Checklist

1. Add JitPack repository + dependency
2. Add `@EnablePermissionCheck` to application class
3. Your principal must implement `PrincipalIdentity`
4. Choose permission source: `PermissionAware` on principal OR custom `PermissionRepository` bean
5. Add `@Require("domain:action")` to service methods
6. Add `@ControllerAdvice` to handle `PermissionDeniedException`

### Dependency Setup

**Gradle (Kotlin DSL)** — add JitPack repository in `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add dependency in `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.github.NFUChen:permission-check-spring-boot-starter:-SNAPSHOT")
}
```

**Gradle (Groovy DSL)** — add JitPack repository in `settings.gradle`:
```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Add dependency in `build.gradle`:
```groovy
dependencies {
    implementation 'com.github.NFUChen:permission-check-spring-boot-starter:-SNAPSHOT'
}
```

**Maven** — add JitPack repository in `pom.xml`:
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Add dependency:
```xml
<dependency>
    <groupId>com.github.NFUChen</groupId>
    <artifactId>permission-check-spring-boot-starter</artifactId>
    <version>-SNAPSHOT</version>
</dependency>
```

### Three Integration Levels

**Level 1 — PermissionAware (simplest)**

The user entity directly implements `PermissionAware` to return permissions. Good for simple apps.

```kotlin
@Entity
class User : UserDetails, PrincipalIdentity, PermissionAware {
    val id: UUID = UUID.randomUUID()
    @ManyToMany val roles: Set<Role> = emptySet()

    override fun getPrincipalId(): UUID = id
    override fun getPermissions(): Set<String> =
        roles.flatMap { it.permissions }.toSet()
}
```

**Level 2 — Custom PermissionRepository (recommended)**

Separate `@Component` implementing `PermissionRepository`. Clean separation, testable.

```kotlin
@Component
@Primary
class AppPermissionRepository(
    private val roleRepository: RoleRepository
) : PermissionRepository {
    override fun getUserPermissions(userId: UUID): Set<String> =
        roleRepository.findByUserId(userId)
            .flatMap { it.permissions }.toSet()
}
```

**Level 3 — Custom PrincipalIdExtractor (advanced)**

For non-standard auth (API keys, custom tokens).

```kotlin
@Component
@Primary
class CustomPrincipalIdExtractor : PrincipalIdExtractor {
    override fun extractPrincipalId(): UUID {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw IllegalStateException("Not authenticated")
        return when (val principal = auth.principal) {
            is CustomUser -> principal.userId
            is String -> lookupUserIdFromApiKey(principal)
            else -> throw IllegalStateException("Unsupported principal type")
        }
    }
}
```

### Configuration Properties

```yaml
permission-check:
  cache:
    enabled: true                  # default: true
    cache-name: "userPermissions"  # default: "userPermissions"
    ttl-seconds: 300               # default: 300 (5 minutes)
  logging:
    debug-enabled: false           # default: false
    audit-enabled: true            # default: true
```

### Common Pitfalls

1. **Forgetting `@EnablePermissionCheck`** — without it, the AOP advice never activates
2. **Principal not implementing `PrincipalIdentity`** — the default extractor throws `IllegalStateException`
3. **Self-invocation bypass** — Spring AOP proxies don't intercept calls within the same bean. `@Require` on a method called internally from the same class will be silently skipped
4. **`requireAll` confusion** — default is `true` (AND logic). For OR logic, set `requireAll = false`
5. **Cache manager conflicts** — if the app already has a `CacheManager` bean, the starter won't create its own (this is correct `@ConditionalOnMissingBean` behavior)

### Exception Handling

```kotlin
@ControllerAdvice
class PermissionExceptionHandler {
    @ExceptionHandler(PermissionDeniedException::class)
    fun handlePermissionDenied(e: PermissionDeniedException): ResponseEntity<*> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(mapOf("error" to "Access denied", "details" to e.message))
}
```

### Permission Design Guidance

- Align domains with business resources: `orders`, `users`, `billing`, `reports`
- Use standard actions: `read`, `create`, `update`, `delete`, `manage`, `admin`
- Use wildcards sparingly — `*:*` is for system administrators only
- Keep permissions flat — the starter only supports `domain:action`
- Permission checking validates **what** a user can do, not **which tenant's data** they access — row-level security is a separate concern
