import { test, expect } from '@playwright/test'

const desktopOnly = async ({}, testInfo) =>
  test.skip(testInfo.project.name !== 'desktop', 'Couvert par mobile.spec')

test.beforeEach(desktopOnly)

test('crée puis supprime un chauffeur via la saisie manuelle', async ({ page }) => {
  const license = `FR-E2E-${Date.now()}`
  page.on('dialog', (d) => d.accept())

  await page.goto('/login')
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await page.getByRole('link', { name: /Saisie/ }).click()
  await expect(page.getByRole('heading', { name: 'Saisie manuelle' })).toBeVisible()

  const textInputs = page.locator('.data-grid input[type="text"]')
  await textInputs.nth(0).fill('E2E')
  await textInputs.nth(1).fill('Testeur')
  await textInputs.nth(2).fill(license)
  await textInputs.nth(3).fill('01 00 00 00 99')
  await page.getByRole('button', { name: 'Ajouter' }).click()

  const row = page.locator('tbody tr').filter({ hasText: license })
  await expect(row).toBeVisible({ timeout: 5000 })
  await expect(row).toContainText('E2E Testeur')

  await row.getByRole('button', { name: 'Supprimer' }).click()
  await expect(row).not.toBeVisible()
})
