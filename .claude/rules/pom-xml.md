---
globs: **/pom.xml
---

# Maven POM Rules

- Define dependency versions as properties in root `pom.xml` `<properties>` (unless managed by Spring Boot parent)
- Manage all versions in root `pom.xml` `<dependencyManagement>` — child modules use versionless entries
- Don't add version-less entries to root dependencyManagement — they override Spring Boot BOM with null
- When adding a new dependency, check if Spring Boot already manages its version first
