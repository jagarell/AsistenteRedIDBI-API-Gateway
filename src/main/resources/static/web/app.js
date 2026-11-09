/* Network Assistant · panel web (0.1). Misma cuenta y mismos datos que la app: usa los endpoints del gateway. */
const $ = (s, r = document) => r.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const store = {
  get: k => { try { return localStorage.getItem(k); } catch (e) { return null; } },
  set: (k, v) => { try { localStorage.setItem(k, v); } catch (e) { /* sin almacenamiento */ } },
  del: k => { try { localStorage.removeItem(k); } catch (e) { /* sin almacenamiento */ } },
};

/* ---------- sesión y API ---------- */
const S = { token: null, user: null };
const ROL = { ADMINISTRADOR: 'Administrador', SUPERVISOR: 'Supervisor', TECNICO: 'Técnico' };

class ApiError extends Error { constructor(status, msg) { super(msg); this.status = status; } }

async function refresh() {
  const rt = store.get('na.refresh');
  if (!rt) return false;
  const r = await fetch('/api/auth/refresh', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: rt }) });
  if (!r.ok) return false;
  guardar(await r.json());
  return true;
}
function guardar(d) {
  S.token = d.accessToken;
  if (d.refreshToken) store.set('na.refresh', d.refreshToken);
  S.user = { id: d.userId ?? S.user?.id, nombre: d.fullName ?? S.user?.nombre, email: d.email ?? S.user?.email, rol: d.role ?? S.user?.rol };
  store.set('na.user', JSON.stringify(S.user));
}
async function api(path, opt = {}, retry = true) {
  const h = { ...(opt.headers || {}) };
  if (opt.body !== undefined && !(opt.body instanceof FormData)) h['Content-Type'] = 'application/json';
  if (S.token) h.Authorization = 'Bearer ' + S.token;
  const r = await fetch(path, { method: opt.method || 'GET', headers: h, body: opt.body === undefined || opt.body instanceof FormData ? opt.body : JSON.stringify(opt.body) });
  if (r.status === 401 && retry && await refresh()) return api(path, opt, false);
  if (r.status === 401) { cerrar(true); throw new ApiError(401, 'Tu sesión expiró.'); }
  if (!r.ok) {
    let m = 'No se pudo completar la acción.';
    try { m = (await r.json()).message || m; } catch (e) { /* sin cuerpo */ }
    throw new ApiError(r.status, m);
  }
  if (opt.raw) return r;
  const t = await r.text();
  return t ? JSON.parse(t) : null;
}
function cerrar(silencioso) {
  const rt = store.get('na.refresh');
  if (!silencioso && rt && S.token) fetch('/api/auth/logout', { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + S.token }, body: JSON.stringify({ refreshToken: rt }) }).catch(() => {});
  S.token = null; S.user = null; store.del('na.refresh'); store.del('na.user');
  location.hash = '#/login';
}

