import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './helpers.js'

const desktopOnly = async ({}, testInfo) =>
  test.skip(testInfo.project.name !== 'desktop', 'Couvert par mobile.spec')

test.beforeEach(desktopOnly)

test('le tableau de bord affiche les KPIs et permet de naviguer', async ({ page }) => {
  await loginAsAdmin(page)
  await expect(page.locator('.north-star-grid')).toBeVisible()
  await expect(page.locator('.kpi-widget').first()).toBeVisible()
  await expect(page.getByText('Coût au kilomètre')).toBeVisible()

  await page.getByRole('link', { name: /Chauffeurs/ }).click()
  await expect(page.getByRole('heading', { name: 'Chauffeurs' })).toBeVisible()
})
