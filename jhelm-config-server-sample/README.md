# jhelm-config-server-sample

A ready-to-run **Spring Cloud Config Server**, with **HTTP basic auth** and **`{cipher}`
encryption** turned on, that serves example jhelm values. It exists for two reasons:

1. **A working example for DevOps** — a minimal, secured config server you can copy as a
   starting point for serving Helm/jhelm values centrally.
2. **A test fixture** — jhelm's own tests ground against it to prove config-server value
   sourcing and client-side `{cipher}` decryption behave identically to Spring Cloud.

> This is a **demo**. The credentials and encryption key below are placeholders — replace
> them (and front the server with TLS) before using anything like this for real.

## What it demonstrates

- **Central storage** — values live in one place (`src/main/resources/config/`), not scattered
  across `-f values.yaml` files on every operator's laptop.
- **Profiles** — a base document plus a `prod` overlay (via `spring.config.activate.on-profile`)
  and a `demo-prod.yml` sidecar, merged server-side per requested profile.
- **Security** — every endpoint requires basic auth.
- **Encryption** — secrets are stored as `{cipher}` tokens. This server serves them
  **verbatim** (`spring.cloud.config.server.encrypt.enabled: false`) so **jhelm decrypts them
  client-side** with the matching key — the plaintext never travels the wire.

## Run it

```bash
./mvnw -pl jhelm-config-server-sample spring-boot:run
```

It listens on `http://localhost:8888`. Demo credentials: `configuser` / `configpass`.
Demo encryption key: `jhelm-demo-encrypt-key`.

Fetch the merged `prod` config directly to see what jhelm receives:

```bash
curl -u configuser:configpass http://localhost:8888/demo/prod
```

You'll see `tier: prod`, `replicas: 3`, `region: eu-west-1`, and a `password` still in
`{cipher}…` form.

## Point jhelm at it

```bash
jhelm template my-release ./my-chart \
  --config-server-uri http://localhost:8888 \
  --config-name       demo \
  --profile           prod \
  --config-username   configuser \
  --config-password   configpass
```

The `--profile` flag is the same one that selects local value profiles (see the value-profiles
docs) — it's forwarded to the config server as the requested profile, so `--profile prod`
returns the `prod` overlay above. `--config-name` sets the config-server application name
(defaults to the release name).

To have jhelm decrypt the `{cipher}` secret at render time, give it the same key (via the
`jhelm.encrypt.key` property / `JHELM_ENCRYPT_KEY` env, or per-run):

```bash
JHELM_ENCRYPT_KEY=jhelm-demo-encrypt-key jhelm template my-release ./my-chart \
  --config-server-uri http://localhost:8888 --config-name demo --profile prod \
  --config-username configuser --config-password configpass
```

## Encrypt / decrypt values yourself

The same `{cipher}` tokens work in a plain jhelm `-f values.yaml` file too — the config server
is optional. Use the jhelm CLI to mint them:

```bash
jhelm encrypt "s3cret" --key jhelm-demo-encrypt-key      # -> {cipher}…
jhelm decrypt "{cipher}…" --key jhelm-demo-encrypt-key   # -> s3cret
```

Both read the key from `jhelm.encrypt.key` when `--key` is omitted, and accept the value on
stdin. The tokens are byte-compatible with Spring Cloud Config's own `/encrypt` endpoint (both
use `Encryptors.text(key, salt)` with the default `deadbeef` salt), so a token minted by this
server's `POST /encrypt` decrypts with `jhelm decrypt`, and vice versa.
