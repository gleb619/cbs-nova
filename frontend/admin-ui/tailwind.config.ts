import type { Config } from 'tailwindcss'
import preset from '@cbs/components/tailwind.config'

export default {
  presets: [preset],
  content: [
    './app/**/*.{vue,ts,tsx}',
    './server/**/*.{ts,tsx}'
  ]
} satisfies Config
