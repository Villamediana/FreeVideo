// O vídeo aberto: o player do YouTube (com os controles dele: qualidade, legendas, tela cheia,
// velocidade) e o que vai pro histórico do cofre — quanto foi assistido de verdade e onde parou, como
// o app anota (TubeLibrary.recordWatch): a cada 15 s tocando, ao pausar, ao trocar de vídeo e ao sair.
import { STATE, YouTubeFrame } from './youtube.js';

const NOTE_EVERY_MS = 15_000;

export class Watcher {
  constructor({ host, onRecord = () => {}, onEnded = () => {}, onToast = () => {} }) {
    Object.assign(this, { onRecord, onEnded, onToast });
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
    this.frame = new YouTubeFrame(host, {
      controls: true,
      onState: (s) => this.onState(s),
      onTime: (t, d) => this.onTime(t, d),
      onError: (code) => this.onError(code),
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
    this.set({ video, time: startMs / 1000, duration: (video.durationMs ?? 0) / 1000, state: STATE.BUFFERING, error: null });
    this.frame.load(video.id, Math.floor(startMs / 1000), true);
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
    this.set({ video, time: startMs / 1000, duration: (video.durationMs ?? 0) / 1000, state: play ? STATE.BUFFERING : STATE.CUED, error: null });
    this.frame.load(video.id, Math.floor(startMs / 1000), play);
  }

  /** Fecha o vídeo (o mini player saiu): o que foi visto fica anotado. */
  close() {
    this.note();
    this.lastActive = Date.now();
    this.frame.stop();
    this.set({ video: null, state: STATE.UNSTARTED, time: 0, duration: 0 });
  }

  toggle() {
    if (!this.video) return;
    this.lastActive = Date.now();
    if (this.playing) this.frame.pause();
    else this.frame.play();
  }

  seek(seconds) {
    this.frame.seek(Math.max(0, seconds));
  }

  onState(s) {
    // Tocou ou pausou pelos controles do próprio YouTube: também é a pessoa aqui.
    if ((s === STATE.PLAYING && this.state !== STATE.PLAYING) || (s === STATE.PAUSED && this.state === STATE.PLAYING)) this.lastActive = Date.now();
    if (s !== STATE.PLAYING) {
      this.lastTick = null;
      if (s === STATE.PAUSED) this.note();
    }
    this.set({ state: s });
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
