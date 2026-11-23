// O cofre do FreeVideo no navegador: as mesmas regras do app (TubeVaultTest.kt), as mudanças da página
// e o feed (o mesmo cérebro do app) com dados de mentira.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { compact, edit, merge, normalize, same } from '../src/core/vault.js';
import { buildTaste, continueWatching, rank, tokens } from '../src/core/feed.js';

const watch = (id, last, pos = 0, watched = 60_000, plays = 1) => ({ i: id, t: `Video ${id}`, cn: 'Canal', cu: `https://www.youtube.com/channel/UC${id}`, d: 600_000, p: pos, w: watched, n: plays, f: last - 1000, l: last });
const V = normalize;

test('inscrições e salvos seguem todos os aparelhos', () => {
  const sub = (u, at = 1) => ({ u, n: `Canal ${u}`, at });
  const sv = (li, i, at = 1) => ({ li, i, t: `Video ${i}`, at });
  const base = V({ s: [sub('a'), sub('b')], sv: [sv('liked', 'x')] });
  const here = V({ s: [sub('a'), sub('b'), sub('c')], sv: [sv('liked', 'x'), sv('later', 'y')] });
  const there = V({ s: [sub('a'), sub('d')], sv: [] });
  const m = merge(base, here, there);
  assert.deepEqual(m.s.map((s) => s.u), ['a', 'c', 'd']);
  assert.deepEqual(m.sv.map((s) => `${s.li}:${s.i}`), ['later:y']);
});

test('o mesmo vídeo nos dois fica com o lugar mais novo e o maior tempo', () => {
  const here = V({ h: [watch('v', 100, 30_000, 90_000, 2)] });
  const there = V({ h: [watch('v', 200, 50_000, 40_000, 1), watch('w', 150)] });
  const m = merge(null, here, there);
  assert.deepEqual(m.h.map((w) => w.i), ['v', 'w']);
  assert.deepEqual([m.h[0].p, m.h[0].w, m.h[0].n, m.h[0].l], [50_000, 90_000, 2, 200]);
  const gone = merge(m, { ...m, h: m.h.filter((w) => w.i !== 'w') }, m);
  assert.deepEqual(gone.h.map((w) => w.i), ['v']);
  assert.ok(same(merge(null, V({}), m), m));
});

test('buscas somam; nada novo compara igual; o compacto volta igual', () => {
  const here = V({ q: [{ q: 'pão caseiro', n: 2, at: 10 }], in: [{ t: 'culinária', at: 5 }] });
  const there = V({ q: [{ q: 'pão caseiro', n: 5, at: 3 }, { q: 'drone', n: 1, at: 20 }], x: [{ k: 'v:abc', t: 'x', at: 1 }] });
  const m = merge(null, here, there);
  assert.deepEqual(m.q.map((q) => q.q), ['drone', 'pão caseiro']);
  assert.deepEqual([m.q[1].n, m.q[1].at], [5, 10]);
  assert.ok(same(merge(m, m, m), m));
  assert.ok(same(normalize(JSON.parse(JSON.stringify(compact(m)))), m));
});

test('assistir anota tempo e lugar; inscrever desfaz o "não recomendar"', () => {
  const v0 = V({ x: [{ k: 'c:https://www.youtube.com/channel/UCabc', t: 'Canal', at: 1 }] });
  const video = { id: 'abcdefghijk', title: 'Oi', channelName: 'Canal', channelUrl: 'https://www.youtube.com/channel/UCabc', durationMs: 0 };
  let v = edit.watch(v0, video, 30_000, 120_000, 25_000, true, 1000);
  v = edit.watch(v, video, 60_000, 120_000, 30_000, false, 2000);
  assert.deepEqual([v.h[0].p, v.h[0].w, v.h[0].n, v.h[0].d, v.h[0].f, v.h[0].l], [60_000, 55_000, 1, 120_000, 1000, 2000]);
  v = edit.toggleSub(v, { url: 'https://www.youtube.com/channel/UCabc', name: 'Canal' }, 3000);
  assert.equal(v.s.length, 1);
  assert.equal(v.x.length, 0);
  v = edit.toggleSub(v, { url: 'https://www.youtube.com/channel/UCabc', name: 'Canal' }, 4000);
  assert.equal(v.s.length, 0);
});

test('o feed: gosto do cofre, sem o que já foi visto, dispensado ou de canal dispensado; variedade', () => {
  const now = 1_800_000_000_000;
  const v = V({
    h: [{ ...watch('a1', now - 3_600_000, 590_000, 590_000), t: 'Receita de pão caseiro fácil' }, { ...watch('a2', now - 7_200_000, 100_000, 400_000), t: 'Pão de fermentação natural' }],
    s: [{ u: 'https://www.youtube.com/channel/UCsub', n: 'Sub', at: 1 }],
    x: [{ k: 'v:bad00000000', t: 'Ruim', at: 1 }, { k: 'c:https://www.youtube.com/channel/UCblock', t: 'Bloqueado', at: 1 }],
    q: [{ q: 'pão caseiro', n: 3, at: now - 1000 }],
  });
  const taste = buildTaste(v, now, { seed: 1 });
  assert.ok(taste.words.get('pao') > 0);
  assert.ok(taste.seeds.length >= 1);
  const vid = (id, title, ch, extra = {}) => ({ video: { id, title, channelName: ch, channelUrl: `https://www.youtube.com/channel/UC${ch}`, views: 10000, uploadedAt: now - 86_400_000, durationMs: 500_000, ...extra }, weight: 1, reason: { source: 'related', label: 'x' } });
  const cands = [
    vid('a1', 'Receita de pão caseiro fácil', 'x'), // já visto inteiro
    vid('bad00000000', 'Ruim', 'y'),
    { ...vid('blk', 'Pão bloqueado', 'block'), video: { ...vid('blk', 'Pão bloqueado', 'block').video, channelUrl: 'https://www.youtube.com/channel/UCblock' } },
    vid('s1', 'Pizza', 'q', { short: true }),
    ...Array.from({ length: 12 }, (_, i) => vid(`p${i}`, `Pão número ${i} ${['brioche', 'baguete', 'ciabatta', 'focaccia'][i % 4]}`, `c${i % 6}`)),
  ];
  const out = rank(cands, taste, now, { seed: 2, limit: 10 });
  const ids = out.map((e) => e.video.id);
  for (const bad of ['a1', 'bad00000000', 'blk', 's1']) assert.ok(!ids.includes(bad), bad);
  const perChannel = new Map();
  for (const e of out) perChannel.set(e.video.channelUrl, (perChannel.get(e.video.channelUrl) ?? 0) + 1);
  assert.ok(Math.max(...perChannel.values()) <= 3);
  assert.deepEqual(continueWatching(v, now).map((w) => w.i), ['a2']);
  assert.deepEqual(tokens('O Pão de Açúcar — HD 2024!'), ['pao', 'acucar']);
});
