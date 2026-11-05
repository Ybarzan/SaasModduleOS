import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './helpers.js'

test.beforeEach(async ({}, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile', 'Réservé au project mobile')
})

test('le menu hamburger ouvre la navigation et permet de naviguer', async ({ page }) => {
  await loginAsAdmin(page)

  await expect(page.getByRole('button', { name: 'Ouvrir le menu' })).toBeVisible()
  await page.getByRole('button', { name: 'Ouvrir le menu' }).click()
  await expect(page.locator('.sidebar')).toHaveClass(/open/)

  // Société de démo en profil « flotte mixte » : le menu dit « Véhicules » (voir fleet.js).
  await page.getByRole('link', { name: /Véhicules/ }).click()
  await expect(page.getByRole('heading', { name: 'Véhicules' })).toBeVisible()
  await expect(page.locator('.sidebar')).not.toHaveClass(/open/)
})

test('la déconnexion fonctionne depuis le menu mobile', async ({ page }) => {
  await loginAsAdmin(page)
  await page.getByRole('button', { name: 'Ouvrir le menu' }).click()
  await page.getByRole('button', { name: 'Déconnexion' }).click()
  await expect(page.getByRole('button', { name: 'Se connecter' })).toBeVisible()
})
