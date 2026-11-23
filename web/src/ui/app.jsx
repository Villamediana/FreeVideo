// A moldura: a barra de cima (marca, busca com sugestões, perfil), o trilho do lado (Início, Inscrições,
// Biblioteca, Histórico, os canais seguidos), a tela aberta e o vídeo — no palco da página do vídeo, ou
// pequeno no canto enquanto se navega.
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Cover, Icon, IconBtn, Logo, Menu, Toasts, Volume, useApp, useWatch, volumeIcon } from './kit.jsx';
import { Home } from './home.jsx';
import { Watch } from './watch.jsx';
import { Channel, Library, List, Playlist, Search, Subscriptions } from './pages.jsx';
import { Avatar, Profile } from './profile.jsx';

export function parse(hash) {
  const h = hash.replace(/^#\/?/, '');
  const [path, query] = h.split('?');
  const parts = path.split('/').map((p) => { try { return decodeURIComponent(p); } catch { return p; } });
  return { name: parts[0] || 'home', id: parts.slice(1).join('/'), params: new URLSearchParams(query ?? '') };
}

function useRoute() {
  const [r, setR] = useState(() => parse(location.hash));
  useEffect(() => {
    const on = () => setR(parse(location.hash));
    window.addEventListener('hashchange', on);
    return () => window.removeEventListener('hashchange', on);
  }, []);
  return r;
}

function SearchBox({ route }) {
  const app = useApp();
  const input = useRef(null);
  const [text, setText] = useState(route.name === 'search' ? route.params.get('q') ?? '' : '');
  const [sugg, setSugg] = useState([]);
  const [open, setOpen] = useState(false);
  const [sel, setSel] = useState(-1);
  useEffect(() => { if (route.name === 'search') setText(route.params.get('q') ?? ''); }, [route.name, route.params.get('q')]);
  useEffect(() => {
    const f = () => input.current?.focus();
    window.addEventListener('freevideo:search', f);
    return () => window.removeEventListener('freevideo:search', f);
  }, []);
  useEffect(() => {
    if (!open) return undefined;
    const q = text.trim();
    if (!q) {
      setSugg(app.sync.data.q.slice(0, 8).map((x) => ({ q: x.q, recent: true })));
      return undefined;
    }
    const ctl = new AbortController();
    const h = setTimeout(() => app.api.suggest(q, ctl.signal).then((r) => setSugg(r.queries.map((x) => ({ q: x }))), () => {}), 120);
    return () => { clearTimeout(h); ctl.abort(); };
  }, [text, open]);
  const go = (q) => {
    const s = q.trim();
    if (!s) return;
    setOpen(false);
    input.current?.blur();
    location.hash = `/search?q=${encodeURIComponent(s)}`;
  };
  return (
    <form className="search-box" onSubmit={(e) => { e.preventDefault(); go(sel >= 0 ? sugg[sel]?.q ?? text : text); }}>
      <input
        ref={input}
        value={text}
        placeholder={app.t('search')}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onChange={(e) => { setText(e.target.value); setSel(-1); setOpen(true); }}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown') { e.preventDefault(); setSel((i) => Math.min(i + 1, sugg.length - 1)); }
          else if (e.key === 'ArrowUp') { e.preventDefault(); setSel((i) => Math.max(i - 1, -1)); }
          else if (e.key === 'Escape') { setOpen(false); e.currentTarget.blur(); }
        }}
        aria-label={app.t('search')}
      />
      {text ? <IconBtn icon="close" label={app.t('clear')} size={16} onClick={() => { setText(''); input.current?.focus(); }} /> : null}
      <button type="submit" className="search-go" aria-label={app.t('search')}><Icon name="search" size={19} /></button>
      {open && sugg.length ? (
        <div className="suggest">
          {sugg.map((s, i) => (
            <div key={s.q} className={`sugg${i === sel ? ' sel' : ''}`}>
              <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={() => { setText(s.q); go(s.q); }}>
                <Icon name={s.recent ? 'history' : 'search'} size={16} /><span>{s.q}</span>
              </button>
              {s.recent ? <IconBtn icon="close" label={app.t('remove')} size={14} onClick={() => { app.forgetSearch(s.q); setSugg((l) => l.filter((x) => x.q !== s.q)); }} /> : null}
            </div>
          ))}
        </div>
      ) : null}
    </form>
  );
}

