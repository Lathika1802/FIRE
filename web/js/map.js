/**
 * map.js -- district map from SUDAR_DATA using each place's x/y position.
 * v2: built for 100+ places -- wheel/drag/button zoom + pan, zoom-dependent
 * labels, taluk filter, search-and-focus, and dispatch-route highlighting.
 */
(function (global) {
  "use strict";

  const TYPE_STYLE = {
    STATION:     { color: "#0b2d4e", glyph: "\u25B2", label: "Station" },
    HOSPITAL:    { color: "#b3261e", glyph: "+",      label: "Hospital" },
    SCHOOL:      { color: "#1a56a8", glyph: "\u25CF", label: "School" },
    MARKET:      { color: "#8a5a00", glyph: "\u25C6", label: "Market" },
    INDUSTRIAL:  { color: "#6a4c93", glyph: "\u25A0", label: "Industrial" },
    RESIDENTIAL: { color: "#0b7a3b", glyph: "\u25CF", label: "Residential" },
    JUNCTION:    { color: "#5a6572", glyph: "\u2022", label: "Junction" }
  };
  const PX_PER_KM = 8; // matches tools/build_data.py

  const state = { containerId: null, data: null, view: null, full: null,
                  taluk: "", highlight: [], focusId: null, raf: 0 };

  function trafficColor(t) { return t >= 1.3 ? "#b3261e" : t >= 1.1 ? "#b8860b" : "#0b7a3b"; }
  function el(tag, attrs, parent) {
    const n = document.createElementNS("http://www.w3.org/2000/svg", tag);
    for (const k in attrs || {}) n.setAttribute(k, attrs[k]);
    if (parent) parent.appendChild(n);
    return n;
  }

  function fullView(data) {
    const PAD = 50, xs = data.locations.map(l => l.x), ys = data.locations.map(l => l.y);
    const minX = Math.min(...xs) - PAD, minY = Math.min(...ys) - PAD;
    return { x: minX, y: minY, w: Math.max(...xs) + PAD - minX, h: Math.max(...ys) + PAD - minY };
  }

  function draw() {
    const { data, view, full } = state;
    const container = document.getElementById(state.containerId);
    container.innerHTML = "";
    const k = full.w / view.w;               // zoom factor (1 = whole district)
    const svg = el("svg", {
      viewBox: `${view.x} ${view.y} ${view.w} ${view.h}`, class: "road-map-svg",
      width: "100%", height: "100%", role: "img",
      "aria-label": "Road network map of " + data.town
    }, container);
    el("rect", { x: full.x, y: full.y, width: full.w, height: full.h, rx: 24,
      fill: "var(--bg)", stroke: "var(--panel-border)", "stroke-width": 1 / k }, svg);

    const byId = {}; data.locations.forEach(l => byId[l.id] = l);
    const incAt = {};
    (data.incidents || []).forEach(i => incAt[i.location] = (incAt[i.location] || 0) + 1);
    const visible = l => !state.taluk || l.taluk === state.taluk;

    // roads
    data.roads.forEach(r => {
      const a = byId[r.from], b = byId[r.to];
      if (!a || !b) return;
      const dim = state.taluk && !(visible(a) || visible(b));
      const line = el("line", { x1: a.x, y1: a.y, x2: b.x, y2: b.y,
        stroke: r.blocked ? "#b3261e" : trafficColor(r.traffic),
        "stroke-width": (r.blocked ? 3 : 2) / k,
        "stroke-dasharray": r.blocked ? `${6 / k},${5 / k}` : "none",
        opacity: dim ? 0.08 : (r.blocked ? 0.9 : 0.55) }, svg);
      el("title", {}, line).textContent =
        `${a.name} \u2194 ${b.name}: ${r.km} km, traffic x${r.traffic}${r.blocked ? " -- CLOSED" : ""}`;
      if (r.blocked) {
        const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
        el("circle", { cx: mx, cy: my, r: 8 / k, fill: "#b3261e" }, svg);
        const t = el("text", { x: mx, y: my + 3.5 / k, "text-anchor": "middle",
          "font-size": 10 / k, fill: "#fff", "font-weight": "700" }, svg);
        t.textContent = "\u2715";
      }
    });

    // dispatch route highlight
    if (state.highlight.length > 1) {
      const pts = state.highlight.map(id => byId[id]).filter(Boolean).map(l => `${l.x},${l.y}`).join(" ");
      const pl = el("polyline", { points: pts, class: "route" }, svg);
      pl.setAttribute("stroke-width", 4 / k);
    }

    // markers -- smaller when zoomed out; labels appear as you zoom in
    const R = Math.max(5, 12) / k * (k < 1.8 ? 0.62 : 0.9);
    data.locations.forEach(loc => {
      const st = TYPE_STYLE[loc.type] || TYPE_STYLE.JUNCTION;
      const dim = state.taluk && !visible(loc);
      const g = el("g", { opacity: dim ? 0.15 : 1 }, svg);
      const focus = state.focusId === loc.id;
      if (focus) el("circle", { cx: loc.x, cy: loc.y, r: R * 2.1, fill: "none", stroke: "#e0a100", "stroke-width": 3 / k }, g);
      el("circle", { cx: loc.x, cy: loc.y, r: R, fill: "var(--panel)", stroke: st.color, "stroke-width": 2 / k }, g);
      if (k >= 1.5 || loc.type === "STATION" || loc.type === "HOSPITAL") {
        const gl = el("text", { x: loc.x, y: loc.y + R * 0.35, "text-anchor": "middle",
          "font-size": R * 1.05, fill: st.color, "font-weight": "700" }, g);
        gl.textContent = st.glyph;
      }
      const major = loc.type === "STATION" || loc.type === "HOSPITAL" || loc.type === "MARKET";
      if (focus || k >= 3 || (k >= 1.8 && major) || (k >= 2.4)) {
        const lab = el("text", { x: loc.x, y: loc.y + R + 11 / k, "text-anchor": "middle",
          "font-size": 10 / k * (k > 3 ? 1 : 1.15), fill: "var(--text)", "font-weight": "600",
          "paint-order": "stroke", stroke: "var(--panel)", "stroke-width": 3 / k }, g);
        lab.textContent = k >= 3 || focus ? loc.name : loc.id;
      }
      el("title", {}, g).textContent = `${loc.name} (${st.label}, ${loc.taluk || "-"})`;
      if (incAt[loc.id]) {
        el("circle", { cx: loc.x + R, cy: loc.y - R, r: R * 0.7, fill: "#b3261e" }, g);
        const b = el("text", { x: loc.x + R, y: loc.y - R + R * 0.25, "text-anchor": "middle",
          "font-size": R * 0.8, fill: "#fff", "font-weight": "700" }, g);
        b.textContent = incAt[loc.id];
      }
      g.style.cursor = "pointer";
      g.addEventListener("click", () => { focusOn(loc.id, 3); });
    });

    // compass + scale bar (fixed on screen, in view units)
    const cx = view.x + 28 / k * 1.1, cy = view.y + 28 / k * 1.1;
    el("circle", { cx, cy, r: 16 / k, fill: "var(--panel)", stroke: "var(--panel-border)" }, svg);
    const n = el("text", { x: cx, y: cy - 4 / k, "text-anchor": "middle", "font-size": 10 / k, "font-weight": "700", fill: "var(--text)" }, svg);
    n.textContent = "N";
    el("line", { x1: cx, y1: cy + 9 / k, x2: cx, y2: cy - 1 / k, stroke: "var(--text)", "stroke-width": 2 / k }, svg);
    const barKm = k >= 4 ? 5 : k >= 2 ? 10 : 20, barPx = PX_PER_KM * barKm;
    const bx = view.x + view.w - 20 / k - barPx, by = view.y + view.h - 18 / k;
    [[bx, by, bx + barPx, by], [bx, by - 4 / k, bx, by + 4 / k], [bx + barPx, by - 4 / k, bx + barPx, by + 4 / k]]
      .forEach(a => el("line", { x1: a[0], y1: a[1], x2: a[2], y2: a[3], stroke: "var(--text)", "stroke-width": 2 / k }, svg));
    const sl = el("text", { x: bx + barPx / 2, y: by - 7 / k, "text-anchor": "middle", "font-size": 10 / k, fill: "var(--muted)" }, svg);
    sl.textContent = `~${barKm} km`;
  }

  function schedule() { cancelAnimationFrame(state.raf); state.raf = requestAnimationFrame(draw); }

  function clamp() {
    const v = state.view, f = state.full;
    v.x = Math.min(Math.max(v.x, f.x - v.w * 0.25), f.x + f.w - v.w * 0.75);
    v.y = Math.min(Math.max(v.y, f.y - v.h * 0.25), f.y + f.h - v.h * 0.75);
  }

  function zoom(factor, cx, cy) {
    const v = state.view, f = state.full;
    const nw = Math.min(f.w, Math.max(f.w / 12, v.w / factor));
    const nh = nw * f.h / f.w;
    cx = cx == null ? v.x + v.w / 2 : cx; cy = cy == null ? v.y + v.h / 2 : cy;
    v.x = cx - (cx - v.x) * nw / v.w; v.y = cy - (cy - v.y) * nh / v.h; v.w = nw; v.h = nh;
    clamp(); schedule();
  }

  function focusOn(id, k) {
    const l = state.data.locations.find(x => x.id === id);
    if (!l) return;
    const f = state.full, w = f.w / (k || 3), h = w * f.h / f.w;
    state.view = { x: l.x - w / 2, y: l.y - h / 2, w, h };
    state.focusId = id; clamp(); schedule();
  }

  function attachInteractions(container) {
    let drag = null;
    container.addEventListener("wheel", e => {
      e.preventDefault();
      const r = container.getBoundingClientRect(), v = state.view;
      zoom(e.deltaY < 0 ? 1.25 : 0.8, v.x + (e.clientX - r.left) / r.width * v.w, v.y + (e.clientY - r.top) / r.height * v.h);
    }, { passive: false });
    container.addEventListener("pointerdown", e => { drag = { x: e.clientX, y: e.clientY, moved: false }; });
    window.addEventListener("pointermove", e => {
      if (!drag) return;
      const r = container.getBoundingClientRect(), v = state.view;
      const dx = e.clientX - drag.x, dy = e.clientY - drag.y;
      if (Math.abs(dx) + Math.abs(dy) > 3) { drag.moved = true; container.classList.add("dragging"); }
      v.x -= dx / r.width * v.w; v.y -= dy / r.height * v.h;
      drag.x = e.clientX; drag.y = e.clientY; clamp(); schedule();
    });
    window.addEventListener("pointerup", () => { drag = null; container.classList.remove("dragging"); });
  }

  const api = {
    render(containerId, data) {
      const first = state.containerId !== containerId;
      state.containerId = containerId; state.data = data; state.full = fullView(data);
      if (first || !state.view) {
        state.view = Object.assign({}, state.full);
        attachInteractions(document.getElementById(containerId));
      }
      draw();
    },
    zoomIn() { zoom(1.5); }, zoomOut() { zoom(1 / 1.5); },
    reset() { state.view = Object.assign({}, state.full); state.focusId = null; state.highlight = []; schedule(); },
    focus: focusOn,
    setTaluk(t) { state.taluk = t || ""; schedule(); },
    setHighlight(path) { state.highlight = path || []; schedule(); }
  };
  global.SudarMap = api;
})(window);
