// Makes the pictures for the GitHub README (.github/readme/*.png) in the style of hookdev.io: a banner, shelves of
// renders, and the faces and the info screen as the docs page draws them. Everything comes from
// the site repo next to this one (its fonts, its renders from render-showcase.mjs and its docs page), shot with
// headless Chrome. Made from Setpoint's tools/readme-art.mjs. Re-run it after the renders change:
//   node tools/readme-art.mjs ../hook-development
import { spawn } from 'node:child_process';
import { createReadStream, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, resolve } from 'node:path';
import { tmpdir } from 'node:os';

const SITE = resolve(process.argv[2] || '../hook-development');
const OUT = '.github/readme';
const CHROME = process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const PORT = 8131, DEBUG = 9341;
// the version on the banner comes from the build, so it can never fall behind a release
const VERSION = readFileSync('gradle.properties', 'utf8').match(/^mod_version=(.+)$/m)[1].trim();
mkdirSync(OUT, { recursive: true });

// ---------- the pictures ----------
const IMG = '/projects/minecraft-mods/bleep/img/';
const HOOK = '<svg width="28" height="28" viewBox="0 0 16 16" shape-rendering="crispEdges"><g fill="#2de2e6"><rect x="7" y="0" width="4" height="1"/><rect x="6" y="1" width="1" height="2"/><rect x="11" y="1" width="1" height="2"/><rect x="7" y="3" width="4" height="1"/><rect x="9" y="4" width="2" height="10"/><rect x="8" y="14" width="2" height="1"/><rect x="2" y="15" width="7" height="1"/><rect x="1" y="11" width="2" height="4"/><rect x="3" y="12" width="1" height="1"/></g></svg>';
const BASE = `<link rel="stylesheet" href="/assets/fonts.css"><style>
*{box-sizing:border-box}html,body{margin:0;background:transparent}
body{font-family:"Chakra Petch",sans-serif;color:#d9deea}
.art{background:#0a0c13;background-image:repeating-linear-gradient(0deg,rgba(255,255,255,.018) 0 1px,transparent 1px 3px);position:relative}
img{image-rendering:pixelated}
.bar{display:flex;align-items:center;gap:8px;padding:10px 14px;border-bottom:2px solid #232a3d;background:#0f121c;font:20px "VT323",monospace;color:#7c869d}
.bar i{width:12px;height:12px;display:block}
.chip{font:12px "Silkscreen",monospace;padding:6px 10px;border:2px solid;letter-spacing:.04em}
</style>`;

const banner = `${BASE}<style>
.art{width:1280px;height:470px;overflow:hidden;border:2px solid #232a3d}
.grid{position:absolute;inset:0;background-image:linear-gradient(#2de2e60d 1px,transparent 1px),linear-gradient(90deg,#2de2e60d 1px,transparent 1px);background-size:32px 32px}
.left{position:absolute;left:56px;top:30px;width:640px;display:flex;flex-direction:column;gap:16px}
.logo{margin:6px 0 10px;font:700 120px/1 "Silkscreen",monospace;color:#f2f4fa;text-shadow:8px 8px 0 #ff4f8b88;letter-spacing:.02em}
h1{margin:0;font:700 26px/1.25 "Silkscreen",monospace;color:#f2f4fa;text-shadow:3px 3px 0 #ff4f8b66}
p{margin:0;font-size:18px;color:#aeb6c9;max-width:440px}
.chips{display:flex;gap:10px}
.sky{position:absolute;right:40px;bottom:0;display:flex;align-items:flex-end;gap:22px}
.ground{position:absolute;left:0;right:0;bottom:0;height:22px;background:#0f121c;border-top:2px solid #232a3d}
.by{position:absolute;right:28px;top:22px;display:flex;align-items:center;gap:10px;font:14px "Silkscreen",monospace;color:#f2f4fa}
</style>
<div class="art" id="art"><div class="grid"></div><div class="ground"></div>
  <div class="left">
    <div class="logo">BLEEP</div>
    <h1>A robot pet<br>with an attitude</h1>
    <p>Build one from a core you find deep down. It shows how it feels, thinks out loud, and never dies: it only runs out of battery.</p>
    <div class="chips"><span class="chip" style="color:#2de2e6">Minecraft 26.1.2 · 26.3</span><span class="chip" style="color:#2de2e6">NeoForge</span><span class="chip" style="color:#ffb547">Version ${VERSION}</span><span class="chip" style="color:#ff4f8b">Open source</span></div>
  </div>
  <div class="sky">
    <img src="${IMG}bleep_blue.png" style="height:240px;margin-bottom:12px">
    <img src="${IMG}bleep_red.png" style="height:225px;margin-bottom:12px">
    <img src="${IMG}bleep_lime.png" style="height:240px;margin-bottom:12px">
  </div>
  <div class="by">${HOOK}HOOK DEV</div>
</div>`;

