import { defineConfig, devices } from '@playwright/test';

/**
 * Roda contra a stack completa do Docker, nunca contra mocks: o valor do E2E e
 * justamente exercitar backend, banco e frontend juntos.
 *
 *   docker compose -f ../docker/docker-compose.yml up -d
 *   pnpm test
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';

export default defineConfig({
  testDir: './specs',
  fullyParallel: true,
  // Teste E2E marcado como only nao deve passar no CI.
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list'], ['html', { open: 'never' }]],
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL,
    // O trace viewer e o que torna uma falha no CI investigavel sem reproduzir local.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
