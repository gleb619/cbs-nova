# Color Brandbook – Admin UI (Vue + Tailwind)

This document defines the complete color system for the admin interface. It is designed to be implemented with Tailwind
CSS in a Vue.js environment. All colors are derived from the primary terracotta and warm background, ensuring a
cohesive, professional, and accessible aesthetic.

## 1. Overview

- **Primary Color:** `#ba7660` (warm terracotta) – conveys warmth, reliability, and sophistication.
- **Background:** `#f8f8f6` (warm off-white) – soft, neutral base that reduces eye strain.
- **Color philosophy:** Muted, natural tones that feel approachable yet authoritative, suitable for long working
  sessions.

---

## 2. Full Color Palette

All colors are available in 10 shades (50–900) following Tailwind’s naming convention.

### Primary – Terracotta

| Shade | Hex       |
|-------|-----------|
| 50    | `#faf3f0` |
| 100   | `#f5e7e1` |
| 200   | `#ebd0c3` |
| 300   | `#e0b8a4` |
| 400   | `#d4937a` |
| 500   | `#ba7660` |
| 600   | `#a86954` |
| 700   | `#8b5e4a` |
| 800   | `#6d4a3d` |
| 900   | `#4f3630` |

### Neutral – Warm Gray

A soft, warm gray to complement the terracotta and provide clear hierarchy.
| Shade | Hex |
|-------|-----------|
| 50 | `#f9f8f6` |
| 100 | `#f2f0ec` |
| 200 | `#e5e2db` |
| 300 | `#d1cdc4` |
| 400 | `#b3ada1` |
| 500 | `#948d80` |
| 600 | `#7a7367` |
| 700 | `#615b51` |
| 800 | `#4a453d` |
| 900 | `#36322b` |

### Semantic Colors

These are muted variants that maintain the overall palette while clearly signaling status.

| Role                       | Base Hex  | 50        | 100       | 200       | 300       | 400       | 500       | 600       | 700       | 800       | 900       |
|----------------------------|-----------|-----------|-----------|-----------|-----------|-----------|-----------|-----------|-----------|-----------|-----------|
| **Success** (Sage Green)   | `#6b8f71` | `#f2f6f2` | `#e2ece3` | `#c5d9c7` | `#a3c4a6` | `#7faa83` | `#6b8f71` | `#5d7c62` | `#4e6752` | `#3e5241` | `#2e3d31` |
| **Warning** (Muted Amber)  | `#d4a373` | `#fcf8f2` | `#f8efe2` | `#f0dec4` | `#e7cba4` | `#dcb583` | `#d4a373` | `#c09063` | `#a77b52` | `#8d6641` | `#735231` |
| **Error** (Soft Coral)     | `#c97c6b` | `#fcf5f3` | `#f8eae6` | `#f0d4cc` | `#e7bcb1` | `#dca294` | `#c97c6b` | `#b36c5d` | `#995a4e` | `#7f493e` | `#65382f` |
| **Info** (Muted Blue-gray) | `#6b7f8f` | `#f2f4f6` | `#e2e8ec` | `#c5d0d9` | `#a3b6c4` | `#7f9aab` | `#6b7f8f` | `#5d6f7c` | `#4e5e68` | `#3e4c54` | `#2e3a40` |

---

## 3. Tailwind CSS Configuration

Add the following to your `tailwind.config.js` to enable the entire palette and semantic mapping.

```javascript
module.exports = {
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#faf3f0',
          100: '#f5e7e1',
          200: '#ebd0c3',
          300: '#e0b8a4',
          400: '#d4937a',
          500: '#ba7660',
          600: '#a86954',
          700: '#8b5e4a',
          800: '#6d4a3d',
          900: '#4f3630',
        },
        neutral: {
          50: '#f9f8f6',
          100: '#f2f0ec',
          200: '#e5e2db',
          300: '#d1cdc4',
          400: '#b3ada1',
          500: '#948d80',
          600: '#7a7367',
          700: '#615b51',
          800: '#4a453d',
          900: '#36322b',
        },
        success: {
          50: '#f2f6f2',
          100: '#e2ece3',
          200: '#c5d9c7',
          300: '#a3c4a6',
          400: '#7faa83',
          500: '#6b8f71',
          600: '#5d7c62',
          700: '#4e6752',
          800: '#3e5241',
          900: '#2e3d31',
        },
        warning: {
          50: '#fcf8f2',
          100: '#f8efe2',
          200: '#f0dec4',
          300: '#e7cba4',
          400: '#dcb583',
          500: '#d4a373',
          600: '#c09063',
          700: '#a77b52',
          800: '#8d6641',
          900: '#735231',
        },
        error: {
          50: '#fcf5f3',
          100: '#f8eae6',
          200: '#f0d4cc',
          300: '#e7bcb1',
          400: '#dca294',
          500: '#c97c6b',
          600: '#b36c5d',
          700: '#995a4e',
          800: '#7f493e',
          900: '#65382f',
        },
        info: {
          50: '#f2f4f6',
          100: '#e2e8ec',
          200: '#c5d0d9',
          300: '#a3b6c4',
          400: '#7f9aab',
          500: '#6b7f8f',
          600: '#5d6f7c',
          700: '#4e5e68',
          800: '#3e4c54',
          900: '#2e3a40',
        },
        // Background alias
        background: '#f8f8f6',
      },
    },
  },
};
```

