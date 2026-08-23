import { test, expect } from '@playwright/test'

const desktopOnly = async ({}, testInfo) =>
  test.skip(testInfo.project.name !== 'desktop', 'Couvert par mobile.spec')

test.beforeEach(desktopOnly)

async function login(page) {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible()
}

test('le tableau de bord affiche les KPIs et permet de naviguer', async ({ page }) => {
  await login(page)
  await expect(page.locator('.north-star-grid')).toBeVisible()
  await expect(page.locator('.kpi-widget').first()).toBeVisible()
  await expect(page.getByText('Coût au kilomètre')).toBeVisible()

  await page.getByRole('link', { name: /Chauffeurs/ }).click()
  await expect(page.getByRole('heading', { name: 'Chauffeurs' })).toBeVisible()
})
