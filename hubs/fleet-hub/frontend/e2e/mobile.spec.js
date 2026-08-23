import { test, expect } from '@playwright/test'

test.beforeEach(async ({}, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile', 'Réservé au project mobile')
})

test('le menu hamburger ouvre la navigation et permet de naviguer', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible()

  await expect(page.getByRole('button', { name: 'Ouvrir le menu' })).toBeVisible()
  await page.getByRole('button', { name: 'Ouvrir le menu' }).click()
  await expect(page.locator('.sidebar')).toHaveClass(/open/)

  await page.getByRole('link', { name: /Camions/ }).click()
  await expect(page.getByRole('heading', { name: 'Camions' })).toBeVisible()
  await expect(page.locator('.sidebar')).not.toHaveClass(/open/)
})

test('la déconnexion fonctionne depuis le menu mobile', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await page.getByRole('button', { name: 'Ouvrir le menu' }).click()
  await page.getByRole('button', { name: 'Déconnexion' }).click()
  await expect(page.getByRole('button', { name: 'Se connecter' })).toBeVisible()
})
