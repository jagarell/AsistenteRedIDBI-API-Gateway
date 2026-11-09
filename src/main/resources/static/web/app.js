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
  ADMINISTRADOR: [['inicio', 'Inicio', 'inicio'], ['panel', 'Panel', 'panel'], ['notificaciones', 'Notificaciones', 'bell'], ['reportes', 'Reportes', 'rep'],
    ['preguntas', 'Preguntas y reglas', 'preg', 1], ['equipo', 'Equipo y accesos', 'team'], ['plan', 'Plan de la empresa', 'plan', 1], ['cuenta', 'Mi perfil y configuración', 'user']],
  SUPERVISOR: [['panel', 'Panel · mi equipo', 'panel'], ['notificaciones', 'Notificaciones', 'bell'], ['reportes', 'Reportes', 'rep'], ['cuenta', 'Mi perfil y configuración', 'user']],
  TECNICO: [['notificaciones', 'Notificaciones', 'bell'], ['reportes', 'Reportes', 'rep'], ['cuenta', 'Mi perfil y configuración', 'user']],
};
const TITULO = { inicio: 'Inicio', panel: 'Panel', notificaciones: 'Notificaciones', reportes: 'Reportes', equipo: 'Equipo y accesos', cuenta: 'Mi perfil y configuración', preguntas: 'Preguntas y reglas', plan: 'Plan de la empresa' };

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
    <div class="grid g2"><div class="card"><h2>Nueva evaluación</h2><p class="muted">Las evaluaciones con el Chat IA se hacen desde la app del celular.</p></div>
    <div class="card"><h2>Asistente IA</h2><p class="muted">Redacta o actualiza minutas y da reportes desde la app. En la web llegará en una próxima entrega.</p></div></div>
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
      : `<div class="card"><h2>Minutas</h2>${(r.minutas || []).length ? `<div class="tw"><table><thead><tr><th>Local</th><th>Técnico</th><th>Supervisor</th><th>Fecha</th><th>Estado</th><th>Plazo</th><th>Puntaje</th></tr></thead><tbody>${r.minutas.map(m =>
          `<tr><td><b>${esc(m.local)}</b></td><td>${esc(m.tecnico)}</td><td>${esc(m.supervisor)}</td><td>${fmtF(m.fecha)}</td><td>${chipEstado(m.estado)}</td><td>${esc(m.plazo || '—')}</td><td>${m.puntaje == null ? '—' : esc(m.puntaje) + ' %'}</td></tr>`).join('')}</tbody></table></div>` : '<div class="empty">No hay minutas para mostrar.</div>'}</div>`;
    main.innerHTML = `<div class="page">
      <div class="tabs">${(r.pestanas || ['general']).map(t => `<button class="tab ${t === vista ? 'on' : ''}" data-t="${t}">${tabs[t] || t}</button>`).join('')}</div>
      ${kpis}${cuerpo}</div>`;
    main.querySelectorAll('[data-t]').forEach(b => b.onclick = () => { vista = b.dataset.t; cargar().catch(e => toast(errTxt(e))); });
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
          <button class="btn sm" data-ver="${m.id}" data-ev="${m.evaluationId ?? ''}">Ver minuta</button>
          ${puedeValidar && m.estado === 'Completa' ? `<button class="btn sm fill" data-val="${m.id}">Validar</button><button class="btn sm danger" data-rec="${m.id}">Devolver</button>` : ''}</div></td></tr>`).join('')}</tbody></table></div>` : '<div class="empty">No hay minutas en este filtro.</div>'}</div>
      ${otros.length ? `<div class="card"><div class="row"><h2 style="margin:0">Avisos</h2><span class="sp"></span><button class="btn sm" id="leer">Marcar todo como leído</button></div>${otros.map(o => `<div style="padding:10px 0;border-bottom:1px solid var(--bd)"><div class="row"><b class="sp">${esc(o.titulo)}</b><span class="tiny">${fmtF(o.fecha)}</span>${o.leida ? '' : '<span class="chip info">Nuevo</span>'}</div><p class="muted">${esc(o.texto)}</p></div>`).join('')}</div>` : ''}
    </div>`;
    main.querySelectorAll('[data-f]').forEach(t => t.onclick = () => { filtro = t.dataset.f; cargar().catch(e => toast(errTxt(e))); });
    main.querySelectorAll('[data-ver]').forEach(t => t.onclick = () => verMinuta(t.dataset.ver, t.dataset.ev));
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
    const [pers, roles] = await Promise.all([api('/api/equipo'), api('/api/roles')]);
    const sups = pers.filter(p => p.rol === 'SUPERVISOR');
    main.innerHTML = `<div class="page">
      <div class="tabs"><button class="tab ${tab === 'personas' ? 'on' : ''}" data-t="personas">Personas · ${pers.length}</button><button class="tab ${tab === 'roles' ? 'on' : ''}" data-t="roles">Roles y permisos</button></div>
      ${tab === 'personas' ? `<div class="card"><div class="tw"><table><thead><tr><th>Persona</th><th>Rol</th><th>Supervisor</th><th>Estado</th></tr></thead><tbody>${pers.map(p => `<tr>
        <td><div class="row"><span class="av">${esc(iniciales(p.nombre))}</span><div><b>${esc(p.nombre)}</b><div class="tiny">${esc(p.email)}</div></div></div></td>
        <td><select data-rol="${p.id}">${Object.entries(ROL).map(([k, v]) => `<option value="${k}" ${k === p.rol ? 'selected' : ''}>${v}</option>`).join('')}</select></td>
        <td>${p.rol === 'TECNICO' ? `<select data-sup="${p.id}"><option value="">Sin supervisor</option>${sups.map(s => `<option value="${s.id}" ${s.id === p.supervisorId ? 'selected' : ''}>${esc(s.nombre)}</option>`).join('')}</select>` : '<span class="tiny">—</span>'}</td>
        <td>${p.pidioCodigo ? '<span class="chip warn">Pidió código</span>' : '<span class="chip ok">Con acceso</span>'}</td></tr>`).join('')}</tbody></table></div>
        <p class="tiny" style="margin-top:10px">Agregar personas y entregar códigos de contraseña llegan en la próxima entrega de la web.</p></div>`
      : `<div class="grid g3">${roles.map(r => `<div class="card"><div class="row"><h2 style="margin:0">${esc(r.name)}</h2><span class="sp"></span><span class="chip">${r.kind === 'fijo' ? 'Fijo' : r.kind === 'base' ? 'Base' : 'Propio'}</span></div><p class="muted" style="margin:8px 0">${esc(r.description)}</p><p class="tiny">${(r.perms || '').split(',').filter(Boolean).length} permisos activos</p></div>`).join('')}</div>`}
    </div>`;
    main.querySelectorAll('[data-t]').forEach(b => b.onclick = () => { tab = b.dataset.t; cargar().catch(e => toast(errTxt(e))); });
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
  const p = await api('/api/profile/me');
  const oscuro = document.documentElement.dataset.theme === 'dark';
  main.innerHTML = `<div class="page">
    <div class="card"><div class="row"><span class="av" style="width:56px;height:56px;font-size:20px">${esc(iniciales(p.fullName))}</span><div><h2 style="margin:0;font-size:20px">${esc(p.fullName)}</h2><p class="muted">${esc(ROL[p.role] || p.role)} · ${esc(p.company || '')}</p></div></div>
      <div class="grid g2" style="margin-top:16px"><div><label class="f">Correo</label><div>${esc(p.email)}</div></div><div><label class="f">Teléfono</label><div>${esc(p.phone || '—')}</div></div><div><label class="f">Ciudad</label><div>${esc(p.city || '—')}</div></div><div><label class="f">Evaluaciones</label><div>${p.totalEvaluations ?? 0}</div></div></div></div>
    <div class="card"><h2>Apariencia</h2><label class="row" style="cursor:pointer"><input type="checkbox" id="noche" ${oscuro ? 'checked' : ''}> Modo noche</label></div>
    <div class="card"><h2>Sesión</h2><button class="btn danger" id="salir">Cerrar sesión</button></div>
  </div>`;
  $('#noche').onchange = e => { const d = e.target.checked; document.documentElement.dataset.theme = d ? 'dark' : 'light'; store.set('na.theme', d ? 'dark' : 'light'); };
  $('#salir').onclick = () => cerrar(false);
};

V.proximamente = async (main, ruta) => {
  main.innerHTML = `<div class="page"><div class="card"><h2>${esc(TITULO[ruta] || 'Próximamente')}</h2><p class="muted">Este módulo ya está en la app y llegará a la web en una próxima entrega.</p></div></div>`;
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