/* ---------- íconos y utilidades ---------- */
const IC = {
  inicio: 'M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
  panel: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  bell: 'M6 9a6 6 0 0 1 12 0c0 6 3 7 3 7H3s3-1 3-7M10 20a2 2 0 0 0 4 0',
  rep: 'M5 3h10l4 4v14H5zM14 3v5h5M8 13h8M8 17h6',
  preg: 'M12 17v.01M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.7.4-1 1-1 1.7M3 12a9 9 0 1 0 18 0 9 9 0 0 0-18 0',
  team: 'M16 19v-1a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v1M9.5 10a3 3 0 1 0 0-6 3 3 0 0 0 0 6M21 19v-1a4 4 0 0 0-3-3.9M16 4.1a3 3 0 0 1 0 5.8',
  plan: 'M3 7h18v12H3zM3 11h18M7 15h3',
  user: 'M20 21a8 8 0 0 0-16 0M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8',
  chat: 'M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12',
  sparkle: 'M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z',
  eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6',
  eyeoff: 'M3 3l18 18M10.6 6.1A9.7 9.7 0 0 1 12 6c6 0 10 6 10 6a17 17 0 0 1-3.2 3.8M6.5 7.5A17 17 0 0 0 2 12s4 7 10 7a9.7 9.7 0 0 0 4.1-.9',
};
const ico = n => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="${IC[n]}"/></svg>`;
const iniciales = n => String(n || '?').split(/\s+/).filter(Boolean).slice(0, 2).map(x => x[0]).join('').toUpperCase();
const fmtF = s => { try { const d = new Date(s); return isNaN(d) ? esc(s) : d.toLocaleDateString('es-PE', { day: '2-digit', month: '2-digit', year: 'numeric' }); } catch (e) { return esc(s); } };
const chipEstado = e => {
  const k = { 'Completa': 'ok', 'Validada': 'info', 'Pendiente': 'warn', 'En revisión': 'err', 'Por validar': 'lila', 'Borrador': 'lila', 'Rechazada': 'err' }[e] || '';
  return `<span class="chip ${k}">${esc(e || '—')}</span>`;
};
function toast(m) {
  const t = document.createElement('div'); t.className = 'toast'; t.textContent = m; document.body.appendChild(t);
  setTimeout(() => t.remove(), 2600);
}
function dialogo(html, wide) {
  const o = document.createElement('div'); o.className = 'ov';
  o.innerHTML = `<div class="dlg ${wide ? 'wide' : ''}" role="dialog" aria-modal="true">${html}</div>`;
  o.addEventListener('click', e => { if (e.target === o) o.remove(); });
  document.body.appendChild(o);
  const x = () => o.remove();
  o.querySelectorAll('[data-x]').forEach(b => b.addEventListener('click', x));
  return { el: o, close: x };
}
const errTxt = e => (e && e.message) || 'Ocurrió un error. Intenta de nuevo.';

/* ---------- menú por rol (docs §39) ---------- */
const MENU = {
  ADMINISTRADOR: [['inicio', 'Inicio', 'inicio'], ['panel', 'Panel', 'panel'], ['notificaciones', 'Notificaciones', 'bell'], ['chat', 'Nueva evaluación', 'chat'], ['reportes', 'Reportes', 'rep'], ['asistente', 'Asistente IA', 'sparkle'],
    ['preguntas', 'Preguntas y reglas', 'preg'], ['equipo', 'Equipo y accesos', 'team'], ['plan', 'Plan de la empresa', 'plan'], ['cuenta', 'Mi perfil y configuración', 'user']],
  SUPERVISOR: [['panel', 'Panel · mi equipo', 'panel'], ['notificaciones', 'Notificaciones', 'bell'], ['chat', 'Nueva evaluación', 'chat'], ['reportes', 'Reportes', 'rep'], ['asistente', 'Asistente IA', 'sparkle'], ['cuenta', 'Mi perfil y configuración', 'user']],
  TECNICO: [['notificaciones', 'Notificaciones', 'bell'], ['chat', 'Nueva evaluación', 'chat'], ['reportes', 'Reportes', 'rep'], ['asistente', 'Asistente IA', 'sparkle'], ['cuenta', 'Mi perfil y configuración', 'user']],
};
const TITULO = { chat: 'Nueva evaluación', asistente: 'Asistente IA', inicio: 'Inicio', panel: 'Panel', notificaciones: 'Notificaciones', reportes: 'Reportes', equipo: 'Equipo y accesos', cuenta: 'Mi perfil y configuración', preguntas: 'Preguntas y reglas', plan: 'Plan de la empresa' };

/* ---------- pantallas ---------- */
const V = {};

V.login = async root => {
  root.innerHTML = `<div class="auth"><form class="box card" id="f" novalidate>
    <div class="brand" style="padding:0"><i>N</i>Network Assistant</div>
    <h1>Iniciar sesión</h1><p class="muted">Usa la misma cuenta de la app.</p>
    <div class="sso" style="margin:16px 0 10px"><button type="button" class="btn" disabled>Continuar con Gmail · próximamente</button><button type="button" class="btn" disabled>Continuar con Microsoft · próximamente</button></div>
    <div id="e" class="err-t" style="display:none;margin-bottom:10px" role="alert"></div>
    <label class="f" for="em">Correo</label><input id="em" type="email" autocomplete="username" required>
    <label class="f" for="pw" style="margin-top:12px">Contraseña</label>
    <div class="pw"><input id="pw" type="password" autocomplete="current-password" required><button type="button" id="ver" aria-label="Mostrar contraseña" aria-pressed="false">${ico('eye')}</button></div>
    <button class="btn fill" style="width:100%;margin-top:18px;padding:11px" id="go">Entrar</button>
    <p class="tiny" style="margin-top:12px">¿Olvidaste tu contraseña? Pídela desde la app: el administrador te entrega un código.</p>
  </form></div>`;
  $('#ver').onclick = e => {
    const p = $('#pw'), on = p.type === 'password';
    p.type = on ? 'text' : 'password';
    e.currentTarget.setAttribute('aria-pressed', on); e.currentTarget.setAttribute('aria-label', on ? 'Ocultar contraseña' : 'Mostrar contraseña');
    e.currentTarget.innerHTML = ico(on ? 'eyeoff' : 'eye');
  };
  $('#f').onsubmit = async ev => {
    ev.preventDefault();
    const er = $('#e'); er.style.display = 'none';
    const email = $('#em').value.trim(), password = $('#pw').value;
    if (!email || !password) { er.textContent = 'Escribe tu correo y tu contraseña.'; er.style.display = 'block'; return; }
    $('#go').disabled = true;
    try { guardar(await api('/api/auth/login', { method: 'POST', body: { email, password } }, false)); location.hash = '#/' + inicioDe(S.user.rol); }
    catch (e) { er.textContent = e.status === 401 || e.status === 403 ? 'Correo o contraseña incorrectos.' : errTxt(e); er.style.display = 'block'; $('#go').disabled = false; }
  };
};
const inicioDe = rol => rol === 'ADMINISTRADOR' ? 'inicio' : rol === 'SUPERVISOR' ? 'panel' : 'notificaciones';

V.inicio = async main => {
  const [ind, rep] = await Promise.all([api('/api/inicio/indicadores'), api('/api/reportes')]);
  const tono = { por_validar: 'lila', pendientes: 'warn', en_riesgo: 'err' };
  const sub = k => k.clave === 'pendientes' && k.sinTerminar != null ? `${k.sinTerminar} sin terminar` : k.estado || k.criticidad || '';
  const nombre = (S.user.nombre || '').split(' ')[0];
  main.innerHTML = `<div class="page">
    <div><h2 style="font-size:22px">Hola de nuevo, ${esc(nombre)}</h2><p class="muted">Resumen de toda la empresa.</p></div>
    <div class="grid g3">${ind.indicadores.map(k => `<div class="card kpi ${tono[k.clave] || ''}" data-go="${k.clave === 'por_validar' ? 'notificaciones' : 'reportes'}"><span>${esc(k.label)}</span><b>${k.cantidad}</b><small>${esc(sub(k))}</small></div>`).join('')}</div>
    <div class="card"><div class="row"><h2 style="margin:0">Recientes</h2><span class="sp"></span><a href="#/reportes">Ver todo</a></div>${tablaMinutas((rep.minutas || []).slice(0, 8))}</div>
    <div class="grid g2"><div class="card" style="cursor:pointer" data-go="chat"><h2>Nueva evaluación</h2><p class="muted">Inicia la visita guiada con el Chat IA, igual que en la app.</p></div>
    <div class="card" style="cursor:pointer" data-go="asistente"><h2>Asistente IA</h2><p class="muted">Pide reportes, minutas por validar o pendientes. Redactar y actualizar minutas con fotos se hace en la app.</p></div></div>
  </div>`;
  main.querySelectorAll('[data-go]').forEach(e => e.onclick = () => { location.hash = '#/' + e.dataset.go; });
};

function tablaMinutas(ms) {
  if (!ms.length) return '<div class="empty">Aún no hay minutas.</div>';
  return `<div class="tw"><table><thead><tr><th>Local</th><th>Técnico</th><th>Fecha</th><th>Estado</th><th>Puntaje</th></tr></thead><tbody>${ms.map(m =>
    `<tr><td><b>${esc(m.local)}</b></td><td>${esc(m.tecnico)}</td><td>${fmtF(m.fecha)}</td><td>${chipEstado(m.estado)}</td><td>${m.puntaje == null ? '—' : esc(m.puntaje) + ' %'}</td></tr>`).join('')}</tbody></table></div>`;
}

V.panel = async main => {
  const [p, res] = await Promise.all([api('/api/panel'), api('/api/panel/resumen?periodo=hoy').catch(() => null)]);
  const k = p.kpi, dias = Object.entries(p.porDia || {}), max = Math.max(1, ...dias.map(d => d[1]));
  const est = Object.entries(p.porEstado || {});
  main.innerHTML = `<div class="page">
    <div class="grid g4">
      <div class="card kpi"><span>Minutas</span><b>${k.minutas}</b><small>últimos ${p.dias} días</small></div>
      <div class="card kpi lila"><span>Por validar</span><b>${k.porValidar}</b><small>esperan al supervisor</small></div>
      <div class="card kpi warn"><span>Pendientes</span><b>${k.pendientes}</b><small>${k.rechazadas} devueltas a revisión</small></div>
      <div class="card kpi ok"><span>Validadas</span><b>${k.validadas}</b><small>${esc(k.horasAhorradas)} h ahorradas${p.ahorro ? ' · S/ ' + esc(p.ahorro.soles) : ''}</small></div>
    </div>
    <div class="grid g2">
      <div class="card"><h2>Minutas por día</h2><div class="bars">${dias.map(([d, n]) => `<div title="${esc(d)}: ${n}"><i style="height:${Math.round(n / max * 100)}%"></i><small>${esc(d.slice(8))}</small></div>`).join('')}</div></div>
      <div class="card"><h2>Por estado</h2>${est.map(([e, n]) => `<div class="row" style="padding:6px 0"><span class="sp">${esc(e)}</span><b>${n}</b></div>`).join('')}</div>
    </div>
    <div class="card"><h2>Avance de hoy · meta ${p.metaDiaria} por técnico</h2>${(p.avanceHoy || []).length ? (p.avanceHoy).map(a => `<div style="padding:8px 0"><div class="row"><b class="sp">${esc(a.tecnico)}</b><span class="muted">${a.terminadas}/${a.meta} terminadas · ${a.enCurso} en curso</span></div><div class="prog" style="margin-top:6px"><i style="width:${Math.min(100, Math.round(a.terminadas / Math.max(1, a.meta) * 100))}%"></i></div></div>`).join('') : '<div class="empty">Sin técnicos con avance hoy.</div>'}</div>
    ${(p.porSupervisor || []).length ? `<div class="card"><h2>Supervisores</h2><div class="tw"><table><thead><tr><th>Supervisor</th><th>Técnicos</th><th>Por validar</th><th>Horas de espera</th></tr></thead><tbody>${p.porSupervisor.map(s => `<tr><td><b>${esc(s.supervisor)}</b></td><td>${s.tecnicos}</td><td>${s.porValidar}</td><td>${s.horasEspera == null ? '—' : esc(s.horasEspera) + ' h'}</td></tr>`).join('')}</tbody></table></div></div>` : ''}
  </div>`;
};

V.reportes = async main => {
  let vista = null;
  const cargar = async () => {
    const r = await api('/api/reportes' + (vista ? '?vista=' + vista : ''));
    vista = r.vista;
    const ind = r.indicadores || {};
    const tabs = { general: 'General', tecnicos: 'Por técnico', mis: 'Mis minutas' };
    const kpis = vista === 'tecnicos' ? '' : `<div class="grid g4">
        <div class="card kpi ok"><span>Completadas</span><b>${ind.completadas ?? 0}</b><small>de ${ind.total ?? 0}</small></div>
        <div class="card kpi"><span>A tiempo</span><b>${ind.aTiempoPct ?? 0} %</b><small>dentro del plazo</small></div>
        <div class="card kpi"><span>Tiempo promedio</span><b>${ind.tiempoPromedioMin == null ? '—' : ind.tiempoPromedioMin + ' min'}</b><small>por minuta</small></div>
        <div class="card kpi err"><span>En riesgo</span><b>${ind.enRiesgo ?? 0}</b><small>con riesgo detectado</small></div>
      </div>`;
    const cuerpo = vista === 'tecnicos'
      ? `<div class="card"><h2>Por técnico</h2>${(r.tecnicos || []).length ? `<div class="tw"><table><thead><tr><th>Técnico</th><th>Supervisor</th><th>Completadas</th><th>A tiempo</th><th>Tiempo promedio</th><th>En mora</th><th>Devueltas</th><th>En riesgo</th></tr></thead><tbody>${r.tecnicos.map(t =>
          `<tr><td><b>${esc(t.tecnico)}</b></td><td>${esc(t.supervisor || '—')}</td><td>${t.completadas}/${t.total}</td><td>${t.aTiempoPct} %</td><td>${t.tiempoPromedioMin == null ? '—' : esc(t.tiempoPromedioMin) + ' min'}</td><td>${t.enMora}</td><td>${t.devueltas}</td><td>${t.enRiesgo}</td></tr>`).join('')}</tbody></table></div>` : '<div class="empty">No hay técnicos para mostrar.</div>'}</div>`
      : `<div class="card"><h2>Minutas</h2>${(r.minutas || []).length ? `<div class="tw"><table><thead><tr><th>Local</th><th>Técnico</th><th>Supervisor</th><th>Fecha</th><th>Estado</th><th>Plazo</th><th>Puntaje</th><th></th></tr></thead><tbody>${r.minutas.map(m =>
          `<tr><td><b>${esc(m.local)}</b></td><td>${esc(m.tecnico)}</td><td>${esc(m.supervisor)}</td><td>${fmtF(m.fecha)}</td><td>${chipEstado(m.estado)}</td><td>${esc(m.plazo || '—')}</td><td>${m.puntaje == null ? '—' : esc(m.puntaje) + ' %'}</td><td>${m.evaluacionId ? `<button class="btn sm" data-ra="${m.evaluacionId}">Análisis IA</button>` : ''}</td></tr>`).join('')}</tbody></table></div>` : '<div class="empty">No hay minutas para mostrar.</div>'}</div>`;
    main.innerHTML = `<div class="page">
      <div class="row"><div class="tabs">${(r.pestanas || ['general']).map(t => `<button class="tab ${t === vista ? 'on' : ''}" data-t="${t}">${tabs[t] || t}</button>`).join('')}</div><span class="sp"></span>${S.user.rol !== 'TECNICO' ? `<button class="btn" id="plz">${S.user.rol === 'ADMINISTRADOR' ? 'Editar plazos' : 'Ver plazos'}</button>` : ''}</div>
      ${kpis}${cuerpo}</div>`;
    main.querySelectorAll('[data-ra]').forEach(b => b.onclick = () => verAnalisis(b.dataset.ra));
    main.querySelectorAll('[data-t]').forEach(b => b.onclick = () => { vista = b.dataset.t; cargar().catch(e => toast(errTxt(e))); });
    const pz = $('#plz', main); if (pz) pz.onclick = () => plazos(cargar);
  };
  await cargar();
};

V.notificaciones = async main => {
  let filtro = 'ALL';
  const puedeValidar = S.user.rol !== 'TECNICO';
  const cargar = async () => {
    const b = await api('/api/notifications/bandeja');
    const ms = (b.minutas || []).filter(m => filtro === 'ALL' || m.estado === filtro);
    const otros = b.otros || [];
    main.innerHTML = `<div class="page">
      <p class="muted">${esc(b.descripcion || '')}</p>
      <div class="tabs">${Object.entries(b.conteos || {}).map(([k, n]) => `<button class="tab ${k === filtro ? 'on' : ''}" data-f="${esc(k)}">${k === 'ALL' ? 'Todas' : esc(k)} · ${n}</button>`).join('')}</div>
      <div class="card"><h2>Minutas</h2>${ms.length ? `<div class="tw"><table><thead><tr><th>Local</th><th>Técnico</th><th>Fecha</th><th>Estado</th><th></th></tr></thead><tbody>${ms.map(m =>
        `<tr><td><b>${esc(m.local)}</b></td><td>${esc(m.tecnico)}</td><td>${esc(m.fecha)} ${esc(m.hora || '')}</td><td>${chipEstado(m.estado)}</td><td><div class="row" style="justify-content:flex-end">
          <button class="btn sm" data-ver="${m.id}" data-ev="${m.evaluationId ?? ''}">Ver minuta</button>${m.evaluationId ? `<button class="btn sm" data-an="${m.evaluationId}">Análisis IA</button>` : ''}
          ${puedeValidar && m.estado === 'Completa' ? `<button class="btn sm fill" data-val="${m.id}">Validar</button><button class="btn sm danger" data-rec="${m.id}">Devolver</button>` : ''}</div></td></tr>`).join('')}</tbody></table></div>` : '<div class="empty">No hay minutas en este filtro.</div>'}</div>
      ${otros.length ? `<div class="card"><div class="row"><h2 style="margin:0">Avisos</h2><span class="sp"></span><button class="btn sm" id="leer">Marcar todo como leído</button></div>${otros.map(o => `<div style="padding:10px 0;border-bottom:1px solid var(--bd)"><div class="row"><b class="sp">${esc(o.titulo)}</b><span class="tiny">${fmtF(o.fecha)}</span>${o.leida ? '' : '<span class="chip info">Nuevo</span>'}</div><p class="muted">${esc(o.texto)}</p></div>`).join('')}</div>` : ''}
    </div>`;
    main.querySelectorAll('[data-f]').forEach(t => t.onclick = () => { filtro = t.dataset.f; cargar().catch(e => toast(errTxt(e))); });
    main.querySelectorAll('[data-ver]').forEach(t => t.onclick = () => verMinuta(t.dataset.ver, t.dataset.ev));
    main.querySelectorAll('[data-an]').forEach(t => t.onclick = () => verAnalisis(t.dataset.an));
    main.querySelectorAll('[data-val]').forEach(t => t.onclick = async () => {
      t.disabled = true;
      try { await api(`/api/minutas/${t.dataset.val}/validar`, { method: 'POST', body: {} }); toast('Minuta validada.'); await cargar(); } catch (e) { toast(errTxt(e)); t.disabled = false; }
    });
    main.querySelectorAll('[data-rec]').forEach(t => t.onclick = () => rechazar(t.dataset.rec, cargar));
    const l = $('#leer', main); if (l) l.onclick = async () => { try { await api('/api/notifications/read-all', { method: 'POST', body: {} }); await cargar(); } catch (e) { toast(errTxt(e)); } };
  };
  await cargar();
};

function rechazar(id, despues) {
  const d = dialogo(`<h2 style="font-size:18px;margin-bottom:6px">Devolver a revisión</h2><p class="muted" style="margin-bottom:12px">Cuéntale al técnico qué debe corregir.</p>
    <textarea id="mo" rows="4" placeholder="Motivo"></textarea><div id="me" class="err-t" style="display:none;margin-top:10px"></div>
    <div class="row" style="justify-content:flex-end;margin-top:14px"><button class="btn" data-x>Cancelar</button><button class="btn fill" id="ok">Devolver</button></div>`);
  $('#ok', d.el).onclick = async () => {
    const reason = $('#mo', d.el).value.trim();
    if (!reason) { const e = $('#me', d.el); e.textContent = 'Escribe el motivo.'; e.style.display = 'block'; return; }
    try { await api(`/api/minutas/${id}/rechazar`, { method: 'POST', body: { reason } }); d.close(); toast('Minuta devuelta a revisión.'); await despues(); }
    catch (e) { const x = $('#me', d.el); x.textContent = errTxt(e); x.style.display = 'block'; }
  };
}

async function verMinuta(id, ev) {
  if (!ev) { toast('Esta minuta se redactó desde el Asistente IA y aún no tiene documento para ver en la web.'); return; }
  try {
    const r = await api(`/api/evaluations/${ev}/minuta/pdf`, { raw: true });
    const url = URL.createObjectURL(await r.blob());
    const d = dialogo(`<div class="row" style="padding:0 4px 10px"><b class="sp">Minuta</b><a class="btn sm" href="${url}" download="minuta-${esc(id)}.pdf">Descargar</a><button class="btn sm" data-x>Cerrar</button></div><iframe src="${url}" title="Minuta"></iframe>`, true);
    const c = d.close; d.close = () => { URL.revokeObjectURL(url); c(); };
  } catch (e) { toast(errTxt(e)); }
}

V.equipo = async main => {
  if (S.user.rol !== 'ADMINISTRADOR') { main.innerHTML = '<div class="page"><div class="empty">Solo el administrador ve Equipo y accesos.</div></div>'; return; }
  let tab = 'personas';
  const cargar = async () => {
    const [pers, roles, cods] = await Promise.all([api('/api/equipo'), api('/api/roles'), api('/api/recuperaciones').catch(() => [])]);
    const sups = pers.filter(p => p.rol === 'SUPERVISOR');
    main.innerHTML = `<div class="page">
      <div class="row"><div class="tabs"><button class="tab ${tab === 'personas' ? 'on' : ''}" data-t="personas">Personas · ${pers.length}</button><button class="tab ${tab === 'roles' ? 'on' : ''}" data-t="roles">Roles y permisos</button></div><span class="sp"></span>
        ${tab === 'personas' ? `<span class="tiny">Plan Empresa · ${pers.length} de 10 usuarios</span><button class="btn fill" id="ap" ${pers.length >= 10 ? 'disabled' : ''}>Agregar persona</button>` : '<button class="btn fill" id="nrol">Crear un rol</button>'}</div>
      ${tab === 'personas' && cods.length ? `<div class="card" style="border-color:#F57C00"><h2>Cambios de contraseña por atender · ${cods.length}</h2>${cods.map(c => `<div class="row" style="padding:8px 0;border-bottom:1px solid var(--bd)"><span class="sp"><b>${esc(c.nombre)}</b> · ${esc(c.rol)} <span class="tiny">vence en ${c.minutos} min</span></span><button class="btn sm fill" data-cod="${c.id}">Ver código</button><button class="btn sm danger" data-no="${c.id}">No la reconozco</button></div>`).join('')}<p class="tiny" style="margin-top:8px">Dile el número a la persona en persona o por llamada, nunca por chat.</p></div>` : ''}
      ${tab === 'personas' ? `<div class="card"><div class="tw"><table><thead><tr><th>Persona</th><th>Rol</th><th>Supervisor</th><th>Estado</th></tr></thead><tbody>${pers.map(p => `<tr>
        <td><div class="row"><span class="av">${esc(iniciales(p.nombre))}</span><div><b>${esc(p.nombre)}</b><div class="tiny">${esc(p.email)}</div></div></div></td>
        <td><select data-rol="${p.id}">${Object.entries(ROL).map(([k, v]) => `<option value="${k}" ${k === p.rol ? 'selected' : ''}>${v}</option>`).join('')}</select></td>
        <td>${p.rol === 'TECNICO' ? `<select data-sup="${p.id}"><option value="">Sin supervisor</option>${sups.map(s => `<option value="${s.id}" ${s.id === p.supervisorId ? 'selected' : ''}>${esc(s.nombre)}</option>`).join('')}</select>` : '<span class="tiny">—</span>'}</td>
        <td>${p.pidioCodigo ? '<span class="chip warn">Pidió código</span>' : '<span class="chip ok">Con acceso</span>'}</td></tr>`).join('')}</tbody></table></div>
        <p class="tiny" style="margin-top:10px">${pers.length >= 10 ? 'El plan Empresa llegó a sus 10 usuarios: pide más usuarios para agregar personas.' : 'Al agregar una persona le entregas su correo y una contraseña inicial.'}</p></div>`
      : `<div class="grid g3">${roles.map(r => `<div class="card"><div class="row"><h2 style="margin:0">${esc(r.name)}</h2><span class="sp"></span><span class="chip">${r.kind === 'fijo' ? 'Fijo' : r.kind === 'base' ? 'Base' : 'Propio'}</span></div><p class="muted" style="margin:8px 0">${esc(r.description)}</p><p class="tiny">${(r.perms || '').split(',').filter(Boolean).length} permisos activos</p><button class="btn sm" style="margin-top:10px" data-perm="${esc(r.id)}" ${r.id === 'admin' ? 'disabled' : ''}>${r.id === 'admin' ? 'Siempre todos los permisos' : 'Editar permisos'}</button></div>`).join('')}</div>`}
    </div>`;
    main.querySelectorAll('[data-t]').forEach(b => b.onclick = () => { tab = b.dataset.t; cargar().catch(e => toast(errTxt(e))); });
    const ap = $('#ap', main); if (ap) ap.onclick = () => agregarPersona(sups, cargar);
    const nrol = $('#nrol', main); if (nrol) nrol.onclick = () => permisos(null, cargar);
    main.querySelectorAll('[data-perm]').forEach(b => b.onclick = () => permisos(roles.find(r => r.id === b.dataset.perm), cargar));
    main.querySelectorAll('[data-cod]').forEach(b => b.onclick = async () => {
      try { const c = await api(`/api/recuperaciones/${b.dataset.cod}/codigo`); dialogo(`<h2 style="font-size:18px">Código de ${esc(c.nombre)}</h2><p style="font-size:44px;font-weight:700;text-align:center;letter-spacing:.2em;margin:18px 0">${esc(c.numero)}</p><p class="muted" style="text-align:center">Vigente ${c.minutos} min. Díctaselo a la persona; no lo envíes por chat.</p><div class="row" style="justify-content:center;margin-top:14px"><button class="btn" data-x>Cerrar</button></div>`); await cargar(); } catch (e) { toast(errTxt(e)); }
    });
    main.querySelectorAll('[data-no]').forEach(b => b.onclick = async () => {
      try { await api(`/api/recuperaciones/${b.dataset.no}/rechazar`, { method: 'POST', body: {} }); toast('Solicitud rechazada.'); await cargar(); } catch (e) { toast(errTxt(e)); }
    });
    main.querySelectorAll('[data-rol]').forEach(s => s.onchange = async () => {
      try { await api(`/api/equipo/${s.dataset.rol}/rol`, { method: 'PUT', body: { rol: s.value } }); toast('Rol actualizado.'); } catch (e) { toast(errTxt(e)); }
      await cargar();
    });
    main.querySelectorAll('[data-sup]').forEach(s => s.onchange = async () => {
      try { await api(`/api/equipo/${s.dataset.sup}/supervisor`, { method: 'PUT', body: { supervisorId: s.value ? Number(s.value) : null } }); toast('Supervisor asignado.'); } catch (e) { toast(errTxt(e)); }
      await cargar();
    });
  };
  await cargar();
};

V.cuenta = async main => {
  const [p, emp] = await Promise.all([api('/api/profile/me'), S.user.rol === 'ADMINISTRADOR' ? api('/api/empresa') : Promise.resolve(null)]);
  const oscuro = document.documentElement.dataset.theme === 'dark';
  main.innerHTML = `<div class="page">
    <div class="card"><div class="row"><span class="av" style="width:56px;height:56px;font-size:20px">${esc(iniciales(p.fullName))}</span><div><h2 style="margin:0;font-size:20px">${esc(p.fullName)}</h2><p class="muted">${esc(ROL[p.role] || p.role)} · ${esc(p.company || '')}</p></div></div>
      <div class="grid g2" style="margin-top:16px"><div><label class="f">Correo</label><div>${esc(p.email)}</div></div><div><label class="f">Teléfono</label><div>${esc(p.phone || '—')}</div></div><div><label class="f">Ciudad</label><div>${esc(p.city || '—')}</div></div><div><label class="f">Evaluaciones</label><div>${p.totalEvaluations ?? 0}</div></div></div></div>
    ${emp ? `<div class="card"><h2>Empresa</h2><div class="row" style="align-items:flex-end"><div style="flex:1;min-width:220px"><label class="f" for="en">Nombre de la empresa</label><input type="text" id="en" value="${esc(emp.nombre)}" maxlength="150"></div><button class="btn" id="gn">Guardar nombre</button></div>
      <div class="row" style="margin-top:16px"><div style="width:64px;height:64px;border:1px solid var(--bd);border-radius:12px;display:grid;place-items:center;overflow:hidden;background:var(--s2)">${emp.logo ? `<img alt="Logo" src="${esc(emp.logo)}" style="max-width:100%;max-height:100%">` : '<span class="tiny">Sin logo</span>'}</div>
      <div><label class="btn" for="lg" style="display:inline-block">Subir logo</label><input type="file" id="lg" accept="image/png,image/jpeg" class="sr"><p class="tiny" style="margin-top:6px">PNG o JPG de hasta 2 MB.</p></div></div></div>` : ''}
    <div class="card"><h2>Apariencia</h2><label class="row" style="cursor:pointer"><input type="checkbox" id="noche" ${oscuro ? 'checked' : ''}> Modo noche</label></div>
    <div class="card"><h2>Sesión</h2><button class="btn danger" id="salir">Cerrar sesión</button></div>
  </div>`;
  const gn = $('#gn'); if (gn) gn.onclick = async () => { try { await api('/api/empresa', { method: 'PUT', body: { nombre: $('#en').value.trim() } }); toast('Nombre guardado.'); } catch (e) { toast(errTxt(e)); } };
  const lg = $('#lg'); if (lg) lg.onchange = async () => {
    const f = lg.files[0]; if (!f) return;
    if (f.size > 2 * 1024 * 1024) { toast('El logo debe pesar hasta 2 MB.'); return; }
    const url = await new Promise(r => { const fr = new FileReader(); fr.onload = () => r(fr.result); fr.readAsDataURL(f); });
    try { await api('/api/empresa/logo', { method: 'PUT', body: { base64: url, mime: f.type } }); toast('Logo actualizado.'); await V.cuenta(main); } catch (e) { toast(errTxt(e)); }
  };
  $('#noche').onchange = e => { const d = e.target.checked; document.documentElement.dataset.theme = d ? 'dark' : 'light'; store.set('na.theme', d ? 'dark' : 'light'); };
  $('#salir').onclick = () => cerrar(false);
};

V.proximamente = async (main, ruta) => {
  main.innerHTML = `<div class="page"><div class="card"><h2>${esc(TITULO[ruta] || 'Próximamente')}</h2><p class="muted">Este módulo ya está en la app y llegará a la web en una próxima entrega.</p></div></div>`;
};


/* ---------- plazos (Reportes › Editar plazos) ---------- */
async function plazos(despues) {
  let p;
  try { p = await api('/api/panel/plazos'); } catch (e) { toast(errTxt(e)); return; }
  const admin = S.user.rol === 'ADMINISTRADOR';
  const d = dialogo(`<h2 style="font-size:18px;margin-bottom:4px">Plazos de la empresa</h2><p class="muted" style="margin-bottom:12px">Minutos que tomaba una minuta a mano, por tipo de local. Sirven para calcular las horas ahorradas.</p>
    <div id="rb">${Object.entries(p.rubros || {}).map(([k, v]) => `<div class="row" style="margin-bottom:8px"><span class="sp">${esc(k)}</span><input type="number" min="5" max="1440" data-r="${esc(k)}" value="${esc(v)}" style="width:110px" ${admin ? '' : 'disabled'}> min</div>`).join('')}</div>
    <div class="row" style="margin:12px 0"><span class="sp">Costo por hora de técnico (S/)</span><input type="number" min="1" max="1000" step="0.5" id="ch" value="${esc(p.costoHora)}" style="width:110px" ${admin ? '' : 'disabled'}></div>
    <p class="tiny">Meta del técnico: ${esc(p.metaTecnicoMin)} min por minuta · Meta del supervisor: ${esc(p.metaSupervisorHoras)} h para validar.</p>
    <div id="pe" class="err-t" style="display:none;margin-top:10px"></div>
    <div class="row" style="justify-content:flex-end;margin-top:14px"><button class="btn" data-x>${admin ? 'Cancelar' : 'Cerrar'}</button>${admin ? '<button class="btn fill" id="gp">Guardar</button>' : ''}</div>`);
  const g = $('#gp', d.el);
  if (g) g.onclick = async () => {
    const rubros = {}; d.el.querySelectorAll('[data-r]').forEach(i => { rubros[i.dataset.r] = Number(i.value); });
    try { await api('/api/panel/plazos', { method: 'PUT', body: { rubros, costoHora: Number($('#ch', d.el).value) } }); d.close(); toast('Plazos guardados.'); if (despues) await despues(); }
    catch (e) { const x = $('#pe', d.el); x.textContent = errTxt(e); x.style.display = 'block'; }
  };
}

/* ---------- Preguntas y reglas ---------- */
V.preguntas = async main => {
  if (S.user.rol !== 'ADMINISTRADOR') { main.innerHTML = '<div class="page"><div class="empty">Solo el administrador configura las preguntas y reglas.</div></div>'; return; }
  let tab = 'preguntas';
  const TIPOS = ['Aviso', 'Recomendación', 'Observación'];
  const cargar = async () => {
    const [cat, propias, reglas] = await Promise.all([api('/api/flujo/catalogo'), api('/api/preguntas'), api('/api/reglas')]);
    const secs = cat.secciones || [];
    let cuerpo = '';
    if (tab === 'preguntas') {
      cuerpo = `<p class="muted">${cat.total ?? secs.reduce((n, x) => n + x.preguntas.length, 0)} preguntas del Chat IA, por sección y en el orden del motor. Las que agregues tú o el Asistente IA salen aparte.</p>
        ${propias.length ? `<div class="card"><h2>Agregadas por la empresa</h2>${propias.map(p => `<div class="row" style="padding:8px 0;border-bottom:1px solid var(--bd)"><span class="sp">${esc(p.texto || p.id)}</span><span class="chip">${esc(p.seccionId || '—')}</span></div>`).join('')}</div>` : ''}
        ${secs.map(x => `<div class="card"><h2>${esc(x.id)} · ${esc(x.nombre)} <span class="tiny">(${x.preguntas.length})</span></h2>${x.preguntas.map(q => `<div class="row" style="padding:7px 0;border-bottom:1px solid var(--bd)"><span class="tiny" style="width:34px">${q.orden}</span><span class="sp">${esc(q.texto)}</span>${q.evidencia ? '<span class="chip info">Foto</span>' : ''}<span class="chip">${esc(q.tipo)}</span>${q.opcional ? '<span class="chip warn">Opcional</span>' : ''}</div>`).join('')}</div>`).join('')}`;
    } else if (tab === 'reglas') {
      cuerpo = `<div class="row"><p class="muted sp">Reglas que el análisis aplica a cada visita. Puedes desactivarlas, editarlas o crear las tuyas.</p><button class="btn fill" id="nr">Nueva regla</button></div>
        <div class="card"><div class="tw"><table><thead><tr><th>Regla</th><th>Tipo</th><th>Cuándo</th><th>Origen</th><th>Activa</th><th></th></tr></thead><tbody>${reglas.map(r => `<tr>
          <td><b>${esc(r.nombre)}</b><div class="tiny">${esc(r.texto || '')}</div></td><td><span class="chip ${r.tipo === 'Aviso' ? 'warn' : r.tipo === 'Recomendación' ? 'info' : 'lila'}">${esc(r.tipo)}</span></td><td>${esc(r.cuando || '—')}</td>
          <td class="tiny">${r.origen === 'base' ? 'Base' : esc(r.origen || '')}</td>
          <td><input type="checkbox" data-act="${esc(r.id)}" ${r.activa ? 'checked' : ''} aria-label="Activa"></td>
          <td><div class="row"><button class="btn sm" data-ed="${esc(r.id)}">Editar</button>${r.id === 'SISIMP' ? '' : `<button class="btn sm danger" data-del="${esc(r.id)}">Eliminar</button>`}</div></td></tr>`).join('')}</tbody></table></div></div>`;
    } else {
      cuerpo = `<p class="muted">Vista por secciones del flujo del Chat IA, en el orden en que se hacen las preguntas. Las que tienen foto llevan un punto azul.</p>
        <div class="card" style="overflow-x:auto"><div class="row" style="align-items:flex-start;flex-wrap:nowrap;gap:16px">${secs.map(x => `<div style="min-width:170px"><div class="chip info" style="margin-bottom:8px">${esc(x.id)} · ${esc(x.nombre)}</div>${x.preguntas.map(q => `<div title="${esc(q.texto)}" style="font-size:12px;padding:5px 8px;margin-bottom:5px;border:1px solid var(--bd);border-radius:8px;background:var(--s2);${q.evidencia ? 'border-left:3px solid var(--pri)' : ''}">${esc(q.id)}</div>`).join('')}</div>`).join('')}</div></div>`;
    }
    main.innerHTML = `<div class="page"><div class="tabs">${[['preguntas', 'Preguntas'], ['reglas', 'Reglas'], ['mapa', 'Mapa de nodos']].map(([k, v]) => `<button class="tab ${k === tab ? 'on' : ''}" data-t="${k}">${v}</button>`).join('')}</div>${cuerpo}</div>`;
    main.querySelectorAll('[data-t]').forEach(b => b.onclick = () => { tab = b.dataset.t; cargar().catch(e => toast(errTxt(e))); });
    main.querySelectorAll('[data-act]').forEach(c => c.onchange = async () => {
      try { await api('/api/reglas/' + c.dataset.act, { method: 'PUT', body: { activa: c.checked } }); toast(c.checked ? 'Regla activada.' : 'Regla desactivada.'); } catch (e) { toast(errTxt(e)); c.checked = !c.checked; }
    });
    const form = (r, titulo, onSave) => {
      const sis = r && r.id === 'SISIMP';
      const d = dialogo(`<h2 style="font-size:18px;margin-bottom:12px">${titulo}</h2>
        ${sis ? `<label class="f">Nombre del sistema de impresiones</label><input type="text" id="rv" value="${esc(r.valor || '')}">`
        : `<label class="f">Nombre</label><input type="text" id="rn" value="${esc(r?.nombre || '')}"><label class="f" style="margin-top:10px">Tipo</label><select id="rt" style="width:100%">${TIPOS.map(t => `<option ${r?.tipo === t ? 'selected' : ''}>${t}</option>`).join('')}</select>
           <label class="f" style="margin-top:10px">Cuándo aplica</label><input type="text" id="rc" value="${esc(r?.cuando || '')}" placeholder="Ej.: No hay internet contratado">
           <label class="f" style="margin-top:10px">Texto que sale en la minuta</label><textarea id="rx" rows="3">${esc(r?.texto || '')}</textarea>`}
        <div id="re" class="err-t" style="display:none;margin-top:10px"></div>
        <div class="row" style="justify-content:flex-end;margin-top:14px"><button class="btn" data-x>Cancelar</button><button class="btn fill" id="rg">Guardar</button></div>`);
      $('#rg', d.el).onclick = async () => {
        const body = sis ? { valor: $('#rv', d.el).value.trim() } : { nombre: $('#rn', d.el).value.trim(), tipo: $('#rt', d.el).value, cuando: $('#rc', d.el).value.trim(), texto: $('#rx', d.el).value.trim() };
        try { await onSave(body); d.close(); await cargar(); } catch (e) { const x = $('#re', d.el); x.textContent = errTxt(e); x.style.display = 'block'; }
      };
    };
    const nr = $('#nr', main); if (nr) nr.onclick = () => form(null, 'Nueva regla', async b => { await api('/api/reglas', { method: 'POST', body: b }); toast('Regla creada.'); });
    main.querySelectorAll('[data-ed]').forEach(b => b.onclick = () => { const r = reglas.find(x => x.id === b.dataset.ed); form(r, 'Editar regla', async body => { await api('/api/reglas/' + r.id, { method: 'PUT', body }); toast('Regla guardada.'); }); });
    main.querySelectorAll('[data-del]').forEach(b => b.onclick = () => {
      const d = dialogo(`<h2 style="font-size:18px;margin-bottom:6px">Eliminar regla</h2><p class="muted">Se elimina de la empresa y deja de aplicarse en los análisis. Esta acción no se puede deshacer.</p>
        <div class="row" style="justify-content:flex-end;margin-top:14px"><button class="btn" data-x>Cancelar</button><button class="btn danger" id="si">Eliminar</button></div>`);
      $('#si', d.el).onclick = async () => { try { await api('/api/reglas/' + b.dataset.del, { method: 'DELETE' }); d.close(); toast('Regla eliminada.'); await cargar(); } catch (e) { d.close(); toast(errTxt(e)); } };
    });
  };
  await cargar();
};


/* ---------- Equipo y accesos: agregar persona y permisos de los roles ---------- */
const clavePropia = () => { const a = 'abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'; const b = crypto.getRandomValues(new Uint32Array(10)); return Array.from(b, x => a[x % a.length]).join('') + '9'; };

function agregarPersona(sups, despues) {
  const d = dialogo(`<h2 style="font-size:18px;margin-bottom:4px">Agregar persona</h2><p class="muted" style="margin-bottom:12px">Crea su cuenta en la empresa. Después entrégale su correo y la contraseña inicial.</p>
    <label class="f">Nombre completo</label><input type="text" id="an">
    <label class="f" style="margin-top:10px">Correo</label><input type="email" id="ae" autocomplete="off">
    <label class="f" style="margin-top:10px">Celular</label><input type="text" id="at" placeholder="9XXXXXXXX">
    <div class="grid g2" style="margin-top:10px"><div><label class="f">Rol</label><select id="ar" style="width:100%"><option value="TECNICO">Técnico</option><option value="SUPERVISOR">Supervisor</option></select></div>
    <div id="asw"><label class="f">Supervisor</label><select id="as" style="width:100%"><option value="">Sin supervisor</option>${sups.map(x => `<option value="${x.id}">${esc(x.nombre)}</option>`).join('')}</select></div></div>
    <label class="f" style="margin-top:10px">Contraseña inicial</label><div class="row"><input type="text" id="ap1" value="${esc(clavePropia())}" style="flex:1" autocomplete="off"><button type="button" class="btn sm" id="ag">Generar otra</button></div>
    <div id="ax" class="err-t" style="display:none;margin-top:10px"></div>
    <div class="row" style="justify-content:flex-end;margin-top:14px"><button class="btn" data-x>Cancelar</button><button class="btn fill" id="ago">Agregar</button></div>`);
  const sw = () => { $('#asw', d.el).style.display = $('#ar', d.el).value === 'TECNICO' ? '' : 'none'; };
  $('#ar', d.el).onchange = sw; sw();
  $('#ag', d.el).onclick = () => { $('#ap1', d.el).value = clavePropia(); };
  $('#ago', d.el).onclick = async e => {
    const b = e.currentTarget, x = $('#ax', d.el); x.style.display = 'none';
    const body = { nombre: $('#an', d.el).value.trim(), email: $('#ae', d.el).value.trim(), telefono: $('#at', d.el).value.trim(), rol: $('#ar', d.el).value, password: $('#ap1', d.el).value };
    const sid = $('#as', d.el).value; if (body.rol === 'TECNICO' && sid) body.supervisorId = Number(sid);
    b.disabled = true;
    try {
      const r = await api('/api/equipo', { method: 'POST', body });
      d.close();
      const k = dialogo(`<h2 style="font-size:18px;margin-bottom:6px">Persona agregada</h2><p class="muted" style="margin-bottom:12px">Entrégale estos datos en persona o por llamada. Podrá cambiar su contraseña desde Mi perfil.</p>
        <div class="card" style="background:var(--s2)"><div class="tiny">Correo</div><b>${esc(r.email)}</b><div class="tiny" style="margin-top:8px">Contraseña inicial</div><b style="font-size:18px;letter-spacing:.04em">${esc(body.password)}</b></div>
        <div class="row" style="justify-content:flex-end;margin-top:14px"><button class="btn" id="cp">Copiar</button><button class="btn fill" data-x>Listo</button></div>`);
      $('#cp', k.el).onclick = async () => { try { await navigator.clipboard.writeText(`Correo: ${r.email}\nContraseña inicial: ${body.password}`); toast('Datos copiados.'); } catch (er) { toast('No se pudo copiar.'); } };
      k.el.querySelector('[data-x]').addEventListener('click', () => despues());
      await despues();
    } catch (er) { x.textContent = errTxt(er); x.style.display = 'block'; b.disabled = false; }
  };
}

const GRUPOS = [
  ['Minutas', [['m_own', 'Ver sus propias minutas'], ['m_drafts', 'Ver borradores'], ['m_all', 'Ver todas las minutas (General)'], ['m_validated', 'Ver las validadas']]],
  ['Acciones', [['a_create', 'Crear minutas'], ['a_validate', 'Validar'], ['a_reject', 'Devolver a revisión'], ['a_annul', 'Anular'], ['a_map', 'Editar el mapa de red'], ['a_codes', 'Entregar códigos de contraseña']]],
  ['Módulos', [['mod_panel', 'Panel'], ['mod_notif', 'Notificaciones'], ['mod_rep', 'Reportes'], ['mod_equipo', 'Equipo y accesos'], ['mod_preg', 'Preguntas y reglas'], ['mod_plan', 'Plan']]],
  ['Reportes por técnico', [['r_tec_all', 'Por técnico (todos)'], ['r_tec_mine', 'Mis técnicos (los asignados)']]],
  ['Avisos', [['n_own', 'Avisos de sus minutas'], ['n_team', 'Avisos de todo el equipo']]],
];
const BLOQ = { sup: ['a_validate', 'a_reject', 'n_own', 'mod_notif'], tec: ['m_own', 'a_create', 'n_own'] };
const EXCL = { r_tec_all: 'r_tec_mine', r_tec_mine: 'r_tec_all' };

function permisos(rol, despues) {
  const act = new Set(((rol && rol.perms) || '').split(',').filter(Boolean));
  const bloq = new Set(rol ? BLOQ[rol.id] || [] : []);
  const d = dialogo(`<h2 style="font-size:18px;margin-bottom:4px">${rol ? 'Permisos de ' + esc(rol.name) : 'Crear un rol'}</h2>
    <p class="muted" style="margin-bottom:12px">${rol ? 'Los permisos con candado no se pueden quitar a este rol.' : 'Define qué puede hacer este rol. Los cambios valen en la app y en la web.'}</p>
    ${!rol || rol.kind === 'propio' ? `<label class="f">Nombre del rol</label><input type="text" id="rn" value="${esc(rol ? rol.name : '')}" style="margin-bottom:12px">` : ''}
    ${GRUPOS.map(([g, ps]) => `<div style="margin-bottom:12px"><div class="tiny" style="font-weight:700;text-transform:uppercase;letter-spacing:.04em;margin-bottom:4px">${g}</div>${ps.map(([k, l]) => `<label class="row" style="padding:3px 0;cursor:pointer"><input type="checkbox" data-p="${k}" ${act.has(k) ? 'checked' : ''} ${bloq.has(k) ? 'disabled' : ''}><span>${l}${bloq.has(k) ? ' 🔒' : ''}</span></label>`).join('')}</div>`).join('')}
    <div id="pe" class="err-t" style="display:none"></div>
    <div class="row" style="justify-content:flex-end;margin-top:8px"><button class="btn" data-x>Cancelar</button><button class="btn fill" id="pg">Guardar</button></div>`);
  d.el.querySelectorAll('[data-p]').forEach(c => c.onchange = () => {
    const o = EXCL[c.dataset.p]; if (c.checked && o) { const x = d.el.querySelector(`[data-p="${o}"]`); if (x && !x.disabled) x.checked = false; }
  });
  $('#pg', d.el).onclick = async () => {
    const perms = [...d.el.querySelectorAll('[data-p]')].filter(c => c.checked || c.disabled && bloq.has(c.dataset.p)).map(c => c.dataset.p);
    const n = $('#rn', d.el); const name = n ? n.value.trim() : rol.name;
    if (!name) { const x = $('#pe', d.el); x.textContent = 'Escribe el nombre del rol.'; x.style.display = 'block'; return; }
    try {
      if (rol) await api('/api/roles/' + rol.id, { method: 'PUT', body: { name, perms } }); else await api('/api/roles', { method: 'POST', body: { name, perms } });
      d.close(); toast('Permisos guardados.'); await despues();
    } catch (e) { const x = $('#pe', d.el); x.textContent = errTxt(e); x.style.display = 'block'; }
  };
}


/* ---------- Asistente IA (consultas y reportes) ---------- */
const CHAT = { msgs: [], estado: {}, reporte: null };

V.asistente = async main => {
  const SUG = ['Reportes', 'Minutas por validar', 'Mis pendientes de hoy', 'Reporte del mes'];
  const bloque = b => {
    const tabla = b.tabla ? `<div class="tw" style="margin-top:8px"><table><thead><tr>${b.tabla.columnas.map(c => `<th>${esc(c)}</th>`).join('')}</tr></thead><tbody>${b.tabla.filas.map(f => `<tr>${f.map(c => `<td>${esc(c)}</td>`).join('')}</tr>`).join('')}</tbody></table></div>` : '';
    const acc = (b.acciones || []).length ? `<div class="row" style="margin-top:10px">${b.acciones.map((a, i) => `<button class="btn sm ${a.principal ? 'fill' : ''}" data-a='${esc(JSON.stringify(a))}'>${esc(a.label)}</button>`).join('')}</div>` : '';
    return `<div>${b.titulo ? `<b>${esc(b.titulo)}</b>` : ''}${b.texto ? `<p style="white-space:pre-wrap">${esc(b.texto)}</p>` : ''}${tabla}${acc}</div>`;
  };
  const pintar = () => {
    main.innerHTML = `<div class="page" style="display:flex;flex-direction:column;min-height:calc(100vh - 110px)">
      <div id="msgs" style="flex:1">${CHAT.msgs.length ? CHAT.msgs.map(m => m.yo
        ? `<div class="row" style="justify-content:flex-end;margin-bottom:10px"><div class="card" style="background:var(--pri);color:#fff;border-color:var(--pri);max-width:70%;padding:10px 14px">${esc(m.texto)}</div></div>`
        : `<div style="margin-bottom:10px"><div class="card" style="max-width:92%;padding:12px 16px">${m.bloques.map(bloque).join('<hr style="border:0;border-top:1px solid var(--bd);margin:10px 0">')}</div></div>`).join('')
        : `<div class="card"><h2>¿En qué te ayudo?</h2><p class="muted">Pídeme reportes de minutas, las que están por validar o tus pendientes de hoy. Respeto lo que tu rol puede ver.</p><div class="row" style="margin-top:12px">${SUG.map(x => `<button class="tab" data-s="${esc(x)}">${esc(x)}</button>`).join('')}</div></div>`}</div>
      <form id="f" class="row" style="position:sticky;bottom:0;background:var(--bg);padding:10px 0"><input type="text" id="t" placeholder="Escribe o dicta tu pedido" autocomplete="off" style="flex:1">${micBtn('mic')}<button class="btn fill" id="go">Enviar</button></form></div>`;
    window.scrollTo(0, document.body.scrollHeight);
    main.querySelectorAll('[data-s]').forEach(b => b.onclick = () => enviar(b.dataset.s));
    main.querySelectorAll('[data-a]').forEach(b => b.onclick = () => accion(JSON.parse(b.dataset.a)));
    $('#f', main).onsubmit = e => { e.preventDefault(); const t = $('#t', main).value.trim(); if (t) enviar(t); };
    const t = $('#t', main); if (t) t.focus();
    const mc = $('#mic', main); if (mc) mc.onclick = () => dictar(mc, $('#t', main));
  };
  const enviar = async (texto, extra) => {
    CHAT.msgs.push({ yo: true, texto }); pintar();
    const body = { texto, ...CHAT.estado, ...(CHAT.reporte ? { reporte: CHAT.reporte } : {}), ...(extra || {}) };
    try {
      const r = await api('/api/asistente/mensaje', { method: 'POST', body });
      CHAT.estado = r.estado || {};
      const rep = (r.bloques || []).find(b => b.consulta);
      CHAT.reporte = rep ? rep.consulta : null;
      CHAT.msgs.push({ yo: false, bloques: r.bloques || [{ texto: r.texto }] });
    } catch (e) { CHAT.msgs.push({ yo: false, bloques: [{ texto: 'No pude responder: ' + errTxt(e) }] }); }
    pintar();
  };
  const accion = async a => {
    try {
      if (a.id === 'descargar_reporte') {
        const r = await api('/api/asistente/reporte.xlsx', { method: 'POST', body: a.datos.consulta, raw: true });
        const u = URL.createObjectURL(await r.blob()); const l = document.createElement('a'); l.href = u; l.download = 'reporte-minutas.xlsx'; l.click(); setTimeout(() => URL.revokeObjectURL(u), 4000);
      } else if (a.id === 'ver_reportes') location.hash = '#/reportes';
      else if (a.id === 'abrir_preguntas') location.hash = '#/preguntas';
      else if (a.id === 'texto') enviar((a.datos && a.datos.texto) || a.label);
      else if (a.id === 'sugerir') { await api('/api/asistente/sugerencia', { method: 'POST', body: { texto: a.datos.texto } }); toast('Sugerencia enviada al administrador.'); }
      else if (a.id === 'cancelar_programado') { await api(`/api/asistente/programados/${a.datos.id}/cancelar`, { method: 'POST', body: {} }); toast('Envío cancelado.'); }
      else toast('Redactar y actualizar minutas con fotos se hace desde la app por ahora.');
    } catch (e) { toast(errTxt(e)); }
  };
  pintar();
};



/* ---------- dictado por voz (Web Speech API del navegador) ---------- */
const VOZ = window.SpeechRecognition || window.webkitSpeechRecognition;
const micBtn = id => VOZ ? `<button type="button" class="btn sm" id="${id}" aria-label="Dictar" aria-pressed="false" title="Dictar">🎤</button>` : '';
function dictar(btn, input) {
  if (!VOZ) return;
  if (btn._rec) { btn._rec.stop(); return; }
  const r = new VOZ(); r.lang = 'es-PE'; r.interimResults = true; r.continuous = false;
  const base = input.value ? input.value.trim() + ' ' : '';
  btn._rec = r; btn.setAttribute('aria-pressed', 'true'); btn.classList.add('fill');
  r.onresult = e => { input.value = base + Array.from(e.results).map(x => x[0].transcript).join(' '); };
  const fin = () => { btn._rec = null; btn.setAttribute('aria-pressed', 'false'); btn.classList.remove('fill'); input.focus(); };
  r.onend = fin;
  r.onerror = e => { fin(); toast(e.error === 'not-allowed' ? 'Permite el micrófono en el navegador para dictar.' : 'No se pudo dictar. Escribe tu respuesta.'); };
  try { r.start(); } catch (e) { fin(); }
}

/* ---------- mapa de red (editor) ---------- */
const TIPOS_EQ = [['router', 'Router'], ['switch', 'Switch'], ['switch_managed', 'Switch administrable'], ['ap', 'Access point'], ['repeater', 'Repetidor'], ['mesh', 'Mesh'], ['server', 'Servidor'], ['pc', 'PC'], ['laptop', 'Laptop'], ['raspberry', 'Raspberry'], ['printer', 'Impresora'], ['camera', 'Cámara de vigilancia'], ['tablet', 'Sunmi / tablet'], ['otro', 'Otro equipo']];
const INALAMBRICOS = new Set(['access_point', 'ap', 'repeater', 'mesh', 'tablet', 'pos']);

async function verMapa(eid) {
  let m;
  try { const g = await api(`/api/evaluations/${eid}/map`); m = g && g.map; if (!m) { const r = await api(`/api/evaluations/${eid}/map/generate`, { method: 'POST', body: {} }); m = r.map || r; } } catch (e) { toast(errTxt(e)); return; }
  const W = 1000, bw = 150, bh = 56;
  let sel = null, selL = null, selT = null, conectando = false, dirty = false, zoom = 1;
  const H = () => m.canvasH || 800;
  const col = n => n.status === 'broken' ? 'var(--err)' : n.pending ? '#F57C00' : 'var(--pri)';
  const d = dialogo(`<div class="row" style="padding:0 4px 8px"><b>Mapa de red</b><span class="chip warn" id="mdirty" style="display:none">Cambios sin guardar</span><span class="sp"></span><button class="btn sm fill" id="msave" disabled>Guardar mapa</button><button class="btn sm" id="mclose">Cerrar</button></div>
    <div class="row" style="padding:0 4px 8px"><select id="mtipo" aria-label="Tipo de equipo">${TIPOS_EQ.map(([k, l]) => `<option value="${k}">${l}</option>`).join('')}</select><button class="btn sm" id="madd">+ Agregar equipo</button>
      <button class="btn sm" id="mcon" disabled>Conectar con…</button><button class="btn sm danger" id="mdel" disabled>Eliminar</button><button class="btn sm" id="mtxtadd">+ Texto</button><span class="sp"></span>
      <button class="btn sm" id="mzm" aria-label="Alejar">−</button><span class="tiny" id="mzv">100 %</span><button class="btn sm" id="mzp" aria-label="Acercar">+</button><button class="btn sm" id="mzr">Ajustar</button></div>
    <div class="row" id="mprop" style="padding:0 4px 8px;display:none">
      <span id="mfld" class="row" style="display:none"><input type="text" id="mlab" placeholder="Nombre" style="width:150px" aria-label="Nombre del equipo"><input type="text" id="mip" placeholder="IP" style="width:120px" aria-label="IP del equipo"><label class="row tiny"><input type="checkbox" id="mbroken"> Averiado</label><label class="row tiny"><input type="checkbox" id="musb"> USB (sin IP)</label></span>
      <span id="mlnk" class="row" style="display:none"><select id="mstyle" aria-label="Tipo de conexión"><option value="cable">Cable</option><option value="main">Cable principal</option><option value="wifi">WiFi</option><option value="camera">Cámara</option><option value="usb">USB (flecha)</option></select><label class="row tiny"><input type="checkbox" id="mobs"> Con falla (roja)</label></span>
      <span id="mtx" class="row" style="display:none"><input type="text" id="mttxt" placeholder="Texto" style="width:220px" aria-label="Texto del mapa"><select id="mtsize" aria-label="Tamaño"><option value="S">Pequeño</option><option value="M">Mediano</option><option value="L">Grande</option></select></span></div>
    <div class="tiny" id="mayuda" style="padding:0 4px 8px">Arrastra los equipos · toca uno, una línea o un texto para seleccionarlo · naranja punteado = por implementar · violeta punteada = WiFi · roja = con falla · flecha = USB</div>
    <div id="mscroll" style="flex:1;overflow:auto;background:var(--s2);border-radius:10px"><svg id="msvg" style="width:100%;min-width:760px;height:auto;touch-action:none" role="img" aria-label="Topología de red"></svg></div>
    <form id="mcmd" class="row" style="padding:10px 4px 0"><input type="text" id="mtxt" placeholder="Pide un cambio al mapa… (ej.: agrega un switch junto al router)" style="flex:1" autocomplete="off">${micBtn('mmic')}<button class="btn fill">Aplicar</button></form><div id="mrep" class="tiny" style="padding:6px 4px 0"></div>`, true);
  const el = s => $(s, d.el);
  const marcar = v => { dirty = v; el('#mdirty').style.display = v ? '' : 'none'; el('#msave').disabled = !v; el('#mclose').textContent = v ? 'Descartar y cerrar' : 'Cerrar'; };
  const ayuda = t => { el('#mayuda').textContent = t || 'Arrastra los equipos · toca uno, una línea o un texto para seleccionarlo · naranja punteado = por implementar · violeta punteada = WiFi · roja = con falla · flecha = USB'; };
  const dibujar = () => {
    const byId = Object.fromEntries((m.nodes || []).map(n => [n.id, n])), h = H();
    const svg = el('#msvg'); svg.setAttribute('viewBox', `0 0 ${W} ${h}`);
    const pos = n => ({ x: n.x * W, y: n.y * h });
    const links = (m.links || []).map(l => { const a = byId[l.source], b = byId[l.target]; if (!a || !b) return ''; const p = pos(a), q = pos(b), on = l.id === selL;
      const c = l.observed ? 'var(--err)' : l.color || (l.style === 'wifi' ? '#7E57C2' : 'var(--pri)');
      const usb = l.style === 'usb' || l.style === 'dotted', cam = l.style === 'camera';
      return `<g data-l="${esc(l.id)}" style="cursor:pointer"><line x1="${p.x}" y1="${p.y}" x2="${q.x}" y2="${q.y}" stroke="transparent" stroke-width="16"/><line x1="${p.x}" y1="${p.y}" x2="${q.x}" y2="${q.y}" stroke="${on ? 'var(--tx)' : usb && !l.observed ? 'var(--tx2)' : c}" stroke-width="${(l.style === 'main' ? 4 : 2.5) + (on ? 2 : 0)}" ${l.style === 'wifi' || l.style === 'dashed' ? 'stroke-dasharray="7 6"' : cam ? 'stroke-dasharray="3 4"' : ''} ${usb ? 'marker-end="url(#mflecha)"' : ''}/></g>`; }).join('');
    const nodes = (m.nodes || []).map(n => { const p = pos(n), c = col(n), on = n.id === sel;
      return `<g data-n="${esc(n.id)}" style="cursor:${conectando ? 'crosshair' : 'grab'}"><rect x="${p.x - bw / 2}" y="${p.y - bh / 2}" width="${bw}" height="${bh}" rx="12" fill="var(--s1)" stroke="${on ? 'var(--tx)' : c}" stroke-width="${on ? 4 : 2.5}" ${n.pending ? 'stroke-dasharray="6 4"' : ''}/>
        <text x="${p.x}" y="${p.y - 4}" text-anchor="middle" font-size="14" font-weight="700" fill="var(--tx)" pointer-events="none">${esc(String(n.label).slice(0, 20))}</text>
        <text x="${p.x}" y="${p.y + 14}" text-anchor="middle" font-size="11" fill="${n.ipBad ? 'var(--err)' : 'var(--tx2)'}" pointer-events="none">${esc(String(n.usb ? 'USB · sin IP' : (n.ip || n.detail || n.location || (n.pending ? 'Por implementar' : ''))).slice(0, 24))}</text>${n.status === 'broken' ? `<text x="${p.x + bw / 2 - 6}" y="${p.y - bh / 2 + 4}" text-anchor="end" font-size="12" font-weight="700" fill="var(--err)" pointer-events="none">✕ avería</text>` : ''}</g>`; }).join('');
    const tam = { S: 12, M: 15, L: 20 };
    const textos = (m.texts || []).map(t => { const p = pos(t), fs = tam[t.size] || 15, w = Math.max(40, String(t.text).length * fs * 0.58 + 16), on = t.id === selT;
      return `<g data-t="${esc(t.id)}" style="cursor:grab"><rect x="${p.x - w / 2}" y="${p.y - fs}" width="${w}" height="${fs * 1.9}" rx="6" fill="${esc(t.color || '#FFF4CC')}" stroke="${on ? 'var(--tx)' : '#C9B458'}" stroke-width="${on ? 3 : 1}"/><text x="${p.x}" y="${p.y + fs * 0.3}" text-anchor="middle" font-size="${fs}" fill="#333" pointer-events="none">${esc(String(t.text))}</text></g>`; }).join('');
    svg.innerHTML = `<defs><marker id="mflecha" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" style="fill:var(--tx2)"/></marker></defs>` + links + nodes + textos;
    const n = sel && m.nodes.find(x => x.id === sel), lk = selL && (m.links || []).find(x => x.id === selL), tx = selT && (m.texts || []).find(x => x.id === selT);
    el('#mdel').disabled = !(sel || selL || selT); el('#mcon').disabled = !sel; el('#mcon').textContent = conectando ? 'Cancelar conexión' : 'Conectar con…';
    el('#mprop').style.display = (n || lk || tx) ? '' : 'none';
    el('#mfld').style.display = n ? '' : 'none'; el('#mlnk').style.display = lk ? '' : 'none'; el('#mtx').style.display = tx ? '' : 'none';
    if (n) { if (document.activeElement !== el('#mlab')) el('#mlab').value = n.label || ''; if (document.activeElement !== el('#mip')) el('#mip').value = n.ip || ''; el('#mbroken').checked = n.status === 'broken'; el('#musb').checked = !!n.usb; }
    if (lk) { el('#mstyle').value = ({ solid: 'cable', dashed: 'wifi', dotted: 'usb' }[lk.style]) || lk.style || 'cable'; el('#mobs').checked = !!lk.observed; }
    if (tx) { if (document.activeElement !== el('#mttxt')) el('#mttxt').value = tx.text || ''; el('#mtsize').value = tx.size || 'M'; }
    el('#mzv').textContent = Math.round(zoom * 100) + ' %';
  };
  const aSvg = (svg, e) => { const p = svg.createSVGPoint(); p.x = e.clientX; p.y = e.clientY; return p.matrixTransform(svg.getScreenCTM().inverse()); };
  const svg = el('#msvg');
  svg.addEventListener('pointerdown', e => {
    const gl = e.target.closest('[data-l]'), g = e.target.closest('[data-n]'), gt = e.target.closest('[data-t]');
    if (!g && gt) {
      const t = m.texts.find(x => x.id === gt.dataset.t); selT = t.id; sel = null; selL = null; conectando = false; ayuda('Texto seleccionado: arrástralo, edítalo o elimínalo.'); dibujar();
      const h = H(), p0 = aSvg(svg, e), ox = t.x * W - p0.x, oy = t.y * h - p0.y; let movido = false;
      const mover = ev => { const p = aSvg(svg, ev); t.x = Math.min(0.97, Math.max(0.03, (p.x + ox) / W)); t.y = Math.min(0.97, Math.max(0.03, (p.y + oy) / h)); movido = true; dibujar(); };
      const soltar = () => { removeEventListener('pointermove', mover); removeEventListener('pointerup', soltar); if (movido) marcar(true); };
      addEventListener('pointermove', mover); addEventListener('pointerup', soltar); return;
    }
    if (!g && gl) { selL = gl.dataset.l; sel = null; selT = null; conectando = false; ayuda('Conexión seleccionada: cambia su tipo o elimínala.'); dibujar(); return; }
    if (!g) {
      sel = null; selL = null; selT = null; conectando = false; ayuda(); dibujar();
      const sc = el('#mscroll'), x0 = e.clientX, y0 = e.clientY, sl = sc.scrollLeft, st = sc.scrollTop;   // arrastrar el fondo = desplazar el mapa ampliado
      const pan = ev => { sc.scrollLeft = sl - (ev.clientX - x0); sc.scrollTop = st - (ev.clientY - y0); };
      const fin = () => { removeEventListener('pointermove', pan); removeEventListener('pointerup', fin); };
      addEventListener('pointermove', pan); addEventListener('pointerup', fin); return;
    }
    const n = m.nodes.find(x => x.id === g.dataset.n);
    if (conectando && sel && sel !== n.id) {
      const a = m.nodes.find(x => x.id === sel);
      if (!(m.links || []).some(l => (l.source === a.id && l.target === n.id) || (l.source === n.id && l.target === a.id))) {
        const style = a.type === 'camera' || n.type === 'camera' ? 'camera' : (INALAMBRICOS.has(a.type) || INALAMBRICOS.has(n.type)) ? 'wifi' : 'cable';
        m.links = [...(m.links || []), { id: 'wl' + Date.now().toString(36), source: a.id, target: n.id, style, observed: false, color: null }]; marcar(true);
      }
      conectando = false; sel = n.id; selL = null; selT = null; ayuda(); dibujar(); return;
    }
    sel = n.id; selL = null; selT = null; dibujar();
    const h = H(), p0 = aSvg(svg, e), ox = n.x * W - p0.x, oy = n.y * h - p0.y; let movido = false;
    const mover = ev => { const p = aSvg(svg, ev); n.x = Math.min(0.95, Math.max(0.05, (p.x + ox) / W)); n.y = Math.min(0.97, Math.max(0.03, (p.y + oy) / h)); movido = true; dibujar(); };
    const soltar = () => { removeEventListener('pointermove', mover); removeEventListener('pointerup', soltar); if (movido) marcar(true); };
    addEventListener('pointermove', mover); addEventListener('pointerup', soltar);
  });
  el('#madd').onclick = () => {
    const t = el('#mtipo').value, nombre = (TIPOS_EQ.find(x => x[0] === t) || [0, 'Equipo'])[1];
    const n = { id: 'w' + Date.now().toString(36), label: nombre, type: t, x: 0.5, y: 0.5, pending: true, detail: null, color: null, location: null, ip: null, ports: null, status: 'pending', power: null, usb: false, ipBad: false, tier: 2 };
    m.nodes = [...(m.nodes || []), n]; sel = n.id; selL = null; marcar(true); ayuda('Equipo agregado: arrástralo a su lugar y conéctalo con «Conectar con…».'); dibujar();
  };
  el('#mcon').onclick = () => { conectando = !conectando; ayuda(conectando ? 'Toca el equipo con el que quieres conectarlo.' : ''); dibujar(); };
  el('#mdel').onclick = () => {
    if (selT) { m.texts = (m.texts || []).filter(t => t.id !== selT); selT = null; marcar(true); ayuda(); dibujar(); return; }
    if (selL) { m.links = (m.links || []).filter(l => l.id !== selL); selL = null; marcar(true); ayuda(); dibujar(); return; }
    if (!sel) return; const n = m.nodes.find(x => x.id === sel);
    if (n && n.type === 'internet') { toast('El nodo de Internet no se elimina.'); return; }
    m.nodes = m.nodes.filter(x => x.id !== sel); m.links = (m.links || []).filter(l => l.source !== sel && l.target !== sel); sel = null; conectando = false; marcar(true); dibujar();
  };
  const editarCampo = (k, v) => { const n = sel && m.nodes.find(x => x.id === sel); if (!n) return; n[k] = v || null; if (k === 'label') n.label = v || n.label; marcar(true); dibujar(); };
  el('#mlab').oninput = e => { const n = sel && m.nodes.find(x => x.id === sel); if (n && e.target.value.trim()) { n.label = e.target.value.trim(); marcar(true); dibujar(); } };
  el('#mip').oninput = e => editarCampo('ip', e.target.value.trim());
  el('#mbroken').onchange = e => { const n = sel && m.nodes.find(x => x.id === sel); if (!n) return; n.status = e.target.checked ? 'broken' : (n.pending ? 'pending' : 'ok'); marcar(true); dibujar(); };
  el('#musb').onchange = e => { const n = sel && m.nodes.find(x => x.id === sel); if (!n) return; n.usb = e.target.checked; if (n.usb) n.ip = null; marcar(true); dibujar(); };
  el('#mstyle').onchange = e => { const l = selL && (m.links || []).find(x => x.id === selL); if (!l) return; l.style = e.target.value; marcar(true); dibujar(); };
  el('#mobs').onchange = e => { const l = selL && (m.links || []).find(x => x.id === selL); if (!l) return; l.observed = e.target.checked; marcar(true); dibujar(); };
  el('#mtxtadd').onclick = () => { const t = { id: 'wt' + Date.now().toString(36), text: 'Nota', x: 0.5, y: 0.1, size: 'M', color: '#FFF4CC' }; m.texts = [...(m.texts || []), t]; selT = t.id; sel = null; selL = null; marcar(true); ayuda('Texto agregado: escribe su contenido y arrástralo a su lugar.'); dibujar(); el('#mttxt').focus(); el('#mttxt').select(); };
  el('#mttxt').oninput = e => { const t = selT && (m.texts || []).find(x => x.id === selT); if (t && e.target.value.trim()) { t.text = e.target.value; marcar(true); dibujar(); } };
  el('#mtsize').onchange = e => { const t = selT && (m.texts || []).find(x => x.id === selT); if (!t) return; t.size = e.target.value; marcar(true); dibujar(); };
  const aplicarZoom = z => { zoom = Math.min(3, Math.max(0.5, Math.round(z * 100) / 100)); const sv = el('#msvg'); sv.style.width = (zoom * 100) + '%'; sv.style.minWidth = (760 * zoom) + 'px'; el('#mzv').textContent = Math.round(zoom * 100) + ' %'; };
  el('#mzp').onclick = () => aplicarZoom(zoom + 0.25); el('#mzm').onclick = () => aplicarZoom(zoom - 0.25); el('#mzr').onclick = () => aplicarZoom(1);
  el('#mscroll').addEventListener('wheel', e => { if (e.ctrlKey || e.metaKey) { e.preventDefault(); aplicarZoom(zoom + (e.deltaY < 0 ? 0.15 : -0.15)); } }, { passive: false });
  el('#msave').onclick = async () => { try { await api(`/api/evaluations/${eid}/map`, { method: 'PUT', body: m }); marcar(false); toast('Mapa guardado.'); } catch (e) { toast(errTxt(e)); } };
  el('#mclose').onclick = () => d.close();
  el('#mcmd').onsubmit = async e => {
    e.preventDefault(); const t = el('#mtxt').value.trim(); if (!t) return;
    el('#mrep').textContent = 'Aplicando…';
    try { const r = await api(`/api/evaluations/${eid}/map/command`, { method: 'POST', body: { map: m, command: t } }); m = r.map; sel = null; selL = null; selT = null; el('#mtxt').value = ''; el('#mrep').textContent = r.reply || 'Listo.'; marcar(true); dibujar(); }
    catch (er) { el('#mrep').textContent = errTxt(er); }
  };
  const mc = el('#mmic'); if (mc) mc.onclick = () => dictar(mc, el('#mtxt'));
  dibujar();
}