function Rail({ route, wide }) {
  const app = useApp();
  const sync = useWatch(app.sync);
  const subs = [...sync.data.s].sort((a, b) => a.n.localeCompare(b.n));
  const nav = [['home', 'home', '#/'], ['subs', 'subs', '#/subs'], ['library', 'library', '#/library'], ['history', 'history', '#/list/history']];
  const on = (name) => route.name === name || (name === 'history' && route.name === 'list' && route.id === 'history');
  return (
    <nav className={`rail${wide ? ' wide' : ''}`}>
      {nav.map(([name, icon, href]) => (
        <a key={name} href={href} className={on(name) ? 'on' : ''} title={app.t(name)}><Icon name={icon} size={22} /><span>{app.t(name)}</span></a>
      ))}
      {wide && subs.length ? (
        <div className="rail-subs">
          <h4>{app.t('subscriptions')}</h4>
          {subs.slice(0, 40).map((s) => {
            const id = /(UC[A-Za-z0-9_-]{22})/.exec(s.u)?.[1];
            return <a key={s.u} href={id ? `#/channel/${id}` : undefined} className={route.name === 'channel' && route.id === id ? 'on' : ''}><Cover src={s.a} size={26} round icon="person" /><span>{s.n}</span></a>;
          })}
        </div>
      ) : null}
    </nav>
  );
}

/** O player sempre no mesmo lugar do DOM: em cima do palco da página do vídeo, ou pequeno no canto. */
function Dock({ route }) {
  const app = useApp();
  const w = useWatch(app.watcher);
  const host = app.dockRef.current;
  const stage = app.stage();
  const has = !!w.video;
  useLayoutEffect(() => {
    if (!host) return undefined;
    let raf = 0;
    const place = () => {
      const s = stage?.current;
      const r = s?.getBoundingClientRect();
      if (s && r && r.width > 40) {
        Object.assign(host.style, { left: `${r.left}px`, top: `${r.top}px`, width: `${r.width}px`, height: `${r.height}px` });
        host.classList.remove('mini');
      } else {
        for (const k of ['left', 'top', 'width', 'height']) host.style.removeProperty(k);
        host.classList.add('mini');
      }
      host.classList.toggle('empty', !has);
    };
    const loop = (n) => { place(); if (n > 0) raf = requestAnimationFrame(() => loop(n - 1)); };
    loop(20);
    const ro = new ResizeObserver(() => loop(3));
    if (stage?.current) ro.observe(stage.current);
    ro.observe(document.body);
    const scroller = document.querySelector('.main');
    scroller?.addEventListener('scroll', place, { passive: true });
    window.addEventListener('resize', place);
    return () => { cancelAnimationFrame(raf); ro.disconnect(); scroller?.removeEventListener('scroll', place); window.removeEventListener('resize', place); };
  }, [stage, has, route.name]);
  if (!has) return null;
  return (
    <>
      {stage ? null : (
        <div className="mini-bar">
          <button type="button" className="mini-title" onClick={() => { location.hash = `/watch/${w.video.id}`; }} title={w.video.title}>
            <b>{w.video.title}</b><span>{w.video.channelName}</span>
          </button>
          <Volume compact />
          <IconBtn icon={w.playing ? 'pause' : 'play'} fill label={app.t(w.playing ? 'pause' : 'play')} size={18} onClick={() => w.toggle()} />
          <IconBtn icon="expand" label={app.t('open')} size={18} onClick={() => { location.hash = `/watch/${w.video.id}`; }} />
          <IconBtn icon="close" label={app.t('close')} size={18} onClick={() => app.closeVideo()} />
        </div>
      )}
      <VolumeHud host={host} />
    </>
  );
}

/** O volume mudado pelo teclado (↑/↓, M): aparece um instante no meio do vídeo. */
function VolumeHud({ host }) {
  const app = useApp();
  const w = useWatch(app.watcher);
  const [shown, setShown] = useState(false);
  useEffect(() => {
    if (!w.volumeShownAt) return undefined;
    setShown(true);
    const t = setTimeout(() => setShown(false), 900);
    return () => clearTimeout(t);
  }, [w.volumeShownAt]);
  if (!shown || !host) return null;
  const r = host.getBoundingClientRect();
  const level = w.muted ? 0 : w.volume;
  return (
    <div className="vol-hud" role="status" style={{ left: `${r.left + r.width / 2}px`, top: `${r.top + r.height / 2}px` }}>
      <Icon name={volumeIcon(level)} size={22} /><b>{level}%</b>
    </div>
  );
}

