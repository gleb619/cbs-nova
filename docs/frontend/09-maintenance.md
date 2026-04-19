# Maintenance & Troubleshooting

## 15. Known Issues & TODOs

### TypeScript Build Errors

`pnpm build` fails with `vue-tsc` errors:

- `HomepageVue.vue`: Property '$t' does not exist — i18n $t not typed. Fix: Add i18n Vue component type augmentation.

### TokenStorage Migration

- [x] Update `LocalAuthRepository.ts`
- [x] Update `SettingHttp.ts`
- [x] Delete old `TokenStore.ts`
- [ ] Update `LocalAuthRepository.spec.ts`

### UX Improvements

- [ ] Login form — add loading spinner
- [ ] Remember-me toggle

---

## 16. Browser Verification — Live Screenshots

### Index Page (`/`)

- CBS Nova logo, title, tagline
- Launch Dashboard & Documentation links
- **Screenshot:** [index-page.png](../screenshots/index-page.png)

### Help Page (`/help`)

- Quick Navigation cards
- Project Overview & Development Setup
- **Screenshot:** [help-page.png](../screenshots/help-page.png) (to be added)

### Login Page (`/login`)

- Clean Tailwind form
- Credentials hint
- **Screenshot:** [login-page.png](../screenshots/login-page.png) (to be added)

---

## 17. Quick Troubleshooting

| Problem                            | Cause                            | Fix                                                  |
|------------------------------------|----------------------------------|------------------------------------------------------|
| `pnpm dev` fails to start          | Missing dependencies             | Run `pnpm install` from repo root                    |
| Tailwind classes not rendering     | CSS not imported                 | Check `main.css` imports                             |
| `inject(X)` returns undefined      | Separate piqureWrapper instances | Ensure `provide` and `inject` come from same wrapper |
| API calls return 404               | axios `baseURL` not set          | Check `nuxt.config.ts` `apiBase`                     |
| `defineNuxtPlugin` not found by TS | Nuxt types not generated         | Run `nuxt prepare`                                   |
