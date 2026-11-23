// A ponte com o YouTube (InnerTube "WEB", pela youtubei.js), sem o player: busca, sugestões, a página
// de um vídeo (dados, descrição, o que vem a seguir), comentários, canal, playlist, as novidades dos
// canais (pelo RSS: leve). Tudo normalizado no formato do app
// (tube/online/TubeModels.kt):
//
//   vídeo    {kind: 'video', id, title, channelName, channelUrl, channelId, channelAvatar, thumbnailUrl,
//             durationMs, views, viewsText, uploadedAt, uploadedText, live, short, verified}
//   canal    {kind: 'channel', id, url, name, avatarUrl, handle, subscribersText, verified}
//   playlist {kind: 'playlist', id, url, name, uploader, thumbnailUrl, count}
//
// O vídeo em si a página toca pelo player do próprio YouTube.
import { Innertube, Log } from 'youtubei.js';

Log.setLevel(Log.Level.NONE);

export const VIDEO = /^[A-Za-z0-9_-]{11}$/;
export const CHANNEL = /^UC[A-Za-z0-9_-]{22}$/;
export const PLAYLIST = /^(PL|OL|UU|LL|FL|RD)[A-Za-z0-9_-]{8,80}$/;
const LANGS = { pt: 'pt', es: 'es', en: 'en' };
const DAY = 86_400_000;

export function text(x) {
  if (x == null) return '';
  if (typeof x === 'string') return x.trim();
  if (typeof x.text === 'string') return x.text.trim();
  if (typeof x.toString === 'function' && x.toString !== Object.prototype.toString) return String(x).trim();
  return '';
}

const https = (u) => (typeof u === 'string' ? (u.startsWith('//') ? `https:${u}` : u.startsWith('https://') ? u : null) : null);

