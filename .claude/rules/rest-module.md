---
globs: jhelm-rest/src/**/*.java
---

# REST Module Rules

This module provides a REST API for jhelm operations.

- Use Spring MVC annotations (`@RestController`, `@GetMapping`, etc.)
- Use `@Slf4j` for logging — no `System.out.println`
- Input validation at controller level (request bodies, path variables)
- Return proper HTTP status codes (201 for create, 404 for not found, etc.)
- Swagger/OpenAPI annotations for API documentation (`@Operation`, `@ApiResponse`)
