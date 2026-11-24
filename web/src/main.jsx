// O FreeVideo no computador: num celular, a página de baixar o app; num computador ainda não ligado, o
// QR code; ligado, o cofre do perfil aberto aqui (a chave nunca sai deste navegador), junto com o
// celular nos dois sentidos, o player e a página.
import { createRoot } from 'react-dom/client';
import * as lock from './core/crypto.js';
import { Api } from './core/api.js';
import { Store, MemoryStore } from './core/store.js';
import { VaultSync } from './core/sync.js';
import { Watcher } from './core/watch.js';
import { channelKey, edit, fromV, LATER, LIKED } from './core/vault.js';
import { strings, pickLang } from './ui/i18n.js';
import { Ctx, Dialog, Logo, useApp } from './ui/kit.jsx';
import { App, parse } from './ui/app.jsx';
import { LinkPage, PhonePage, isPhone } from './ui/link.jsx';
import { ACCENTS } from './ui/profile.jsx';

const root = createRoot(document.getElementById('root'));
const PREFS = 'freevideo-prefs';
let prefs = {};

let installOffer = null;
addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  installOffer = e;
});

function savedPrefs() {
  try {
    return JSON.parse(localStorage.getItem(PREFS) ?? '{}') ?? {};
  } catch {
    return {};
  }
}

const ASKED_PERSIST = `${PREFS}-persist`;
/**
 * Pede ao navegador pra não apagar sozinho o que este computador guarda (as chaves e o cofre) quando o
 * disco aperta: apagado, é QR code de novo. Chrome e Edge decidem calados (app instalado ganha), então
 * pede a cada abertura; o Firefox pergunta à pessoa, então lá uma vez só.
 */
function keepStorage() {
  const s = navigator.storage;
  if (!s?.persist || !s.persisted) return;
  const asks = /Firefox\//.test(navigator.userAgent);
  try {
    if (asks && localStorage.getItem(ASKED_PERSIST)) return;
  } catch { /* sem localStorage */ }
  s.persisted().then((yes) => {
    if (yes) return undefined;
    try {
      if (asks) localStorage.setItem(ASKED_PERSIST, '1');
    } catch { /* sem localStorage */ }
    return s.persist();
  }).catch(() => {});
}

