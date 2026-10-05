# Runbook fixture

Read-only health probe.

```bash
curl -s http://localhost:8090/actuator/health | jq .status
```

Read-only with continuation and a header.

```bash
# Continuation example
curl -s \
  -H "Accept: application/json" \
  http://localhost:8090/actuator/health | jq .components
```

Write: POST.

```bash
curl -s -X POST http://localhost:8090/api/dsl/run/LoanDsl \
  -H "Content-Type: application/json" \
  -d '{"body":{}}'
```

Write: data without explicit method.

```bash
curl -s -d '{}' http://localhost:8090/api/dsl/preview/LoanDsl
```

Write: DELETE.

```bash
curl -sS -X DELETE http://localhost:3000/api/v1/dsl/schedules/monthly-closing
```

Non-curl command inside a bash block.

```bash
docker compose -f app/docker-compose.yml ps
make logs
```

BFF read-only list.

```bash
curl -s http://localhost:3000/api/v1/dsl/definitions | head
```

This block is not a bash fence and should be ignored.

```sh
curl -s http://localhost:8090/actuator/health
```
