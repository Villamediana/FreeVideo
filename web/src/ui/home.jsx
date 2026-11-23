// A Início: chips de assunto (Tudo e os da pessoa), "Continuar assistindo" e o feed sem fim (o mesmo
// cérebro do app, core/feed.js). A sessão fica viva enquanto a aba vive: voltar à Início não refaz tudo.
import { useEffect, useMemo, useRef, useState } from 'react';
import { continueWatching, FeedSession } from '../core/feed.js';
import { fromV } from '../core/vault.js';
import { Icon, Spinner, useApp, useWatch } from './kit.jsx';
import { Grid, VideoCard } from './cards.jsx';

/** A sessão do feed (uma por aba e por chip): o que já veio fica, a rolagem continua de onde parou. */
const sessions = new Map();

function useFeed(chip) {
  const app = useApp();
  const key = chip ?? '';
  let s = sessions.get(key);
  if (!s) {
    const api = app.api;
    const vault = () => app.sync.data;
    const session = chip
      ? { kind: 'topic', q: chip, items: [], next: undefined, loading: false, done: false }
      : { kind: 'home', feed: new FeedSession({ api, vault, starterTopics: app.starterTopics() }), items: [], loading: false, done: false, failed: false };
    s = { state: session, listeners: new Set(), version: 0 };
    sessions.set(key, s);
  }
  const [, force] = useState(0);
  useEffect(() => {
    const f = () => force((x) => x + 1);
    s.listeners.add(f);
    return () => s.listeners.delete(f);
  }, [key]);
  const emit = () => { s.version++; for (const f of s.listeners) f(); };
  const more = async () => {
    const st = s.state;
    if (st.loading || st.done) return;
    st.loading = true;
    st.failed = false;
    emit();
    try {
      if (st.kind === 'home') {
        const page = await st.feed.next();
        st.items.push(...page.map((e) => ({ ...e.video, _reason: e.reason })));
        if (!page.length) st.done = st.items.length > 0;
        if (!page.length && !st.items.length) st.failed = true;
      } else {
        const r = st.next === undefined ? await app.api.search(st.q, 'videos') : await app.api.more(st.next);
        const seen = new Set(st.items.map((x) => x.id));
        st.items.push(...r.items.filter((x) => x.kind === 'video' && !x.short && !seen.has(x.id)));
        st.next = r.next;
        if (!r.next) st.done = true;
      }
    } catch {
      st.failed = true;
    }
    st.loading = false;
    emit();
  };
  const reset = () => {
    sessions.delete(key);
    force((x) => x + 1);
  };
  return { st: s.state, more, reset };
}

export function Home({ chip }) {
  const app = useApp();
  const sync = useWatch(app.sync);
  const v = sync.data;
  const { st, more, reset } = useFeed(chip);
  const sentinel = useRef(null);
  const chips = useMemo(() => {
    const own = [...v.in.map((x) => x.t), ...v.q.slice(0, 8).map((x) => x.q)];
    return [...new Set(own)].slice(0, 12);
  }, [v.in, v.q]);
  const cont = useMemo(() => continueWatching(v, Date.now(), 8).map(fromV), [v.h]);
  useEffect(() => {
    if (!st.items.length && !st.loading && !st.failed) more();
  }, [chip]);
  useEffect(() => {
    const el = sentinel.current;
    if (!el) return undefined;
    const io = new IntersectionObserver((e) => { if (e[0].isIntersecting) more(); }, { rootMargin: '1200px' });
    io.observe(el);
    return () => io.disconnect();
  });
  const dismissed = new Set(v.x.map((x) => x.k));
  const items = st.items.filter((x) => !dismissed.has(`v:${x.id}`));
  return (
    <div className="page">
      <div className="chips sticky">
        <a className={`chip${!chip ? ' on' : ''}`} href="#/">{app.t('all')}</a>
        {chips.map((c) => <a key={c} className={`chip${chip === c ? ' on' : ''}`} href={`#/?t=${encodeURIComponent(c)}`}>{c}</a>)}
        {!chip ? <button type="button" className="chip icon" title={app.t('refresh')} onClick={reset}><Icon name="retry" size={16} /></button> : null}
      </div>
      {!chip && cont.length ? (
        <section className="shelf">
          <h2>{app.t('continueWatching')}</h2>
          <Grid>{cont.slice(0, 4).map((x) => <VideoCard key={x.id} v={x} source="continue" />)}</Grid>
        </section>
      ) : null}
      <Grid>{items.map((x) => <VideoCard key={x.id} v={x} source={x._reason?.source ?? 'topic'} />)}</Grid>
      {st.loading ? <div className="loading"><Spinner /></div> : null}
      {st.failed && !st.loading ? (
        <div className="empty">
          <Icon name="offline" size={34} />
          <button type="button" className="pill" onClick={more}><Icon name="retry" size={16} /> {app.t('tryAgain')}</button>
        </div>
      ) : null}
      <div ref={sentinel} style={{ height: 1 }} />
    </div>
  );
}
