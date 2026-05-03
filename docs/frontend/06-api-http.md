# HTTP Layer & API Proxy

## 11. HTTP Layer & API Proxy

### Dev Proxy

Nuxt dev server proxies API requests to Spring Boot. Configured in `nuxt.config.ts`:

```typescript
nitro: {
  devProxy: {
    '/api': {
      target: process.env.SPRING_BOOT_URL ?? 'http://localhost:7070',
      changeOrigin: true,
    },
  },
},
```

**Flow:** `localhost:3000/api/*` → Nuxt devProxy → `localhost:7070/api/*`

### Axios Configuration

All HTTP clients use `axios.create({ baseURL: config.public.apiBase })` where `apiBase` defaults to
`http://localhost:7070`.

**Important:** In SPA mode (`ssr: false`), Nitro's devProxy only handles SSR/server requests. Client-side axios calls go
**directly** to the `baseURL`.

### Bearer Token

The `SettingHttp` adapter attaches the Bearer token from `TokenStorage`:

```typescript
const token = TokenStorage.get();
const response = await this.http.get<Setting[]>('/api/settings', {
  headers: { Authorization: `Bearer ${token}` },
});
```

### Runtime Config

| Key                | Default                 | Env Override                  |
|--------------------|-------------------------|-------------------------------|
| `public.apiBase`   | `http://localhost:7070` | `SPRING_BOOT_URL`             |
| `public.localAuth` | `false`                 | `NUXT_PUBLIC_LOCAL_AUTH=true` |
