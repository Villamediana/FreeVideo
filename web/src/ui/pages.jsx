// As outras telas: busca (vídeos, canais, playlists), canal, playlist, Inscrições (as novidades dos
// canais seguidos), Biblioteca (histórico, mais tarde, curtidos, inscrições) e as listas inteiras.
import { useEffect, useMemo, useRef, useState } from 'react';
import { subscriptionsFeed } from '../core/feed.js';
import { fromV } from '../core/vault.js';
import { Cover, Failed, Icon, Loading, Spinner, useApp, useLoad, useWatch } from './kit.jsx';
import { ChannelRow, Grid, PlaylistRow, VideoCard, VideoRow } from './cards.jsx';

/** Uma lista que continua ao rolar (a próxima página pela ponte). */
function useMore(first, deps) {
  const app = useApp();
  const r = useLoad(first, deps);
  const [extra, setExtra] = useState({ items: [], next: undefined, loading: false });
  const sentinel = useRef(null);
  useEffect(() => setExtra({ items: [], next: undefined, loading: false }), deps);
  const next = extra.next === undefined ? r.data?.next : extra.next;
  const more = async () => {
    if (!next || extra.loading) return;
    setExtra((x) => ({ ...x, loading: true }));
    try {
      const page = await app.api.more(next);
      setExtra((x) => ({ items: [...x.items, ...page.items], next: page.next, loading: false }));
    } catch {
      setExtra((x) => ({ ...x, next: null, loading: false }));
    }
  };
  useEffect(() => {
    const el = sentinel.current;
    if (!el) return undefined;
    const io = new IntersectionObserver((e) => { if (e[0].isIntersecting) more(); }, { rootMargin: '900px' });
    io.observe(el);
    return () => io.disconnect();
  });
  return { r, extra, sentinel };
}

const TYPES = ['', 'videos', 'channels', 'playlists'];

export function Search({ q, type }) {
  const app = useApp();
  const { r, extra, sentinel } = useMore(() => app.api.search(q, type), [q, type, app.lang]);
  useEffect(() => { if (q) app.noteSearch(q); }, [q]);
  if (!q) return <div className="page"><div className="empty"><Icon name="search" size={40} /></div></div>;
  const items = [...(r.data?.items ?? []), ...extra.items].filter((x) => !x.short);
  return (
    <div className="page narrow">
      <div className="chips">
        {TYPES.map((tp) => <a key={tp || 'all'} className={`chip${(type || '') === tp ? ' on' : ''}`} href={`#/search?q=${encodeURIComponent(q)}${tp ? `&type=${tp}` : ''}`}>{app.t(tp || 'all')}</a>)}
      </div>
      {r.loading ? <Loading /> : r.error ? <Failed onRetry={r.retry} /> : !items.length ? <div className="empty"><p>{app.t('noResults')}</p></div> : (
        <div className="list">
          {items.map((it) => (it.kind === 'channel' ? <ChannelRow key={`c${it.id}`} c={it} /> : it.kind === 'playlist' ? <PlaylistRow key={`p${it.id}`} p={it} /> : <VideoRow key={it.id} v={it} source="search" />))}
        </div>
      )}
      {extra.loading ? <div className="loading"><Spinner /></div> : null}
      <div ref={sentinel} style={{ height: 1 }} />
    </div>
  );
}

export function Channel({ id }) {
  const app = useApp();
  useWatch(app.sync);
  const { r, extra, sentinel } = useMore(() => app.api.channel(id), [id, app.lang]);
  useEffect(() => { if (r.data?.channel) app.refreshSub(r.data.channel); }, [r.data]);
  if (r.loading) return <Loading />;
  if (r.error) return <Failed onRetry={r.retry} />;
  const c = r.data.channel;
  const subscribed = app.isSubscribed(c.url, c.name);
  const videos = [...r.data.videos, ...extra.items].filter((x) => x.kind === 'video' && !x.short);
  return (
    <div className="page">
      {c.bannerUrl ? <div className="banner"><img src={c.bannerUrl} alt="" referrerPolicy="no-referrer" /></div> : null}
      <header className="channel-head">
        <Cover src={c.avatarUrl} size={120} round icon="person" />
        <div>
          <h1>{c.name}</h1>
          <p className="faint">{[c.handle, c.subscribersText, c.videosText].filter(Boolean).join(' · ')}</p>
          {c.description ? <p className="channel-desc">{c.description}</p> : null}
          <div className="row-actions">
            <button type="button" className={`pill${subscribed ? '' : ' on'}`} onClick={() => app.toggleSub(c)}>
              {subscribed ? <><Icon name="bell" size={16} /> {app.t('subscribed')}</> : app.t('subscribe')}
            </button>
            <button type="button" className="pill" onClick={(e) => app.openMenu(e, [{ label: app.t('dontRecommendChannel'), icon: 'block', onClick: () => app.dismissChannel(c.url, c.name) }])}><Icon name="more" size={17} /></button>
          </div>
        </div>
      </header>
      <Grid>{videos.map((v) => <VideoCard key={v.id} v={v} source="channel" />)}</Grid>
      {extra.loading ? <div className="loading"><Spinner /></div> : null}
      <div ref={sentinel} style={{ height: 1 }} />
    </div>
  );
}

