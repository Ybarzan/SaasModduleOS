// Parcours chauffeur simulé sur mobile (GPS, saisie rapide, annulation, hors ligne) contre la démo locale.
// Usage : backend dev (8090) + front (5199) lancés, puis : node scripts/driver-ux-check.mjs <dossier-captures>
import { chromium } from '@playwright/test'
const B = 'http://localhost:5199', A = `${B}/api`, out = process.argv[2]
const log = (...a) => console.log(...a)
const browser = await chromium.launch(process.env.PW_CHANNEL ? { channel: process.env.PW_CHANNEL } : {})
const ctx = await browser.newContext({
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 2,
  isMobile: true,
  hasTouch: true,
  permissions: ['geolocation'],
  geolocation: { latitude: 45.733, longitude: 4.827, accuracy: 20 } // au dépôt
})
const req = ctx.request
const j = async (r) => {
  if (!r.ok()) throw new Error(`${r.url()} ${r.status()} ${await r.text()}`)
  return r.status() === 204 ? null : r.json()
}
const admin = await j(await req.post(`${A}/auth/login`, { data: { username: 'admin', password: 'admin' } }))
const H = { Authorization: `Bearer ${admin.token}` }
const drivers = await j(await req.get(`${A}/drivers`, { headers: H }))
const sophie = drivers.find((d) => d.firstName === 'Sophie')
await j(await req.post(`${A}/drivers/${sophie.id}/pin`, { headers: H, data: { pin: '2468' } }))
await ctx.clearCookies()

const page = await ctx.newPage()
const errors = []
page.on('pageerror', (e) => errors.push(e.message))
await page.goto(`${B}/pointage/${admin.companyAccessCode}`)
await page.getByText('Sophie Lambert').click()
for (const d of '2468') await page.getByRole('button', { name: d, exact: true }).click()
await page.getByRole('tab', { name: /Ma tournée/ }).click()
await page.waitForSelector('.drv-focus')
await page.waitForTimeout(1200)
await page.screenshot({ path: `${out}/drv-1-focus.png` })

// Le chauffeur arrive à proximité du premier arrêt : arrivée détectée sans rien toucher
const first = await page.locator('.drv-focus-name').innerText()
const tours = await j(await req.get(`${A}/tours`, { headers: H }))
const tour = await j(await req.get(`${A}/tours/${tours.find((t) => t.driverName === 'Sophie Lambert').id}`, { headers: H }))
const stop1 = tour.stops.find((s) => s.siteName === first)
await ctx.setGeolocation({ latitude: stop1.latitude + 0.0004, longitude: stop1.longitude, accuracy: 15 })
await page.waitForSelector('text=Arrivée détectée', { timeout: 20000 })
await page.screenshot({ path: `${out}/drv-2-arrived.png` })
log('arrivée auto détectée pour', first)

// « Tout est OK » → feuille rapide : température au pavé numérique
await page.getByRole('button', { name: /Tout est OK/ }).click()
await page.waitForSelector('.qp-sheet')
await page.getByRole('button', { name: '5', exact: true }).click()
await page.getByRole('button', { name: ',', exact: true }).click()
await page.getByRole('button', { name: '2', exact: true }).click()
await page.screenshot({ path: `${out}/drv-3-sheet.png` })
await page.getByRole('button', { name: /Valider le passage/ }).click()
await page.waitForSelector('.drv-toast')
await page.screenshot({ path: `${out}/drv-4-toast.png` })

// Arrêt suivant : on teste « Annuler » sur un problème déclaré par erreur
await page.getByRole('button', { name: /Je suis arrivé/ }).click()
await page.getByRole('button', { name: /Problème/ }).click()
await page.waitForSelector('.qp-reasons')
await page.screenshot({ path: `${out}/drv-5-problem.png` })
await page.getByRole('button', { name: 'Destinataire absent' }).click()
await page.getByRole('button', { name: 'Annuler' }).click()
await page.waitForTimeout(500)
const stillOpen = await page.locator('.drv-focus').isVisible()
log('annulation : arrêt de nouveau à faire =', stillOpen)

// Passage en zone blanche : la validation est gardée en file et envoyée au retour du réseau
await ctx.setOffline(true)
await page.getByRole('button', { name: /Tout est OK/ }).click()
if (await page.locator('.qp-sheet').isVisible()) {
  for (const k of ['4', ',', '8']) await page.getByRole('button', { name: k, exact: true }).click()
  await page.getByRole('button', { name: /Valider le passage/ }).click()
}
await page.waitForTimeout(6500) // délai d'annulation écoulé, envoi impossible
await page.screenshot({ path: `${out}/drv-6-offline.png` })
log('badge hors ligne :', await page.locator('.drv-sync').innerText())
await ctx.setOffline(false)
await page.evaluate(() => window.dispatchEvent(new Event('online')))
await page.waitForFunction(() => !document.querySelector('.drv-sync'), null, { timeout: 30000 })
log('file vidée après retour du réseau')

const after = await j(await req.get(`${A}/tours/${tour.id}`, { headers: H }))
for (const s of after.stops.filter((x) => x.status !== 'A_FAIRE')) {
  log(' ', s.sequence, s.siteName, s.status, 'arrivé', s.arrivedAt?.slice(11, 16), 'clos', s.completedAt?.slice(11, 16), 'temp', s.temperatureCelsius, 'signé', s.signedBy)
}
await page.getByRole('button', { name: /Voir tous les arrêts/ }).click()
await page.screenshot({ path: `${out}/drv-7-list.png`, fullPage: true })
log(errors.length ? errors.join('\n') : 'aucune erreur page')
await browser.close()
