# Backend Quick Start

## Quick Start Guide

### 1. Start PostgreSQL

```bash
docker compose up -d
```

### 2. Start the Application

```bash
./gradlew :backend:bootRun
```

App starts on **http://localhost:7070**.

### 3. Login & Get JWT Token

```bash
curl -s -X POST http://localhost:7070/api/public/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin1","password":"admin1"}' | jq .
```

### 4. Swagger UI

Open **http://localhost:7070/swagger-ui.html** for interactive API documentation.

---

## Full curl Demo

```bash
# 1. Login
TOKEN=$(curl -s -X POST http://localhost:7070/api/public/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin1","password":"admin1"}' | jq -r '.access_token')

# 2. List all settings
curl -s http://localhost:7070/api/settings \
  -H "Authorization: Bearer $TOKEN"

# 3. Create a setting
curl -s -X POST http://localhost:7070/api/settings \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"app.theme","value":"dark","description":"Default UI theme"}'

# 4. Get by ID
curl -s http://localhost:7070/api/settings/1 \
  -H "Authorization: Bearer $TOKEN"

# 5. Update
curl -s -X PUT http://localhost:7070/api/settings/1 \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"value":"light"}'

# 6. Delete
curl -s -X DELETE http://localhost:7070/api/settings/1 \
  -H "Authorization: Bearer $TOKEN"
```