export function Playlist({ id }) {
  const app = useApp();
  const { r, extra, sentinel } = useMore(() => app.api.playlist(id), [id, app.lang]);
  if (r.loading) return <Loading />;
  if (r.error) return <Failed onRetry={r.retry} />;
  const p = r.data.playlist;
  const videos = [...r.data.videos, ...extra.items].filter((x) => x.kind === 'video');
  return (
    <div className="page narrow">
      <header className="list-head">
        <h1>{p.name}</h1>
        <p className="faint">{[p.uploader, p.count ? app.t('videosCount', p.count) : null].filter(Boolean).join(' · ')}</p>
        {videos.length ? <button type="button" className="pill on" onClick={() => app.openVideo(videos[0], { source: 'playlist' })}><Icon name="play" fill size={16} /> {app.t('play')}</button> : null}
      </header>
      <div className="list numbered">{videos.map((v, i) => <div key={v.id} className="num-row"><span className="n">{i + 1}</span><VideoRow v={v} source="playlist" /></div>)}</div>
      {extra.loading ? <div className="loading"><Spinner /></div> : null}
      <div ref={sentinel} style={{ height: 1 }} />
    </div>
  );
}

export function Subscriptions() {
  const app = useApp();
  const sync = useWatch(app.sync);
  const subs = sync.data.s;
  const ids = subs.map((s) => s.u).sort().join(',');
  const r = useLoad(() => (subs.length ? subscriptionsFeed(app.api, sync.data) : Promise.resolve([])), [ids]);
  const [limit, setLimit] = useState(36);
  const sentinel = useRef(null);
  useEffect(() => {
    const el = sentinel.current;
    if (!el) return undefined;
    const io = new IntersectionObserver((e) => { if (e[0].isIntersecting) setLimit((x) => x + 24); }, { rootMargin: '900px' });
    io.observe(el);
    return () => io.disconnect();
  });
  return (
    <div className="page">
      {subs.length ? (
        <div className="sub-strip">
          {[...subs].sort((a, b) => a.n.localeCompare(b.n)).map((s) => {
            const id = /(UC[A-Za-z0-9_-]{22})/.exec(s.u)?.[1];
            return (
              <a key={s.u} className="sub-chip" href={id ? `#/channel/${id}` : undefined} title={s.n}>
                <Cover src={s.a} size={56} round icon="person" />
                <span>{s.n}</span>
              </a>
            );
          })}
        </div>
      ) : null}
      {!subs.length ? <div className="empty"><Icon name="subs" size={40} /><p>{app.t('noSubs')}</p></div>
        : r.loading ? <Loading /> : r.error ? <Failed onRetry={r.retry} /> : (
          <Grid>{(r.data ?? []).slice(0, limit).map((v) => <VideoCard key={v.id} v={v} source="subscription" />)}</Grid>
        )}
      <div ref={sentinel} style={{ height: 1 }} />
    </div>
  );
}

/** O tempo assistido: em minutos até uma hora, depois em horas ("1 hora", não "1 horas"). */
function totalHours(ms, app) {
  const min = Math.round(ms / 60_000);
  if (min < 60) return { value: min.toLocaleString(app.lang), label: app.t('minutes') };
  const h = Math.round(ms / 3_600_000);
  return { value: h.toLocaleString(app.lang), label: app.t(h === 1 ? 'hour' : 'hours') };
}

function Shelf({ title, href, children, count }) {
  return (
    <section className="shelf">
      <div className="shelf-head">
        <h2>{href ? <a href={href}>{title}</a> : title}{count != null ? <span className="count">{count}</span> : null}</h2>
        {href ? <a className="more-link" href={href}><Icon name="forward" size={18} /></a> : null}
      </div>
      {children}
    </section>
  );
}

