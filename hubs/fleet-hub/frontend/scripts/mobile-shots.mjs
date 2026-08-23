import { chromium } from '@playwright/test'
import fs from 'fs'
import path from 'path'

const baseURL = 'http://localhost:5199'
const outDir = process.argv[2] || 'C:/WINDOWS/TEMP/opencode/mobile-shots'
fs.mkdirSync(outDir, { recursive: true })

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })

await page.goto(`${baseURL}/login`)
await page.getByLabel('Utilisateur').fill(process.env.SHOT_USER || 'admin')
await page.getByLabel('Mot de passe').fill(process.env.SHOT_PASSWORD || 'admin')
await page.getByRole('button', { name: 'Se connecter' }).click()
await page.waitForURL(`${baseURL}/`)

const pages = [
  ['dashboard', '/'],
  ['drivers', '/drivers'],
  ['driver-detail', '/drivers/1'],
  ['trucks', '/trucks'],
  ['map', '/map']
]

for (const [name, url] of pages) {
  await page.goto(baseURL + url, { waitUntil: 'load' })
  await page.waitForTimeout(1200)
  const m = await page.evaluate(() => {
    const d = document.documentElement
    return { scrollW: d.scrollWidth, innerW: window.innerWidth, scrollH: d.scrollHeight }
  })
  const bad = m.scrollW > m.innerW
  console.log(`${name}: scrollW=${m.scrollW} innerW=${m.innerW} ${bad ? 'OVERFLOW-X' : 'ok'} height=${m.scrollH}`)
  await page.screenshot({ path: path.join(outDir, name + '.png'), fullPage: true })
}

await browser.close()
