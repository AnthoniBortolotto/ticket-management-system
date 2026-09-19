import { expect, test } from '@playwright/test';

/**
 * Prova que a stack do Docker esta de pe e que o harness funciona. Os cenarios de
 * fluxo real (abrir, atender, atribuir, resolver) entram com as features — a lista
 * deles e escrita antes de implementar, como criterio de aceite.
 */
test.describe('stack local', () => {
  test('o frontend responde', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });

  test('o backend responde saudavel', async ({ request }) => {
    const apiUrl = process.env.E2E_API_URL ?? 'http://localhost:8080';

    const response = await request.get(`${apiUrl}/actuator/health`);

    expect(response.ok()).toBeTruthy();
    expect((await response.json()).status).toBe('UP');
  });
});
