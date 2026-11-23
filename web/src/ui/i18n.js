// Os textos da página (curtos, como no app): português (o padrão), espanhol e inglês.

export const LANGS = [['pt', 'Português'], ['es', 'Español'], ['en', 'English']];

const n = (lang) => (x) => Number(x).toLocaleString(lang);
const compact = (lang) => (x) => new Intl.NumberFormat(lang, { notation: 'compact', maximumFractionDigits: 1 }).format(x);

const PT = {
  home: 'Início', subs: 'Inscrições', library: 'Biblioteca', history: 'Histórico', subscriptions: 'Inscrições', menu: 'Menu',
  search: 'Buscar', clear: 'Limpar', remove: 'Tirar', all: 'Tudo', videos: 'Vídeos', channels: 'Canais', playlists: 'Playlists', playlist: 'Playlist',
  refresh: 'Atualizar', continueWatching: 'Continuar assistindo', tryAgain: 'Tentar de novo', loadFailed: 'Não deu pra carregar agora.', noResults: 'Nada encontrado.',
  more: 'Mais', play: 'Assistir', pause: 'Pausar', open: 'Abrir', close: 'Fechar', back: 'Voltar',
  subscribe: 'Inscrever-se', subscribed: 'Inscrito', subscribedTo: (c) => `Inscrito em ${c}`, unsubscribed: 'Inscrição cancelada',
  like: 'Gostei', unlike: 'Tirar o gostei', liked: 'Nos vídeos curtidos', unliked: 'Tirado dos curtidos',
  later: 'Mais tarde', watchLater: 'Assistir mais tarde', removeLater: 'Tirar do mais tarde', addedLater: 'Salvo pra mais tarde', removedLater: 'Tirado do mais tarde',
  likedVideos: 'Vídeos curtidos', copyLink: 'Copiar link', linkCopied: 'Link copiado',
  notInterested: 'Não tenho interesse', dontRecommendChannel: 'Não recomendar o canal', dismissed: 'Não vai aparecer mais', channelDismissed: 'Esse canal não vai mais aparecer', undo: 'Desfazer',
  removeFromHistory: 'Tirar do histórico', removeFromList: 'Tirar da lista', clearHistory: 'Limpar histórico', filter: 'Filtrar',
  comments: 'Comentários', moreComments: 'Mais comentários', repliesCount: (c) => (c === 1 ? '1 resposta' : `${n('pt-BR')(c)} respostas`),
  showMore: 'mais', showLess: 'Mostrar menos', noSubs: 'Os canais que você seguir aparecem aqui.', hours: 'horas',
  viewsCount: (c) => `${compact('pt-BR')(c)} visualizações`, videosCount: (c) => (c === 1 ? '1 vídeo' : `${n('pt-BR')(c)} vídeos`),
  cantEmbed: 'O dono deste vídeo não deixa ele tocar fora do YouTube.', cantPlay: 'Não deu pra tocar este vídeo agora.',
  theme: 'Tema', mode_system: 'Do sistema', mode_light: 'Claro', mode_dark: 'Escuro', accent: 'Cor', language: 'Idioma', topics: 'Assuntos', addTopic: 'Novo assunto',
  syncState: 'Sincronização', syncing: 'Sincronizando…', offline: 'Sem internet', syncedWith: (o) => `Junto com ${o}`, yourPhone: 'o seu celular',
  noVaultYet: 'Abra o FreeVideo no celular uma vez.', installApp: 'Instalar como aplicativo', install: 'Instalar',
  thisComputer: 'Este computador', sharedComputer: 'Computador emprestado', sharedComputerHint: 'Fechar a aba desliga este computador.',
  unlinkComputer: 'Desligar este computador', unlink: 'Desligar', editName: 'Nome', save: 'Salvar', cancel: 'Cancelar',
  unlinked: 'Este computador foi desligado', otherTab: 'O FreeVideo está aberto em outra aba', useHere: 'Usar aqui',
  linkTitle: 'Assista no computador', linkLead: 'Seu histórico, inscrições e salvos, iguais aos do celular.',
  link1: 'No FreeVideo do celular, abra a Biblioteca e toque em Ajustes.', link2: 'Em "Computador", toque em "Ligar".', link3: 'Aponte o celular pra este código.',
  linkSafe: 'Só ligue um código que você mesmo abriu. O que passa entre o celular e este computador vai cifrado.',
  keepLinked: 'Manter ligado neste computador', keepLinkedHint: 'Desmarque se o computador não for seu: fechar a aba desliga.',
  linkExpired: 'O código expirou.', linkFailed: 'Não deu certo.', linkNew: 'Mostrar outro código', linkWaiting: 'Esperando o celular…', linking: 'Ligando…',
  noPhone: 'Sem o celular? Use o código de recuperação', useCode: 'Código de recuperação', useCodeHint: 'O código do app em Perfil.',
  recoveryCode: 'Código de recuperação', enter: 'Entrar', codeShape: 'O código tem 16 letras e números.', codeWrong: 'Esse código não é de nenhum perfil.',
  codeLocked: 'O código abriu o perfil, mas não o cofre. Use o QR code pelo celular.', slowDown: 'Muitas tentativas. Espere um pouco.',
  tooManyComputers: 'Computadores demais ligados. Desligue um pelo celular.',
  browser: 'Navegador', browserOn: (b, o) => `${b} no ${o}`,
  mobileTitle: 'No celular, o FreeVideo é o app', mobileLead: 'Baixe o FreeVideo pra Android e ligue o computador por ele.', mobileGet: 'Baixar o FreeVideo',
};