export function Library() {
  const app = useApp();
  const sync = useWatch(app.sync);
  const v = sync.data;
  const history = v.h.slice(0, 8).map(fromV);
  const hours = totalHours(v.h.reduce((n, w) => n + w.w, 0), app);
  const later = v.sv.filter((x) => x.li === 'later').sort((a, b) => b.at - a.at).map(fromV);
  const liked = v.sv.filter((x) => x.li === 'liked').sort((a, b) => b.at - a.at).map(fromV);
  return (
    <div className="page">
      <div className="lib-stats">
        <div><b>{v.h.length.toLocaleString(app.lang)}</b><span>{app.t('videos')}</span></div>
        <div><b>{hours.value}</b><span>{hours.label}</span></div>
        <div><b>{v.s.length.toLocaleString(app.lang)}</b><span>{app.t('subscriptions')}</span></div>
      </div>
      <Shelf title={app.t('history')} href="#/list/history" count={v.h.length}>
        {history.length ? <Grid row>{history.map((x) => <VideoCard key={x.id} v={x} source="history" />)}</Grid> : <p className="faint">—</p>}
      </Shelf>
      <Shelf title={app.t('watchLater')} href="#/list/later" count={later.length}>
        {later.length ? <Grid row>{later.slice(0, 8).map((x) => <VideoCard key={x.id} v={x} source="later" />)}</Grid> : <p className="faint">—</p>}
      </Shelf>
      <Shelf title={app.t('likedVideos')} href="#/list/liked" count={liked.length}>
        {liked.length ? <Grid row>{liked.slice(0, 8).map((x) => <VideoCard key={x.id} v={x} source="liked" />)}</Grid> : <p className="faint">—</p>}
      </Shelf>
      <Shelf title={app.t('subscriptions')} href="#/subs" count={v.s.length}>
        <div className="sub-strip">
          {[...v.s].sort((a, b) => a.n.localeCompare(b.n)).map((s) => {
            const id = /(UC[A-Za-z0-9_-]{22})/.exec(s.u)?.[1];
            return <a key={s.u} className="sub-chip" href={id ? `#/channel/${id}` : undefined}><Cover src={s.a} size={56} round icon="person" /><span>{s.n}</span></a>;
          })}
        </div>
      </Shelf>
    </div>
  );
}

/** Uma lista inteira: histórico (por dia), mais tarde, curtidos. */
export function List({ which }) {
  const app = useApp();
  const sync = useWatch(app.sync);
  const v = sync.data;
  const [filter, setFilter] = useState('');
  const items = useMemo(() => {
    const base = which === 'history' ? v.h.map((w) => ({ ...fromV(w), _at: w.l }))
      : v.sv.filter((x) => x.li === which).sort((a, b) => b.at - a.at).map((s) => ({ ...fromV(s), _at: s.at }));
    const f = filter.trim().toLowerCase();
    return f ? base.filter((x) => `${x.title} ${x.channelName}`.toLowerCase().includes(f)) : base;
  }, [v, which, filter]);
  const title = which === 'history' ? app.t('history') : which === 'later' ? app.t('watchLater') : app.t('likedVideos');
  const groups = [];
  for (const x of items) {
    const day = which === 'history' ? new Date(x._at).toLocaleDateString(app.lang, { weekday: 'long', day: 'numeric', month: 'long' }) : '';
    if (!groups.length || groups.at(-1).day !== day) groups.push({ day, items: [] });
    groups.at(-1).items.push(x);
  }
  const extra = (x) => (which === 'history'
    ? { label: app.t('removeFromHistory'), icon: 'trash', onClick: () => app.forgetWatch(x.id) }
    : { label: app.t('removeFromList'), icon: 'trash', onClick: () => app.toggleSaved(which, x) });
  return (
    <div className="page narrow">
      <header className="list-head">
        <h1>{title}</h1>
        <div className="row-actions">
          <label className="filter"><Icon name="search" size={16} /><input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder={app.t('filter')} /></label>
          {which === 'history' && v.h.length ? <button type="button" className="pill danger" onClick={() => app.confirmClearHistory()}><Icon name="trash" size={16} /> {app.t('clearHistory')}</button> : null}
        </div>
      </header>
      {!items.length ? <div className="empty"><Icon name={which === 'history' ? 'history' : which === 'later' ? 'later' : 'thumbUp'} size={40} /></div> : groups.map((g) => (
        <section key={g.day || 'all'}>
          {g.day ? <h2 className="day">{g.day}</h2> : null}
          <div className="list">{g.items.map((x) => <VideoRow key={x.id} v={x} source={which} extra={extra(x)} />)}</div>
        </section>
      ))}
    </div>
  );
}