function bestImage(list, size = 0) {
  const arr = Array.isArray(list) ? list : [];
  const best = [...arr].filter((t) => t?.url).sort((a, b) => (b.width ?? 0) - (a.width ?? 0))[0];
  let u = https(best?.url);
  if (u && size && /googleusercontent\.com\/|ggpht\.com\//.test(u)) u = u.replace(/=s\d+(?=-|$)/, `=s${size}`).replace(/=w\d+-h\d+/, `=w${size}-h${size}`);
  return u;
}

/** A miniatura de um vídeo: a 16:9 do YouTube (mqdefault não tem tarja; hq720 quando existe). */
export const thumb = (id) => `https://i.ytimg.com/vi/${id}/mqdefault.jpg`;

/** "10:43" / "1:02:03" → ms. */
export function duration(t) {
  const m = /^(?:(\d+):)?(\d{1,2}):(\d{2})$/.exec(String(t ?? '').trim());
  return m ? (Number(m[1] ?? 0) * 3600 + Number(m[2]) * 60 + Number(m[3])) * 1000 : 0;
}

/** "10.505.020 visualizações", "1,2 mi", "203 mil", "1.2M views", "203K" → número (−1 se não diz). */
export function views(t) {
  const s = String(t ?? '').toLowerCase().replace(/ /g, ' ').trim();
  if (!s || /sem visualiza|no views|sin vistas/.test(s)) return s ? 0 : -1;
  const m = /([\d.,]+)\s*(mil|mi|bi|k|m|b|mill|mil millones)?\b/.exec(s);
  if (!m) return -1;
  let n = m[1];
  const unit = m[2];
  if (!unit) return Number(n.replace(/[.,\s]/g, '')) || 0;
  n = Number(n.replace(/\./g, n.includes(',') ? '' : '.').replace(',', '.'));
  const mult = { mil: 1e3, k: 1e3, mi: 1e6, m: 1e6, mill: 1e6, bi: 1e9, b: 1e9, 'mil millones': 1e9 }[unit] ?? 1;
  return Number.isFinite(n) ? Math.round(n * mult) : -1;
}

const UNITS = [
  [/segundo|second/, 1000], [/minuto|minute/, 60_000], [/hora|hour/, 3_600_000], [/dia|día|day/, DAY],
  [/semana|week/, 7 * DAY], [/m[eê]s|month/, 30 * DAY], [/ano|año|year/, 365 * DAY],
];

/** "há 3 dias", "hace 2 semanas", "5 months ago", "Transmitido há 1 hora" → a hora aproximada (ms). */
export function uploadedAt(t, now = Date.now()) {
  const s = String(t ?? '').toLowerCase();
  const m = /(\d+)\s*([a-zçãáéíóúêô]+)/.exec(s);
  if (!m) return null;
  for (const [re, ms] of UNITS) if (re.test(m[2])) return now - Number(m[1]) * ms;
  return null;
}

function channelOfAuthor(a) {
  const id = CHANNEL.test(a?.id ?? '') ? a.id : null;
  return { channelName: text(a?.name), channelId: id, channelUrl: id ? `https://www.youtube.com/channel/${id}` : null, channelAvatar: bestImage(a?.thumbnails, 88), verified: !!(a?.is_verified || a?.is_verified_artist) };
}

/** Um vídeo de qualquer lista (Video, CompactVideo, GridVideo, LockupView, Reel/Shorts); null se não é. */
export function video(it, ctx = {}) {
  if (!it) return null;
  if (it.type === 'LockupView') {
    if (it.content_type && it.content_type !== 'VIDEO') return null;
    const id = it.content_id;
    if (!VIDEO.test(id ?? '')) return null;
    const md = it.metadata;
    const rows = md?.metadata?.metadata_rows?.map((r) => r.metadata_parts?.map((p) => text(p.text)) ?? []) ?? [];
    const flat = rows.flat().filter(Boolean);
    const badge = (it.content_image?.overlays ?? []).flatMap((o) => (o.badges ?? []).map((b) => text(b.text) || text(b))).find(Boolean) ?? '';
    const avatarPayload = md?.image?.renderer_context?.command_context?.on_tap?.payload ?? {};
    const chId = CHANNEL.test(avatarPayload.browseId ?? '') ? avatarPayload.browseId : ctx.channelId ?? null;
    const live = /ao vivo|live|en vivo|en directo/i.test(badge);
    const viewsText = flat.find((x) => /\d/.test(x) && !/há|ago|hace/.test(x) && x !== badge && /(mil|mi|bi|k|m|b|visualiza|views|vistas|assistindo|watching|viendo|\d)/i.test(x)) ?? '';
    const when = flat.find((x) => /há|ago|hace|transmitido|streamed/i.test(x)) ?? '';
    // Na página de um canal o nome é o dele; nas listas, a primeira linha (se não for data nem visualizações).
    const first = rows.length > 1 ? rows[0].join(' ') : '';
    const name = ctx.channelName || (/\d/.test(first) && /há|ago|hace|visualiza|views|vistas/i.test(first) ? '' : first);
    return {
      kind: 'video',
      id,
      title: text(md?.title),
      channelName: name || ctx.channelName || '',
      channelId: chId,
      channelUrl: chId ? `https://www.youtube.com/channel/${chId}` : null,
      channelAvatar: bestImage(md?.image?.avatar?.image ?? md?.image?.image, 88) ?? ctx.channelAvatar ?? null,
      thumbnailUrl: thumb(id),
      durationMs: duration(badge),
      views: views(viewsText),
      viewsText: viewsText || null,
      uploadedAt: uploadedAt(when),
      uploadedText: when || null,
      live,
      short: false,
      verified: false,
    };
  }
  if (it.type === 'ShortsLockupView' || it.type === 'ReelItem') {
    const id = it.on_tap_endpoint?.payload?.videoId ?? it.id;
    if (!VIDEO.test(id ?? '')) return null;
    return { kind: 'video', id, title: text(it.overlay_metadata?.primary_text ?? it.title), channelName: ctx.channelName ?? '', channelId: ctx.channelId ?? null, channelUrl: ctx.channelId ? `https://www.youtube.com/channel/${ctx.channelId}` : null, channelAvatar: null, thumbnailUrl: thumb(id), durationMs: 0, views: views(text(it.overlay_metadata?.secondary_text ?? it.views)), viewsText: text(it.overlay_metadata?.secondary_text) || null, uploadedAt: null, uploadedText: null, live: false, short: true, verified: false };
  }
  if (!/^(Video|CompactVideo|GridVideo|PlaylistVideo|PlaylistPanelVideo)$/.test(it.type)) return null;
  const id = it.id ?? it.video_id;
  if (!VIDEO.test(id ?? '')) return null;
  const author = channelOfAuthor(it.author);
  const pub = text(it.published);
  const vt = text(it.view_count) || text(it.short_view_count);
  const live = !!it.is_live || (it.badges ?? []).some((b) => /ao vivo|live/i.test(b?.label ?? ''));
  return {
    kind: 'video',
    id,
    title: text(it.title),
    channelName: author.channelName || ctx.channelName || '',
    channelId: author.channelId ?? ctx.channelId ?? null,
    channelUrl: author.channelUrl ?? (ctx.channelId ? `https://www.youtube.com/channel/${ctx.channelId}` : null),
    channelAvatar: author.channelAvatar ?? ctx.channelAvatar ?? null,
    thumbnailUrl: thumb(id),
    durationMs: (it.duration?.seconds ? it.duration.seconds * 1000 : 0) || duration(text(it.length_text) || text(it.duration)),
    views: views(vt),
    viewsText: vt || null,
    uploadedAt: uploadedAt(pub),
    uploadedText: pub || null,
    live,
    short: false,
    verified: author.verified,
  };
}

export function channel(it) {
  if (!it) return null;
  const id = it.id ?? it.author?.id;
  if (!CHANNEL.test(id ?? '')) return null;
  const subs = text(it.subscriber_count) || text(it.subscribers);
  const vids = text(it.video_count);
  // A busca às vezes põe o @ no lugar dos inscritos.
  const handle = [subs, vids].find((x) => x.startsWith('@')) ?? null;
  return {
    kind: 'channel',
    id,
    url: `https://www.youtube.com/channel/${id}`,
    name: text(it.author?.name) || text(it.title),
    avatarUrl: bestImage(it.author?.thumbnails ?? it.thumbnails, 176),
    handle,
    subscribersText: [subs, vids].find((x) => /inscrit|subscri|suscrip/i.test(x)) ?? null,
    verified: !!it.author?.is_verified,
  };
}

export function playlist(it) {
  if (!it) return null;
  if (it.type === 'LockupView') {
    if (it.content_type !== 'PLAYLIST') return null;
    const id = it.content_id;
    if (!PLAYLIST.test(id ?? '')) return null;
    const rows = it.metadata?.metadata?.metadata_rows?.map((r) => r.metadata_parts?.map((p) => text(p.text)) ?? []) ?? [];
    const badge = (it.content_image?.primary_thumbnail?.overlays ?? it.content_image?.overlays ?? []).flatMap((o) => (o.badges ?? []).map((b) => text(b.text))).find(Boolean) ?? '';
    const img = it.content_image?.primary_thumbnail?.image ?? it.content_image?.image;
    return { kind: 'playlist', id, url: `https://www.youtube.com/playlist?list=${id}`, name: text(it.metadata?.title), uploader: rows[0]?.join(' ') || null, thumbnailUrl: bestImage(img), count: Number.parseInt(badge.replace(/\D/g, ''), 10) || null };
  }
  const id = it.id;
  if (!PLAYLIST.test(id ?? '')) return null;
  return { kind: 'playlist', id, url: `https://www.youtube.com/playlist?list=${id}`, name: text(it.title), uploader: text(it.author?.name) || null, thumbnailUrl: bestImage(it.thumbnails ?? it.thumbnail), count: Number.parseInt(text(it.video_count).replace(/\D/g, ''), 10) || null };
}

const anything = (it, ctx) => video(it, ctx) ?? channel(it) ?? playlist(it);

function clean(list, fn = anything, ctx = {}) {
  const seen = new Set();
  const out = [];
  for (const it of list ?? []) {
    const v = fn(it, ctx);
    const key = v && `${v.kind}:${v.id}`;
    if (!v || seen.has(key)) continue;
    seen.add(key);
    out.push(v);
  }
  return out;
}

const TYPES = { videos: 'video', channels: 'channel', playlists: 'playlist' };

/** O RSS de um canal: os 15 vídeos mais novos, com a hora exata (leve: nada de InnerTube). */
export function parseRss(xml, channelId) {
  const name = /<author>\s*<name>([^<]*)<\/name>/.exec(xml)?.[1] ?? '';
  const out = [];
  for (const m of xml.matchAll(/<entry>([\s\S]*?)<\/entry>/g)) {
    const e = m[1];
    const id = /<yt:videoId>([A-Za-z0-9_-]{11})<\/yt:videoId>/.exec(e)?.[1];
    if (!id) continue;
    const title = /<title>([^<]*)<\/title>/.exec(e)?.[1] ?? '';
    const published = Date.parse(/<published>([^<]+)<\/published>/.exec(e)?.[1] ?? '');
    const views = Number(/<media:statistics views="(\d+)"/.exec(e)?.[1] ?? -1);
    const link = /<link rel="alternate" href="([^"]+)"/.exec(e)?.[1] ?? '';
    out.push({
      kind: 'video', id, title: unescapeXml(title), channelName: unescapeXml(name), channelId, channelUrl: `https://www.youtube.com/channel/${channelId}`, channelAvatar: null,
      thumbnailUrl: thumb(id), durationMs: 0, views, viewsText: null, uploadedAt: Number.isFinite(published) ? published : null, uploadedText: null, live: false, short: link.includes('/shorts/'), verified: false,
    });
  }
  return out;
}

