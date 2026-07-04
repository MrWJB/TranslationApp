let puppeteer = null;
try {
  puppeteer = require('puppeteer-core');
} catch {
  // puppeteer-core optional
}

const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';

const CHROME_PATHS = [
  process.env.CHROME_BIN,
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
].filter(Boolean);

function resolveChromePath() {
  return CHROME_PATHS[0] || null;
}

function launchArgs(stealth) {
  const base = ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'];
  if (!stealth) {
    return base;
  }
  return [
    ...base,
    '--disable-gpu',
    '--disable-accelerated-2d-canvas',
    '--no-first-run',
    '--disable-infobars',
    '--disable-features=IsolateOrigins,site-per-process',
    '--disable-blink-features=AutomationControlled',
    '--window-size=1920,1080',
  ];
}

async function applyStealth(page) {
  await page.evaluateOnNewDocument(() => {
    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
    Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
    Object.defineProperty(navigator, 'languages', { get: () => ['zh-CN', 'zh', 'en-US', 'en'] });
    Object.defineProperty(navigator, 'platform', { get: () => 'Win32' });
    Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8 });
    Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });
  });
}

async function fetchWithPuppeteer(url, options = {}) {
  if (!puppeteer) {
    throw new Error('puppeteer-core is not available');
  }

  const executablePath = options.executablePath || resolveChromePath();
  if (!executablePath) {
    throw new Error('Chrome executable not found (set CHROME_BIN)');
  }

  const timeout = options.timeout ?? 60000;
  const stealth = options.stealth === true;
  let browser = null;
  try {
    console.log(`Fetching ${url} with Puppeteer...`);

    browser = await puppeteer.launch({
      executablePath,
      headless: 'new',
      args: launchArgs(stealth),
      timeout,
      protocolTimeout: options.protocolTimeout ?? 120000,
    });

    const page = await browser.newPage();
    await page.setUserAgent(USER_AGENT);

    if (stealth) {
      await applyStealth(page);
      await page.setExtraHTTPHeaders({
        Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
        'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
        Referer: url,
        'Sec-Ch-Ua': '"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"',
        'Sec-Ch-Ua-Mobile': '?0',
        'Sec-Ch-Ua-Platform': '"Windows"',
        'Sec-Fetch-Dest': 'document',
        'Sec-Fetch-Mode': 'navigate',
        'Sec-Fetch-Site': 'none',
        'Sec-Fetch-User': '?1',
        'Upgrade-Insecure-Requests': '1',
      });
      await page.setViewport({ width: 1920, height: 1080 });
    }

    await page.setDefaultTimeout(timeout);

    const waitUntil = options.waitUntil || 'domcontentloaded';
    const response = await page.goto(url, {
      waitUntil,
      timeout,
    });

    await new Promise((resolve) => setTimeout(resolve, options.settleMs ?? 1000));

    const html = await page.content();
    const title = await page.title();

    return {
      data: html,
      status: response?.status() ?? 200,
      title,
    };
  } catch (error) {
    console.error(`Puppeteer fetch failed for ${url}:`, error.message);
    throw error;
  } finally {
    if (browser) {
      try {
        await browser.close();
      } catch (e) {
        console.warn('Failed to close browser:', e.message);
      }
    }
  }
}

function isPuppeteerAvailable() {
  return Boolean(puppeteer && resolveChromePath());
}

module.exports = {
  USER_AGENT,
  fetchWithPuppeteer,
  resolveChromePath,
  isPuppeteerAvailable,
};