const ES = {
  ...PT,
  home: 'Inicio', subs: 'Suscripciones', library: 'Biblioteca', history: 'Historial', subscriptions: 'Suscripciones', menu: 'Menú',
  search: 'Buscar', clear: 'Borrar', remove: 'Quitar', all: 'Todo', videos: 'Videos', channels: 'Canales', playlists: 'Playlists', playlist: 'Playlist',
  refresh: 'Actualizar', continueWatching: 'Seguir viendo', tryAgain: 'Intentar de nuevo', loadFailed: 'No se pudo cargar ahora.', noResults: 'No se encontró nada.',
  more: 'Más', play: 'Ver', pause: 'Pausar', open: 'Abrir', close: 'Cerrar', back: 'Atrás',
  subscribe: 'Suscribirse', subscribed: 'Suscrito', subscribedTo: (c) => `Suscrito a ${c}`, unsubscribed: 'Suscripción cancelada',
  like: 'Me gusta', unlike: 'Quitar me gusta', liked: 'En videos que te gustan', unliked: 'Quitado de los que te gustan',
  later: 'Ver luego', watchLater: 'Ver más tarde', removeLater: 'Quitar de ver más tarde', addedLater: 'Guardado para más tarde', removedLater: 'Quitado de ver más tarde',
  likedVideos: 'Videos que te gustan', copyLink: 'Copiar enlace', linkCopied: 'Enlace copiado',
  notInterested: 'No me interesa', dontRecommendChannel: 'No recomendar el canal', dismissed: 'No va a aparecer más', channelDismissed: 'Ese canal no va a aparecer más', undo: 'Deshacer',
  removeFromHistory: 'Quitar del historial', removeFromList: 'Quitar de la lista', clearHistory: 'Borrar historial', filter: 'Filtrar',
  comments: 'Comentarios', moreComments: 'Más comentarios', repliesCount: (c) => (c === 1 ? '1 respuesta' : `${n('es')(c)} respuestas`),
  showMore: 'más', showLess: 'Mostrar menos', noSubs: 'Los canales que sigas aparecen aquí.', hours: 'horas',
  viewsCount: (c) => `${compact('es')(c)} vistas`, videosCount: (c) => (c === 1 ? '1 video' : `${n('es')(c)} videos`),
  cantEmbed: 'El dueño de este video no deja que se reproduzca fuera de YouTube.', cantPlay: 'No se pudo reproducir este video ahora.',
  theme: 'Tema', mode_system: 'Del sistema', mode_light: 'Claro', mode_dark: 'Oscuro', accent: 'Color', language: 'Idioma', topics: 'Temas', addTopic: 'Nuevo tema',
  syncState: 'Sincronización', syncing: 'Sincronizando…', offline: 'Sin internet', syncedWith: (o) => `Junto con ${o}`, yourPhone: 'tu celular',
  noVaultYet: 'Abre FreeVideo en el celular una vez.', installApp: 'Instalar como aplicación', install: 'Instalar',
  thisComputer: 'Esta computadora', sharedComputer: 'Computadora prestada', sharedComputerHint: 'Cerrar la pestaña desconecta esta computadora.',
  unlinkComputer: 'Desconectar esta computadora', unlink: 'Desconectar', editName: 'Nombre', save: 'Guardar', cancel: 'Cancelar',
  unlinked: 'Esta computadora fue desconectada', otherTab: 'FreeVideo está abierto en otra pestaña', useHere: 'Usar aquí',
  linkTitle: 'Mira en la computadora', linkLead: 'Tu historial, suscripciones y guardados, iguales a los del celular.',
  link1: 'En FreeVideo del celular, abre la Biblioteca y toca Ajustes.', link2: 'En "Computadora", toca "Conectar".', link3: 'Apunta el celular a este código.',
  linkSafe: 'Conecta solo un código que tú mismo abriste. Lo que pasa entre el celular y esta computadora va cifrado.',
  keepLinked: 'Mantener conectada esta computadora', keepLinkedHint: 'Desmárcalo si la computadora no es tuya: cerrar la pestaña la desconecta.',
  linkExpired: 'El código expiró.', linkFailed: 'No funcionó.', linkNew: 'Mostrar otro código', linkWaiting: 'Esperando el celular…', linking: 'Conectando…',
  noPhone: '¿Sin el celular? Usa el código de recuperación', useCode: 'Código de recuperación', useCodeHint: 'El código de la app en Perfil.',
  recoveryCode: 'Código de recuperación', enter: 'Entrar', codeShape: 'El código tiene 16 letras y números.', codeWrong: 'Ese código no es de ningún perfil.',
  codeLocked: 'El código abrió el perfil, pero no la bóveda. Usa el código QR desde el celular.', slowDown: 'Demasiados intentos. Espera un poco.',
  tooManyComputers: 'Demasiadas computadoras conectadas. Desconecta una desde el celular.',
  browser: 'Navegador', browserOn: (b, o) => `${b} en ${o}`,
  mobileTitle: 'En el celular, FreeVideo es la app', mobileLead: 'Descarga FreeVideo para Android y conecta la computadora desde ella.', mobileGet: 'Descargar FreeVideo',
};

