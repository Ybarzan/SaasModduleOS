// Genere public/og-image.png a partir d'un template HTML statique via Playwright.
// Usage : node scripts/gen-og-image.mjs [chemin-source.html] [chemin-sortie.png]
import { chromium } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const htmlPath = process.argv[2] || path.join(__dirname, 'og-image-template.html');
const outPath = process.argv[3] || path.join(__dirname, '..', 'public', 'og-image.png');

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1200, height: 630 } });
await page.goto('file://' + htmlPath.replace(/\\/g, '/'));
await page.waitForTimeout(300);
await page.screenshot({ path: outPath, clip: { x: 0, y: 0, width: 1200, height: 630 } });
await browser.close();
console.log('OG image written to', outPath);