/* ---------- Análisis IA de una evaluación ---------- */
async function verAnalisis(eid) {
  let a;
  try { a = await api(`/api/evaluations/${eid}/analysis`); } catch (e) { toast(errTxt(e)); return; }
  const tono = c => ({ green: 'ok', blue: 'info', orange: 'warn', red: 'err', yellow: 'warn' }[c] || '');
  const barra = v => `<div class="prog" style="min-width:90px"><i style="width:${Math.round((v || 0) * 100)}%;${v < 0.5 ? 'background:#D32F2F' : v < 0.8 ? 'background:#F57C00' : ''}"></i></div>`;
  dialogo(`<div class="row" style="padding:0 4px 10px"><b class="sp">Análisis IA</b><button class="btn sm" data-x>Cerrar</button></div>
    <div style="flex:1;overflow:auto;padding:0 4px">
      <div class="grid g3"><div class="card kpi ${a.globalScore >= 80 ? 'ok' : a.globalScore >= 60 ? 'warn' : 'err'}"><span>Puntaje general</span><b>${esc(a.globalScore)} %</b><small>de la visita</small></div><div class="card kpi"><span>Áreas evaluadas</span><b>${esc(a.evaluatedAreas)}</b><small>del local</small></div><div class="card kpi ${a.attentionRequired ? 'err' : 'ok'}"><span>Requieren atención</span><b>${esc(a.attentionRequired)}</b><small>${a.attentionRequired ? 'revisa los criterios en rojo' : 'todo en orden'}</small></div></div>
      ${(a.results || []).map(r => `<div class="card" style="margin-top:14px"><div class="row"><h2 style="margin:0" class="sp">${esc(r.title)}</h2><span class="chip ${tono(r.color)}">${esc(r.status)}</span><b>${r.score == null ? '—' : esc(r.score) + ' %'}</b></div>
        <p class="muted" style="margin:8px 0">${esc(r.description)}</p>
        <div class="tw"><table><thead><tr><th>Criterio</th><th>Peso</th><th>Cumple</th><th>Detalle</th></tr></thead><tbody>${(r.criteria || []).map(c => `<tr><td><b>${esc(c.name)}</b></td><td>${esc(c.weight)}</td><td>${c.compliance == null ? '<span class="chip">N/A</span>' : `<div class="row" style="flex-wrap:nowrap">${barra(c.compliance)}<span class="tiny" style="white-space:nowrap">${Math.round(c.compliance * 100)} %</span></div>`}</td><td class="muted">${esc(c.detail)}</td></tr>`).join('')}</tbody></table></div></div>`).join('')}
    </div>`, true);
}

