import preset from '@cbs/components/tailwind.config'
import type { Config } from 'tailwindcss'

export default {
  presets: [preset],
  content: ['./app/**/*.{vue,ts,tsx}', './server/**/*.{ts,tsx}'],
} satisfies Config
