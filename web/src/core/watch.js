// O vídeo aberto: o player do YouTube (com os controles dele: qualidade, legendas, tela cheia,
// velocidade) e o que vai pro histórico do cofre — quanto foi assistido de verdade e onde parou, como
// o app anota (TubeLibrary.recordWatch): a cada 15 s tocando, ao pausar, ao trocar de vídeo e ao sair.
// E o volume: o do FreeVideo (a régua, a roda do mouse, ↑/↓ e M), lembrado neste navegador e posto no
// player sempre que ele fica pronto; mexido nos controles do YouTube, vem pra cá também.
import { STATE, YouTubeFrame } from './youtube.js';

const NOTE_EVERY_MS = 15_000;
// Pediu pra tocar e nada em tanto tempo: o "tocar" aparece em cima do vídeo (em vez de girar pra sempre).
const STUCK_AFTER_MS = 12_000;
// Depois de mudar o volume daqui, o que o player ainda diz (o volume de antes) não volta pra régua.
const VOLUME_QUIET_MS = 1500;

export const clampVolume = (v) => (Number.isFinite(v) ? Math.round(Math.max(0, Math.min(100, v))) : 100);

export class Watcher {
  constructor({ host, volume = 100, muted = false, onVolume = () => {}, onRecord = () => {}, onEnded = () => {}, onToast = () => {} }) {
    Object.assign(this, { onVolume, onRecord, onEnded, onToast });
    this.volume = clampVolume(volume);
    this.muted = muted === true;
    this.volumeQuietUntil = 0;
    this.volumeShownAt = 0; // a última mudança pelo teclado ou pela roda (o aviso em cima do vídeo)
    this.video = null;
    this.state = STATE.UNSTARTED;
    this.time = 0;
    this.duration = 0;
    this.version = 0;
    this.listeners = new Set();
    this.played = 0; // ms tocados desde a última anotação
    this.lastTick = null;
    this.lastNote = 0;
    this.newVisit = false;
    this.lastActive = 0; // a última vez que a pessoa abriu, tocou, pausou ou fechou um vídeo aqui (ms)
    this.wanted = false; // pediu pra tocar este vídeo
    this.started = false; // este vídeo já mostrou imagem (até lá, a capa e o "carregando" no lugar do preto)
    this.blocked = false; // o navegador não deixou tocar sozinho: um clique da pessoa toca
    this.frame = new YouTubeFrame(host, {
      controls: true,
      onState: (s) => this.onState(s),
      onTime: (t, d) => this.onTime(t, d),
      onError: (code) => this.onError(code),
      onReady: () => this.applyVolume(),
      onBlocked: () => this.set({ blocked: true }),
      onVolume: (v, m) => this.heardVolume(v, m),
    });
    // O frame do YouTube já pronto: o primeiro play é um comando, como os outros.
    this.frame.warm();
  }