/* ---------- Chat IA (evaluación guiada) ---------- */
const CH = { eid: null, state: null, node: null, resp: null, log: [], busy: false, done: false };

V.chat = async main => {
  const nuevaEv = async () => {
    Object.assign(CH, { eid: null, state: null, node: null, resp: null, log: [], busy: false, done: false });
    const ev = await api('/api/evaluations', { method: 'POST', body: {} });
    CH.eid = ev.id;
    aplicar(await api(`/api/evaluations/${ev.id}/chat/start`, { method: 'POST', body: {} }));
  };
  const aplicar = r => {
    CH.resp = r; CH.state = r.state; CH.node = r.node; CH.done = !!r.completed;
    (r.notices || []).forEach(n => CH.log.push({ k: 'n', t: n.text || n.title || '' }));
    (r.crossChecks || []).forEach(t => CH.log.push({ k: 'n', t }));
    persistir();
  };
  // la evaluación en curso queda en este navegador para poder retomarla (el servidor no guarda el estado de la conversación)
  const persistir = () => {
    if (CH.done) { store.del('na.chat'); return; }
    store.set('na.chat', JSON.stringify({ eid: CH.eid, state: CH.state, node: CH.node, prog: CH.resp && CH.resp.progressPercent, log: CH.log.slice(-80), user: S.user && S.user.id }));
  };
  const guardada = () => { try { const g = JSON.parse(store.get('na.chat') || 'null'); return g && g.eid && g.state && g.node && g.user === (S.user && S.user.id) ? g : null; } catch (e) { return null; } };
  const enviar = async (answer, burbuja, extra) => {
    if (CH.busy) return; CH.busy = true; pintar();
    const n = CH.node; if (burbuja !== null) CH.log.push({ k: 'q', t: n.text, id: n.nodeId }, { k: 'a', t: burbuja ?? answer, id: n.nodeId });
    try {
      const r = await api(`/api/evaluations/${CH.eid}/chat/answer`, { method: 'POST', body: { evaluationId: String(CH.eid), state: CH.state, answer, ...(extra || {}) } });
      if (r.validationError) { CH.log.pop(); CH.log.pop(); CH.error = r.validationError; CH.resp = r; } else { CH.error = null; aplicar(r); }
    } catch (e) { CH.error = errTxt(e); }
    CH.busy = false; pintar();
  };
  const subirFotos = async (files, extra) => {
    if (!files.length || CH.busy) return; CH.busy = true; CH.error = null; pintar();
    CH.lastFiles = [...files].slice(0, 3);
    const fd = new FormData(); CH.lastFiles.forEach(f => fd.append('files', f)); fd.append('state', CH.state); fd.append('mode', (extra && extra.mode) || 'subir'); if (extra && extra.nota) fd.append('nota', extra.nota);
    try { const r = await api(`/api/evaluations/${CH.eid}/chat/answer-photos`, { method: 'POST', body: fd }); if (r.validationError) CH.error = r.validationError; else aplicar(r); }
    catch (e) { CH.error = errTxt(e); }
    CH.busy = false; pintar();
  };

  const editar = async id => {
    if (CH.busy) return; CH.busy = true; CH.error = null; pintar();
    try {
      const r = await api(`/api/evaluations/${CH.eid}/chat/edit`, { method: 'POST', body: { evaluationId: String(CH.eid), state: CH.state, nodeId: id, mode: 'reopen' } });
      if (r.validationError) CH.error = r.validationError;
      else { CH.log = CH.log.filter(x => x.id !== id); CH.log.push({ k: 'n', t: 'Editando una respuesta anterior: lo que respondiste después se conserva.' }); aplicar(r); }
    } catch (e) { CH.error = errTxt(e); }
    CH.busy = false; pintar();
  };

  const adjuntar = async (files, tipo) => {
    if (!files.length || CH.busy) return;
    const fd = new FormData(); [...files].slice(0, 3).forEach(f => fd.append('files', f)); fd.append('state', CH.state); if (tipo) fd.append('tipo', tipo);
    toast('Leyendo el documento…');
    let r;
    try { r = await api(`/api/evaluations/${CH.eid}/chat/adjunto`, { method: 'POST', body: fd }); } catch (e) { toast(errTxt(e)); return; }
    if (r.status === 'PDF') { toast('Sube una foto del documento, no un PDF.'); return; }
    if (r.status === 'UNAVAILABLE') { toast('La lectura automática no está disponible ahora. Escribe los datos.'); return; }
    if (r.status === 'TIPO') {
      const d = dialogo(`<h2 style="font-size:18px;margin-bottom:8px">¿Qué documento es?</h2><div class="row">${Object.entries(r.tipos || {}).map(([k, l]) => `<button class="btn" data-ty="${esc(k)}">${esc(l)}</button>`).join('')}</div>`);
      d.el.querySelectorAll('[data-ty]').forEach(b => b.onclick = () => { d.close(); adjuntar(files, b.dataset.ty); });
      return;
    }
    const cs = r.campos || [];
    if (!cs.length) { toast('No pude leer datos en el documento. Escríbelos a mano.'); return; }
    const d = dialogo(`<h2 style="font-size:18px;margin-bottom:4px">Datos leídos del documento</h2><p class="muted" style="margin-bottom:10px">${esc(r.titulo || '')}. Marca lo que quieres usar; puedes corregirlo.</p>${r.nota ? `<p class="tiny" style="margin-bottom:8px">${esc(r.nota)}</p>` : ''}
      ${cs.map((c, i) => `<div style="padding:6px 0;border-bottom:1px solid var(--bd)"><label class="row" style="cursor:pointer"><input type="checkbox" data-ck="${i}" ${c.usar ? 'checked' : ''} ${c.respondido ? 'disabled' : ''}><b>${esc(c.label)}</b>${c.respondido ? '<span class="chip">Ya respondida</span>' : ''}</label><input type="text" data-cv="${i}" value="${esc(c.valor)}" style="margin-top:4px">${c.error ? `<div class="tiny" style="color:var(--err)">${esc(c.error)}</div>` : ''}</div>`).join('')}
      <div class="row" style="justify-content:flex-end;margin-top:14px"><button class="btn" data-x>Cancelar</button><button class="btn fill" id="uso">Usar los datos marcados</button></div>`);
    $('#uso', d.el).onclick = async () => {
      const campos = cs.map((c, i) => ({ nodeId: c.nodeId, valor: d.el.querySelector(`[data-cv="${i}"]`).value.trim(), ok: d.el.querySelector(`[data-ck="${i}"]`).checked })).filter(c => c.ok && c.valor);
      d.close(); if (!campos.length) return;
      CH.busy = true; pintar();
      try { const u = await api(`/api/evaluations/${CH.eid}/chat/adjunto/usar`, { method: 'POST', body: { state: CH.state, campos } }); CH.log.push({ k: 'n', t: `Usé ${campos.length} dato(s) del documento.` }); CH.error = null; aplicar(u); } catch (e) { CH.error = errTxt(e); }
      CH.busy = false; pintar();
    };
  };

  const ubicar = () => {
    const r = $('#gpsr', main), nombre = (CH.log.find(x => x.k === 'a' && x.id === 'P01c') || {}).t || '';
    if (!navigator.geolocation) { r.innerHTML = '<p class="err-t" style="margin-top:8px">Este navegador no puede dar tu ubicación.</p>'; return; }
    r.innerHTML = '<p class="muted" style="margin-top:8px">Buscando tu ubicación…</p>';
    navigator.geolocation.getCurrentPosition(async pos => {
      const { latitude: lat, longitude: lng, accuracy } = pos.coords;
      try {
        const geo = await api('/api/geo/reverse', { method: 'POST', body: { lat, lng, precision_m: accuracy, nombre_local: nombre } });
        CH.gps = { lat, lng, acc: Math.round(accuracy || 0), t: new Date().toLocaleString('es-PE'), geo };
        const co = geo.coincidencia;
        r.innerHTML = `<div class="card" style="margin-top:8px;background:var(--s2)"><b>Ubicación GPS del local</b><div class="tiny">${lat.toFixed(5)}, ${lng.toFixed(5)} · precisión ±${CH.gps.acc} m</div>
          ${geo.direccion ? `<p style="margin-top:6px">${esc(geo.direccion)}</p><div class="tiny">${esc([geo.distrito, geo.departamento].filter(Boolean).join(' / '))}</div>` : '<p class="muted" style="margin-top:6px">No se encontró la dirección: escríbela a mano. El enlace al mapa sí se guarda.</p>'}
          ${co ? `<p class="chip ok" style="margin-top:8px;white-space:normal">Coincide con «${esc(co.nombre)}» a ${esc(co.distancia_m)} m: la ubicación queda confirmada.</p>` : ''}
          <div class="row" style="margin-top:10px">${geo.direccion ? '<button type="button" class="btn sm fill" id="gusar">Usar esta dirección</button>' : ''}<a class="btn sm" target="_blank" rel="noopener" href="${esc(geo.mapa_url || 'https://www.google.com/maps?q=' + lat + ',' + lng)}">Ver en Google Maps</a></div></div>`;
        const u = $('#gusar', main); if (u) u.onclick = () => { $('#tx', main).value = geo.direccion; guardarGps(); };
        guardarGps();
      } catch (e) { r.innerHTML = `<p class="err-t" style="margin-top:8px">${esc(errTxt(e))}</p>`; }
    }, err => { r.innerHTML = `<p class="err-t" style="margin-top:8px">${err.code === 1 ? 'Permite el acceso a tu ubicación en el navegador.' : 'No se pudo obtener tu ubicación.'}</p>`; }, { enableHighAccuracy: true, timeout: 15000 });
  };
  const guardarGps = async () => {
    if (!CH.gps) return;
    try { const cur = await api(`/api/evaluations/${CH.eid}/minuta/ediciones`).catch(() => ({})); await api(`/api/evaluations/${CH.eid}/minuta/ediciones`, { method: 'PUT', body: { ...(cur || {}), gps: CH.gps } }); } catch (e) { /* las coordenadas son un complemento: no frenan la evaluación */ }
  };

  const adjMarkup = n => n.attachHint ? `<div style="margin-top:10px"><label class="btn sm" for="adj" style="display:inline-block">📎 Adjuntar un documento</label><input type="file" id="adj" accept="image/*" multiple class="sr"><span class="tiny"> Ficha RUC, recibo, tarjeta… la IA lee los datos.</span></div>` : '';

  const entrada = n => {
    const t = n.inputType;
    if (n.kind === 'evidence') {
      const ev = n.evidence || {}, rev = ev.status === 'REVIEW';
      if (rev) return `<div class="card" style="background:var(--s2)"><b>${esc(ev.label || 'Esto fue lo que leí')}</b><p class="muted">${esc(ev.message || 'Revisa los datos. Puedes corregirlos antes de continuar.')}</p>
        <div class="grid g2" style="margin-top:8px">${(ev.fields || []).map(f => `<div><label class="f">${esc(f.label)}</label>${f.type === 'sel' ? `<select data-ef="${esc(f.key)}" style="width:100%">${(f.options || []).map(o => `<option value="${esc(o.value)}" ${String((ev.data || {})[f.key] ?? '') === String(o.value) ? 'selected' : ''}>${esc(o.label)}</option>`).join('')}</select>` : `<input type="text" data-ef="${esc(f.key)}" value="${esc((ev.data || {})[f.key] ?? '')}">`}</div>`).join('')}</div>
        <div class="row" style="margin-top:12px"><button class="btn fill" id="evok">Correcto</button><button class="btn" id="evno">No es la foto correcta</button></div></div>`;
      return `<div class="card" style="background:var(--s2)">${ev.message ? `<div class="err-t" style="margin-bottom:10px">${esc(ev.message)}</div>` : ''}<p class="muted" style="margin-bottom:10px">Sube de 1 a ${n.maxFiles || 3} fotos. ${n.skippable ? '' : '<b>Esta foto es obligatoria.</b>'}</p>
        <div class="row"><label class="btn fill" for="fo" style="display:inline-block">Elegir foto</label><input type="file" id="fo" accept="image/*" multiple class="sr">
        ${n.skippable ? '<button class="btn" id="evskip">Saltar</button>' : ''}${ev.canLater ? '<button class="btn" id="evlater">Subir después</button>' : ''}${ev.canForce && CH.lastFiles ? '<button class="btn" id="evforce">Guardar como correcta</button>' : ''}</div></div>`;
    }
    if (n.kind === 'summary') return `<button class="btn fill" id="ok">Continuar al diagnóstico</button>`;
    if (t === 'CHOICE' || t === 'YES_NO') {
      const ops = n.options || [{ value: 'SI', label: 'Sí' }, { value: 'NO', label: 'No' }];
      return `<div class="row">${ops.map(o => `<button class="btn ${n.editing && n.currentValue === o.value ? 'fill' : ''}" data-o="${esc(o.value)}" data-ot="${o.other ? 1 : 0}" data-num="${o.numeric ? 1 : 0}" data-l="${esc(o.label)}">${esc(o.label)}</button>`).join('')}</div><div id="otro" style="display:none;margin-top:10px" class="row"><input type="text" id="otx" placeholder="Escribe cuál es" style="flex:1"><button class="btn fill" id="otok">Enviar</button></div>${adjMarkup(n)}`;
    }
    if (t === 'MULTI_SELECT') {
      const sel = new Set(n.selected || []);
      return `<div>${(n.options || []).map(o => `<label class="row" style="padding:4px 0;cursor:pointer"><input type="checkbox" data-m="${esc(o.value)}" ${sel.has(o.value) ? 'checked' : ''}> ${esc(o.label)}</label>`).join('')}
        <div class="row" style="margin-top:6px"><input type="text" id="mx" placeholder="Otro (escribir)" style="flex:1"><button class="btn sm" id="madd">Agregar</button></div>
        <div class="row" style="margin-top:12px"><button class="btn fill" id="mok">Continuar</button>${n.noneLabel ? `<button class="btn" id="mnone">${esc(n.noneLabel)}</button>` : ''}</div></div>`;
    }
    if (t === 'READ_CARD') return `<div class="grid g2">${(n.fields || []).map(f => `<div><label class="f">${esc(f.label)}</label><input type="text" data-rc="${esc(f.key)}" value="${esc((n.value || {})[f.key] ?? '')}"></div>`).join('')}</div><button class="btn fill" id="rcok" style="margin-top:12px">Correcto</button>`;
    if (t === 'LINKS') return `<p class="muted">Un enlace por línea (Drive, OneDrive, YouTube u otro). Es opcional.</p><textarea id="lk" rows="4" placeholder="https://…"></textarea><div class="row" style="margin-top:10px"><button class="btn fill" id="lkok">Continuar</button></div>`;
    if (t === 'STEPS') return `<div id="ps">${(n.value || []).map((p, i) => `<div class="row" style="margin-bottom:6px"><select data-pp="${i}"><option ${p.p === 'ID' ? 'selected' : ''}>ID</option><option ${p.p === 'Cliente' ? 'selected' : ''}>Cliente</option></select><input type="text" data-pt="${i}" value="${esc(p.t)}" style="flex:1"></div>`).join('')}</div><button class="btn fill" id="psok" style="margin-top:8px">Continuar</button>`;
    if (t === 'WIFI_MEASURE') return `<p class="muted">Mide la señal en cada zona. Puedes dejar en blanco lo que no midas.</p>${(n.zones || []).map((z, i) => `<div class="row" style="margin-bottom:6px"><span style="width:160px">${esc(z)}</span><input type="text" data-wd="${i}" placeholder="dBm" style="width:90px"><input type="text" data-wp="${i}" placeholder="ping ms" style="width:90px"></div>`).join('')}<button class="btn fill" id="wok" style="margin-top:8px">Continuar</button>`;
    // texto, número y fecha
    const gps = n.nodeId === 'P03' ? `<div style="margin-top:10px"><button type="button" class="btn sm" id="gps">📍 Usar mi ubicación (Google Maps)</button><div id="gpsr"></div></div>` : '';
    const tipo = n.keyboard === 'date' ? 'date' : 'text';
    const sug = (n.suggestions || []).length ? `<div class="row" style="margin-top:8px">${n.suggestions.map(x => `<button type="button" class="tab" data-sg="${esc(x)}">${esc(x)}</button>`).join('')}</div>` : '';
    return `<form id="tf"><div class="row"><${n.multiline ? 'textarea rows="3"' : 'input type="' + tipo + '"'} id="tx" placeholder="${esc(n.hint || '')}" value="${esc(n.defaultValue || '')}" style="flex:1" autocomplete="off">${n.multiline ? esc(n.defaultValue || '') + '</textarea>' : ''}${tipo === 'date' ? '' : micBtn('mic')}<button class="btn fill">Enviar</button></div>${sug}${gps}${adjMarkup(n)}</form>`;
  };

  const pintar = () => {
    const n = CH.node, r = CH.resp || {};
    const pct = r.progressPercent ?? 0;
    main.innerHTML = `<div class="page" style="max-width:820px">
      <div class="row"><div class="sp"><b>${esc((n && n.blockLabel) || (CH.done ? 'Evaluación terminada' : 'Evaluación'))}</b><div class="tiny">${n && n.questionNumber ? `Pregunta ${n.questionNumber} de ~${n.questionsTotal}` : ''}</div></div><button class="btn sm danger" id="nv">Empezar otra</button></div>
      <div class="prog"><i style="width:${pct}%"></i></div>
      <div id="log">${CH.log.map(x => x.k === 'q' ? `<div style="margin:10px 0"><div class="card" style="display:inline-block;max-width:88%;padding:10px 14px">${esc(x.t)}</div></div>` : x.k === 'a' ? `<div class="row" style="justify-content:flex-end;margin:6px 0"><button class="btn sm" data-edit="${esc(x.id)}" aria-label="Editar esta respuesta" style="padding:2px 8px">Editar</button><div class="card" style="background:var(--pri);color:#fff;border-color:var(--pri);max-width:70%;padding:10px 14px">${esc(x.t)}</div></div>` : `<div class="chip info" style="margin:6px 0;white-space:normal">${esc(x.t)}</div>`).join('')}</div>
      ${CH.done ? `<div class="card"><h2>¡Listo! La evaluación quedó completa.</h2><p class="muted">La minuta se genera con tus respuestas.</p><div class="row" style="margin-top:10px"><button class="btn fill" id="pdf">Ver minuta (PDF)</button><button class="btn" id="mapa">Ver mapa de red</button><button class="btn" id="anal">Ver análisis IA</button><a class="btn" href="#/reportes">Ir a Reportes</a></div></div>`
      : n ? `<div class="card"><p style="font-weight:600;margin-bottom:12px;white-space:pre-wrap">${esc(n.text)}</p>${CH.error ? `<div class="err-t" style="margin-bottom:10px">${esc(CH.error)}</div>` : ''}${CH.busy ? '<p class="muted">Procesando…</p>' : entrada(n)}</div>` : '<div class="card"><p class="muted">Cargando…</p></div>'}</div>`;
    window.scrollTo(0, document.body.scrollHeight);
    ligar(n);
  };
  const ligar = n => {
    $('#nv', main).onclick = async () => { try { await nuevaEv(); pintar(); } catch (e) { toast(errTxt(e)); } };
    const pdf = $('#pdf', main); if (pdf) pdf.onclick = () => verMinuta(CH.eid, CH.eid);
    const mp = $('#mapa', main); if (mp) mp.onclick = () => verMapa(CH.eid);
    const an = $('#anal', main); if (an) an.onclick = () => verAnalisis(CH.eid);
    main.querySelectorAll('[data-edit]').forEach(b => b.onclick = () => editar(b.dataset.edit));
    if (!n || CH.busy || CH.done) return;
    const q = (s, f) => { const e = $(s, main); if (e) e.onclick = f; };
    if (n.kind === 'evidence') {
      const fo = $('#fo', main); if (fo) fo.onchange = () => subirFotos(fo.files);
      q('#evskip', () => enviar('@SKIP', 'Saltar'));
      q('#evlater', () => enviar('@LATER', 'Subir después'));
      q('#evforce', () => {
        const d = dialogo(`<h2 style="font-size:18px;margin-bottom:6px">Guardar como correcta</h2><p class="muted" style="margin-bottom:10px">La IA no pudo confirmar la foto, pero tú sabes que es la correcta. Queda marcada como guardada por el técnico.</p><label class="f">Nota (opcional)</label><textarea id="fn" rows="3"></textarea><div class="row" style="justify-content:flex-end;margin-top:12px"><button class="btn" data-x>Cancelar</button><button class="btn fill" id="fok">Guardar como correcta</button></div>`);
        $('#fok', d.el).onclick = () => { const nota = $('#fn', d.el).value.trim(); d.close(); subirFotos(CH.lastFiles, { mode: 'forzar', nota }); };
      });
      q('#evno', () => enviar('@WRONG', null));
      q('#evok', () => { const d = {}; main.querySelectorAll('[data-ef]').forEach(i => { d[i.dataset.ef] = i.value; }); enviar(JSON.stringify(d), 'Correcto'); });
      return;
    }
    if (n.kind === 'summary') { q('#ok', () => enviar('OK', 'Continuar al diagnóstico')); return; }
    const t = n.inputType;
    if (t === 'CHOICE' || t === 'YES_NO') {
      main.querySelectorAll('[data-o]').forEach(b => b.onclick = () => {
        if (b.dataset.ot === '1') { $('#otro', main).style.display = 'flex'; $('#otx', main).dataset.v = b.dataset.o; $('#otx', main).dataset.num = b.dataset.num; $('#otx', main).focus(); return; }
        enviar(b.dataset.o, b.dataset.l);
      });
      const aj2 = $('#adj', main); if (aj2) aj2.onchange = () => adjuntar(aj2.files, n.attachHint);
      q('#otok', () => { const x = $('#otx', main), v = x.value.trim(); if (v) enviar('OTRO:' + v, v); });
    } else if (t === 'MULTI_SELECT') {
      q('#madd', () => { const x = $('#mx', main), v = x.value.trim(); if (!v) return; const l = document.createElement('label'); l.className = 'row'; l.style.cssText = 'padding:4px 0'; l.innerHTML = `<input type="checkbox" data-m="${esc(v)}" checked> ${esc(v)}`; x.parentElement.before(l); x.value = ''; });
      q('#mok', () => { const a = [...main.querySelectorAll('[data-m]')].filter(c => c.checked).map(c => c.dataset.m); enviar(JSON.stringify(a), a.join(', ') || 'Ninguno'); });
      q('#mnone', () => enviar('@NONE', n.noneLabel));
    } else if (t === 'READ_CARD') {
      q('#rcok', () => { const d = {}; main.querySelectorAll('[data-rc]').forEach(i => { d[i.dataset.rc] = i.value; }); enviar(JSON.stringify(d), 'Correcto'); });
    } else if (t === 'LINKS') {
      q('#lkok', () => { const a = $('#lk', main).value.split('\n').map(x => x.trim()).filter(Boolean).map((u, i) => ({ d: 'Video ' + (i + 1), u })); enviar(JSON.stringify(a), a.length ? a.length + ' enlace(s)' : 'Continuar sin enlaces'); });
    } else if (t === 'STEPS') {
      q('#psok', () => { const a = [...main.querySelectorAll('[data-pt]')].map(i => ({ p: main.querySelector(`[data-pp="${i.dataset.pt}"]`).value, t: i.value })); enviar(JSON.stringify(a), 'Pasos confirmados'); });
    } else if (t === 'WIFI_MEASURE') {
      q('#wok', () => { const d = {}; (n.zones || []).forEach((z, i) => { const dbm = main.querySelector(`[data-wd="${i}"]`).value, pg = main.querySelector(`[data-wp="${i}"]`).value; if (dbm || pg) d[z] = { dbm, ping: pg }; }); enviar(JSON.stringify(d), 'Medición enviada'); });
    } else {
      const f = $('#tf', main); if (f) { f.onsubmit = e => { e.preventDefault(); const v = $('#tx', main).value.trim(); enviar(v, v); }; $('#tx', main).focus(); }
      main.querySelectorAll('[data-sg]').forEach(b => b.onclick = () => enviar(b.dataset.sg, b.dataset.sg));
      const g = $('#gps', main); if (g) g.onclick = ubicar;
      const mc = $('#mic', main); if (mc) mc.onclick = () => dictar(mc, $('#tx', main));
      const aj = $('#adj', main); if (aj) aj.onchange = () => adjuntar(aj.files, n.attachHint);
    }
  };

  // retomar la evaluación a medias o empezar una nueva, salvo que ya haya una en curso en esta pestaña
  if (!(CH.eid && CH.node)) {
    const g = guardada();
    if (g) {
      main.innerHTML = `<div class="page" style="max-width:640px"><div class="card"><h2>Tienes una evaluación a medias</h2><p class="muted">Va en ${esc(g.prog ?? 0)} %. ¿La retomas o empiezas una nueva?</p><div class="row" style="margin-top:12px"><button class="btn fill" id="rt">Continuar la anterior</button><button class="btn" id="nw">Empezar una nueva</button></div></div></div>`;
      $('#rt', main).onclick = () => { Object.assign(CH, { eid: g.eid, state: g.state, node: g.node, resp: { progressPercent: g.prog }, log: [...g.log, { k: 'n', t: 'Retomaste la evaluación que dejaste a medias.' }], busy: false, done: false, error: null }); pintar(); };
      $('#nw', main).onclick = async () => { try { await nuevaEv(); pintar(); } catch (e) { toast(errTxt(e)); } };
      return;
    }
    await nuevaEv();
  }
  pintar();
};

