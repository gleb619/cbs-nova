import { computed, ref, watch } from 'vue'

// Expose Vue reactivity macros as globals so <script setup> SFCs that rely on
// Nuxt/Volar-style auto-imports resolve `ref` / `computed` / `watch` during test mount.
// @ts-expect-error - augmenting globalThis for SFC auto-import parity
globalThis.ref = ref
// @ts-expect-error - see above
globalThis.computed = computed
// @ts-expect-error - see above
globalThis.watch = watch
