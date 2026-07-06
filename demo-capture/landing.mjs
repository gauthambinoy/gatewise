import { chromium } from 'playwright';
const b = await chromium.launch();
const ctx = await b.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2, ignoreHTTPSErrors: true });
const p = await ctx.newPage();
await p.goto('https://gatewise.54.170.218.176.nip.io/', { waitUntil: 'networkidle' });
await p.waitForTimeout(2500);
await p.screenshot({ path: 'shots-landing/landing.png', fullPage: true });
await b.close(); console.log('done');