function Page({ route }) {
  switch (route.name) {
    case 'watch': return <Watch id={route.id} startMs={Number(route.params.get('t') ?? -1)} />;
    case 'search': return <Search q={route.params.get('q') ?? ''} type={route.params.get('type') ?? ''} />;
    case 'channel': return <Channel id={route.id} />;
    case 'playlist': return <Playlist id={route.id} />;
    case 'subs': return <Subscriptions />;
    case 'library': return <Library />;
    case 'list': return <List which={route.id} />;
    case 'profile': return <Profile />;
    default: return <Home chip={route.params.get('t')} />;
  }
}

export function App() {
  const app = useApp();
  const route = useRoute();
  const [wide, setWide] = useState(() => app.prefs().rail ?? innerWidth >= 1280);
  const [menu, setMenu] = useState(null);
  const [toasts, setToasts] = useState([]);
  const [dialog, setDialog] = useState(null);
  const [, setStageTick] = useState(0);
  const main = useRef(null);
  useEffect(() => {
    app.bindUi({
      menu: (e, items) => {
        const r = e?.currentTarget?.getBoundingClientRect?.();
        const at = e?.type === 'contextmenu' || !r ? { x: e.clientX, y: e.clientY } : { x: r.left, y: r.bottom + 4 };
        setMenu({ at, items, key: Date.now() });
      },
      toast: (text, action) => {
        const id = Math.random();
        setToasts((l) => [...l.slice(-2), { id, text, action }]);
        setTimeout(() => setToasts((l) => l.filter((x) => x.id !== id)), action ? 6000 : 2600);
      },
      dialog: setDialog,
      stageChanged: () => setStageTick((x) => x + 1),
    });
  }, []);
  useEffect(() => { main.current?.scrollTo(0, 0); }, [route.name, route.id, route.params.get('q'), route.params.get('type'), route.params.get('t')]);
  useEffect(() => {
    const key = (e) => {
      // A régua do volume não é "digitando": M nela ainda tira o som (as setas nela, ela mesma cuida).
      const slider = e.target.tagName === 'INPUT' && e.target.type === 'range';
      const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName) && !slider;
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); window.dispatchEvent(new Event('freevideo:search')); return; }
      if (typing || e.ctrlKey || e.metaKey || e.altKey) return;
      if (e.key === '/') { e.preventDefault(); window.dispatchEvent(new Event('freevideo:search')); return; }
      // O volume pelo teclado (com o foco na página; dentro do player valem as teclas do YouTube):
      // M liga e desliga o som; ↑/↓ na página do vídeo (nas outras, as setas rolam a lista).
      const w = app.watcher;
      if (!w.video) return;
      if (e.key === 'm' || e.key === 'M') { e.preventDefault(); w.toggleMute({ show: true }); return; }
      if ((e.key === 'ArrowUp' || e.key === 'ArrowDown') && !slider && location.hash.startsWith('#/watch/')) {
        e.preventDefault();
        w.stepVolume(e.key === 'ArrowUp' ? 5 : -5, { show: true });
      }
    };
    window.addEventListener('keydown', key);
    return () => window.removeEventListener('keydown', key);
  }, []);
  const me = app.me();
  const watching = route.name === 'watch';
  return (
    <div className={`shell${wide && !watching ? ' rail-wide' : ''}${watching ? ' watching' : ''}`}>
      <header className="top">
        <IconBtn icon="lyrics" label={app.t('menu')} onClick={() => { setWide(!wide); app.setPrefs({ ...app.prefs(), rail: !wide }, { quiet: true }); }} />
        <a className="brand" href="#/"><Logo size={30} /><span>FreeVideo</span></a>
        <SearchBox route={route} />
        <div className="top-end">
          {app.shared ? <span className="shared-badge" title={app.t('sharedComputerHint')}><Icon name="lock" size={14} /></span> : null}
          <a className="me" href="#/profile" title={me?.name}><Avatar me={me} size={34} /></a>
        </div>
      </header>
      {!watching ? <Rail route={route} wide={wide} /> : null}
      <main className="main" ref={main}><Page route={route} /></main>
      <Dock route={route} />
      {menu ? <Menu key={menu.key} at={menu.at} items={menu.items} onClose={() => setMenu(null)} /> : null}
      {dialog}
      <Toasts list={toasts} />
    </div>
  );
}
