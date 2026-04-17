---
globs: **/src/main/java/**/*.java
---

# Production Java Code Rules

- Use `@Slf4j` for logging — never `System.out.println` (exception: CLI output in jhelm-app)
- Use `import` statements — never fully-qualified class names inline
- Use Lombok: `@Getter`/`@Setter` for fields, `@Data` for POJOs, `@Builder` for complex objects
- Use modern Java 21: text blocks, enhanced switch, streams, try-with-resources for resource streams
- Throw descriptive exceptions; always include original cause when rethrowing
- No Mockito imports in production code
- No BouncyCastle PGP type imports in jhelm-app module — pass strings to action layer
- Validate inputs at system boundaries only (user input, external APIs) — trust internal code
