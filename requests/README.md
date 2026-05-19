# HTTP scratch requests (IntelliJ / Fleet / VS Code REST Client)

- Pick an environment (**local** / **prod**) in the client UI; variables come from
  `http-client.env.json`.
- **`authToken` / `refreshToken`** are defined there as empty strings so `{{authToken}}` always
  substitutes (avoids IDE “unsubstituted variable” errors). Run the **Register** request at the
  top of `categories.http` or `expenses.http`, or paste a real JWT into the env file, before
  calling protected routes.
- **`auth.http`** — longer auth flows (login, refresh, negative cases); also sets globals.
- **`health.http`** — public health probes.
- **`categories.http`** — starts with Register, then JWT category CRUD; list call refreshes
  `{{categoryId}}` from the seeded `food` row.
- **`expenses.http`** — Register + GET categories, then expense list / batch / dedup / update /
  delete.

## Run the API locally (otherwise `Connection refused` on `:8080`)

The HTTP files assume **`{{baseUrl}}` = `http://localhost:8080`**. That only works while the
server process is listening.

1. **Postgres** (matches `server/.../application.conf` defaults: db `skilky`, user/password `skilky`):
   ```bash
   docker compose -f docker/docker-compose.yml up -d postgres
   ```
2. **Ktor server** (port **8080**, overridable with env `PORT`):
   ```bash
   ./gradlew :server:run
   ```
   Wait until the log shows the engine started, then re-run your `.http` request.

If something else already uses 8080, set `PORT` when starting Gradle, and set `baseUrl` in
`http-client.env.json` to match (e.g. `http://localhost:9090`).

Contract details: `docs/api-spec.md`.
