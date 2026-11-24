// Os vídeos na tela: o cartão da grade (miniatura, avatar do canal, título, quem e quando), a linha
// (busca, "a seguir", listas), o canal e a playlist da busca — e o menu de um vídeo.
import { Cover, Icon, IconBtn, Thumb, meta, useApp, useWatch } from './kit.jsx';

/** Onde a pessoa parou neste vídeo (0..1), pelo histórico. */
export function useProgress(id) {
  const app = useApp();
  useWatch(app.sync);
  const w = app.sync.data.h.find((x) => x.i === id);
  return w && w.d > 0 ? Math.min(1, w.p / w.d) : 0;
}

function ChannelLink({ v, children }) {
  return v.channelId ? <a href={`#/channel/${v.channelId}`} onClick={(e) => e.stopPropagation()}>{children}</a> : <span>{children}</span>;
}

export function VideoCard({ v, source, onDismissed }) {
  const app = useApp();
  const progress = useProgress(v.id);
  const open = () => app.openVideo(v, { source });
  return (
    <article className="vcard" onClick={open} onContextMenu={(e) => { e.preventDefault(); app.openMenu(e, app.videoMenu(v, { onDismissed })); }}>
      <Thumb video={v} progress={progress} />
      <div className="vcard-body">
        {v.channelAvatar ? <ChannelLink v={v}><Cover src={v.channelAvatar} size={36} round icon="person" /></ChannelLink> : null}
        <div className="vcard-text">
          <h3 title={v.title}>{v.title}</h3>
          <p><ChannelLink v={v}>{v.channelName}</ChannelLink>{v.verified ? <Icon name="check" size={13} /> : null}</p>
          <p className="faint">{meta(v, app.t, app.lang)}</p>
        </div>
        <IconBtn icon="more" label={app.t('more')} kind="vcard-more" size={18} onClick={(e) => { e.stopPropagation(); app.openMenu(e, app.videoMenu(v, { onDismissed })); }} />
      </div>
    </article>
  );
}

export function VideoRow({ v, compact = false, source, extra, current = false }) {
  const app = useApp();
  const progress = useProgress(v.id);
  return (
    <article className={`vrow${compact ? ' compact' : ''}${current ? ' current' : ''}`} onClick={() => app.openVideo(v, { source })} onContextMenu={(e) => { e.preventDefault(); app.openMenu(e, app.videoMenu(v, { extra })); }}>
      <Thumb video={v} progress={progress} />
      <div className="vrow-text">
        <h3 title={v.title}>{v.title}</h3>
        <p><ChannelLink v={v}>{v.channelName}</ChannelLink></p>
        <p className="faint">{meta(v, app.t, app.lang)}</p>
      </div>
      <IconBtn icon="more" label={app.t('more')} kind="vcard-more" size={18} onClick={(e) => { e.stopPropagation(); app.openMenu(e, app.videoMenu(v, { extra })); }} />
    </article>
  );
}

export function ChannelRow({ c }) {
  const app = useApp();
  useWatch(app.sync);
  const subscribed = app.isSubscribed(c.url, c.name);
  return (
    <a className="crow" href={`#/channel/${c.id}`}>
      <Cover src={c.avatarUrl} size={120} round icon="person" />
      <div className="crow-text">
        <h3>{c.name}{c.verified ? <Icon name="check" size={15} /> : null}</h3>
        <p className="faint">{[c.handle, c.subscribersText].filter(Boolean).join(' · ')}</p>
      </div>
      <button type="button" className={`pill${subscribed ? '' : ' on'}`} onClick={(e) => { e.preventDefault(); app.toggleSub(c); }}>
        {subscribed ? <><Icon name="check" size={16} /> {app.t('subscribed')}</> : app.t('subscribe')}
      </button>
    </a>
  );
}

export function PlaylistRow({ p }) {
  const app = useApp();
  return (
    <a className="vrow" href={`#/playlist/${p.id}`}>
      <span className="thumb stack">
        {p.thumbnailUrl ? <img src={p.thumbnailUrl} alt="" loading="lazy" referrerPolicy="no-referrer" /> : <Icon name="queue" size={28} />}
        <span className="badge"><Icon name="queue" size={13} /> {p.count ?? ''}</span>
      </span>
      <div className="vrow-text">
        <h3>{p.name}</h3>
        <p>{p.uploader}</p>
        <p className="faint">{app.t('playlist')}</p>
      </div>
    </a>
  );
}

export function Grid({ children, row = false }) {
  return <div className={`vgrid${row ? ' one-row' : ''}`}>{children}</div>;
}
