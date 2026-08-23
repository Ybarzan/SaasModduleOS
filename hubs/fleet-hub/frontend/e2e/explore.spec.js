import { test, expect } from '@playwright/test'

const desktopOnly = async ({}, testInfo) =>
  test.skip(testInfo.project.name !== 'desktop', 'Couvert par mobile.spec')

test.beforeEach(desktopOnly)

async function login(page) {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible()
}

test('ouvre la fiche camion depuis la liste des camions', async ({ page }) => {
  await login(page)
  await page.getByRole('link', { name: /Camions/ }).click()
  await expect(page.getByRole('heading', { name: 'Camions' })).toBeVisible()

  await page.locator('tbody tr').first().locator('a.cell-link').click()
  await expect(page).toHaveURL(/\/trucks\/\d+/)
  await expect(page.getByRole('heading', { name: /^🚛/ }).first()).toBeVisible()
  await expect(page.locator('.detail-northstar').first()).toBeVisible()
})

test('navigue depuis la fiche camion vers les données détaillées', async ({ page }) => {
  await login(page)
  await page.goto('/trucks')
  await page.locator('tbody tr').first().locator('a.cell-link').click()
  await expect(page).toHaveURL(/\/trucks\/\d+/)

  await page.getByRole('button', { name: /Carburant/ }).click()
  await expect(page.locator('.card .table')).toBeVisible()

  await page.getByRole('button', { name: /Maintenance/ }).click()
  await page.getByRole('button', { name: /Événements/ }).click()
  await page.getByRole('button', { name: /Trajets/ }).click()
})

test('clique sur un couple du tableau de bord pour ouvrir le détail', async ({ page }) => {
  await login(page)
  const row = page.locator('.row-clickable').first()
  await row.click()
  await expect(page).toHaveURL(/\/drivers\/\d+/)
  await expect(page.getByText('← Retour aux chauffeurs')).toBeVisible()
})

test('les widgets KPI mènent au classement trié des chauffeurs', async ({ page }) => {
  await login(page)
  await page.locator('button.kpi-widget-clickable').first().click()
  await expect(page).toHaveURL(/\/drivers\?kpi=/)
  await expect(page.getByText('Effacer le focus')).toBeVisible()
})

test('les lignes de la carte mènent à la fiche camion', async ({ page }) => {
  await login(page)
  await page.getByRole('link', { name: /Carte temps réel/ }).click()
  await expect(page.getByRole('heading', { name: 'Carte temps réel' })).toBeVisible()

  await page.locator('.desktop-table tbody tr').first().click()
  await expect(page).toHaveURL(/\/trucks\/\d+/)
})
