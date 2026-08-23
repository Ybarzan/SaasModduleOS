import { test, expect } from '@playwright/test'

const desktopOnly = async ({}, testInfo) =>
  test.skip(testInfo.project.name !== 'desktop', 'Couvert par mobile.spec')

test.beforeEach(desktopOnly)

test('inscrit une nouvelle société et atterrit sur le tableau de bord', async ({ page }) => {
  const suffix = Date.now()
  await page.goto('/login')
  await page.getByRole('link', { name: 'Créer ma société' }).click()
  await expect(page).toHaveURL(/\/register/)

  await page.getByLabel('Nom de la société').fill('Playwright Transports')
  await page.getByLabel('Prénom').fill('Jean')
  await page.getByLabel('Nom', { exact: true }).fill('Dupont')
  await page.getByLabel('Email (identifiant)').fill(`pw-${suffix}@test.fr`)
  await page.getByLabel('Mot de passe', { exact: true }).fill('password123')
  await page.getByLabel('Confirmer le mot de passe').fill('password123')
  await page.getByRole('button', { name: 'Créer mon compte' }).click()

  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible()
  await expect(page.getByText('Playwright Transports')).toBeVisible()
})

test('le back-office plateforme liste les sociétés pour saasadmin', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('Utilisateur').fill('saasadmin')
  await page.getByLabel('Mot de passe').fill('admin')
  await page.getByRole('button', { name: 'Se connecter' }).click()

  await page.getByRole('link', { name: 'Administration' }).click()
  await expect(page).toHaveURL(/\/admin/)
  await expect(page.getByRole('heading', { name: 'Back-office plateforme' })).toBeVisible()
  await expect(page.getByText('Fleet Hub Démo')).toBeVisible()
})

test('un administrateur de société ne voit pas l’accès au back-office', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Se connecter' }).click()

  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Administration' })).toHaveCount(0)
})
