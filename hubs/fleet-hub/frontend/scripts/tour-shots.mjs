// Captures du module Tournées (desktop + mobile) et contrôle d'erreurs console.
// Usage : node scripts/tour-shots.mjs [dossier]  (backend 8090 + front 5199 lancés)
import { chromium } from '@playwright/test'
import fs from 'fs'
import path from 'path'

const baseURL = 'http://localhost:5199'
const outDir = process.argv[2] || 'tour-shots'
fs.mkdirSync(outDir, { recursive: true })

const browser = await chromium.launch(process.env.PW_CHANNEL ? { channel: process.env.PW_CHANNEL } : {})
const errors = []

for (const [device, viewport] of [
  ['desktop', { width: 1440, height: 900 }],
  ['mobile', { width: 390, height: 844 }]
]) {
  const page = await browser.newPage({ viewport, deviceScaleFactor: device === 'mobile' ? 2 : 1 })
  page.on('pageerror', (e) => errors.push(`${device} pageerror: ${e.message}`))
  page.on('console', (m) => m.type() === 'error' && errors.push(`${device} console: ${m.text()}`))

  await page.goto(`${baseURL}/login`)
  await page.getByLabel('Utilisateur').fill(process.env.SHOT_USER || 'admin')
  await page.getByLabel('Mot de passe').fill(process.env.SHOT_PASSWORD || 'admin')
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await page.waitForURL(`${baseURL}/`)

  await page.goto(`${baseURL}/tours`)
  await page.waitForSelector('.tour-card')
  const href = await page.locator('.tour-card').first().getAttribute('href')

  for (const [name, url, ready] of [
    ['tours', '/tours', '.tour-card'],
    ['tour-detail', href, '.stop-list'],
    ['sites', '/sites', 'table'],
    ['trucks', '/trucks', '.truck-card']
  ]) {
    await page.goto(baseURL + url, { waitUntil: 'load' })
    await page.waitForSelector(ready, { state: 'attached', timeout: 60000 })
    await page.waitForTimeout(1500)
    const m = await page.evaluate(() => ({
      scrollW: document.documentElement.scrollWidth,
      innerW: window.innerWidth
    }))
    console.log(`${device}/${name}: ${m.scrollW > m.innerW ? 'OVERFLOW-X ' + m.scrollW : 'ok'}`)
    await page.screenshot({ path: path.join(outDir, `${device}-${name}.png`), fullPage: true })
  }
  await page.close()
}

await browser.close()
console.log(errors.length ? errors.join('\n') : 'Aucune erreur console')