const EN = {
  ...PT,
  home: 'Home', subs: 'Subscriptions', library: 'Library', history: 'History', subscriptions: 'Subscriptions', menu: 'Menu',
  search: 'Search', clear: 'Clear', remove: 'Remove', all: 'All', videos: 'Videos', channels: 'Channels', playlists: 'Playlists', playlist: 'Playlist',
  refresh: 'Refresh', continueWatching: 'Continue watching', tryAgain: 'Try again', loadFailed: "Couldn't load right now.", noResults: 'Nothing found.',
  more: 'More', play: 'Watch', pause: 'Pause', open: 'Open', close: 'Close', back: 'Back',
  subscribe: 'Subscribe', subscribed: 'Subscribed', subscribedTo: (c) => `Subscribed to ${c}`, unsubscribed: 'Unsubscribed',
  like: 'Like', unlike: 'Unlike', liked: 'Added to liked videos', unliked: 'Removed from liked videos',
  later: 'Later', watchLater: 'Watch later', removeLater: 'Remove from watch later', addedLater: 'Saved for later', removedLater: 'Removed from watch later',
  likedVideos: 'Liked videos', copyLink: 'Copy link', linkCopied: 'Link copied',
  notInterested: 'Not interested', dontRecommendChannel: "Don't recommend channel", dismissed: "It won't show up again", channelDismissed: "That channel won't show up again", undo: 'Undo',
  removeFromHistory: 'Remove from history', removeFromList: 'Remove from list', clearHistory: 'Clear history', filter: 'Filter',
  comments: 'Comments', moreComments: 'More comments', repliesCount: (c) => (c === 1 ? '1 reply' : `${n('en')(c)} replies`),
  showMore: 'more', showLess: 'Show less', noSubs: 'Channels you follow show up here.', hours: 'hours',
  viewsCount: (c) => `${compact('en')(c)} views`, videosCount: (c) => (c === 1 ? '1 video' : `${n('en')(c)} videos`),
  cantEmbed: "This video's owner doesn't let it play outside YouTube.", cantPlay: "Couldn't play this video right now.",
  theme: 'Theme', mode_system: 'System', mode_light: 'Light', mode_dark: 'Dark', accent: 'Color', language: 'Language', topics: 'Topics', addTopic: 'New topic',
  syncState: 'Sync', syncing: 'Syncing…', offline: 'Offline', syncedWith: (o) => `In step with ${o}`, yourPhone: 'your phone',
  noVaultYet: 'Open FreeVideo on your phone once.', installApp: 'Install as an app', install: 'Install',
  thisComputer: 'This computer', sharedComputer: 'Borrowed computer', sharedComputerHint: 'Closing the tab unlinks this computer.',
  unlinkComputer: 'Unlink this computer', unlink: 'Unlink', editName: 'Name', save: 'Save', cancel: 'Cancel',
  unlinked: 'This computer was unlinked', otherTab: 'FreeVideo is open in another tab', useHere: 'Use here',
  linkTitle: 'Watch on your computer', linkLead: 'Your history, subscriptions and saved videos, the same as on your phone.',
  link1: 'In FreeVideo on your phone, open Library and tap Settings.', link2: 'Under "Computer", tap "Link".', link3: 'Point your phone at this code.',
  linkSafe: 'Only link a code you opened yourself. What goes between your phone and this computer is encrypted.',
  keepLinked: 'Keep this computer linked', keepLinkedHint: "Uncheck it if the computer isn't yours: closing the tab unlinks it.",
  linkExpired: 'The code expired.', linkFailed: "That didn't work.", linkNew: 'Show another code', linkWaiting: 'Waiting for your phone…', linking: 'Linking…',
  noPhone: 'No phone? Use your recovery code', useCode: 'Recovery code', useCodeHint: "The app's code in Profile.",
  recoveryCode: 'Recovery code', enter: 'Sign in', codeShape: 'The code has 16 letters and numbers.', codeWrong: "That code isn't any profile's.",
  codeLocked: 'The code opened the profile but not the vault. Use the QR code from your phone.', slowDown: 'Too many tries. Wait a little.',
  tooManyComputers: 'Too many computers linked. Unlink one from your phone.',
  browser: 'Browser', browserOn: (b, o) => `${b} on ${o}`,
  mobileTitle: 'On a phone, FreeVideo is the app', mobileLead: 'Get FreeVideo for Android and link your computer from it.', mobileGet: 'Get FreeVideo',
};

const ALL = { pt: PT, es: ES, en: EN };

export function pickLang(saved) {
  if (saved && ALL[saved]) return saved;
  const l = (navigator.language || 'pt').slice(0, 2).toLowerCase();
  return ALL[l] ? l : 'pt';
}

export function strings(lang) {
  const table = ALL[lang] ?? PT;
  return (key, ...args) => {
    const v = table[key] ?? PT[key];
    if (typeof v === 'function') return v(...args);
    return v ?? key;
  };
}
