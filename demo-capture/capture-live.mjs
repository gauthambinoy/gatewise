import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = 'https://auvex.54.170.218.176.nip.io';
fs.mkdirSync('shots-v2', { recursive: true });
fs.mkdirSync('video-v2', { recursive: true });

const browser = await chromium.launch();
const ctx = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  deviceScaleFactor: 2,
  recordVideo: { dir: 'video-v2', size: { width: 1440, height: 900 } },
  ignoreHTTPSErrors: true,
});
const page = await ctx.newPage();
const wait = (ms) => page.waitForTimeout(ms);

await page.goto(BASE + '/', { waitUntil: 'networkidle' });
await wait(1600);
await page.screenshot({ path: 'shots-v2/01-login.png', fullPage: true });

await page.getByRole('button', { name: /try the live demo/i }).click();
await page.waitForLoadState('networkidle');
await wait(3200); // let the count-up + blur-in entrance + aurora play (recorded to video)
await page.screenshot({ path: 'shots-v2/02-dashboard.png', fullPage: true });

// hover a card to show the lift + animated gradient border
try {
  await page.locator('.card').first().hover();
  await wait(1600);
} catch {}

await page.goto(BASE + '/audit', { waitUntil: 'networkidle' });
await wait(2600);
await page.screenshot({ path: 'shots-v2/03-audit.png', fullPage: true });

await page.goto(BASE + '/usage', { waitUntil: 'networkidle' });
await wait(2400);
await page.screenshot({ path: 'shots-v2/04-usage.png', fullPage: true });
await wait(1000);

await ctx.close();
await browser.close();
const vids = fs.readdirSync('video-v2').filter((f) => f.endsWith('.webm'));
if (vids.length) fs.renameSync('video-v2/' + vids[0], 'video-v2/auvex-ui-v2.webm');
console.log('done -> shots-v2/ + video-v2/auvex-ui-v2.webm');