const unescapeXml = (s) => s.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&#39;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');

export class Tube {
  constructor({ create, fetch: f = globalThis.fetch, clock = Date.now } = {}) {
    this.create = create ?? ((lang) => Innertube.create({ lang, location: 'BR', retrieve_player: false, generate_session_locally: true }));
    this.fetch = f;
    this.clock = clock;
    this.clients = new Map();
    this.pages = new Map();
  }

  async yt(lang) {
    const l = LANGS[lang] ?? 'pt';
    const hit = this.clients.get(l);
    if (hit && this.clock() - hit.at < 6 * 3600_000) return hit.p;
    const p = this.create(l);
    this.clients.set(l, { at: this.clock(), p });
    p.catch(() => this.clients.delete(l));
    return p;
  }

  reset(lang) {
    this.clients.delete(LANGS[lang] ?? 'pt');
  }

  keep(page, ctx = {}) {
    const now = this.clock();
    for (const [k, v] of this.pages) if (now - v.at > 15 * 60_000) this.pages.delete(k);
    if (this.pages.size > 5000) this.pages.delete(this.pages.keys().next().value);
    const token = globalThis.crypto.randomUUID();
    this.pages.set(token, { at: now, page, ctx });
    return token;
  }

  async search(q, type, lang) {
    const yt = await this.yt(lang);
    const r = await yt.search(q, TYPES[type] ? { type: TYPES[type] } : {});
    return { items: clean(r.results), next: r.has_continuation ? this.keep(r) : null };
  }

