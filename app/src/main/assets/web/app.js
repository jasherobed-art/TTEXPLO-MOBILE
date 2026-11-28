(function () {
  'use strict';

  var $ = function (s) { return document.querySelector(s); };
  var state = {
    vales: [], tipo: 'TODOS', q: '', sel: new Set(),
    abierto: null, detalle: null, draft: null, editando: false, foco: null,
    edits: {}, delKeys: []
  };
  var TIPOS = [
    ['TODOS', 'Todos'], ['ENTRADA', 'Entradas'], ['DESPACHO', 'Salidas'],
    ['ACCESORIOS', 'Accesorios'], ['DEVOLUCION', 'Devoluciones'], ['CONTEO', 'Conteos']
  ];
  var CODE = { ENTRADA: 'PRV', DESPACHO: 'REF', ACCESORIOS: 'ACC', DEVOLUCION: 'DEV', CONTEO: 'CAJ' };
  var CLASE = { ENTRADA: 't--entrada', DESPACHO: 't--salida', ACCESORIOS: 't--acc', DEVOLUCION: 't--dev', CONTEO: 't--conteo' };
  var SING = { ENTRADA: 'entrada', DESPACHO: 'salida', ACCESORIOS: 'accesorio', DEVOLUCION: 'devolución', CONTEO: 'conteo' };
  var ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
  var catPorId = {};
  var prodEditando = null;

  function esc(v) { return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) { return ESC[c]; }); }
  function norm(v) {
    return String(v == null ? '' : v).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  }
  function clone(o) { return JSON.parse(JSON.stringify(o)); }
  function nowStamp() {
    var d = new Date();
    var p = function (n) { return String(n).padStart(2, '0'); };
    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' +
      p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
  }

  // Todo renglón del .txt lleva "yyyy-MM-dd HH:mm:ss"; acepta también solo la hora y le pone la fecha del vale.
  function normStamp(value, fecha) {
    var p = function (n) { return String(n).padStart(2, '0'); };
    var v = String(value == null ? '' : value).trim();
    if (!v) return nowStamp();
    var full = v.match(/^(\d{4})-(\d{1,2})-(\d{1,2})[ T](\d{1,2}):(\d{2})(?::(\d{2}))?$/);
    if (full) {
      return full[1] + '-' + p(full[2]) + '-' + p(full[3]) + ' ' +
        p(full[4]) + ':' + p(full[5]) + ':' + p(full[6] || '0');
    }
    var hora = v.match(/^(\d{1,2}):(\d{2})(?::(\d{2}))?$/);
    if (!hora) return null;
    var f = String(fecha || '').match(/^(\d{4})-(\d{2})-(\d{2})/);
    var base = f ? f[1] + '-' + f[2] + '-' + f[3] : nowStamp().slice(0, 10);
    return base + ' ' + p(hora[1]) + ':' + p(hora[2]) + ':' + p(hora[3] || '0');
  }

  async function api(url) {
    var r = await fetch(url, { headers: { Accept: 'application/json' }, cache: 'no-store' });
    if (r.status === 401) { location.href = '/login'; throw new Error('Sesión expirada'); }
    if (!r.ok) throw new Error('No se pudo cargar (' + r.status + ')');
    return r.json();
  }

  function toast(msg) {
    var t = $('#toast');
    t.textContent = msg;
    t.hidden = false;
    clearTimeout(toast._t);
    toast._t = setTimeout(function () { t.hidden = true; }, 3200);
  }

  function visibles() {
    var q = norm(state.q);
    return state.vales.filter(function (v) {
      return (state.tipo === 'TODOS' || v.tipo === state.tipo) && (!q || v._hay.indexOf(q) >= 0);
    });
  }

  function renderChips() {
    var counts = {};
    state.vales.forEach(function (v) { counts[v.tipo] = (counts[v.tipo] || 0) + 1; });
    $('#chips').innerHTML = TIPOS.map(function (t) {
      var n = t[0] === 'TODOS' ? state.vales.length : (counts[t[0]] || 0);
      return '<button type="button" class="chip" data-tipo="' + t[0] + '" aria-pressed="' +
        (state.tipo === t[0] ? 'true' : 'false') + '">' +
        '<span class="cd" aria-hidden="true"></span>' + t[1] + ' <span class="n">' + n + '</span></button>';
    }).join('');
  }

  function fila(v) {
    var on = state.sel.has(v.key);
    var cls = CLASE[v.tipo] || '';
    var f = String(v.fecha || '').split(' ');
    var editado = state.edits[v.key] ? ' <span class="badge">Editado</span>' : '';
    var pz = v.piezas || 0;
    var unidad = v.tipo === 'ACCESORIOS' ? 'leídas' : (pz === 1 ? 'pza' : 'pzas');
    var aria = [v.tipoLabel || v.tipo, 'folio ' + v.folio, v.resumen, f[0] || '', pz + ' ' + unidad].join(', ');
    return '<div class="vale ' + cls + (on ? ' sel' : '') + '" data-key="' + esc(v.key) + '">' +
      '<span class="rail" aria-hidden="true"></span>' +
      '<label class="chkwrap"><input type="checkbox" class="vchk"' + (on ? ' checked' : '') +
        ' aria-label="Seleccionar ' + esc((v.tipoLabel || v.tipo) + ' ' + v.folio) + '"></label>' +
      '<button type="button" class="vale__main" data-open aria-label="Abrir ' + esc(aria) + '">' +
        '<span class="tipo"><span class="t ' + cls + '">' + esc(v.tipoLabel || v.tipo) + '</span>' +
          '<span class="tc">' + (CODE[v.tipo] || '') + '</span></span>' +
        '<span class="vmain"><span class="f">' + esc(v.folio) + editado + '</span>' +
          '<span class="r">' + esc(v.resumen) + '</span></span>' +
        '<span class="d"><b>' + esc(f[0] || '') + '</b>' + (f[1] ? '<span>' + esc(f[1]) + '</span>' : '') + '</span>' +
        '<span class="pz">' + pz + '<small>' + unidad + '</small></span>' +
      '</button></div>';
  }

  function render() {
    var rows = visibles();
    $('#list').innerHTML = rows.length
      ? rows.map(fila).join('')
      : '<div class="empty">No hay vales con este filtro.</div>';
    $('#regs').textContent = rows.length + ' de ' + state.vales.length + ' vales';
    sync();
  }

  function plural(n, s) { return n + ' ' + s + (n === 1 ? '' : 's'); }

  function sync() {
    var n = state.sel.size;
    $('#barCount').textContent = n;
    $('#barWord').textContent = n === 1 ? 'seleccionado' : 'seleccionados';
    $('#selCount').textContent = n + (n === 1 ? ' marcado' : ' marcados');
    $('#expSel').disabled = n === 0;
    $('#expSel').textContent = n
      ? 'Descargar ' + n + (n === 1 ? ' vale (.txt)' : ' vales (.txt)')
      : 'Descargar seleccionados (.txt)';
    $('#batchDate').hidden = n === 0;
    $('#delSel').hidden = n === 0;
    $('#delSel').textContent = 'Borrar ' + n + (n === 1 ? ' vale' : ' vales');

    var porTipo = {};
    state.sel.forEach(function (k) {
      var v = state.vales.find(function (x) { return x.key === k; });
      if (v) porTipo[v.tipo] = (porTipo[v.tipo] || 0) + 1;
    });
    $('#barTipos').innerHTML = Object.keys(porTipo).map(function (t) {
      return '<span><i class="' + (CLASE[t] || '') + '" aria-hidden="true"></i>' + plural(porTipo[t], SING[t] || t) + '</span>';
    }).join('');

    var rows = visibles();
    var all = $('#all');
    all.checked = rows.length > 0 && rows.every(function (v) { return state.sel.has(v.key); });
    all.indeterminate = n > 0 && !all.checked;
    Array.prototype.forEach.call(document.querySelectorAll('.vale'), function (el) {
      var on = state.sel.has(el.dataset.key);
      el.classList.toggle('sel', on);
      var cb = el.querySelector('.vchk');
      if (cb) cb.checked = on;
    });
  }

  function totalDe(d) {
    return d.tipo === 'ACCESORIOS'
      ? (d.codigos || []).reduce(function (a, c) { return a + (parseInt(c.cantidad, 10) || 0); }, 0)
      : (d.codigos || []).length;
  }

  function resumenHtml(d) {
    var t = d.totales || {};
    var total = t.piezas || 0;
    var label = d.tipo === 'ACCESORIOS' ? 'leídas' : d.tipo === 'CONTEO' ? 'piezas' : (total === 1 ? 'pieza' : 'piezas');
    var packs = d.tipo === 'ACCESORIOS' ? []
      : d.tipo === 'CONTEO' ? [['Cajas', t.cajas]]
      : [['Cajas', t.cajas], ['Sacos', t.sacos], ['Cartuchos', t.cartuchos]];
    return '<div class="resumen' + (d.alerta ? ' alert' : '') + '">' +
      '<div class="big"><b>' + total + '</b><span>' + label + '</span></div>' +
      '<div class="packs">' + packs.map(function (p) {
        return '<div><span>' + p[0] + '</span><b>' + (p[1] || 0) + '</b></div>';
      }).join('') + '</div>' +
      '<div class="estado">' + (d.alerta
        ? '&#33;&#160; Revisar: las piezas no coinciden con lo esperado'
        : '&#10003;&#160; Conforme') + '</div></div>';
  }

  function applyLocal(d, edit) {
    if (!edit) return d;
    if (d.campos) {
      d.campos = d.campos.map(function (c) {
        return { field: c.field, label: c.label, value: edit.datos[c.field] !== undefined ? edit.datos[c.field] : c.value };
      });
      d.datos = d.campos.map(function (c) { return { label: c.label, value: c.value }; });
    }
    if (edit.codigos) d.codigos = edit.codigos.map(function (c) { return Object.assign({}, c); });
    if (d.totales) {
      var total = totalDe(d);
      d.totales.piezas = total;
      if (d.tipo === 'ACCESORIOS') d.totales.accesorios = total;
    }
    return d;
  }

  function renderDetail(d) {
    $('#pTag').textContent = d.tipoLabel || d.tipo;
    $('#pTag').className = 't ' + (CLASE[d.tipo] || '');
    $('#pFolio').textContent = d.folio;
    $('#panel').classList.toggle('editando', state.editando);
    var h = '';

    if (state.editando) {
      h += '<div class="band"><b>Editando</b><span>Correcciones solo en esta sesión</span></div>';
    }

    h += resumenHtml(d);

    if (state.editando) {
      h += '<h4>Datos del vale</h4><div class="grid">';
      (d.campos || []).forEach(function (c) {
        h += '<div><label><span>' + esc(c.label) + '</span>' +
          '<input class="edit-input" data-field="' + esc(c.field) + '" value="' + esc(c.value) + '"></label></div>';
      });
      h += '</div>';
    } else if (d.datos && d.datos.length) {
      h += '<h4>Datos del vale</h4><div class="grid">' + d.datos.map(function (x) {
        return '<div><span>' + esc(x.label) + '</span><b>' + esc(x.value) + '</b></div>';
      }).join('') + '</div>';
    }

    h += '<h4>Materiales</h4><ul class="items">' + (d.items || []).map(function (x) {
      return '<li><span>' + esc(x.nombre) + ' <span class="pk">' + esc(x.empaque) + '</span></span><b>' + x.cantidad + '</b></li>';
    }).join('') + '</ul>';

    if (state.editando) {
      h += '<div class="codes-hd"><h4>Códigos</h4><span class="count">' + (d.codigos || []).length + '</span>' +
        '<button type="button" class="btn btn--ghost btn--mini" id="valeDate">Cambiar fecha del vale</button></div><ul class="codes">';
      (d.codigos || []).forEach(function (c, i) {
        h += '<li><input class="edit-input" data-code="' + i + '" aria-label="Código ' + (i + 1) + '" value="' + esc(c.codigo) + '">' +
          '<input class="edit-input time-in" data-time="' + i + '" aria-label="Fecha y hora del código ' + (i + 1) + '" value="' + esc(c.hora) + '">' +
          (d.tipo === 'ACCESORIOS' ? '<input class="edit-input qty-in" data-qty="' + i + '" aria-label="Cantidad del código ' + (i + 1) + '" value="' + (c.cantidad || 1) + '">' : '') +
          '<button type="button" class="del" data-del="' + i + '" aria-label="Eliminar código">×</button></li>';
      });
      h += '</ul><div class="add"><input id="newCode" class="edit-input" aria-label="Código nuevo" placeholder="Código">' +
        '<input id="newTime" class="edit-input time-in" aria-label="Fecha y hora del código nuevo" placeholder="YYYY-MM-DD HH:MM:SS">' +
        (d.tipo === 'ACCESORIOS' ? '<input id="newQty" class="edit-input qty-in" aria-label="Cantidad del código nuevo" value="1">' : '') +
        '<button type="button" class="btn" id="addCode">Agregar</button></div>' +
        '<p class="note note--aviso">No se guardan en la terminal; el .txt sí las incluye.</p>';
    } else if (d.codigos && d.codigos.length) {
      h += '<h4>Códigos</h4><ul class="codes">' + d.codigos.map(function (c) {
        return '<li><span>' + esc(c.codigo) + '</span><span>' + esc(c.hora) + '</span></li>';
      }).join('') + '</ul>';
    }

    if (d.firmas && d.firmas.length) {
      h += '<h4>Firmas</h4><div class="firmas">' + d.firmas.map(function (f) {
        return '<div>' + esc(f) + '</div>';
      }).join('') + '</div>';
    }

    $('#pBody').innerHTML = h;
    $('#pEdit').hidden = state.editando || d.tipo === 'CONTEO';
    $('#pBorrar').hidden = state.editando;
    $('#pCerrar').hidden = state.editando;
    $('#pSave').hidden = !state.editando;
    $('#pCancel').hidden = !state.editando;
    if (state.editando) bindEdit();
  }

  function bindEdit() {
    Array.prototype.forEach.call(document.querySelectorAll('[data-del]'), function (b) {
      b.onclick = function () {
        state.draft.codigos.splice(parseInt(b.dataset.del, 10), 1);
        renderDetail(state.draft);
      };
    });
    var vd = $('#valeDate');
    if (vd) vd.onclick = function () { abrirFecha('vale'); };
    var add = $('#addCode');
    if (!add) return;
    add.onclick = function () {
      var code = ($('#newCode').value || '').trim();
      if (!code) return;
      var dup = findDuplicate(code, state.abierto, -1);
      if (dup) { toast(dup); return; }
      var fecha = (state.draft.campos || []).find(function (c) { return c.field === 'fecha'; });
      var hora = normStamp($('#newTime').value, fecha ? fecha.value : '');
      if (hora === null) { toast('Fecha y hora inválidas: usa YYYY-MM-DD HH:MM:SS'); return; }
      state.draft.codigos.push({
        codigo: code,
        hora: hora,
        cantidad: $('#newQty') ? (parseInt($('#newQty').value, 10) || 1) : 1
      });
      state.draft.totales.piezas = totalDe(state.draft);
      renderDetail(state.draft);
    };
  }

  function codeInOtherVale(code, exceptKey) {
    var c = String(code || '').trim().toUpperCase();
    if (!c) return null;
    for (var i = 0; i < state.vales.length; i++) {
      var v = state.vales[i];
      if (v.key === exceptKey) continue;
      var codes = String(v.codigos || '').split(/\s+/);
      if (codes.some(function (x) { return x.toUpperCase() === c; })) return v;
    }
    return null;
  }

  function findDuplicate(code, currentKey, skipIndex) {
    var c = String(code || '').trim().toUpperCase();
    if (!c) return 'Código vacío';
    var same = (state.draft.codigos || []).some(function (x, i) {
      return i !== skipIndex && x.codigo.toUpperCase() === c;
    });
    if (same) return 'Código ' + c + ' repetido en este vale';
    var other = codeInOtherVale(c, currentKey);
    if (other) return 'Código ' + c + ' repetido en ' + (other.tipoLabel || other.tipo) + ' ' + (other.folio || other.key);
    return null;
  }

  async function openVale(key) {
    state.foco = document.activeElement;
    state.abierto = key;
    $('#pFolio').textContent = '';
    $('#pBody').innerHTML = '<p class="note">Cargando vale…</p>';
    $('#scrim').hidden = false;
    $('#panel').hidden = false;
    document.body.classList.add('detalle-abierto');
    try {
      var d = await api('/api/vale?key=' + encodeURIComponent(key));
      if (state.abierto !== key) return;
      d = applyLocal(d, state.edits[key]);
      state.detalle = d;
      state.draft = clone(d);
      state.editando = false;
      renderDetail(d);
      $('#pFolio').focus();
    } catch (e) {
      $('#pBody').innerHTML = '<p class="note">' + esc(e.message) + '</p>';
    }
  }

  function closeVale() {
    if (state.editando && !confirm('¿Descartar los cambios sin guardar?')) return;
    state.abierto = null;
    state.editando = false;
    $('#scrim').hidden = true;
    $('#panel').hidden = true;
    document.body.classList.remove('detalle-abierto');
    if (state.foco && state.foco.focus) state.foco.focus();
  }

  function saveEdit() {
    var d = state.draft;
    Array.prototype.forEach.call(document.querySelectorAll('[data-field]'), function (i) {
      var c = d.campos.find(function (x) { return x.field === i.dataset.field; });
      if (c) c.value = i.value;
    });
    Array.prototype.forEach.call(document.querySelectorAll('[data-code]'), function (i) {
      var c = d.codigos[parseInt(i.dataset.code, 10)];
      if (c) c.codigo = i.value;
    });
    Array.prototype.forEach.call(document.querySelectorAll('[data-time]'), function (i) {
      var c = d.codigos[parseInt(i.dataset.time, 10)];
      if (c) c.hora = i.value;
    });
    Array.prototype.forEach.call(document.querySelectorAll('[data-qty]'), function (i) {
      var c = d.codigos[parseInt(i.dataset.qty, 10)];
      if (c) c.cantidad = parseInt(i.value, 10) || 1;
    });
    var datos = {};
    d.campos.forEach(function (c) { datos[c.field] = c.value; });
    var fechaVale = (datos.fecha || '');
    var codigos = [];
    for (var j = 0; j < d.codigos.length; j++) {
      var hora = normStamp(d.codigos[j].hora, fechaVale);
      if (hora === null) { toast('Fecha y hora inválidas en el código ' + (d.codigos[j].codigo || '') + ': usa YYYY-MM-DD HH:MM:SS'); return; }
      codigos.push({ codigo: d.codigos[j].codigo, hora: hora, cantidad: d.codigos[j].cantidad || 1 });
    }
    var seen = {};
    for (var i = 0; i < codigos.length; i++) {
      var c = String(codigos[i].codigo || '').trim().toUpperCase();
      if (!c) { toast('Hay un código vacío'); return; }
      if (seen[c]) { toast('Código ' + c + ' repetido en este vale'); return; }
      seen[c] = true;
      var other = codeInOtherVale(c, state.abierto);
      if (other) { toast('Código ' + c + ' repetido en ' + (other.tipoLabel || other.tipo) + ' ' + (other.folio || other.key)); return; }
    }
    state.edits[state.abierto] = { datos: datos, codigos: codigos };
    var prev = {};
    (state.detalle.campos || []).forEach(function (c) { prev[c.field] = c.value; });
    var cambios = d.campos.filter(function (c) { return prev[c.field] && prev[c.field] !== c.value; });
    if (cambios.length && d.firmas) {
      d.firmas = d.firmas.map(function (f) {
        cambios.forEach(function (c) { f = f.split(prev[c.field]).join(c.value); });
        return f;
      });
    }
    d.datos = d.campos.map(function (c) { return { label: c.label, value: c.value }; });
    d.codigos = codigos;
    d.totales.piezas = totalDe(d);
    if (d.tipo === 'ACCESORIOS') d.totales.accesorios = d.totales.piezas;
    state.detalle = d;
    state.editando = false;
    renderDetail(d);

    var item = state.vales.find(function (v) { return v.key === state.abierto; });
    if (item) {
      item.codigos = codigos.map(function (c) { return c.codigo; }).join(' ');
      item.piezas = d.totales.piezas;
      if (datos.fecha) item.fecha = datos.fecha;
      item._hay = norm([item.folio, item.tipoLabel, item.resumen, item.fecha, item.codigos].join(' '));
      render();
    }
    toast('Cambios guardados en esta sesión');
  }

  function cancelEdit() {
    state.editando = false;
    state.draft = clone(state.detalle);
    renderDetail(state.detalle);
  }

  async function downloadTxt(keys) {
    var body = new URLSearchParams();
    if (keys && keys.length) body.set('keys', keys.join(','));
    body.set('edits', JSON.stringify(state.edits));
    var r = await fetch('/export/vales.txt', { method: 'POST', body: body });
    if (r.status === 401) { location.href = '/login'; return; }
    if (!r.ok) { toast('No se pudo descargar el .txt'); return; }
    var blob = await r.blob();
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'vales-consolidado-' + nowStamp().slice(0, 16).replace(' ', '_').replace(':', '') + '.txt';
    a.click();
    setTimeout(function () { URL.revokeObjectURL(a.href); }, 1500);
  }

  function abrirFecha(destino) {
    state.fechaDestino = destino || 'lote';
    if (state.fechaDestino === 'vale') {
      var d = state.draft;
      if (!d || !(d.codigos || []).length) { toast('Este vale no tiene códigos'); return; }
      var fv = (d.campos || []).find(function (c) { return c.field === 'fecha'; });
      $('#dlgDateH').textContent = 'Cambiar fecha del vale';
      $('#dlgDateNote').textContent = d.codigos.length + ' códigos · ' + (d.tipoLabel || d.tipo) + ' ' + d.folio;
      $('#dlgDateInput').value = fv && /^\d{4}-\d{2}-\d{2}/.test(fv.value) ? fv.value.slice(0, 10) : nowStamp().slice(0, 10);
      $('#dlgDate').showModal();
      return;
    }
    if (!state.sel.size) return;
    var conteos = Array.from(state.sel).filter(function (k) { return k.indexOf('CONTEO:') === 0; }).length;
    var editables = state.sel.size - conteos;
    if (!editables) { toast('Los conteos no tienen fecha editable'); return; }
    $('#dlgDateH').textContent = 'Cambiar fecha del lote';
    $('#dlgDateNote').textContent = editables + (editables === 1 ? ' vale seleccionado' : ' vales seleccionados') +
      (conteos ? ' · ' + conteos + ' conteo(s) se omiten' : '');
    var uno = state.vales.find(function (v) { return state.sel.has(v.key); });
    $('#dlgDateInput').value = uno && /^\d{4}-\d{2}-\d{2}/.test(uno.fecha) ? uno.fecha.slice(0, 10) : nowStamp().slice(0, 10);
    $('#dlgDate').showModal();
  }

  /** Cambia la fecha de todos los códigos del vale abierto, en el borrador (se aplica al guardar). */
  function aplicarFechaVale(fecha) {
    var d = state.draft;
    d.codigos = d.codigos.map(function (c) {
      var hora = String(c.hora || '').slice(11) || '00:00:00';
      return { codigo: c.codigo, hora: fecha + ' ' + hora, cantidad: c.cantidad || 1 };
    });
    var campo = (d.campos || []).find(function (c) { return c.field === 'fecha'; });
    if (campo) campo.value = fecha + ' ' + (String(campo.value || '').slice(11) || '00:00:00');
    renderDetail(d);
    toast(d.codigos.length + ' códigos con fecha ' + fecha + ' · guarda para aplicarlo');
  }

  async function aplicarFecha(fecha) {
    var keys = Array.from(state.sel);
    toast('Aplicando fecha a ' + keys.length + ' vales…');
    var hechos = 0, omitidos = 0;
    for (var i = 0; i < keys.length; i++) {
      var key = keys[i];
      if (key.indexOf('CONTEO:') === 0) { omitidos++; continue; }
      var det = (state.abierto === key && state.detalle) ? state.detalle : null;
      if (!det) {
        try { det = await api('/api/vale?key=' + encodeURIComponent(key)); }
        catch (e) { omitidos++; continue; }
      }
      var edit = state.edits[key];
      var codigos = (edit && edit.codigos) ? edit.codigos : (det.codigos || []).map(function (c) {
        return { codigo: c.codigo, hora: c.hora, cantidad: c.cantidad || 1 };
      });
      if (!codigos.length) { omitidos++; continue; }
      codigos = codigos.map(function (c) {
        var hora = String(c.hora || '').slice(11) || '00:00:00';
        return { codigo: c.codigo, hora: fecha + ' ' + hora, cantidad: c.cantidad || 1 };
      });
      var datos = Object.assign({}, edit && edit.datos);
      var campo = (det.campos || []).find(function (c) { return c.field === 'fecha'; });
      datos.fecha = fecha + ' ' + ((datos.fecha || (campo && campo.value) || '').slice(11) || '00:00:00');
      state.edits[key] = { datos: datos, codigos: codigos };
      var item = state.vales.find(function (v) { return v.key === key; });
      if (item) {
        item.fecha = datos.fecha;
        item.codigos = codigos.map(function (c) { return c.codigo; }).join(' ');
        item._hay = norm([item.folio, item.tipoLabel, item.resumen, item.fecha, item.codigos].join(' '));
      }
      hechos++;
    }
    if (state.abierto && state.edits[state.abierto]) {
      var d = applyLocal(clone(state.detalle), state.edits[state.abierto]);
      state.detalle = d;
      state.draft = clone(d);
      renderDetail(d);
    }
    render();
    toast(hechos + (hechos === 1 ? ' vale con fecha ' : ' vales con fecha ') + fecha +
      (omitidos ? ' · ' + omitidos + ' omitido(s)' : ''));
  }

  function conectarSubida(fileId, btnId, msgId, etiqueta, cual) {
    var file = $('#' + fileId), btn = $('#' + btnId), msg = $('#' + msgId);
    var texto = '';
    file.addEventListener('change', function () {
      var f = file.files && file.files[0];
      btn.disabled = !f;
      texto = '';
      msg.textContent = '';
      if (!f) return;
      var lector = new FileReader();
      lector.onload = function () {
        texto = String(lector.result || '');
        var filas = texto.split(/\r?\n/).filter(function (l) { return l.trim(); }).length;
        msg.textContent = f.name + ' · ' + filas + ' filas';
      };
      lector.onerror = function () {
        msg.textContent = 'No se pudo leer el archivo';
        btn.disabled = true;
      };
      lector.readAsText(f, 'UTF-8');
    });

    btn.addEventListener('click', async function () {
      if (!texto) { msg.textContent = 'Elige un archivo CSV'; return; }
      if (!confirm('Se actualizará el catálogo de ' + etiqueta + ' en la terminal. ¿Continuar?')) return;
      btn.disabled = true;
      msg.textContent = 'Subiendo…';
      try {
        var r = await fetch('/api/catalogo', {
          method: 'POST',
          headers: { 'Content-Type': 'text/csv; charset=utf-8' },
          body: texto
        });
        if (r.status === 401) { location.href = '/login'; return; }
        var j = await r.json().catch(function () { return {}; });
        if (r.ok) {
          var cuantos = j.importados === 1 ? '1 producto actualizado' : j.importados + ' productos actualizados';
          msg.textContent = cuantos + ' en la terminal';
          toast(cuantos);
          cargarCatalogo(cual);
        } else if (j.error === 'sin_productos') {
          msg.textContent = 'No se reconoció ningún producto. Revisa el encabezado y las columnas.';
        } else if (j.error === 'archivo_muy_grande') {
          msg.textContent = 'El archivo es mayor a 1 MB.';
        } else {
          msg.textContent = 'No se pudo actualizar (' + r.status + ')';
        }
      } catch (e) {
        msg.textContent = 'Error de conexión con la terminal';
      }
      btn.disabled = false;
    });
  }

  function unidadPara(p) {
    var u = String(p.unidad || '').toUpperCase();
    if (u === 'MTS' || u === 'METRO' || u === 'METROS') return 'MTS';
    if (u === 'PZA' || u === 'PIEZA' || u === 'PIEZAS') return 'PZA';
    if (u === 'KG' || u === 'KGS') return 'KG';
    var tipo = String(p.tipo || '').toUpperCase();
    var accesorio = tipo === 'ACCESORIO' || (!tipo && String(p.id || '').toUpperCase().indexOf('X') === 0);
    if (!accesorio) return 'KG';
    return /CORDON|PRIMACORD|DETACORD|MECHA|GUIA|CABLE/.test(String(p.producto || '').toUpperCase()) ? 'MTS' : 'PZA';
  }

  function unidadCorta(u) {
    return u === 'MTS' ? 'MTS' : u === 'PZA' ? 'PZA' : u === 'KG' ? 'KG' : String(u || '').toUpperCase();
  }

  function textoUnidad(p) {
    var corta = unidadCorta(p.unidad);
    return p.peso ? p.peso + ' ' + corta : corta;
  }

  function etiquetaCantidad(unidad) {
    if (unidad === 'MTS') return 'Cantidad (MTS)';
    if (unidad === 'PZA') return 'Cantidad (PZA)';
    return 'Cantidad (KG)';
  }

  /** Ajusta la etiqueta y el paso del campo cantidad a la unidad elegida. */
  function aplicarUnidad(unidad) {
    $('#pCantLabel').textContent = etiquetaCantidad(unidad);
    var paso = unidad === 'PZA' ? '1' : unidad === 'MTS' ? '0.01' : '0.001';
    $('#pPeso').step = paso;
  }

  function cargarCatalogo(cual) {
    var cfg = cual === 'ALTO'
      ? { tipo: 'ALTO', q: '#qAlto', list: '#listAlto', n: '#nAlto' }
      : { tipo: 'ACCESORIOS', q: '#qAcc', list: '#listAcc', n: '#nAcc' };
    var term = ($(cfg.q).value || '').trim();
    var destino = $(cfg.list);
    destino.innerHTML = '<div class="empty">Consultando la terminal…</div>';
    fetch('/api/catalogo?tipo=' + cfg.tipo + '&q=' + encodeURIComponent(term), {
      headers: { Accept: 'application/json' }, cache: 'no-store'
    })
      .then(function (r) {
        if (r.status === 401) { location.href = '/login'; throw new Error('Sesión expirada'); }
        if (!r.ok) throw new Error('No se pudo consultar');
        return r.json();
      })
      .then(function (j) {
        var lista = j.productos || [];
        $(cfg.n).textContent = lista.length + (lista.length === 1 ? ' producto' : ' productos');
        destino.innerHTML = lista.length
          ? lista.map(function (p) {
            catPorId[p.id] = { p: p, cual: cual };
            return '<button type="button" class="cat-row" data-cat="' + esc(p.id) + '" ' +
              'aria-label="Editar ' + esc(p.id + ' ' + p.producto) + '">' +
              '<span class="cid">' + esc(p.id) + '</span>' +
              '<span class="cnom" title="' + esc(p.producto + ' · ' + p.presentacion) + '">' + esc(p.producto) +
              (p.presentacion ? ' <span class="pk">' + esc(p.presentacion) + '</span>' : '') + '</span>' +
              '<span class="cpes">' + esc(textoUnidad(p)) + '</span></button>';
          }).join('')
          : '<div class="empty">' + (term ? 'Sin resultados para «' + esc(term) + '»' : 'El catálogo está vacío') + '</div>';
      })
      .catch(function (e) {
        var msg = e && e.message === 'Failed to fetch'
          ? 'No se pudo conectar con la terminal'
          : (e && e.message) || 'No se pudo consultar el catálogo';
        destino.innerHTML = '<div class="empty">' + esc(msg) + '</div>';
        $(cfg.n).textContent = '';
      });
  }

  function conectarConsulta() {
    [['ALTO', '#qAlto', '#listAlto'], ['ACCESORIOS', '#qAcc', '#listAcc']].forEach(function (cfg) {
      var timer;
      $(cfg[1]).addEventListener('input', function () {
        clearTimeout(timer);
        timer = setTimeout(function () { cargarCatalogo(cfg[0]); }, 200);
      });
      $(cfg[2]).addEventListener('click', function (e) {
        var fila = e.target.closest('[data-cat]');
        if (fila) abrirProducto(fila.dataset.cat);
      });
    });
  }

  function unidadFamilia(familia) {
    return familia === 'CORDON_DETONANTE' ? 'MTS' : 'PZA';
  }

  function refrescarFormulario() {
    var tipo = $('#pTipo').value;
    var accesorio = tipo === 'ACCESORIO';
    $('#pFamWrap').hidden = !accesorio;
    $('#pAltoWrap').hidden = tipo !== 'ALTO_EXPLOSIVO';
    $('#pIdLabel').textContent = accesorio ? 'Código accesorio (inicia con X)' : 'ID prefijo (3 dígitos)';
    $('#pCantLabel').textContent = etiquetaCantidad($('#pUnidad').value);
  }

  function recalcularUnitario() {
    if ($('#pTipo').value !== 'ALTO_EXPLOSIVO') return;
    var p = parseFloat($('#pPeso').value) || 0;
    var c = parseInt($('#pCart').value, 10) || 0;
    if (p > 0 && c > 0) $('#pUnit').value = (p / c).toFixed(6);
  }

  function abrirProducto(id) {
    var ref = catPorId[id];
    if (!ref) return;
    prodEditando = { p: ref.p, cual: ref.cual, crear: false };
    var p = ref.p;
    $('#dlgProdH').textContent = 'Editar producto';
    $('#pId').value = p.id;
    $('#pId').readOnly = true;
    $('#dlgProdId').textContent = p.codBarras && p.codBarras !== p.id ? 'Código de barras: ' + p.codBarras : '';
    $('#pNombre').value = p.producto || '';
    $('#pPres').value = p.presentacion || '';
    $('#pTipo').value = p.tipo || 'ALTO_EXPLOSIVO';
    $('#pFamilia').value = ['CORDON_DETONANTE', 'CANUELAS', 'DETONADORES'].indexOf(p.categoria) >= 0
      ? p.categoria
      : unidadPara(p) === 'MTS' ? 'CORDON_DETONANTE' : 'DETONADORES';
    $('#pSap').value = p.noSap || '';
    $('#pCart').value = p.cartuchosPorCaja || 0;
    $('#pUnit').value = p.pesoUnitarioKg || 0;
    $('#pUnidad').value = unidadPara(p);
    aplicarUnidad($('#pUnidad').value);
    $('#pPeso').value = p.peso || 0;
    refrescarFormulario();
    $('#dlgProd').showModal();
    $('#pNombre').focus();
  }

  function abrirNuevo(cual) {
    prodEditando = {
      p: { id: '', producto: '', presentacion: '', unidad: '', peso: 0, tipo: cual === 'ALTO' ? 'ALTO_EXPLOSIVO' : 'ACCESORIO', codBarras: '' },
      cual: cual,
      crear: true
    };
    $('#dlgProdH').textContent = 'Agregar producto';
    $('#pId').value = '';
    $('#pId').readOnly = false;
    $('#dlgProdId').textContent = '';
    $('#pNombre').value = '';
    $('#pPres').value = '';
    $('#pTipo').value = prodEditando.p.tipo;
    $('#pFamilia').value = 'DETONADORES';
    $('#pSap').value = '';
    $('#pCart').value = 0;
    $('#pUnit').value = 0;
    var unidad = prodEditando.p.tipo === 'ACCESORIO' ? 'PZA' : 'KG';
    $('#pUnidad').value = unidad;
    aplicarUnidad(unidad);
    $('#pPeso').value = 0;
    refrescarFormulario();
    $('#dlgProd').showModal();
    $('#pId').focus();
  }

  async function guardarProducto() {
    if (!prodEditando) return;
    var datos = {
      id: $('#pId').value.trim().toUpperCase(),
      producto: $('#pNombre').value.trim(),
      presentacion: $('#pPres').value.trim(),
      unidad: $('#pUnidad').value,
      peso: parseFloat($('#pPeso').value) || 0,
      tipo: $('#pTipo').value,
      noSap: $('#pSap').value.trim(),
      categoria: $('#pTipo').value === 'ACCESORIO' ? $('#pFamilia').value : '',
      cartuchosPorCaja: $('#pTipo').value === 'ALTO_EXPLOSIVO' ? (parseInt($('#pCart').value, 10) || 0) : 0,
      pesoUnitarioKg: $('#pTipo').value === 'ALTO_EXPLOSIVO' ? (parseFloat($('#pUnit').value) || 0) : 0
    };
    if (!datos.id) { toast('Escribe el ID del producto'); return; }
    if (prodEditando.crear) {
      var idOk = datos.tipo === 'ACCESORIO'
        ? /^X/.test(datos.id) && datos.id.length >= 2
        : datos.id.length === 3;
      if (!idOk) {
        toast(datos.tipo === 'ACCESORIO' ? 'El código debe iniciar con X' : 'El ID debe ser de 3 caracteres');
        return;
      }
    }
    if (!datos.producto) { toast('El nombre no puede quedar vacío'); return; }
    if (prodEditando.crear) datos.crear = true;

    async function enviar(carga) {
      try {
        return await fetch('/api/catalogo/producto', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(carga)
        });
      } catch (e) {
        toast('No se pudo conectar con la terminal');
        return null;
      }
    }

    var r = await enviar(datos);
    if (!r) return;
    if (r.status === 401) { location.href = '/login'; return; }
    if (r.status === 404) {
      if (!confirm('Ese producto ya no existe en la terminal. ¿Darlo de alta?')) {
        toast('Producto no encontrado');
        return;
      }
      datos.crear = true;
      r = await enviar(datos);
      if (!r) return;
    }
    if (r.status === 401) { location.href = '/login'; return; }
    if (r.status === 409) { toast('Ya existe un producto con ese ID'); return; }
    if (!r.ok) { toast('No se pudo guardar el producto (' + r.status + ')'); return; }
    $('#dlgProd').close();
    toast(datos.crear ? 'Producto agregado' : 'Producto actualizado');
    cargarCatalogo(prodEditando.cual);
  }

  var codigoBorrado = '';

  function abrirBorrado(keys) {
    if (!keys || !keys.length) return;
    if (state.editando) { toast('Termina o cancela la edición antes de borrar'); return; }
    state.delKeys = keys.slice();
    codigoBorrado = String(Math.floor(1000 + Math.random() * 9000));
    $('#dlgDelH').textContent = keys.length === 1 ? 'Borrar vale' : 'Borrar ' + keys.length + ' vales';
    $('#dlgDelNote').textContent = keys.length === 1
      ? 'Se borrará 1 vale de forma permanente.'
      : 'Se borrarán ' + keys.length + ' vales de forma permanente.';
    $('#delCodeShow').textContent = codigoBorrado;
    $('#delCode').value = '';
    $('#dlgDel').showModal();
    $('#delCode').focus();
  }

  async function confirmarBorrado() {
    var codigo = $('#delCode').value.trim();
    if (codigo !== codigoBorrado) { toast('El código no coincide'); return; }
    if (!state.delKeys.length) return;
    var body = { codigo: codigo };
    if (state.delKeys.length === 1) body.key = state.delKeys[0];
    else body.keys = state.delKeys;
    var r;
    try {
      r = await fetch('/api/borrar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
    } catch (e) {
      toast('No se pudo conectar con la terminal');
      return;
    }
    if (r.status === 401) { location.href = '/login'; return; }
    if (r.status === 403) { toast('Código incorrecto o vencido'); return; }
    if (!r.ok) { toast('No se pudo borrar (' + r.status + ')'); return; }
    var j = await r.json().catch(function () { return {}; });
    var n = j.eliminados || 0;
    var borrados = state.delKeys.slice();
    codigoBorrado = '';
    $('#dlgDel').close();
    borrados.forEach(function (k) { state.sel.delete(k); });
    state.vales = state.vales.filter(function (v) { return borrados.indexOf(v.key) < 0; });
    state.delKeys = [];
    if (state.abierto && borrados.indexOf(state.abierto) >= 0) {
      state.abierto = null;
      state.editando = false;
      $('#scrim').hidden = true;
      $('#panel').hidden = true;
      document.body.classList.remove('detalle-abierto');
    }
    renderChips();
    render();
    toast(n === 1 ? '1 vale eliminado' : n + ' vales eliminados');
  }

  function show(which) {
    var dl = which === 'dl';
    $('#viewDl').hidden = !dl;
    $('#viewTools').hidden = dl;
    $('#tabDl').classList.toggle('on', dl);
    $('#tabTools').classList.toggle('on', !dl);
    $('#tabDl').setAttribute('aria-pressed', dl ? 'true' : 'false');
    $('#tabTools').setAttribute('aria-pressed', dl ? 'false' : 'true');
    $('.bar').hidden = !dl;
    if (!dl) closeVale();
  }

  function bind() {
    $('#chips').addEventListener('click', function (e) {
      var b = e.target.closest('.chip');
      if (!b) return;
      state.tipo = b.dataset.tipo;
      renderChips();
      render();
    });

    var timer;
    $('#q').addEventListener('input', function () {
      clearTimeout(timer);
      var v = this.value;
      timer = setTimeout(function () { state.q = v; render(); }, 120);
    });

    $('#list').addEventListener('click', function (e) {
      var row = e.target.closest('.vale');
      if (!row || e.target.closest('.chkwrap')) return;
      openVale(row.dataset.key);
    });

    $('#list').addEventListener('change', function (e) {
      var row = e.target.closest('.vale');
      if (!row || !e.target.classList.contains('vchk')) return;
      if (e.target.checked) state.sel.add(row.dataset.key); else state.sel.delete(row.dataset.key);
      sync();
    });

    $('#all').addEventListener('change', function () {
      var rows = visibles();
      if (this.checked) rows.forEach(function (v) { state.sel.add(v.key); });
      else rows.forEach(function (v) { state.sel.delete(v.key); });
      sync();
    });

    $('#expAll').addEventListener('click', function () { downloadTxt(null); });
    $('#expSel').addEventListener('click', function () {
      if (state.sel.size) downloadTxt(Array.from(state.sel));
    });

    $('#batchDate').addEventListener('click', abrirFecha);
    $('#delSel').addEventListener('click', function () {
      if (state.sel.size) abrirBorrado(Array.from(state.sel));
    });
    $('#pBorrar').addEventListener('click', function () {
      if (state.abierto) abrirBorrado([state.abierto]);
    });
    $('#dlgDelCancel').addEventListener('click', function () { $('#dlgDel').close(); });
    $('#dlgDelOk').addEventListener('click', confirmarBorrado);
    $('#delCode').addEventListener('keydown', function (e) {
      if (e.key === 'Enter') { e.preventDefault(); confirmarBorrado(); }
    });
    $('#dlgCancel').addEventListener('click', function () { $('#dlgDate').close(); });
    $('#dlgApply').addEventListener('click', function () {
      var fecha = $('#dlgDateInput').value;
      if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha)) { toast('Elige una fecha válida'); return; }
      $('#dlgDate').close();
      if (state.fechaDestino === 'vale') aplicarFechaVale(fecha);
      else aplicarFecha(fecha);
    });

    $('#pEdit').addEventListener('click', function () {
      state.editando = true;
      state.draft = clone(state.detalle);
      renderDetail(state.draft);
      var first = document.querySelector('[data-field]');
      if (first) first.focus();
    });
    $('#pSave').addEventListener('click', saveEdit);
    $('#pCancel').addEventListener('click', cancelEdit);
    $('#pClose').addEventListener('click', closeVale);
    $('#pCerrar').addEventListener('click', closeVale);
    $('#scrim').addEventListener('click', closeVale);
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && !$('#panel').hidden) closeVale();
    });

    $('#tabDl').addEventListener('click', function () { show('dl'); });
    $('#tabTools').addEventListener('click', function () {
      show('tools');
      cargarCatalogo('ALTO');
      cargarCatalogo('ACCESORIOS');
    });

    conectarSubida('fileAlto', 'upAlto', 'msgAlto', 'alto explosivo y agente', 'ALTO');
    conectarSubida('fileAcc', 'upAcc', 'msgAcc', 'accesorios y artificios', 'ACCESORIOS');
    conectarConsulta();

    $('#dlgProdCancel').addEventListener('click', function () { $('#dlgProd').close(); });
    $('#dlgProdSave').addEventListener('click', guardarProducto);
    $('#pTipo').addEventListener('change', function () {
      if (!prodEditando) return;
      var unidad = this.value === 'ACCESORIO' ? unidadFamilia($('#pFamilia').value) : 'KG';
      $('#pUnidad').value = unidad;
      aplicarUnidad(unidad);
      refrescarFormulario();
    });
    $('#pFamilia').addEventListener('change', function () {
      if ($('#pTipo').value !== 'ACCESORIO') return;
      var unidad = unidadFamilia(this.value);
      $('#pUnidad').value = unidad;
      aplicarUnidad(unidad);
    });
    $('#pUnidad').addEventListener('change', function () { aplicarUnidad(this.value); });
    $('#pPeso').addEventListener('input', recalcularUnitario);
    $('#pCart').addEventListener('input', recalcularUnitario);
    $('#addAlto').addEventListener('click', function () { abrirNuevo('ALTO'); });
    $('#addAcc').addEventListener('click', function () { abrirNuevo('ACCESORIOS'); });
  }

  async function init() {
    bind();
    try {
      var list = await api('/api/vales');
      state.vales = list.map(function (v) {
        v._hay = norm([v.folio, v.tipoLabel, v.resumen, v.fecha, v.codigos].join(' '));
        return v;
      }).sort(function (a, b) { return String(b.fecha || '').localeCompare(String(a.fecha || '')); });
      renderChips();
      render();
      $('#sync').textContent = 'Conectado · ' + nowStamp().slice(11);
    } catch (e) {
      $('#dot').classList.add('dot--off');
      $('#sync').textContent = 'Sin conexión';
      $('#list').innerHTML = '<div class="empty">' + esc(e.message) + '</div>';
    }
  }

  init();
})();
