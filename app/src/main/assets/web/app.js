(function () {
  'use strict';

  var $ = function (s) { return document.querySelector(s); };
  var state = { vales: [], tipo: 'TODOS', q: '', sel: new Set(), abierto: null, foco: null };
  var TIPOS = [
    ['TODOS', 'Todos'], ['ENTRADA', 'Entradas'], ['DESPACHO', 'Salidas'],
    ['ACCESORIOS', 'Accesorios'], ['DEVOLUCION', 'Devoluciones'], ['CONTEO', 'Conteos']
  ];
  var CLASE = { ENTRADA: '', DESPACHO: 't--salida', ACCESORIOS: 't--acc', DEVOLUCION: 't--dev', CONTEO: 't--conteo' };
  var ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

  function esc(v) { return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) { return ESC[c]; }); }
  function norm(v) {
    return String(v == null ? '' : v).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  }
  function qs(k) { return new URLSearchParams(location.search).get(k); }

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
    toast._t = setTimeout(function () { t.hidden = true; }, 3000);
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
        (state.tipo === t[0] ? 'true' : 'false') + '">' + t[1] + ' <span class="n">' + n + '</span></button>';
    }).join('');
  }

  function fila(v) {
    var on = state.sel.has(v.key);
    return '<div class="vale' + (on ? ' sel' : '') + '" data-key="' + esc(v.key) + '" tabindex="0" role="button" ' +
      'aria-label="Abrir vale ' + esc(v.folio) + '">' +
      '<input type="checkbox" class="vchk"' + (on ? ' checked' : '') + ' aria-label="Seleccionar ' + esc(v.folio) + '">' +
      '<span class="t ' + (CLASE[v.tipo] || '') + '">' + esc(v.tipoLabel || v.tipo) + '</span>' +
      '<span><span class="f">' + esc(v.folio) + '</span><span class="r">' + esc(v.resumen) + '</span></span>' +
      '<span class="d">' + esc(v.fecha) + '<br><span class="pz">' + (v.piezas || 0) +
      ' ' + (v.piezas === 1 ? 'pza' : 'pzas') + '</span></span></div>';
  }

  function render() {
    var rows = visibles();
    $('#list').innerHTML = rows.length
      ? rows.map(fila).join('')
      : '<div class="empty">No hay vales con este filtro.</div>';
    $('#regs').textContent = rows.length + ' de ' + state.vales.length + ' registros';
    sync();
  }

  function sync() {
    var n = state.sel.size;
    $('#barCount').textContent = n;
    $('#selCount').textContent = n + (n === 1 ? ' marcado' : ' marcados');
    $('#expSel').disabled = n === 0;
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

  function packTotals(d) {
    var t = d.totales || {};
    var tipos = d.tipo === 'ACCESORIOS' ? [['ACCESORIO', t.accesorios]]
      : d.tipo === 'CONTEO' ? [['CAJA', t.cajas]]
      : [['CAJA', t.cajas], ['SACO', t.sacos], ['CARTUCHO', t.cartuchos]];
    return '<div class="packtotals">' + tipos.map(function (x) {
      return '<div><span>' + x[0] + '</span><b>' + (x[1] || 0) + '</b></div>';
    }).join('') + '</div>';
  }

  function renderDetail(d) {
    $('#pTag').textContent = d.tipoLabel || d.tipo;
    $('#pTag').className = 't ' + (CLASE[d.tipo] || '');
    $('#pFolio').textContent = d.folio;
    var h = '<h4>Datos del vale</h4><div class="grid">' + (d.datos || []).map(function (x) {
      return '<div><span>' + esc(x.label) + '</span><b>' + esc(x.value) + '</b></div>';
    }).join('') + '</div>';

    h += '<h4>Materiales</h4><ul class="items">' + (d.items || []).map(function (x) {
      return '<li><span>' + esc(x.nombre) + ' <span class="pk">' + esc(x.empaque) + '</span></span><b>' + x.cantidad + '</b></li>';
    }).join('') + '</ul>' + packTotals(d);

    if (d.codigos && d.codigos.length) {
      h += '<h4>Códigos</h4><ul class="codes">' + d.codigos.map(function (c) {
        return '<li><span>' + esc(c.codigo) + '</span><span>' + esc(c.hora) + '</span></li>';
      }).join('') + '</ul>';
    }

    h += '<div class="totalbox' + (d.alerta ? ' alert' : '') + '"><span class="n">' + (d.totales.piezas || 0) +
      '</span><span class="l">' + (d.tipo === 'ACCESORIOS' ? 'leídas' : 'piezas') + '</span><span class="sub">' +
      esc(d.sub || '') + '</span></div>';

    if (d.firmas && d.firmas.length) {
      h += '<h4>Firmas</h4><div class="firmas">' + d.firmas.map(function (f) {
        return '<div>' + esc(f) + '</div>';
      }).join('') + '</div>';
    }
    $('#pBody').innerHTML = h;
  }

  async function openVale(key) {
    state.foco = document.activeElement;
    state.abierto = key;
    $('#pBody').innerHTML = '<p class="note">Cargando vale…</p>';
    $('#scrim').hidden = false;
    $('#panel').hidden = false;
    try {
      var d = await api('/api/vale?key=' + encodeURIComponent(key));
      if (state.abierto !== key) return;
      renderDetail(d);
      $('#pFolio').focus();
    } catch (e) {
      $('#pBody').innerHTML = '<p class="note">' + esc(e.message) + '</p>';
    }
  }

  function closeVale() {
    state.abierto = null;
    $('#scrim').hidden = true;
    $('#panel').hidden = true;
    if (state.foco && state.foco.focus) state.foco.focus();
  }

  function show(which) {
    var dl = which === 'dl';
    $('#viewDl').hidden = !dl;
    $('#viewTools').hidden = dl;
    $('#tabDl').classList.toggle('on', dl);
    $('#tabTools').classList.toggle('on', !dl);
    $('#tabDl').setAttribute('aria-pressed', dl ? 'true' : 'false');
    $('#tabTools').setAttribute('aria-pressed', dl ? 'false' : 'true');
    document.querySelector('.bar').hidden = !dl;
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
      if (!row) return;
      if (e.target.classList.contains('vchk')) {
        if (e.target.checked) state.sel.add(row.dataset.key); else state.sel.delete(row.dataset.key);
        sync();
        return;
      }
      openVale(row.dataset.key);
    });

    $('#list').addEventListener('keydown', function (e) {
      var row = e.target.closest('.vale');
      if (!row || e.target.classList.contains('vchk')) return;
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openVale(row.dataset.key); }
    });

    $('#all').addEventListener('change', function () {
      var rows = visibles();
      if (this.checked) rows.forEach(function (v) { state.sel.add(v.key); });
      else rows.forEach(function (v) { state.sel.delete(v.key); });
      sync();
    });

    $('#expSel').addEventListener('click', function () {
      if (!state.sel.size) return;
      var keys = Array.from(state.sel).join(',');
      location.href = '/export/vales.txt?keys=' + encodeURIComponent(keys);
    });

    $('#tabDl').addEventListener('click', function () { show('dl'); });
    $('#tabTools').addEventListener('click', function () { show('tools'); });
    $('#pClose').addEventListener('click', closeVale);
    $('#scrim').addEventListener('click', closeVale);
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && !$('#panel').hidden) closeVale();
    });
  }

  async function init() {
    bind();
    try {
      var s = await api('/api/summary');
      var labels = [['totalVales', 'Vales'], ['entradas', 'Entradas'], ['despachos', 'Salidas'],
        ['devoluciones', 'Devoluciones'], ['accesorios', 'Accesorios'], ['conteos', 'Conteos']];
      $('#kpis').innerHTML = labels.map(function (x) {
        return '<div class="kpi"><b>' + Number(s[x[0]] || 0) + '</b><span>' + x[1] + '</span></div>';
      }).join('');

      var list = await api('/api/vales');
      state.vales = list.map(function (v) {
        v._hay = norm([v.folio, v.tipoLabel, v.resumen, v.fecha].join(' '));
        return v;
      });
      renderChips();
      render();
    } catch (e) {
      $('#list').innerHTML = '<div class="empty">' + esc(e.message) + '</div>';
    }
  }

  init();
})();