/* ---------- Plan de la empresa ---------- */
V.plan = async main => {
  const [emp, pers] = await Promise.all([api('/api/empresa'), api('/api/equipo')]);
  const n = pers.length, tope = 10, pct = Math.min(100, Math.round(n / tope * 100));
  main.innerHTML = `<div class="page">
    <div class="card"><span class="tiny" style="letter-spacing:.04em;text-transform:uppercase;font-weight:600">Plan actual</span>
      <div class="row" style="margin-top:4px"><h2 style="margin:0;font-size:20px">${esc(emp.nombre || 'Empresa')} · Plan Empresa</h2><span class="chip ok">Plan actual</span></div>
      <p class="muted" style="margin:6px 0 12px">Hasta ${tope} usuarios, contando al administrador. ${emp.enProduccion ? 'En producción desde el ' + esc(emp.produccionDesde || '—') + '.' : 'Cuenta DEMO: aún no pasa a producción.'}</p>
      <div class="prog"><i style="width:${pct}%"></i></div><p class="tiny" style="margin-top:6px">${n} de ${tope} usuarios</p></div>
    <div class="grid g3">
      <div class="card"><h2>Free</h2><p class="muted">Para probar la herramienta con un solo usuario.</p><span class="chip" style="margin-top:10px">Próximamente</span></div>
      <div class="card" style="border-color:var(--pri)"><h2>Empresa</h2><p class="muted">Todo el flujo, el panel y hasta ${tope} usuarios.</p><span class="chip ok" style="margin-top:10px">Disponible · plan actual</span></div>
      <div class="card"><h2>Empresarial</h2><p class="muted">A medida: más usuarios e integraciones.</p><span class="chip" style="margin-top:10px">Próximamente</span></div>
    </div>
    <p class="tiny">Cambiar de plan y pedir más usuarios se coordina con el equipo de ID.</p></div>`;
};

