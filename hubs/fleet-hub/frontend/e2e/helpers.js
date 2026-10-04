import { expect } from '@playwright/test'

// Identifiants seedés par DataSeeder (app.security.admin-password, défaut "admin").
export async function loginAsAdmin(page) {
  await page.goto('/login')
  await page.getByLabel('Utilisateur').fill('admin')
  await page.getByLabel('Mot de passe').fill('admin')
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible()
}
