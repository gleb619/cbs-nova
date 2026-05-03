# Backend Security & Authentication

## 1. Authentication Flow

### Local Mode (Development)

Enabled when `app.keycloak.enabled=false`:

1. **RSA Key Pair**: Loaded from `local-jwt-pkcs8.pem` and `local-jwt-public.pem`.
2. **Token Generation**: `POST /api/public/auth/token` validates against in-memory users and signs a JWT.
3. **Token Validation**: `JwtDecoder` verifies signature with the public key.
4. **Local Users**: Defined in `application.yml` (e.g., `admin1/admin1`).

### Keycloak Mode (Production)

Enabled when `app.keycloak.enabled=true`:

- `JwtDecoder` fetches JWKS from Keycloak URL.
- Local auth controller is disabled.

### Public Endpoints

- `/api/public/**` (including auth)
- `/actuator/health`
- `/swagger-ui/**`, `/v3/api-docs/**`
- All `OPTIONS` requests

## 2. Security Configuration

`SecurityConfig.java` defines:

- **Stateless Sessions**: No HTTP session, every request requires JWT.
- **CORS**: Allows `http://localhost:3000` with credentials.
- **Conditional Beans**: Switches between local and Keycloak decoders/UDS based on configuration.
- **Password Encoder**: `NoOpPasswordEncoder` for local development.