  /** A próxima página de uma busca, de um canal, de uma playlist ou dos comentários. */
  async more(token) {
    const hit = this.pages.get(token);
    if (!hit) return null;
    this.pages.delete(token);
    const r = await hit.page.getContinuation();
    if (hit.ctx.comments) return { comments: (r.contents ?? []).map(comment).filter(Boolean), next: r.has_continuation ? this.keep(r, hit.ctx) : null };
    const list = r.results ?? r.videos ?? r.items ?? r.contents ?? [];
    return { items: clean(list, anything, hit.ctx), next: r.has_continuation ? this.keep(r, hit.ctx) : null };
  }

  /** As sugestões da busca (o mesmo serviço do YouTube; a resposta vem em latin-1 ou UTF-8, conforme o idioma). */
  async suggest(q, lang) {
    const l = LANGS[lang] ?? 'pt';
    const url = `https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&ds=yt&hl=${l}&gl=br&q=${encodeURIComponent(q)}`;
    const r = await this.fetch(url, { signal: AbortSignal.timeout(6000), headers: { 'User-Agent': 'Mozilla/5.0 FreeVideo' } });
    if (!r.ok) throw new Error(`suggest ${r.status}`);
    const charset = /charset=([\w-]+)/i.exec(r.headers.get('content-type') ?? '')?.[1] ?? 'utf-8';
    const body = new TextDecoder(charset.toLowerCase() === 'iso-8859-1' ? 'latin1' : charset).decode(await r.arrayBuffer());
    const json = JSON.parse(body.slice(body.indexOf('('), body.lastIndexOf(')') + 1).replace(/^\(|\)$/g, ''));
    return { queries: (json?.[1] ?? []).map((x) => (Array.isArray(x) ? x[0] : '')).filter((x) => typeof x === 'string' && x).slice(0, 10) };
  }

  /** A página de um vídeo, sem o player (que o YouTube só dá ao navegador de quem assiste). */
  async video(id, lang) {
    const yt = await this.yt(lang);
    const info = await yt.getInfo(id);
    const p = info.primary_info;
    const s = info.secondary_info;
    const owner = s?.owner;
    const chId = CHANNEL.test(owner?.author?.id ?? '') ? owner.author.id : null;
    const title = text(p?.title) || text(info.basic_info?.title);
    if (!title) throw new Error('no video');
    const viewsText = text(p?.view_count?.view_count) || text(p?.view_count?.original_view_count) || text(p?.view_count);
    const related = clean(info.watch_next_feed, video).filter((v) => v.id !== id);
    return {
      video: {
        kind: 'video', id, title, channelName: text(owner?.author?.name), channelId: chId, channelUrl: chId ? `https://www.youtube.com/channel/${chId}` : null,
        channelAvatar: bestImage(owner?.author?.thumbnails, 88), thumbnailUrl: thumb(id), durationMs: (info.basic_info?.duration ?? 0) * 1000,
        views: views(viewsText), viewsText: viewsText || null, uploadedAt: uploadedAt(text(p?.relative_date)), uploadedText: text(p?.relative_date) || text(p?.published) || null,
        live: !!info.basic_info?.is_live, short: false, verified: !!owner?.author?.is_verified,
      },
      published: text(p?.published) || null,
      description: text(s?.description).slice(0, 8000),
      likes: Number.isFinite(info.basic_info?.like_count) ? info.basic_info.like_count : -1,
      subscribersText: text(owner?.subscriber_count) || null,
      related,
      relatedNext: info.watch_next_feed && info.has_continuation ? null : null,
    };
  }

