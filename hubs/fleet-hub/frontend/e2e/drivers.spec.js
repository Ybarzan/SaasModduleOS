import { test, expect } from '@playwright/test'

const desktopOnly = async ({}, testInfo) =>
  test.skip(testInfo.project.name !== 'desktop', 'Couvert par mobile.spec')

test.beforeEach(desktopOnly)

async function openDrivers(page) {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible()
  await page.getByRole('link', { name: /Chauffeurs/ }).click()
  await expect(page.getByRole('heading', { name: 'Chauffeurs' })).toBeVisible()
}

test('liste les chauffeurs seedés et filtre par recherche', async ({ page }) => {
  await openDrivers(page)
  await expect(page.locator('tbody tr').first()).toBeVisible()

  await page.getByPlaceholder('Rechercher…').fill('Jean')
  await expect(page.getByText('Jean Martin', { exact: true }).filter({ visible: true })).toBeVisible()
})

test('ouvre la fiche détaillée d un chauffeur', async ({ page }) => {
  await openDrivers(page)
  await page.getByText('Jean Martin', { exact: true }).filter({ visible: true }).click()
  await expect(page).toHaveURL(/\/drivers\/\d+/)
  await expect(page.getByText('Jean Martin', { exact: true }).first()).toBeVisible()
})
