/** dispatch.js -- live dispatch engine: Dijkstra routing + priority replay. */
(function (g) {
  "use strict";
  const SPEED_KMH = 80, ON_SCENE_MIN = 20;
  const minutes = r => r.km * r.traffic * 60 / SPEED_KMH;

  function graph(data) {
    const adj = {};
    data.locations.forEach(l => adj[l.id] = []);
    data.roads.forEach(r => {
      if (r.blocked || !adj[r.from] || !adj[r.to]) return;
      const w = minutes(r);
      adj[r.from].push({ to: r.to, w, km: r.km });
      adj[r.to].push({ to: r.from, w, km: r.km });
    });
    return adj;
  }

  // Dijkstra from src -> {dist, km, prev}
  function dijkstra(adj, src) {
    const dist = {}, km = {}, prev = {}, done = new Set();
    Object.keys(adj).forEach(k => dist[k] = Infinity);
    dist[src] = 0; km[src] = 0;
    while (true) {
      let u = null, best = Infinity;
      for (const k in dist) if (!done.has(k) && dist[k] < best) { best = dist[k]; u = k; }
      if (u === null) break;
      done.add(u);
      adj[u].forEach(e => {
        if (dist[u] + e.w < dist[e.to]) { dist[e.to] = dist[u] + e.w; km[e.to] = km[u] + e.km; prev[e.to] = u; }
      });
    }
    return { dist, km, prev };
  }

  function pathTo(prev, src, dst) {
    const p = [dst]; let c = dst;
    while (c !== src) { c = prev[c]; if (!c) return null; p.unshift(c); }
    return p;
  }

  const f1 = n => n.toFixed(1).padStart(5);

  // Replays every incident in report order (highest severity first on ties).
  function run(data) {
    const adj = graph(data), cache = {};
    const spt = s => cache[s] || (cache[s] = dijkstra(adj, s));
    const busy = {}; data.units.forEach(u => busy[u.id] = 0);
    const assignments = [], log = [];
    const incs = data.incidents.slice().sort((a, b) => a.reportedAt - b.reportedAt || b.severity - a.severity);
    incs.forEach(inc => {
      log.push(`t=${f1(inc.reportedAt)}  REPORT   ${inc.id} ${inc.type} at ${inc.location} (severity ${inc.severity})`);
      const t = spt(inc.location);
      const arrivals = []; let delayed = false, missing = 0;
      inc.needs.forEach(need => {
        let best = null;
        data.units.filter(u => u.type === need).forEach(u => {
          const d = t.dist[u.station];
          if (!isFinite(d)) return;
          const start = Math.max(inc.reportedAt, busy[u.id]);
          const arr = start + d;
          if (!best || arr < best.arr) best = { u, start, arr, d };
        });
        if (!best) { missing++; log.push(`t=${f1(inc.reportedAt)}  NO UNIT  ${need} unavailable/unreachable for ${inc.id}`); return; }
        const path = pathTo(t.prev, inc.location, best.u.station);
        const route = path ? path.slice().reverse() : [best.u.station];
        busy[best.u.id] = best.arr + ON_SCENE_MIN;
        if (best.start > inc.reportedAt) delayed = true;
        arrivals.push(best.arr);
        assignments.push({ incident: inc.id, unit: best.u.id, path: route, minutes: best.d,
          km: t.km[best.u.station], dispatchedAt: best.start, arrivalAt: best.arr });
        log.push(`t=${f1(best.start)}  DISPATCH ${best.u.id} -> ${inc.id} (${route.length - 1} hops, ${best.d.toFixed(1)} min)`);
      });
      inc.status = arrivals.length === 0 ? "UNREACHABLE" : (delayed || missing) ? "QUEUED" : "DISPATCHED";
      inc.responseMinutes = arrivals.length ? Math.min(...arrivals) - inc.reportedAt : null;
    });
    const rt = incs.filter(i => i.responseMinutes !== null).map(i => i.responseMinutes);
    data.averageResponseMinutes = rt.length ? rt.reduce((a, b) => a + b, 0) / rt.length : 0;
    data.assignments = assignments; data.log = log;
    return data;
  }

  g.SudarDispatch = { run, SPEED_KMH, ON_SCENE_MIN };
})(window);
