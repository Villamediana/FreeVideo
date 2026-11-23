// A ponte (/api): sem passe de computador ligado não atende; com passe, cada computador tem seus
// limites (pedidos, idas ao YouTube, ao mesmo tempo) e um não enche a fila dos outros. YouTube de mentira.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createWeb } from '../server/app.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const A = 'A'.repeat(22);
const B = 'B'.repeat(22);

function setup() {
  let calls = 0;
  let hold = null;
  const tube = {
    reset() {},
    async search(text) {
      calls++;
      if (hold) await hold;
      return { items: [{ title: text }] };
    },
  };
  const passes = { check: async (h) => (h === 'pass-a' ? A : h === 'pass-b' ? B : null) };
  const web = createWeb({ publicDir: path.join(here, '..', 'public'), tube, passes, log: { error() {} } });
  return { web, tube, calls: () => calls, holdOn: () => { let open; hold = new Promise((r) => { open = r; }); return () => { hold = null; open(); }; } };
}

async function serve(web) {
  await new Promise((r) => web.server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${web.server.address().port}`;
  let n = 0;
  const get = (p, pass, ip) => fetch(base + p, { headers: { ...(pass ? { 'x-freevideo-pass': pass } : {}), 'cf-connecting-ip': ip ?? `10.1.${Math.floor(++n / 200)}.${n % 200}` } });
  return { get, close: () => new Promise((r) => { web.server.closeAllConnections?.(); web.server.close(r); }) };
}

test('sem passe a ponte não atende; as páginas, sim', async () => {
  const s = setup();
  const { get, close } = await serve(s.web);
  try {
    const no = await get('/api/search?q=oceano');
    assert.equal(no.status, 401);
    assert.equal((await no.json()).error, 'pass');
    assert.equal((await get('/api/search?q=oceano', 'inventado')).status, 401);
    assert.equal(s.calls(), 0, 'nada foi ao YouTube');
    assert.equal((await get('/')).status, 200);
    assert.equal((await get('/api/health')).status, 200);
    const ok = await get('/api/search?q=oceano', 'pass-a');
    assert.equal(ok.status, 200);
    assert.equal((await ok.json()).items[0].title, 'oceano');
  } finally {
    await close();
  }
});

test('cada computador: no máximo 6 ao mesmo tempo, e o outro segue sendo atendido', async () => {
  const s = setup();
  const { get, close } = await serve(s.web);
  const open = s.holdOn();
  try {
    const slow = Array.from({ length: 6 }, (_, i) => get(`/api/search?q=lento${i}`, 'pass-a'));
    await new Promise((r) => setTimeout(r, 100));
    assert.equal((await get('/api/search?q=mais', 'pass-a')).status, 429);
    open();
    assert.equal((await get('/api/search?q=outro', 'pass-b')).status, 200);
    for (const r of await Promise.all(slow)) assert.equal(r.status, 200);
  } finally {
    open();
    await close();
  }
});

test('idas ao YouTube por computador têm teto; o que está no cache não conta', async () => {
  const s = setup();
  const { get, close } = await serve(s.web);
  try {
    let refused = 0;
    for (let i = 0; i < 245; i++) if ((await get(`/api/search?q=busca${i}`, 'pass-a')).status === 429) refused++;
    assert.ok(refused >= 5, `recusou ${refused}`);
    assert.equal((await get('/api/search?q=busca1', 'pass-a')).status, 200, 'do cache: atende');
    assert.equal((await get('/api/search?q=nova', 'pass-b')).status, 200, 'o outro computador não sente');
  } finally {
    await close();
  }
});

test('as novidades das inscrições contam um por canal fora do cache', async () => {
  let rss = 0;
  const tube = { reset() {}, async latest(id) { rss++; return [{ id: `v-${id}` }]; } };
  const passes = { check: async (h) => (h === 'pass-a' ? A : null) };
  const web = createWeb({ publicDir: path.join(here, '..', 'public'), tube, passes, log: { error() {} } });
  const { get, close } = await serve(web);
  try {
    const ids = (k) => Array.from({ length: 60 }, (_, i) => `UC${String(k).padStart(2, '0')}${String(i).padStart(20, '0')}`).join(',');
    for (let k = 0; k < 4; k++) assert.equal((await get(`/api/latest?ids=${ids(k)}`, 'pass-a')).status, 200);
    assert.equal(rss, 240, 'quatro vezes 60 canais novos: o teto');
    const fifth = await (await get(`/api/latest?ids=${ids(9)}`, 'pass-a')).json();
    assert.equal(rss, 240, 'passou do teto: nenhum canal novo vai ao YouTube');
    assert.equal(fifth.items.length, 0);
    const cached = await (await get(`/api/latest?ids=${ids(0)}`, 'pass-a')).json();
    assert.equal(cached.items.length, 60, 'os do cache seguem vindo');
  } finally {
    await close();
  }
});