/* ---------- enrutador ---------- */
async function pintar() {
  const root = $('#root');
  const ruta = (location.hash.replace(/^#\//, '') || '').split('?')[0];
  if (!S.token) {
    // sesión guardada: el token de acceso vive solo en memoria; se renueva con el refresh token
    if (!S.user && store.get('na.refresh')) {
      try { S.user = JSON.parse(store.get('na.user') || 'null'); } catch (e) { S.user = null; }
      if (!(await refresh())) S.user = null;
    }
  }
  if (!S.token) { if (ruta !== 'login') { location.hash = '#/login'; return; } return V.login(root); }
  if (ruta === 'login' || !ruta) { location.hash = '#/' + inicioDe(S.user.rol); return; }
  const items = MENU[S.user.rol] || MENU.TECNICO;
  const it = items.find(i => i[0] === ruta);
  if (!it) { location.hash = '#/' + inicioDe(S.user.rol); return; }
  root.innerHTML = `<div class="shell"><aside class="side"><div class="brand"><i>N</i>Network Assistant</div>
    <nav class="nav" aria-label="Menú">${items.map(i => i[3] ? `<span class="soon">${ico(i[2])}${esc(i[1])}<small>Pronto</small></span>` : `<a href="#/${i[0]}" class="${i[0] === ruta ? 'on' : ''}">${ico(i[2])}${esc(i[1])}</a>`).join('')}</nav>
    <div class="sidefoot"><button class="logout" id="out">Cerrar sesión</button><button class="me" id="me"><span class="av">${esc(iniciales(S.user.nombre))}</span><span><b>${esc(S.user.nombre)}</b><span>${esc(ROL[S.user.rol] || '')}</span></span></button></div></aside>
    <section class="main"><header class="bar"><h1>${esc(it[1])}</h1><span class="sync">✓ Sincronizado con la app</span></header><div id="v"><div class="page"><p class="muted">Cargando…</p></div></div></section></div>`;
  $('#out').onclick = () => cerrar(false);
  $('#me').onclick = () => { location.hash = '#/cuenta'; };
  const v = $('#v');
  try { await (it[3] ? V.proximamente(v, ruta) : V[ruta](v, ruta)); }
  catch (e) { if (e.status !== 401) v.innerHTML = `<div class="page"><div class="card"><h2>No se pudo cargar</h2><p class="muted">${esc(errTxt(e))}</p><button class="btn" id="re" style="margin-top:12px">Reintentar</button></div></div>`; const r = $('#re'); if (r) r.onclick = pintar; }
}
addEventListener('hashchange', pintar);
pintar();