  async comments(id, lang) {
    const yt = await this.yt(lang);
    const r = await yt.getComments(id);
    return { comments: (r.contents ?? []).map(comment).filter(Boolean), next: r.has_continuation ? this.keep(r, { comments: true }) : null };
  }

  async channel(id, lang) {
    const yt = await this.yt(lang);
    const ch = await yt.getChannel(id);
    const h = ch.header;
    const rows = h?.content?.metadata?.metadata_rows?.map((r) => r.metadata_parts?.map((x) => text(x.text)) ?? []) ?? [];
    const flat = rows.flat();
    const name = ch.metadata?.title ?? text(h?.content?.title?.text) ?? '';
    const avatar = bestImage(ch.metadata?.avatar ?? h?.content?.image?.avatar?.image, 176);
    let videos = [];
    let next = null;
    try {
      const tab = await ch.getVideos();
      videos = clean(tab.videos, video, { channelId: id, channelName: name, channelAvatar: avatar });
      next = tab.has_continuation ? this.keep(tab, { channelId: id, channelName: name, channelAvatar: avatar }) : null;
    } catch { /* sem aba de vídeos (só shorts, só lives) */ }
    return {
      channel: {
        kind: 'channel', id, url: `https://www.youtube.com/channel/${id}`, name, avatarUrl: avatar,
        bannerUrl: bestImage(h?.content?.banner?.image ?? h?.banner?.image),
        handle: flat.find((x) => x.startsWith('@')) ?? null,
        subscribersText: flat.find((x) => /inscrit|subscri|suscrip/i.test(x)) ?? null,
        videosText: flat.find((x) => /vídeo|video/i.test(x) && /\d/.test(x)) ?? null,
        description: (ch.metadata?.description ?? '').slice(0, 2000) || null,
      },
      videos,
      next,
    };
  }

  async playlist(id, lang) {
    const yt = await this.yt(lang);
    const p = await yt.getPlaylist(id);
    const info = p.info ?? {};
    return {
      playlist: { kind: 'playlist', id, url: `https://www.youtube.com/playlist?list=${id}`, name: text(info.title), uploader: text(info.author?.name) || null, thumbnailUrl: bestImage(info.thumbnails), count: Number.parseInt(String(info.total_items ?? '').replace(/\D/g, ''), 10) || null },
      videos: clean(p.videos ?? p.items, video),
      next: p.has_continuation ? this.keep(p) : null,
    };
  }

  /** Os 15 vídeos mais novos de um canal (RSS). */
  async latest(channelId) {
    const r = await this.fetch(`https://www.youtube.com/feeds/videos.xml?channel_id=${channelId}`, { signal: AbortSignal.timeout(8000), headers: { 'User-Agent': 'Mozilla/5.0 FreeVideo' } });
    if (r.status === 404) return [];
    if (!r.ok) throw new Error(`rss ${r.status}`);
    return parseRss(await r.text(), channelId);
  }

}

function comment(t) {
  const c = t?.comment ?? t;
  if (!c?.comment_id && !c?.id) return null;
  return {
    id: c.comment_id ?? c.id,
    author: text(c.author?.name),
    authorAvatar: bestImage(c.author?.thumbnails, 88),
    authorChannelId: CHANNEL.test(c.author?.id ?? '') ? c.author.id : null,
    text: text(c.content).slice(0, 4000),
    likes: text(c.like_count) || null,
    published: text(c.published_time) || null,
    replies: Number.parseInt(String(c.reply_count ?? '0').replace(/\D/g, ''), 10) || 0,
    pinned: !!c.is_pinned,
    hearted: !!c.is_hearted,
    byUploader: !!c.author_is_channel_owner,
  };
}