---

## 4. Semantic Mapping

Use these color roles consistently across the UI.

| Role          | Tailwind Class                         | Description                              |
|---------------|----------------------------------------|------------------------------------------|
| Primary       | `bg-primary-500`                       | Main buttons, links, primary actions     |
| Primary hover | `bg-primary-600`                       | Hover state for primary actions          |
| Surface       | `bg-neutral-50`                        | Cards, modals, dropdowns                 |
| Background    | `bg-background`                        | Page background (same as neutral-50)     |
| Text          | `text-neutral-800`                     | Primary body text                        |
| Muted text    | `text-neutral-500`                     | Secondary, hint, placeholder text        |
| Success       | `text-success-600` or `bg-success-100` | Positive confirmations, completed states |
| Warning       | `text-warning-600` or `bg-warning-100` | Caution, pending actions                 |
| Error         | `text-error-600` or `bg-error-100`     | Errors, destructive actions              |
| Info          | `text-info-600` or `bg-info-100`       | Informational messages, help             |

---

## 5. Accessibility & Contrast

- All primary text on `neutral-800` against `background` (or `neutral-50`) exceeds WCAG AA (contrast ratio > 4.5:1).
- Buttons with `primary-500` text on `primary-100` background are used only for low-emphasis actions; for high emphasis,
  use `white` text on `primary-500`.
- Semantic colors (`success`, `warning`, `error`, `info`) should always be accompanied by an icon or label to ensure
  color-blind users can distinguish meaning.
- Minimum contrast for UI components: 3:1 for graphical objects, 4.5:1 for text.

---

## 6. Component Patterns

### Buttons

- **Primary:** `bg-primary-500 text-white hover:bg-primary-600 focus:ring-primary-300`
- **Secondary:** `bg-neutral-100 text-neutral-800 hover:bg-neutral-200`
- **Success:** `bg-success-500 text-white hover:bg-success-600`
- **Danger:** `bg-error-500 text-white hover:bg-error-600`

### Cards

- Background: `bg-background` or `bg-neutral-50`
- Border (optional): `border-neutral-200`
- Shadow: `shadow-sm` or `shadow-md` (use `ring-1 ring-neutral-200/10` for subtle border)

### Alerts

- **Info:** `bg-info-50 border-info-300 text-info-800`
- **Success:** `bg-success-50 border-success-300 text-success-800`
- **Warning:** `bg-warning-50 border-warning-300 text-warning-800`
- **Error:** `bg-error-50 border-error-300 text-error-800`

### Badges / Tags

- Use the semantic 100 backgrounds with 700 text for a soft, legible look.
- Example: `bg-success-100 text-success-700`

---

## 7. Usage Examples (Vue + Tailwind)

```vue
<template>
  <!-- Primary Button -->
  <button class="bg-primary-500 text-white px-4 py-2 rounded hover:bg-primary-600">
    Save Changes
  </button>

  <!-- Success Alert -->
  <div class="bg-success-50 border border-success-300 text-success-800 p-4 rounded-md">
    <strong>Success!</strong> Your changes have been saved.
  </div>

  <!-- Card -->
  <div class="bg-background shadow-md rounded-lg p-6 border border-neutral-200">
    <h3 class="text-neutral-800 text-lg font-semibold">Dashboard</h3>
    <p class="text-neutral-500">Welcome back, user.</p>
  </div>
</template>
```

---

## 8. Design Principles

- **Harmony:** All colors are derived from the same warm base to ensure visual unity.
- **Clarity:** Semantic colors are distinct but muted to avoid visual clutter in data-heavy interfaces.
- **Accessibility:** Contrast ratios are intentionally checked for readability.
- **Flexibility:** The 10‑shade system provides enough variation for hover, active, and disabled states without
  introducing new hues.

---

## 9. Background and Surface Usage

- **Page Background:** `bg-background` (alias of `#f8f8f6`) – use as base for all pages.
- **Cards / Panels:** `bg-neutral-50` or `bg-background` with subtle shadow or border.
- **Modals / Drawers:** `bg-white` with `shadow-xl` for elevated prominence.
- **Inputs:** `bg-white` with `border-neutral-300` and focus ring `ring-primary-300`.

---

## 10. Dark Mode (Optional)

If dark mode is implemented, invert the neutral shades (e.g., `neutral-900` becomes background, `neutral-50` becomes
text) while keeping primary and semantic colors consistent but slightly desaturated. Provide a separate configuration
file.

---

This brandbook ensures a consistent, maintainable, and visually appealing admin interface. All developers should refer
to this document when adding new components or modifying existing styles.