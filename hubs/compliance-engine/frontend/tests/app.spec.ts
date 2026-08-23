import { test, expect } from '@playwright/test';

function randomEmail(): string {
  return `e2e-${Date.now()}-${Math.random().toString(36).slice(2)}@test.com`;
}

test.describe('Authentication', () => {
  test('login page loads', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: /connexion/i })).toBeVisible();
  });

  test('register page loads', async ({ page }) => {
    await page.goto('/register');
    await expect(page.getByRole('heading', { name: /inscription/i })).toBeVisible();
  });

  test('register creates a user and redirects to dashboard', async ({ page }) => {
    await page.goto('/register');
    await page.getByLabel('Nom Complet').fill('E2E Test User');
    await page.getByLabel('Email').fill(randomEmail());
    await page.getByLabel('Mot de passe').fill('TestPass123!');
    await page.getByRole('button', { name: /s'inscrire/i }).click();
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('login with existing user redirects to dashboard', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill('e2e@test.com');
    await page.getByLabel('Mot de passe').fill('TestPass123!');
    await page.getByRole('button', { name: /se connecter/i }).click();
    await expect(page).toHaveURL(/\/dashboard/);
  });
});

test.describe('Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.getByLabel('Email').fill('nav@test.com');
    await page.getByLabel('Mot de passe').fill('TestPass123!');
    await page.getByRole('button', { name: /se connecter/i }).click();
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('dashboard is visible after login', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible();
  });

  test('sidebar navigation links to shipments', async ({ page }) => {
    const shipmentsLink = page.getByRole('link', { name: /shipments/i });
    await expect(shipmentsLink).toBeVisible();
  });
});