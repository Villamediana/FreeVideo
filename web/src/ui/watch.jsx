// A página do vídeo: o player do YouTube grande (no "palco"), o título, o canal (inscrever-se), as ações
// (curtir, mais tarde, compartilhar, não tenho interesse), a descrição, os comentários e o que vem a
// seguir. O vídeo segue tocando no canto quando a pessoa sai daqui.
import { useEffect, useRef, useState } from 'react';
import { Cover, Failed, Icon, Loading, useApp, useLoad, useWatch } from './kit.jsx';
import { VideoRow } from './cards.jsx';

/** Links e quebras de linha na descrição (sem virar HTML: só texto e <a> feitos aqui). */
function Rich({ text }) {
  const parts = String(text ?? '').split(/(https?:\/\/[^\s]+)/g);
  return parts.map((p, i) => (/^https?:\/\//.test(p)
    ? <a key={i} href={p} target="_blank" rel="noopener noreferrer nofollow">{p.length > 60 ? `${p.slice(0, 57)}…` : p}</a>
    : <span key={i}>{p}</span>));
}

function Comments({ id }) {
  const app = useApp();
  const [st, setSt] = useState({ items: [], next: undefined, loading: false, error: false, open: false });
  const load = async () => {
    setSt((s) => ({ ...s, loading: true, error: false, open: true }));
    try {
      const r = st.next ? await app.api.more(st.next) : await app.api.comments(id);
      setSt((s) => ({ items: [...s.items, ...(r.comments ?? [])], next: r.next, loading: false, error: false, open: true }));
    } catch {
      setSt((s) => ({ ...s, loading: false, error: true }));
    }
  };
  useEffect(() => { setSt({ items: [], next: undefined, loading: false, error: false, open: false }); }, [id]);
  if (!st.open) return <button type="button" className="pill wide" onClick={load}><Icon name="lyrics" size={17} /> {app.t('comments')}</button>;
  return (
    <section className="comments">
      <h3>{app.t('comments')}</h3>
      {st.items.map((c) => (
        <div key={c.id} className="comment">
          <Cover src={c.authorAvatar} size={36} round icon="person" />
          <div>
            <p className="comment-head"><b>{c.author}</b>{c.byUploader ? <Icon name="check" size={13} /> : null}<span className="faint">{c.published}</span>{c.pinned ? <Icon name="pin" size={13} /> : null}</p>
            <p className="comment-text"><Rich text={c.text} /></p>
            <p className="faint comment-meta">{c.likes ? <><Icon name="thumbUp" size={14} /> {c.likes}</> : null}{c.replies ? <span>{app.t('repliesCount', c.replies)}</span> : null}</p>
          </div>
        </div>
      ))}
      {st.loading ? <Loading /> : null}
      {st.error ? <button type="button" className="pill" onClick={load}>{app.t('tryAgain')}</button> : null}
      {st.next && !st.loading ? <button type="button" className="pill wide" onClick={load}>{app.t('moreComments')}</button> : null}
    </section>
  );
}

