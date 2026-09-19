import coreWebVitals from 'eslint-config-next/core-web-vitals';
import typescript from 'eslint-config-next/typescript';

// O eslint-config-next 16 ja exporta flat config; nao ha FlatCompat aqui de proposito.
const config = [
  { ignores: ['.next/**', 'node_modules/**', 'src/types/api.d.ts'] },
  ...coreWebVitals,
  ...typescript,
  {
    rules: {
      // Regra do CLAUDE.md: nao silencie o compilador com `any`.
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    },
  },
];

export default config;
