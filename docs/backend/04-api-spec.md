# API Specification & Generation

## 1. Settings API Endpoints

| Method   | Path                        | Body               | Status | Description             |
|----------|-----------------------------|--------------------|--------|-------------------------|
| `GET`    | `/api/settings`             | —                  | 200    | List all settings       |
| `GET`    | `/api/settings/{id}`        | —                  | 200    | Get by ID               |
| `GET`    | `/api/settings/code/{code}` | —                  | 200    | Get by unique code      |
| `POST`   | `/api/settings`             | `SettingCreateDto` | 201    | Create new setting      |
| `PUT`    | `/api/settings/{id}`        | `SettingUpdateDto` | 200    | Update existing setting |
| `DELETE` | `/api/settings/{id}`        | —                  | 204    | Delete setting          |

### Error Responses

- **400**: Invalid input.
- **401/403**: Security issues.
- **404**: Not found (handled by `SettingExceptionHandler`).
- **409**: Duplicate code.
- **422**: Validation failed.

---

## 2. OpenAPI & Code Generation

### Swagger UI

- **URL**: http://localhost:7070/swagger-ui.html
- **Spec**: http://localhost:7070/v3/api-docs

### Client Generation Commands

**Generate All Clients:**

```bash
./gradlew generateAllClients
```

**TypeScript Client:**

```bash
./gradlew :starter:generateTsClient
```

Output: `starter/generated-ts/`

**Java Feign Client:**

```bash
./gradlew :client:generateFeignClient
```

Output: `client/`
