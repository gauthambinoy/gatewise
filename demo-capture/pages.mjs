import { chromium } from 'playwright';
const BASE = 'https://auvex.54.170.218.176.nip.io';
const b = await chromium.launch();
const ctx = await b.newContext({ viewport: { width: 1440, height: 980 }, deviceScaleFactor: 2, ignoreHTTPSErrors: true });
const p = await ctx.newPage(); const wait = (m) => p.waitForTimeout(m);
await p.goto(BASE + '/', { waitUntil: 'networkidle' }); await wait(1500);
await p.getByRole('button', { name: /try the live demo/i }).click();
await p.waitForLoadState('networkidle'); await wait(2500);
await p.goto(BASE + '/connect', { waitUntil: 'networkidle' }); await wait(2000);
// run the test
try { await p.getByRole('button', { name: /run test/i }).click(); await wait(2200); } catch {}
await p.screenshot({ path: 'shots-pages/connect.png', fullPage: true });
await p.goto(BASE + '/monitor', { waitUntil: 'networkidle' }); await wait(3000);
await p.screenshot({ path: 'shots-pages/monitor.png', fullPage: true });
await b.close(); console.log('done');
