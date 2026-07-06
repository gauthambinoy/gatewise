import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = 'http://127.0.0.1:3000';
fs.mkdirSync('screenshots', { recursive: true });
fs.mkdirSync('videos', { recursive: true });

// Each console page, in walkthrough order, with a friendly screenshot name.
const routes = [
  ['/', '02-dashboard'],
  ['/audit', '03-audit-log'],
  ['/policies', '04-policies'],
  ['/models', '05-models-routing'],
  ['/usage', '06-usage-cost'],
  ['/users', '07-users'],
  ['/team', '08-team-roles'],
  ['/keys', '09-api-keys'],
  ['/settings', '10-settings'],
];

const browser = await chromium.launch();
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  deviceScaleFactor: 2,
  recordVideo: { dir: 'videos', size: { width: 1440, height: 900 } },
});
const page = await context.newPage();
const settle = (ms = 2300) => page.waitForTimeout(ms);

// 1) Login screen.
await page.goto(BASE + '/', { waitUntil: 'networkidle' });
await settle(1800);
await page.screenshot({ path: 'screenshots/01-login.png', fullPage: true });

// 2) Enter via the live-demo sandbox.
await page.getByRole('button', { name: /try the live demo/i }).click();
await page.waitForLoadState('networkidle');
await settle(2800);

// 3) Walk every page, full-page screenshot each.
for (const [path, name] of routes) {
  await page.goto(BASE + path, { waitUntil: 'networkidle' });
  await settle(2400);
  await page.screenshot({ path: `screenshots/${name}.png`, fullPage: true });
  console.log('captured', name);
}

// 4) Drill into one audit entry (the request-detail view).
try {
  await page.goto(BASE + '/audit', { waitUntil: 'networkidle' });
  await settle(1600);
  await page.locator('table tbody tr').first().click();
  await page.waitForLoadState('networkidle');
  await settle(2200);
  await page.screenshot({ path: 'screenshots/11-request-detail.png', fullPage: true });
  console.log('captured 11-request-detail');
} catch (e) {
  console.log('request-detail capture skipped:', e.message);
}

await settle(1200);
await context.close(); // flush the recorded video
await browser.close();

const vids = fs.readdirSync('videos').filter((f) => f.endsWith('.webm'));
if (vids.length) {
  fs.renameSync('videos/' + vids[0], 'videos/gatewise-walkthrough.webm');
  console.log('video -> videos/gatewise-walkthrough.webm');
}
console.log('done — screenshots/ + videos/');