export function Watch({ id, startMs }) {
  const app = useApp();
  const w = useWatch(app.watcher);
  useWatch(app.sync);
  const slot = useRef(null);
  const [descOpen, setDescOpen] = useState(false);
  const r = useLoad(() => app.api.video(id), [id, app.lang]);
  // O vídeo começa ao abrir a página (de onde parou, se não acabou), mesmo antes dos dados chegarem.
  useEffect(() => {
    const known = app.sync.data.h.find((x) => x.i === id);
    const hint = app.resumeAt(id);
    const resume = startMs >= 0 ? startMs : hint != null ? hint : known && known.d > 0 && known.p / known.d < 0.95 && known.p > 10_000 ? known.p : 0;
    const card = app.peek(id);
    const seed = card ?? { kind: 'video', id, title: known?.t ?? '', channelName: known?.cn ?? '', channelUrl: known?.cu ?? null, thumbnailUrl: known?.th ?? null, durationMs: known?.d ?? 0 };
    app.openVideo(seed, { navigate: false, startMs: resume });
    setDescOpen(false);
  }, [id]);
  useEffect(() => {
    if (r.data?.video && w.video?.id === id) app.watcher.open({ ...r.data.video, durationMs: w.duration * 1000 || r.data.video.durationMs });
  }, [r.data]);
  useEffect(() => { app.setStage(slot); return () => app.setStage(null); }, []);
  const d = r.data;
  const v = d?.video ?? w.video;
  const liked = app.isSaved('liked', id);
  const later = app.isSaved('later', id);
  const subscribed = v?.channelUrl ? app.isSubscribed(v.channelUrl, v.channelName) : false;
  const playNext = () => {
    const next = d?.related?.find((x) => !x.live);
    if (next) app.openVideo(next, { source: 'related' });
  };
  useEffect(() => { app.setNext(playNext); }, [d]);
  return (
    <div className="watch">
      <div className="watch-main">
        <div className="stage" ref={slot} />
        {v ? (
          <>
            <h1 className="watch-title">{v.title || '…'}</h1>
            <div className="watch-bar">
              <div className="watch-channel">
                {v.channelAvatar ? <a href={v.channelId ? `#/channel/${v.channelId}` : undefined}><Cover src={v.channelAvatar} size={42} round icon="person" /></a> : null}
                <div>
                  <a className="watch-ch-name" href={v.channelId ? `#/channel/${v.channelId}` : undefined}>{v.channelName}</a>
                  {d?.subscribersText ? <span className="faint">{d.subscribersText}</span> : null}
                </div>
                {v.channelUrl ? (
                  <button type="button" className={`pill${subscribed ? '' : ' on'}`} onClick={() => app.toggleSub({ url: v.channelUrl, name: v.channelName, avatarUrl: v.channelAvatar })}>
                    {subscribed ? <><Icon name="bell" size={16} /> {app.t('subscribed')}</> : app.t('subscribe')}
                  </button>
                ) : null}
              </div>
              <div className="watch-actions">
                <button type="button" className={`pill${liked ? ' active' : ''}`} onClick={() => app.toggleSaved('liked', v)}><Icon name="thumbUp" size={17} fill={liked} />{d?.likes >= 0 ? <span>{app.compact(d.likes + (liked ? 1 : 0))}</span> : null}</button>
                <button type="button" className={`pill${later ? ' active' : ''}`} onClick={() => app.toggleSaved('later', v)} title={app.t('watchLater')}><Icon name="later" size={17} /> {app.t('later')}</button>
                <button type="button" className="pill" onClick={() => app.copy(`https://www.youtube.com/watch?v=${id}`)} title={app.t('copyLink')}><Icon name="share" size={17} /></button>
                <button type="button" className="pill" onClick={(e) => app.openMenu(e, app.videoMenu(v, {}))} aria-label={app.t('more')}><Icon name="more" size={17} /></button>
              </div>
            </div>
            {d ? (
              <div className={`desc${descOpen ? ' open' : ''}`} onClick={() => setDescOpen(true)}>
                <p className="desc-meta"><b>{d.video.viewsText}</b> <b>{d.published ?? d.video.uploadedText}</b></p>
                {d.description ? <p className="desc-text"><Rich text={d.description} /></p> : null}
                {d.description && !descOpen ? <span className="desc-more">{app.t('showMore')}</span> : null}
                {descOpen ? <button type="button" className="desc-more" onClick={(e) => { e.stopPropagation(); setDescOpen(false); }}>{app.t('showLess')}</button> : null}
              </div>
            ) : null}
            <Comments id={id} />
          </>
        ) : null}
      </div>
      <aside className="watch-side">
        {r.loading ? <Loading /> : r.error ? <Failed onRetry={r.retry} /> : (d?.related ?? []).map((x) => <VideoRow key={x.id} v={x} compact source="related" />)}
      </aside>
    </div>
  );
}
