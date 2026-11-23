// Ponta a ponta no Chromium (não entra no npm test: usa a rede de verdade e leva uns minutos):
//   node test/e2e.mjs <pasta das capturas>
// Sobe o relay (relay/) e a web num endereço só (como o túnel), faz um "celular" (conta + cofre com
// histórico, inscrições e salvos), liga o computador pelo QR code e confere: o feed, a página do vídeo,
// busca, canal, se inscrever aqui → chega no celular, o celular muda → aparece aqui, desligar, código.
import http from 'node:http';
import { mkdtempSync, mkdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';
import { createRelay } from '../../relay/src/server.js';
import { createWeb } from '../server/app.js';
import * as lock from '../src/core/crypto.js';
import { normalize } from '../src/core/vault.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const shots = path.resolve(process.argv[2] ?? path.join(tmpdir(), 'freevideo-web-shots'));
mkdirSync(shots, { recursive: true });
const CHROME = process.env.CHROME ?? `${process.env.HOME}/.cache/ms-playwright/chromium-1248/chrome-linux64/chrome`;

const dataDir = mkdtempSync(path.join(tmpdir(), 'freevideo-e2e-'));
const relay = createRelay({ dataDir, log: { error() {} } });
const web = createWeb({ publicDir: path.join(here, '..', 'public') });
const listen = (server) => new Promise((r) => server.listen(0, '127.0.0.1', () => r(server.address().port)));
const relayPort = await listen(relay.server);
const webPort = await listen(web.server);
// O "túnel" (o mesmo caminho que está no novelo.yml): o que é do relay vai pra ele, o resto pra web.
const RELAY = /^\/(v1|avatars|\.well-known)(\/|$)/;
const front = http.createServer((req, res) => {
  const port = RELAY.test(new URL(req.url, 'http://x').pathname) ? relayPort : webPort;
  const up = http.request({ host: '127.0.0.1', port, path: req.url, method: req.method, headers: req.headers }, (r) => {
    res.writeHead(r.statusCode, r.headers);
    r.pipe(res);
  });
  up.on('error', () => res.destroy());
  req.pipe(up);
});
const port = await listen(front);
const base = `http://127.0.0.1:${port}`;

async function api(method, p, { token, body } = {}) {
  const r = await fetch(base + p, { method, headers: { ...(token ? { authorization: `Bearer ${token}` } : {}), ...(body ? { 'content-type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined });
  return { status: r.status, json: await r.json().catch(() => null) };
}

const steps = [];
const check = (ok, what) => {
  steps.push(`${ok ? 'ok ' : 'FALHOU'} ${what}`);
  console.log(`${ok ? 'ok ' : 'FALHOU'} ${what}`);
  if (!ok) process.exitCode = 1;
};

// ------------------------------------------------------------------ o celular
const acct = (await api('POST', '/v1/accounts', { body: {} })).json;
const phone = { token: acct.token, code: acct.recoveryCode, id: acct.me.id };
await api('PATCH', '/v1/me', { token: phone.token, body: { name: 'Miguel' } });
const raw = globalThis.crypto.getRandomValues(new Uint8Array(32));
const phoneKey = await globalThis.crypto.subtle.importKey('raw', raw, 'AES-GCM', false, ['encrypt', 'decrypt']);
const now = Date.now();
const found = (await (await fetch(`${base}/api/search?q=${encodeURIComponent('receita pão caseiro')}&type=videos`)).json()).items.filter((x) => x.kind === 'video');
const tech = (await (await fetch(`${base}/api/search?q=${encodeURIComponent('review celular')}&type=videos`)).json()).items.filter((x) => x.kind === 'video');
const w = (x, i, frac) => ({ i: x.id, t: x.title, cn: x.channelName, cu: x.channelUrl, th: x.thumbnailUrl, d: 600_000, p: Math.round(600_000 * frac), w: Math.round(600_000 * Math.max(frac, 0.5)), n: 1, f: now - i * 3_600_000, l: now - i * 3_600_000 });
const content = {
  h: [...found.slice(0, 5).map((x, i) => w(x, i, i === 0 ? 0.4 : 0.95)), ...tech.slice(0, 4).map((x, i) => w(x, i + 5, 0.9))],
  s: [found[0], tech[0]].filter((x) => x.channelUrl).map((x, i) => ({ u: x.channelUrl, n: x.channelName, at: now - i * 1000 })),
  sv: [{ li: 'later', i: tech[5].id, t: tech[5].title, cn: tech[5].channelName, cu: tech[5].channelUrl, th: tech[5].thumbnailUrl, d: 0, at: now }],
  q: [{ q: 'receita pão caseiro', n: 3, at: now - 1000 }, { q: 'review celular', n: 1, at: now - 2000 }],
  in: [{ t: 'culinária', at: now }],
};
const sealed = await lock.phoneVault(content, raw, phone.code, phone.id);
check((await api('PUT', '/v1/me/vault', { token: phone.token, body: { baseRev: 0, data: sealed.data, key: sealed.key } })).json?.rev === 1, 'o celular grava o cofre');

// ------------------------------------------------------------------ o computador
const browser = await chromium.launch({ executablePath: CHROME, args: ['--autoplay-policy=no-user-gesture-required'] });
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'pt-BR', colorScheme: 'light' });
const page = await ctx.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (m.type() === 'error' && !/youtube|ytimg|googlevideo|doubleclick|google|ggpht/.test(m.text())) errors.push(m.text()); });
const shot = async (name) => page.screenshot({ path: path.join(shots, `${name}.png`) });

await page.goto(`${base}/`);
await page.waitForSelector('.qr[data-code]', { timeout: 20_000 });
await page.waitForTimeout(400);
await shot('01-ligar');
const url = await page.getAttribute('.qr', 'data-code');
const m = /#([A-Za-z0-9_-]{22})\.([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]{22})$/.exec(url);
check(!!m && url.startsWith(`${base}/link#`), 'o QR code tem o endereço, a chave e o segredo');
const wrapped = await lock.wrapLink(m[2], m[1], m[3], { user: phone.id, key: raw, owner: 'Pixel 8' });
check((await api('POST', `/v1/link/${m[1]}/approve`, { token: phone.token, body: { wrapped, owner: 'Pixel 8' } })).status === 200, 'o celular aprova');
await page.waitForSelector('.vgrid .vcard', { timeout: 60_000 });
await page.waitForTimeout(4000);
await shot('02-inicio');
check(await page.locator('.vcard').count() >= 8, `o feed tem vídeos (${await page.locator('.vcard').count()})`);
check(await page.locator('.shelf', { hasText: 'Continuar assistindo' }).count() === 1, 'continuar assistindo (do histórico do celular)');

// A página do vídeo
await page.locator('.vgrid .vcard').nth(3).click();
await page.waitForSelector('.watch-title', { timeout: 20_000 });
await page.waitForTimeout(6000);
await shot('03-video');
const dock = await page.evaluate(() => document.querySelector('#yt-dock iframe')?.src ?? null);
check(!!dock && dock.includes('youtube-nocookie.com/embed/') && dock.includes('controls=1'), 'o player do YouTube abriu no palco');
// Inscrever-se aqui → chega no celular
const chName = await page.locator('.watch-ch-name').innerText();
const wasSubscribed = (await page.locator('.watch-channel .pill').innerText()).includes('Inscrito');
await page.click('.watch-channel .pill');
await page.waitForTimeout(3500);
const after = (await api('GET', '/v1/me/vault', { token: phone.token })).json;
const opened = normalize(await lock.openVault(after.data, phoneKey));
check(opened.s.some((s) => s.n === chName) === !wasSubscribed, `${wasSubscribed ? 'cancelar a inscrição' : 'inscrever-se'} aqui chega no celular ("${chName}")`);
// Comentários
await page.click('button:has-text("Comentários")');
await page.waitForSelector('.comment', { timeout: 20_000 }).catch(() => {});
await page.waitForTimeout(800);
await page.locator('.comments').scrollIntoViewIfNeeded();
await shot('04-comentarios');

// O celular salva um vídeo pra mais tarde → aparece aqui na hora
const later = { ...opened, sv: [...opened.sv, { li: 'later', i: found[6].id, t: 'Do celular: ' + found[6].title, cn: found[6].channelName, cu: found[6].channelUrl, th: found[6].thumbnailUrl, d: 0, at: Date.now() }] };
check((await api('PUT', '/v1/me/vault', { token: phone.token, body: { baseRev: after.rev, data: await lock.sealVault(later, phoneKey) } })).status === 200, 'o celular grava de novo');
await page.evaluate(() => { location.hash = '/list/later'; });
const t0 = Date.now();
await page.waitForSelector('.vrow:has-text("Do celular:")', { timeout: 15_000 }).catch(() => {});
check(await page.locator('.vrow', { hasText: 'Do celular:' }).count() === 1, `o que o celular salvou aparece aqui (${Date.now() - t0} ms)`);
// O vídeo continua no canto
await shot('05-mais-tarde-mini');
check(await page.locator('.mini-bar').count() === 1, 'o vídeo segue pequeno no canto');

// Busca, canal, inscrições, biblioteca, histórico, perfil
await page.click('.search-box input');
await page.keyboard.type('documentário espaço', { delay: 30 });
await page.keyboard.press('Enter');
await page.waitForSelector('.list .vrow', { timeout: 20_000 });
await page.waitForTimeout(1500);
await shot('06-busca');
await page.evaluate((id) => { location.hash = `/channel/${id}`; }, found[0].channelId);
await page.waitForSelector('.channel-head h1', { timeout: 20_000 });
await page.waitForTimeout(1500);
await shot('07-canal');
for (const [hash, name] of [['/subs', '08-inscricoes'], ['/library', '09-biblioteca'], ['/list/history', '10-historico'], ['/profile', '11-perfil']]) {
  await page.evaluate((h) => { location.hash = h; }, hash);
  await page.waitForTimeout(2500);
  await shot(name);
}
await page.evaluate(() => { location.hash = '/'; });
await page.waitForTimeout(1500);
await page.locator('.vgrid .vcard').first().click({ button: 'right' });
await page.waitForSelector('.menu.ready');
await shot('12-menu');
await page.keyboard.press('Escape');
await page.emulateMedia({ colorScheme: 'dark' });
await page.evaluate(() => { localStorage.setItem('freevideo-prefs', JSON.stringify({ mode: 'dark' })); });
await page.reload();
await page.waitForSelector('.vgrid .vcard', { timeout: 60_000 });
await page.waitForTimeout(3000);
await shot('13-inicio-escuro');

// Desligar daqui
await page.evaluate(() => { location.hash = '/profile'; });
await page.click('button:has-text("Desligar este computador")');
await page.click('.dialog button:has-text("Desligar")');
await page.waitForSelector('text=Este computador foi desligado', { timeout: 10_000 });
check((await api('GET', '/v1/me/computers', { token: phone.token })).json.computers.length === 0, 'desligado: some da lista do celular');
const left = await page.evaluate(async () => (await indexedDB.databases()).filter((d) => d.name === 'freevideo-web').length);
check(left === 0, 'e nada fica no navegador');

const p2 = await ctx.newPage();
await p2.goto(`${base}/`);
await p2.click('.text-btn');
await p2.fill('.code-form input', phone.code.toLowerCase());
await p2.click('.code-form button[type=submit]');
await p2.waitForSelector('.vgrid .vcard', { timeout: 60_000 });
check(await p2.locator('.rail').count() === 1, 'pelo código de recuperação o cofre abre');

check(errors.length === 0, `sem erros na página${errors.length ? `: ${errors.slice(0, 3).join(' | ')}` : ''}`);
await browser.close();
await new Promise((r) => front.close(r));
await relay.close();
web.server.close();
rmSync(dataDir, { recursive: true, force: true });
console.log(`\n${steps.filter((s) => s.startsWith('ok')).length}/${steps.length} ok · capturas em ${shots}`);
process.exit(process.exitCode ?? 0);
