import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

import { layerRules } from './eslint.layers.js'

export default defineConfig([
  // src/components/arc is vendored from the @devs7udio registry (shadcn add). Keep it as shipped
  // so a re-add does not fight local edits; readme code that wraps it is still linted.
  globalIgnores(['dist', 'src/components/arc']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
    rules: {
      'react-refresh/only-export-components': 'off',
    },
  },
  // 층 규칙: 어긴 import는 lint 오류다. 규칙과 이유는 eslint.layers.js에 있다.
  ...layerRules,
])
