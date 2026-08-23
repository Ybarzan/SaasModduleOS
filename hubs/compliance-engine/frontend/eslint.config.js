import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import reactPlugin from 'eslint-plugin-react'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    plugins: {
      react: reactPlugin,
    },
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' }],
      // eslint-plugin-react n'était pas installé avant : un key mal placé (sur le <tr>
      // interne d'un fragment au lieu du fragment lui-même) n'était détecté par aucun
      // lint, seulement en relecture manuelle (cf. Shipments/ApprovalWorkflows/ShippingRates,
      // corrigé le 2026-08-21). N'active que jsx-key ici, pas le recommended complet du
      // plugin, pour rester un changement ciblé sans relancer un audit de règles react/*.
      'react/jsx-key': 'error',
    },
  },
])
