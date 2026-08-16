import preset from '@cbs/components/tailwind.config'
import type { Config } from 'tailwindcss'

export default {
  presets: [preset],
  content: [
    './*.{vue,ts,tsx}',
    './pages/**/*.{vue,ts,tsx}',
    './layouts/**/*.{vue,ts,tsx}',
    './components/**/*.{vue,ts,tsx}',
    './node_modules/@cbs/admin-ui-plugin/app/**/*.{vue,ts,tsx}',
    './node_modules/@cbs/components/src/**/*.{vue,ts,tsx}',
  ],
} satisfies Config
