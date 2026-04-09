# Key Dependencies & Developer Workflow

← [Back to TDD](../tdd.md)

## 17. Key Dependencies

| Package                   | Purpose                                          |
|---------------------------|--------------------------------------------------|
| `nuxt` ^3.x               | Framework + BFF server routes                    |
| `vue` ^3.x                | UI framework                                     |
| `@pinia/nuxt`             | State management                                 |
| `@nuxtjs/tailwindcss`     | Styling                                          |
| `@nuxtjs/i18n`            | Internationalization                             |
| `@vueuse/nuxt`            | Utility composables                              |
| `keycloak-js`             | Auth — SPA flow                                  |
| `zod`                     | Validation (BFF + client schemas)                |
| `@tanstack/vue-query`     | Server state, caching                            |
| `@headlessui/vue`         | Accessible UI primitives                         |
| `@heroicons/vue`          | Icons                                            |
| `bpmn-js`                 | BPMN 2.0 diagram rendering (orchestration pages) |
| `@hey-api/openapi-ts`     | TS API client generation                         |
| `hygen`                   | Scaffold code generation                         |
| `@tailwindcss/forms`      | Form styling plugin                              |
| `@tailwindcss/typography` | Typography plugin                                |

---

## 18. Developer Workflow

```bash
# 1. Start backend (Spring Boot)
cd backend && ./gradlew bootRun

# 2. Generate fresh TS API client + entity scaffolding for a new entity
pnpm generate:api                                         # regenerate TS client
pnpm generate:module --entity Loan --group finance        # scaffold entity

# 3. Review generated:
#    - admin.config.ts (entity registration, formMode auto-detected)
#    - i18n/en.json (field label stubs)
#    - server/api/loans/ (BFF routes)
#    - api/generated/ (TS types)
#    Adjust field config, add Zod schema to BFF routes, tweak labels

# 4. Start frontend dev
pnpm dev

# 5. For handmade pages (executions, settings, complex dictionaries):
#    - Create page in app/pages/admin/...
#    - Set customRoute in EntityRegistration
#    - Register in admin.config.ts with appropriate group + permissions
```

---

## 19. Deferred / Out of Scope

| Feature                                 | Phase / Version                         |
|-----------------------------------------|-----------------------------------------|
| Rich text editor (TipTap)               | Phase 4                                 |
| Media upload (S3/MinIO)                 | Phase 4                                 |
| Dark mode                               | Phase 3                                 |
| Mobile responsiveness                   | Phase 3                                 |
| i18n additional locales (RU, KK)        | After English baseline                  |
| Stakeholder funnel/heatmap UI           | v2 (backend data model ready)           |
| Dynamic BPMN instance heatmap           | v2                                      |
| Temporal UI iframe embed                | v2 (sidebar external link in v1)        |
| Saved search `visibleToUserIds` scoping | v2 (JSONB column ready)                 |
| VSCode extension                        | v2 (server-side code-server link in v1) |
| Audit log viewer                        | Phase 5                                 |
| Annotation processor typed DSL bindings | v2 (backend)                            |
