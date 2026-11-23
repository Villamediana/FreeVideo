// O perfil e os ajustes deste computador: foto, nome e @ (os do celular), tema, cor, idioma, assuntos
// (os interesses da Início), a sincronização e desligar. Rótulos curtos, sem parágrafos.
import { useState } from 'react';
import { Cover, Dialog, Icon, useApp, useWatch } from './kit.jsx';
import { LANGS } from './i18n.js';

export const ACCENTS = [
  ['ink', '#3F5B83', '#A9BCD8'], ['ember', '#CF6A58', '#F2A08F'], ['orange', '#C97A3E', '#F3B27E'], ['gold', '#B38B32', '#EACB7E'],
  ['green', '#5E9874', '#A3D2B2'], ['teal', '#4E9396', '#98CFD0'], ['blue', '#5C80BE', '#A8BEE9'], ['purple', '#8A70C2', '#C5B3EC'], ['pink', '#C46B8E', '#EFAEC6'],
];

export function Avatar({ me, size }) {
  return me?.avatar ? <Cover src={me.avatar} size={size} round icon="person" /> : (
    <span className="avatar-letter" style={{ width: size, height: size, fontSize: size * 0.42 }}>{(me?.name ?? '?').slice(0, 1).toUpperCase()}</span>
  );
}

function Topics() {
  const app = useApp();
  const sync = useWatch(app.sync);
  const [text, setText] = useState('');
  const topics = sync.data.in.map((x) => x.t);
  const add = () => {
    const t = text.trim();
    if (!t) return;
    app.setInterests([...topics, t]);
    setText('');
  };
  return (
    <div className="topics">
      {topics.map((t) => (
        <span key={t} className="chip on removable">{t}<button type="button" aria-label="×" onClick={() => app.setInterests(topics.filter((x) => x !== t))}><Icon name="close" size={14} /></button></span>
      ))}
      <form onSubmit={(e) => { e.preventDefault(); add(); }} className="topic-add">
        <input value={text} onChange={(e) => setText(e.target.value)} placeholder={app.t('addTopic')} maxLength={40} />
        <button type="submit" className="icon-btn" aria-label={app.t('addTopic')}><Icon name="plus" size={18} /></button>
      </form>
    </div>
  );
}

export function Profile() {
  const app = useApp();
  const sync = useWatch(app.sync);
  const me = app.me();
  const prefs = app.prefs();
  const st = sync.status;
  const [confirm, setConfirm] = useState(false);
  const [naming, setNaming] = useState(false);
  const dark = document.documentElement.dataset.mode === 'dark';
  return (
    <div className="page narrow">
      <header className="profile-head">
        <Avatar me={me} size={112} />
        <div>
          <h1>{me?.name ?? '…'} <button type="button" className="icon-btn" aria-label={app.t('editName')} onClick={() => setNaming(true)}><Icon name="edit" size={18} /></button></h1>
          <p className="faint">@{me?.username}</p>
        </div>
      </header>
      <section className="settings">
        <div className="set-row"><span><Icon name="sun" size={18} /> {app.t('theme')}</span>
          <div className="seg">{['system', 'light', 'dark'].map((m) => <button key={m} type="button" className={(prefs.mode ?? 'light') === m ? 'on' : ''} onClick={() => app.setPrefs({ ...prefs, mode: m })}>{app.t(`mode_${m}`)}</button>)}</div>
        </div>
        <div className="set-row"><span><Icon name="spark" size={18} /> {app.t('accent')}</span>
          <div className="swatches">{ACCENTS.map(([id, l, d]) => <button key={id} type="button" className={`swatch${(prefs.accent ?? 'ink') === id ? ' on' : ''}`} style={{ '--c': dark ? d : l }} aria-label={id} onClick={() => app.setPrefs({ ...prefs, accent: id })} />)}</div>
        </div>
        <div className="set-row"><span><Icon name="globe" size={18} /> {app.t('language')}</span>
          <div className="seg">{LANGS.map(([id, label]) => <button key={id} type="button" className={app.lang === id ? 'on' : ''} onClick={() => app.setPrefs({ ...prefs, lang: id })}>{label}</button>)}</div>
        </div>
        <div className="set-row column"><span><Icon name="spark" size={18} /> {app.t('topics')}</span><Topics /></div>
        <div className="set-row"><span><Icon name="sync" size={18} /> {app.t('syncState')}</span>
          <span className="faint">{!st.online ? app.t('offline') : st.novault ? app.t('noVaultYet') : st.syncing ? app.t('syncing') : st.synced ? app.t('syncedWith', app.owner || app.t('yourPhone')) : '…'}</span>
        </div>
        {app.canInstall() ? <div className="set-row"><span><Icon name="download" size={18} /> {app.t('installApp')}</span><button type="button" className="pill" onClick={() => app.install()}>{app.t('install')}</button></div> : null}
        <div className="set-row"><span><Icon name="laptop" size={18} /> {app.shared ? app.t('sharedComputer') : app.t('thisComputer')}</span>
          <button type="button" className="pill danger" onClick={() => setConfirm(true)}>{app.t('unlinkComputer')}</button>
        </div>
      </section>
      {naming ? (
        <Dialog title={app.t('editName')} onClose={() => setNaming(false)}>
          {(close) => (
            <form className="form" onSubmit={async (e) => {
              e.preventDefault();
              const name = new FormData(e.currentTarget).get('name')?.toString().trim();
              if (!name) return;
              try { await app.rename(name); close(); } catch { app.toast(app.t('loadFailed')); }
            }}>
              <input name="name" defaultValue={me?.name ?? ''} maxLength={40} autoFocus />
              <div className="form-actions"><button type="button" className="pill ghost" onClick={close}>{app.t('cancel')}</button><button type="submit" className="pill on">{app.t('save')}</button></div>
            </form>
          )}
        </Dialog>
      ) : null}
      {confirm ? (
        <Dialog title={app.t('unlinkComputer')} onClose={() => setConfirm(false)}>
          {(close) => (
            <div className="form">
              <div className="form-actions"><button type="button" className="pill ghost" onClick={close}>{app.t('cancel')}</button><button type="button" className="pill danger on" onClick={() => app.unlink()}>{app.t('unlink')}</button></div>
            </div>
          )}
        </Dialog>
      ) : null}
    </div>
  );
}
