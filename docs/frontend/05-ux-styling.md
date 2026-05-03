# Styling, State & i18n

## 8. Styling — Tailwind CSS v4

### CSS Import Chain

```
frontend/src/app/assets/main.css
  → @import "tailwindcss"
  → @import "../../../../frontend-plugin/assets/main.scss"
  → @import "tailwindcss" (in SCSS)
  → @theme { ... } (custom design tokens)
```

The main CSS file imports Tailwind and the plugin's SCSS. `@source` directives tell Tailwind to scan plugin files for
class usage.

### Design Tokens

Defined in `frontend-plugin/assets/main.scss` `@theme` block:

| Token             | Value                                   | Usage                                                      |
|-------------------|-----------------------------------------|------------------------------------------------------------|
| **Primary color** | `#D4532D` (terracotta/rust)             | `bg-primary-600`, `text-primary-600`, `border-primary-600` |
| **Secondary**     | `#1A1A2E` (navy)                        | `bg-secondary-900`                                         |
| **Neutral**       | Warm gray scale (`#FAFAF9` → `#1C1917`) | `bg-neutral-50`, `text-neutral-900`                        |
| **Base font**     | `0.75rem` (12px)                        | `text-base`                                                |
| **Spacing**       | Compact scale (~25% reduction)          | `p-3` ≈ 0.5625rem                                          |

---

## 9. State Management

### Pinia

Pinia 3 is initialized in `frontend/src/app/main.ts`. The `pinia-plugin-persistedstate` plugin automatically persists
Pinia store state to `localStorage`/`sessionStorage` based on store configuration.

### piqure DI

For repository/service injection, piqure is used instead of Pinia. This keeps the hexagonal architecture clean —
repositories are injected, not stored in reactive state.

---

## 10. i18n — Internationalization

### Setup

- **Library:** i18next 25 + i18next-browser-languagedetector + i18next-vue
- **Fallback:** English (`en`)
- **Languages:** English (`en.ts`), Russian (`ru.ts`)

### Usage in Vue

```vue
<template>
  <h2>{{ $t('home.translationEnabled') }}</h2>
</template>
```

**Known issue:** `$t` is not typed in Vue components — `tsconfig.build.json` doesn't include i18n Vue component type
augmentation.
