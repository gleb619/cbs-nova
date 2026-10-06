import preset from '@cbs/components/tailwind.config'
import type { Config } from 'tailwindcss'

export default {
  presets: [preset],
  content: [
    './app/*.{vue,ts,tsx}',
    './app/pages/**/*.{vue,ts,tsx}',
    './app/layouts/**/*.{vue,ts,tsx}',
    './app/components/**/*.{vue,ts,tsx}',
    './node_modules/@cbs/admin-ui-plugin/app/**/*.{vue,ts,tsx}',
    './node_modules/@cbs/components/src/**/*.{vue,ts,tsx}',
  ],
} satisfies Config
