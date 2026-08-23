import { test, expect } from '@playwright/test'

const desktopOnly = async ({}, testInfo) =>
  test.skip(testInfo.project.name !== 'desktop', 'Couvert par mobile.spec')

test.beforeEach(desktopOnly)

test('se connecte avec les identifiants admin et atterrit sur le tableau de bord', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible()
})

test('refuse les identifiants invalides', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('Utilisateur').fill('inconnu')
  await page.getByLabel('Mot de passe').fill('mauvais')
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page.getByText('Identifiants invalides')).toBeVisible()
})

test('se déconnecte et retourne à la page de connexion', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible()
  await page.getByRole('button', { name: 'Déconnexion' }).click()
  await expect(page.getByRole('button', { name: 'Se connecter' })).toBeVisible()
  await expect(page).toHaveURL(/\/login/)
})