/** O papel: claro por padrão (como o app), tinta azul de destaque. */
function look(p) {
  const dark = p.mode === 'dark' || (p.mode === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
  const html = document.documentElement;
  html.dataset.mode = dark ? 'dark' : 'light';
  const [, light, darkC] = ACCENTS.find(([id]) => id === p.accent) ?? ACCENTS[0];
  html.style.setProperty('--accent', dark ? darkC : light);
  html.style.setProperty('--on-accent', dark ? '#1C1A17' : '#FFFFFF');
  html.lang = p.lang === 'en' ? 'en' : p.lang === 'es' ? 'es' : 'pt-BR';
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#1C1A17' : '#FAF7F1');
}

function OtherTab({ t, onUse }) {
  return <div className="center"><div className="phone-page"><Logo size={72} /><h1>{t('otherTab')}</h1><button type="button" className="pill on big" onClick={onUse}>{t('useHere')}</button></div></div>;
}

function Unlinked({ t }) {
  return <div className="center"><div className="phone-page"><Logo size={72} /><h1>{t('unlinked')}</h1><button type="button" className="pill on big" onClick={() => location.reload()}>{t('linkNew')}</button></div></div>;
}

const ACTIVE = 'freevideo-web-active';
let releaseTab = () => {};

function exclusive(run, t) {
  if (!navigator.locks?.request) return run();
  const hold = (opts) => navigator.locks.request(ACTIVE, opts, async (held) => {
    if (!held) return 'busy';
    await run();
    return new Promise((r) => { releaseTab = r; });
  });
  const watch = (p) => p.then(
    (r) => { if (r === 'busy') root.render(<OtherTab t={t} onUse={() => watch(hold({ steal: true }))} />); },
    () => location.reload(),
  );
  watch(hold({ ifAvailable: true }));
  return undefined;
}

const STARTERS = {
  pt: ['em alta hoje', 'música', 'futebol', 'humor', 'games', 'tecnologia', 'receitas', 'ciência'],
  es: ['tendencias hoy', 'música', 'fútbol', 'humor', 'videojuegos', 'tecnología', 'recetas', 'ciencia'],
  en: ['trending today', 'music', 'football', 'comedy', 'gaming', 'technology', 'recipes', 'science'],
};

async function start() {
  prefs = savedPrefs();
  look(prefs);
  matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => look(prefs));
  const lang = pickLang(prefs.lang);
  const t = strings(lang);
  if (isPhone()) {
    root.render(<PhonePage t={t} />);
    return;
  }
  const store = await new Store().open();
  const [key, token, proof, me] = [await store.get('key'), await store.get('token'), await store.get('proof'), await store.get('me')];
  if (!key || !token || !proof || !me) {
    if (key || token || proof || me) await store.wipe().then(() => store.open());
    const api = new Api({ lang });
    root.render(<LinkPage api={api} t={t} onLinked={async (got) => {
      const where = got.shared ? new MemoryStore() : store;
      let k = got.key;
      if (!k) {
        const linkedApi = new Api({ token: got.token, proof: got.proof, lang });
        const v = await linkedApi.vault();
        if (v) {
          k = await lock.unwrapWithCode(v.key, got.code, got.me.id);
          if (!k) {
            await linkedApi.unlink().catch(() => {});
            throw new Error('locked');
          }
        } else {
          const fresh = await lock.newDataKey(got.code, got.me.id);
          k = fresh.key;
          await linkedApi.putVault(0, await lock.sealVault({}, k), fresh.wrapped);
        }
      }
      await where.set('key', k);
      await where.set('proof', got.proof);
      await where.set('token', got.token);
      await where.set('me', got.me);
      await where.set('owner', got.owner ?? '');
      if (got.shared) return linked({ store: where, shared: true, t, lang });
      location.reload();
      return undefined;
    }} />);
    return;
  }
  store.close();
  exclusive(async () => {
    const s2 = await new Store().open();
    if (!(await s2.get('key')) || !(await s2.get('token'))) return location.reload();
    return linked({ store: s2, shared: false, t, lang });
  }, t);
}