// one shelf: [render, label, height in px]
function shelf(file, items) {
  return `${BASE}<style>
.art{width:1200px;border:2px solid #232a3d}
.items{display:flex;flex-wrap:wrap;align-items:flex-end;justify-content:center;gap:26px 30px;padding:30px 26px 24px}
figure{margin:0;display:flex;flex-direction:column;align-items:center;gap:10px}
figcaption{font:20px "VT323",monospace;color:#aeb6c9;text-align:center;max-width:160px;line-height:1}
</style>
<div class="art" id="art"><div class="bar"><i style="background:#ff4f8b"></i><i style="background:#ffb547"></i><i style="background:#2de2e6"></i><span>&nbsp;${file}</span></div>
<div class="items">${items.map(([img, label, h]) => `<figure><img src="${IMG}${img}.png" style="height:${h}px"><figcaption>${label}</figcaption></figure>`).join('')}</div></div>`;
}
// every face on its display, in a white rim like the bleep's head
function faceShelf() {
  const FACES = [['happy', 'Happy'], ['excited', 'Excited'], ['sad', 'Sad'], ['angry', 'Angry'], ['bored', 'Bored'], ['surprised', 'Surprised'],
    ['tired', 'Tired'], ['asleep', 'Asleep'], ['stubborn', 'Stubborn'], ['glitch_0', 'Glitching'], ['boot_1', 'Starting up']];
  return shelf('display.render', FACES.map(([f, label]) => ['faces/' + f, label, 80])).replace('figure{margin:0;', 'figure img{border:4px solid #b7c0c9}figure{margin:0;');
}
const M = 150, L = 200;
const SHELVES = {
  bleep: shelf('bleep.render', [['bleep_assembling', 'Being put together', M - 30], ['bleep', 'Happy', L], ['bleep_excited', 'Excited', L], ['bleep_asleep', 'Switched off', L], ['bleep_home', 'Charging at home', L + 20]]),
  faces: faceShelf(),
  dress: shelf('dress-up.render', [['bleep_red', 'Party hat', L], ['bleep_blue', 'Top hat', L + 10], ['bleep_lime', 'Antenna, heart', L + 10], ['bleep_yellow', 'Star sticker', L - 30], ['bleep_magenta', 'Party hat, bolt', L], ['bleep_cyan', 'Antenna', L + 10]]),
  parts: shelf('parts.render', [['bleep_core', 'Bleep Core', 96], ['bleep_display', 'Bleep Display', 96], ['bleep_tracks', 'Bleep Tracks', 96], ['bleep_arms', 'Bleep Arms', 96], ['power_bank', 'Power Bank', 96], ['homebase_charging', 'Homebase', M]]),
};

// ---------- a little server for the site, plus the pictures above ----------
const TYPES = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
const PAGES = { banner, ...SHELVES };
const server = createServer((req, res) => {
  let p = decodeURIComponent(req.url.split('?')[0]);
  const page = p.match(/^\/__readme\/(\w+)$/);
  if (page) { res.writeHead(200, { 'content-type': 'text/html' }); res.end(`<!doctype html><meta charset="utf-8">${PAGES[page[1]]}`); return; }
  if (p.endsWith('/')) p += 'index.html';
  const f = join(SITE, p);
  if (!existsSync(f)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': TYPES[extname(f)] || 'application/octet-stream' }); createReadStream(f).pipe(res);
}).listen(PORT);

const profile = join(tmpdir(), 'bleep-readme-chrome');
rmSync(profile, { recursive: true, force: true });
const chrome = spawn(CHROME, ['--headless=new', '--disable-gpu', `--remote-debugging-port=${DEBUG}`, `--user-data-dir=${profile}`, '--window-size=1400,1000', 'about:blank']);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let tabs;
for (let i = 0; i < 120 && !tabs?.length; i++) { try { tabs = await (await fetch(`http://127.0.0.1:${DEBUG}/json`)).json(); } catch {} await sleep(250); }
const ws = new WebSocket(tabs.find((t) => t.type === 'page').webSocketDebuggerUrl);
await new Promise((r) => (ws.onopen = r));
let id = 0; const pend = {};
ws.onmessage = (e) => { const m = JSON.parse(e.data); if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; } };
const send = (method, params = {}) => new Promise((r) => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
const ev = async (x) => (await send('Runtime.evaluate', { expression: x, returnByValue: true, awaitPromise: true })).result.result.value;
await send('Page.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 1400, height: 1000, deviceScaleFactor: 2, mobile: false });

async function shoot(url, selector, name, prep = '') {
  await send('Page.navigate', { url: `http://127.0.0.1:${PORT}${url}` });
  await sleep(1500);
  await ev('document.fonts.ready.then(()=>true)');
  // no boot screen, no pixel transition, no footer toasts in the shots
  await ev(`document.querySelectorAll('.ws-boot,.ws-toast,.px-overlay').forEach(e=>e.remove());${prep}`);
  await sleep(400);
  const box = await ev(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();return {x:r.x+scrollX,y:r.y+scrollY,width:r.width,height:r.height}})()`);
  await sleep(300);
  const shot = await send('Page.captureScreenshot', { format: 'png', clip: { ...box, scale: 1 }, captureBeyondViewport: true });
  writeFileSync(join(OUT, name + '.png'), Buffer.from(shot.result.data, 'base64'));
  console.log('wrote', join(OUT, name + '.png'), Math.round(box.width), 'x', Math.round(box.height));
}

for (const name of Object.keys(PAGES)) await shoot(`/__readme/${name}`, '#art', name === 'banner' ? 'banner' : 'shelf-' + name);
const DOCS = '/projects/minecraft-mods/bleep/';
await shoot(DOCS, '#info', 'info-screen');

chrome.kill(); server.close(); process.exit(0);