  subscribe(fn) {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  set(change = {}) {
    Object.assign(this, change);
    this.version++;
    for (const f of this.listeners) f();
  }

  get playing() {
    return this.state === STATE.PLAYING || this.state === STATE.BUFFERING;
  }

  /** Abrindo: pediu pra tocar e o vídeo ainda não mostrou nada (o frame do YouTube é só preto). */
  get loading() {
    return !!this.video && this.wanted && !this.started && !this.blocked && this.error == null;
  }

  /** Se não começar a tocar a tempo, aparece o "tocar" (um clique da pessoa sempre vale). */
  watchStart() {
    clearTimeout(this.stuckTimer);
    const v = this.video;
    this.stuckTimer = setTimeout(() => {
      if (this.video === v && !this.started && this.wanted && this.error == null) this.set({ blocked: true });
    }, STUCK_AFTER_MS);
  }

  /** Abre [video] (de [startMs], quando volta de onde parou). O mesmo vídeo de novo só continua. */
  open(video, startMs = 0) {
    if (this.video?.id === video.id) {
      this.set({ video: { ...this.video, ...pick(video) } });
      return;
    }
    this.note();
    this.newVisit = true;
    this.played = 0;
    this.lastTick = null;
    this.lastActive = Date.now();
    this.set({ video, time: startMs / 1000, duration: (video.durationMs ?? 0) / 1000, state: STATE.BUFFERING, error: null, wanted: true, started: false, blocked: false });
    this.frame.load(video.id, Math.floor(startMs / 1000), true);
    this.watchStart();
  }

  /**
   * Deixa [video] pronto no mini player, pausado em [startMs] ([play]: tenta já tocar): o que tocava
   * nesta aba antes de recarregar, ou o que o celular estava vendo. [at]: quando aquilo foi visto.
   */
  cue(video, startMs = 0, { play = false, at = Date.now() } = {}) {
    this.note();
    this.newVisit = true;
    this.played = 0;
    this.lastTick = null;
    this.lastActive = at;
    this.set({ video, time: startMs / 1000, duration: (video.durationMs ?? 0) / 1000, state: play ? STATE.BUFFERING : STATE.CUED, error: null, wanted: play, started: false, blocked: false });
    this.frame.load(video.id, Math.floor(startMs / 1000), play);
    if (play) this.watchStart();
  }

  /** Fecha o vídeo (o mini player saiu): o que foi visto fica anotado. */
  close() {
    this.note();
    this.lastActive = Date.now();
    this.frame.stop();
    clearTimeout(this.stuckTimer);
    this.set({ video: null, state: STATE.UNSTARTED, time: 0, duration: 0, wanted: false, blocked: false });
  }

  toggle() {
    if (!this.video) return;
    this.lastActive = Date.now();
    if (this.playing && !this.blocked) this.frame.pause();
    else {
      this.set({ wanted: true, blocked: false });
      this.frame.play();
      if (!this.started) this.watchStart();
    }
  }

  seek(seconds) {
    this.frame.seek(Math.max(0, seconds));
  }

  /** O volume (0–100). Subir tira o "sem som". [show]: veio do teclado ou da roda (aparece no vídeo). */
  setVolume(v, { show = false } = {}) {
    const volume = clampVolume(v);
    this.changeVolume(volume, volume > 0 ? false : this.muted, show);
  }

  /** Sobe ([step] > 0) ou desce o volume; subir com o som desligado religa ele. */
  stepVolume(step, { show = false } = {}) {
    const from = this.muted && step > 0 ? 0 : this.volume;
    this.setVolume(from + step, { show });
  }

  /** Sem som / com som (religar num volume zero volta num volume que se ouve). */
  toggleMute({ show = false } = {}) {
    if (this.muted || this.volume === 0) this.changeVolume(this.volume === 0 ? 30 : this.volume, false, show);
    else this.changeVolume(this.volume, true, show);
  }

  changeVolume(volume, muted, show) {
    if (volume === this.volume && muted === this.muted && !show) return;
    this.volumeQuietUntil = performance.now() + VOLUME_QUIET_MS;
    if (volume !== this.volume) this.frame.volume(volume);
    if (muted !== this.muted) this.frame.mute(muted);
    this.set({ volume, muted, volumeShownAt: show ? performance.now() : this.volumeShownAt });
    this.onVolume(volume, muted);
  }

  /** O player ficou pronto (o frame nasceu ou recarregou): com o volume daqui. */
  applyVolume() {
    this.volumeQuietUntil = performance.now() + VOLUME_QUIET_MS;
    this.frame.volume(this.volume);
    this.frame.mute(this.muted);
  }

  /** O player disse o volume dele: mexido nos controles do YouTube, a régua daqui vai junto. */
  heardVolume(volume, muted) {
    if (performance.now() < this.volumeQuietUntil) return;
    const v = clampVolume(volume);
    if (v === this.volume && muted === this.muted) return;
    this.set({ volume: v, muted });
    this.onVolume(v, muted);
  }

  onState(s) {
    // Tocou ou pausou pelos controles do próprio YouTube: também é a pessoa aqui.
    if ((s === STATE.PLAYING && this.state !== STATE.PLAYING) || (s === STATE.PAUSED && this.state === STATE.PLAYING)) this.lastActive = Date.now();
    if (s !== STATE.PLAYING) {
      this.lastTick = null;
      if (s === STATE.PAUSED) this.note();
    }
    const change = { state: s };
    if (s === STATE.PLAYING) {
      Object.assign(change, { started: true, blocked: false, wanted: true });
      clearTimeout(this.stuckTimer);
    } else if (s === STATE.PAUSED) change.wanted = false;
    this.set(change);
    if (s === STATE.ENDED) {
      this.note(true);
      this.onEnded(this.video);
    }
  }

  onTime(t, d) {
    if (this.state === STATE.PLAYING) {
      const now = performance.now();
      if (this.lastTick != null) {
        const step = now - this.lastTick;
        if (step > 0 && step < 5000) this.played += step;
      }
      this.lastTick = now;
      if (Date.now() - this.lastNote > NOTE_EVERY_MS && this.played > 1000) this.note();
    }
    this.set({ time: t, duration: d || this.duration });
  }

  onError(code) {
    this.set({ error: code });
    this.onToast(code === 101 || code === 150 ? 'cantEmbed' : 'cantPlay');
  }

  /** O que foi assistido desde a última anotação vai pro histórico. [ended]: o vídeo acabou (posição = fim). */
  note(ended = false) {
    const v = this.video;
    if (!v) return;
    if (this.played < 1000 && !this.newVisit && !ended) return;
    const durationMs = (this.duration || 0) * 1000;
    const positionMs = ended ? durationMs : (this.time || 0) * 1000;
    // Uma visita que nem começou (aberto e fechado sem tocar) não entra no histórico.
    if (this.newVisit && this.played < 1000 && !ended) return;
    this.onRecord(v, positionMs, durationMs, this.played, this.newVisit);
    this.newVisit = false;
    this.played = 0;
    this.lastNote = Date.now();
  }
}

const pick = (v) => Object.fromEntries(Object.entries(v).filter(([, x]) => x != null && x !== '' && x !== -1 && x !== 0));