async function linked({ store, shared, t, lang }) {
  const [key, token, proof] = [await store.get('key'), await store.get('token'), await store.get('proof')];
  let me = await store.get('me');
  const owner = await store.get('owner');
  let unlinked = false;
  let tabs = null;
  const unlink = async (tellServer) => {
    if (unlinked) return;
    unlinked = true;
    sync.stop();
    watcher.close();
    if (tellServer) await api.unlink().catch(() => {});
    await store.wipe();
    try { tabs?.postMessage('unlinked'); } catch { /* fechado */ }
    document.getElementById('yt-dock')?.replaceChildren();
    root.render(<Unlinked t={t} />);
    releaseTab();
  };
  if (!shared) {
    keepStorage();
    try {
      tabs = new BroadcastChannel('freevideo-web');
      tabs.onmessage = (e) => { if (e.data === 'unlinked') unlink(false); };
    } catch { /* navegador antigo */ }
  }
  const api = new Api({ token, proof, lang, onUnlinked: () => unlink(false) });
  const sync = new VaultSync({ api, key, store });
  await sync.load();

  const setPrefs = (p, { quiet = false } = {}) => {
    const langChanged = p.lang && p.lang !== lang;
    prefs = p;
    try { localStorage.setItem(PREFS, JSON.stringify(p)); } catch { /* janela anônima */ }
    if (quiet) return;
    look(p);
    if (langChanged) location.reload();
    else rerender();
  };

  const ui = { menu: () => {}, toast: () => {}, dialog: () => {}, stageChanged: () => {} };
  const toast = (text, action) => ui.toast(text, action);
  let stage = null;
  let next = null;
  let lastOpened = null; // o vídeo do cartão clicado (a página dele mostra título e canal na hora)

  const watcher = new Watcher({
    host: document.getElementById('yt-dock'),
    // O volume é deste computador (não vai pro cofre: o celular tem o dele).
    volume: prefs.volume ?? 100,
    muted: prefs.muted === true,
    onVolume: (volume, muted) => setPrefs({ ...prefs, volume, muted }, { quiet: true }),
    onRecord: (video, positionMs, durationMs, addMs, newVisit) => {
      lastOwnNote = Date.now();
      sync.change((v) => edit.watch(v, video, positionMs, durationMs, addMs, newVisit, lastOwnNote), { quiet: true });
    },
    // Acabou: o próximo do "a seguir" (como o app, com reprodução automática).
    onEnded: () => { if (prefs.autoplay !== false) next?.(); },
    onToast: (what) => toast(t(what)),
  });

  // O vídeo desta aba, guardado (fechado com a chave do cofre): recarregar a página volta com ele, no
  // mesmo ponto — na página dele ou no mini player, tocando se estava tocando (e o navegador deixar).
  let lastOwnNote = 0;
  const saveOpen = async () => {
    if (unlinked) return;
    try {
      const v = watcher.video;
      const snap = { v: 1, video: v, time: v ? watcher.time : 0, playing: !!v && watcher.playing, at: watcher.lastActive, saved: Date.now() };
      await store.set('open', await lock.sealLocal(snap, key));
    } catch { /* sem espaço, aba fechando: a próxima vez guarda */ }
  };
  let openKey = '';
  watcher.subscribe(() => {
    const k = `${watcher.video?.id ?? ''}|${watcher.playing}|${watcher.lastActive}`;
    if (k === openKey) return;
    openKey = k;
    setTimeout(saveOpen, 300);
  });
  setInterval(() => { if (watcher.playing) saveOpen(); }, 5000);
  let resumeHint = null; // {id, ms}: a página do vídeo aberta ao recarregar continua daqui
  try {
    const sealedOpen = await store.get('open');
    const o = sealedOpen ? await lock.openLocal(sealedOpen, key) : null;
    if (o?.v === 1) {
      watcher.lastActive = o.at ?? 0;
      if (o.video?.id && /^[A-Za-z0-9_-]{11}$/.test(o.video.id)) {
        const wasPlaying = o.playing === true && Date.now() - (o.saved ?? 0) < 10 * 60_000;
        const route = parse(location.hash);
        if (route.name === 'watch' && route.id === o.video.id) resumeHint = { id: o.video.id, ms: Math.round((o.time ?? 0) * 1000) };
        else watcher.cue(o.video, Math.round((o.time ?? 0) * 1000), { play: wasPlaying, at: o.at ?? 0 });
      }
    }
  } catch { /* de outra chave ou estragado: começa sem vídeo */ }

  // O que o celular está vendo (o histórico do cofre anota onde parou, a cada 15 s): com nada tocando
  // aqui e fora da página de um vídeo, o mini player mostra, pausado ali — é só dar play pra continuar.
  const followPhone = () => {
    if (unlinked || watcher.playing || parse(location.hash).name === 'watch') return;
    let last = null;
    for (const w of sync.data.h) if (!last || w.l > last.l) last = w;
    if (!last || last.i === watcher.video?.id) return;
    if (last.l <= Math.max(watcher.lastActive, lastOwnNote) || Date.now() - last.l > 2 * 3_600_000) return;
    if (last.p < 5000 || (last.d > 0 && last.p / last.d >= 0.95)) return;
    watcher.cue(fromV(last), last.p, { at: last.l });
  };
  sync.subscribe(followPhone);

  const isSaved = (list, id) => sync.data.sv.some((x) => x.li === list && x.i === id);
  const nf = new Intl.NumberFormat(lang, { notation: 'compact', maximumFractionDigits: 1 });

  const app = {
    t, lang, api, sync, watcher, shared, owner,
    dockRef: { current: document.getElementById('yt-dock') },
    me: () => me,
    prefs: () => prefs,
    setPrefs,
    bindUi: (u) => Object.assign(ui, u),
    toast,
    starterTopics: () => STARTERS[lang] ?? STARTERS.pt,
    compact: (n) => nf.format(n),
    openMenu: (e, items) => { e?.stopPropagation?.(); ui.menu(e, items); },
    stage: () => stage,
    setStage: (ref) => { stage = ref; ui.stageChanged(); },
    setNext: (fn) => { next = fn; },
    /** Abre um vídeo: a página dele ([navigate]) e o player (de onde parou, se for o caso). */
    openVideo: (v, { navigate = true, startMs = 0 } = {}) => {
      if (navigate) {
        lastOpened = v;
        location.hash = `/watch/${v.id}`;
        return;
      }
      watcher.open(v, startMs);
    },
    closeVideo: () => watcher.close(),
    peek: (id) => (lastOpened?.id === id ? lastOpened : null),
    /** A página deste vídeo aberta ao recarregar: continua de onde estava (uma vez). */
    resumeAt: (id) => {
      if (resumeHint?.id !== id) return null;
      const ms = resumeHint.ms;
      resumeHint = null;
      return ms;
    },
    isSaved,
    toggleSaved: (list, v) => {
      const was = isSaved(list, v.id);
      sync.change((x) => edit.toggleSaved(x, list, v, Date.now()));
      toast(t(list === LIKED ? (was ? 'unliked' : 'liked') : (was ? 'removedLater' : 'addedLater')));
    },
    isSubscribed: (url, name) => {
      const k = channelKey(url, name);
      return sync.data.s.some((s) => channelKey(s.u, s.n) === k);
    },
    toggleSub: (c) => {
      const k = channelKey(c.url, c.name);
      const was = sync.data.s.some((s) => channelKey(s.u, s.n) === k);
      sync.change((x) => edit.toggleSub(x, { url: c.url, name: c.name, avatarUrl: c.avatarUrl ?? null }, Date.now()));
      toast(t(was ? 'unsubscribed' : 'subscribedTo', c.name));
    },
    /** A página de um canal seguido foi aberta: a foto e o nome dele ficam em dia (o app faz o mesmo). */
    refreshSub: (c) => {
      const k = channelKey(c.url, c.name);
      const s = sync.data.s.find((x) => channelKey(x.u, x.n) === k);
      if (!s || !c.avatarUrl || (s.a === c.avatarUrl && s.n)) return;
      sync.change((x) => ({ ...x, s: x.s.map((y) => (channelKey(y.u, y.n) === k ? { ...y, a: c.avatarUrl, n: y.n || c.name } : y)) }));
    },
    dismissVideo: (v) => {
      sync.change((x) => edit.dismissVideo(x, v, Date.now()));
      toast(t('dismissed'), { label: t('undo'), run: () => sync.change((x) => edit.undoDismissal(x, `v:${v.id}`)) });
    },
    dismissChannel: (url, name) => {
      if (!url) return;
      sync.change((x) => edit.dismissChannel(x, url, name, Date.now()));
      toast(t('channelDismissed'), { label: t('undo'), run: () => sync.change((x) => edit.undoDismissal(x, `c:${url}`)) });
    },
    forgetWatch: (id) => sync.change((x) => edit.forget(x, id)),
    confirmClearHistory: () => ui.dialog(<ClearHistory onClose={() => ui.dialog(null)} />),
    clearHistory: () => sync.change((x) => edit.clearHistory(x)),
    noteSearch: (q) => sync.change((x) => edit.search(x, q, Date.now())),
    forgetSearch: (q) => sync.change((x) => edit.forgetSearch(x, q)),
    setInterests: (topics) => sync.change((x) => edit.setInterests(x, topics, Date.now())),
    copy: async (text) => {
      try {
        await navigator.clipboard.writeText(text);
        toast(t('linkCopied'));
      } catch { toast(text); }
    },
    /** O menu de um vídeo (clique direito ou "…"). */
    videoMenu: (v, { extra } = {}) => [
      { label: isSaved(LATER, v.id) ? t('removeLater') : t('watchLater'), icon: 'later', onClick: () => app.toggleSaved(LATER, v) },
      { label: isSaved(LIKED, v.id) ? t('unlike') : t('like'), icon: 'thumbUp', onClick: () => app.toggleSaved(LIKED, v) },
      { label: t('copyLink'), icon: 'share', onClick: () => app.copy(`https://www.youtube.com/watch?v=${v.id}`) },
      ...(extra ? ['-', extra] : []),
      '-',
      { label: t('notInterested'), icon: 'block', onClick: () => app.dismissVideo(v) },
      ...(v.channelUrl ? [{ label: t('dontRecommendChannel'), icon: 'block', onClick: () => app.dismissChannel(v.channelUrl, v.channelName) }] : []),
    ],
    rename: async (name) => {
      const r = await api.rename(name);
      me = r.me;
      await store.set('me', me);
      rerender();
    },
    unlink: () => unlink(true),
    canInstall: () => !!installOffer && !shared,
    install: async () => {
      if (!installOffer) return;
      await installOffer.prompt();
      installOffer = null;
    },
  };

  function rerender() {
    root.render(<Ctx.Provider value={{ ...app }}><App /></Ctx.Provider>);
  }

  addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden') { watcher.note(); sync.flush(); } });
  addEventListener('pagehide', () => { watcher.note(); sync.save().catch(() => {}); saveOpen(); });
  addEventListener('online', () => sync.push());

  if (shared) {
    let farewell = null;
    const sign = async () => { farewell = await api.sign('DELETE', '/v1/link').catch(() => farewell); };
    await sign();
    setInterval(sign, 60_000);
    addEventListener('pagehide', () => {
      if (unlinked || !farewell) return;
      fetch('/v1/link', { method: 'DELETE', keepalive: true, credentials: 'omit', headers: { Authorization: `Bearer ${token}`, 'X-FreeVideo-Sig': farewell } }).catch(() => {});
    });
    api.markShared().catch(() => {});
  }

  const refreshMe = () => api.me().then(async (r) => {
    if (!r?.me?.id) return; // resposta sem perfil: fica o de antes (sem ele, a próxima abertura pediria o QR code)
    me = r.me;
    await store.set('me', me);
    rerender();
  }, () => {});
  refreshMe();
  setInterval(refreshMe, 10 * 60_000);

  // Um link de vídeo (#/v/<id>, ou ?v=<id>): abre a página dele.
  const r = parse(location.hash);
  if (r.name === 'v' && /^[A-Za-z0-9_-]{11}$/.test(r.id)) history.replaceState(null, '', `#/watch/${r.id}`);
  rerender();
  sync.run();
}

function ClearHistory({ onClose }) {
  const app = useApp();
  return (
    <Dialog title={app.t('clearHistory')} onClose={onClose}>
      {(close) => (
        <div className="form">
          <div className="form-actions"><button type="button" className="pill ghost" onClick={close}>{app.t('cancel')}</button><button type="button" className="pill danger on" onClick={() => { app.clearHistory(); close(); }}>{app.t('clear')}</button></div>
        </div>
      )}
    </Dialog>
  );
}

start().catch((e) => {
  console.error(e);
  root.render(<div className="center"><p className="faint">{String(e?.message ?? e)}</p></div>);
});
