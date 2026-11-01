/** backend.js -- "Java + MySQL mode": same dashboard, but every number comes from the Java program's REST API. */
(function () {
  "use strict";
  const session = SudarAuth.requireSession();
  if (!session) return;

  const $ = id => document.getElementById(id);
  let data = null, byId = {};
  let base = window.location.protocol === "file:" ? "http://localhost:8080" : window.location.origin;
  try { base = localStorage.getItem("sudar_api_base") || base; } catch (e) { /* ignore */ }
  $("apiBase").value = base;

  // theme + session chip (same as dashboard)
  const savedTheme = (function () { try { return localStorage.getItem("sudar_theme"); } catch (e) { return null; } })() || "govt-light";
  document.documentElement.setAttribute("data-theme", savedTheme); $("themeSelect").value = savedTheme;
  $("themeSelect").addEventListener("change", function () {
    document.documentElement.setAttribute("data-theme", this.value);
    try { localStorage.setItem("sudar_theme", this.value); } catch (e) { /* ignore */ }
  });
  $("sessionName").textContent = session.fullName || session.username;
  $("sessionRole").textContent = session.role;
  $("logoutBtn").addEventListener("click", () => { SudarAuth.logout(); window.location.href = "login.html"; });
  if (session.role === "ADMINISTRATOR") $("adminPanel").style.display = "block";

  const esc = s => String(s).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const name = id => byId[id] ? byId[id].name : id;

  function setConn(ok, text) {
    const pill = $("connState");
    pill.textContent = text; pill.className = "status-pill " + (ok ? "status-DISPATCHED" : "status-UNREACHABLE");
  }

  async function call(path, body) {
    const opt = body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {};
    const res = await fetch(base + path, opt);
    const j = await res.json();
    if (!res.ok) throw new Error(j.error || ("HTTP " + res.status));
    return j;
  }

  async function load() {
    base = $("apiBase").value.trim().replace(/\/+$/, "");
    try { localStorage.setItem("sudar_api_base", base); } catch (e) { /* ignore */ }
    try {
      apply(await call("/api/state"));
    } catch (e) {
      $("liveArea").style.display = "none";
      setConn(false, "Not connected");
      $("sourceNote").innerHTML = "Could not reach the Java program at <b>" + esc(base) + "</b> (" + esc(e.message) + "). " +
        "Start it with <code>run.bat api</code> / <code>./run.sh api</code> or console menu option 10, then press Connect.";
    }
  }

  function apply(d) {
    data = d; byId = {}; d.locations.forEach(l => byId[l.id] = l);
    setConn(true, "Connected");
    $("sourceNote").innerHTML = "Data source of the Java program: <b>" + esc(d.source) + "</b> &middot; " +
      (d.persistent ? "changes are saved in the MySQL database." : "changes stay in memory only (switch the Java program to MySQL with its menu 9).");
    $("liveArea").style.display = "block";
    render();
  }

  function ranked() {   // same 4-tier order as Incident.compareTo in Java
    return data.incidents.slice().sort((a, b) =>
      (b.criticalInfrastructure - a.criticalInfrastructure) || (b.severity - a.severity) ||
      (b.populationExposure - a.populationExposure) || (a.reportedAt - b.reportedAt) || a.id.localeCompare(b.id));
  }

  let formReady = false;
  function render() {
    const open = data.roads.filter(r => !r.blocked).length;
    $("kpiRow").innerHTML = [
      ["Places Mapped", data.locations.length], ["Total Incidents", data.incidents.length], ["Units in Fleet", data.units.length],
      ["Avg. Response Time", data.averageResponseMinutes.toFixed(1) + " min"], ["Roads Open / Closed", open + " / " + (data.roads.length - open)]
    ].map(k => `<div class="kpi"><div class="label">${k[0]}</div><div class="value">${k[1]}</div></div>`).join("");

    SudarMap.render("mapContainer", data);
    $("logFeed").innerHTML = data.log.map(l => `<div>${esc(l)}</div>`).join("");
    $("unitsBody").innerHTML = data.units.map(u =>
      `<tr><td>${esc(u.id)}</td><td><span class="badge badge-${u.type}">${u.type}</span></td><td>${esc(name(u.station))}</td></tr>`).join("");

    $("incidentsBody").innerHTML = ranked().map((i, n) => `
      <tr><td>${n + 1}</td><td>${esc(i.id)}</td><td>${esc(i.type)}</td><td>${esc(name(i.location))}</td>
      <td>${i.criticalInfrastructure ? "Yes" : "No"}</td><td>${i.severity}</td><td>${i.populationExposure}</td>
      <td>${i.needs.map(x => `<span class="badge badge-${x}">${x}</span>`).join(" ")}</td>
      <td><span class="status-pill status-${i.status}">${i.status}</span></td>
      <td>${i.responseMinutes == null ? "&mdash;" : i.responseMinutes.toFixed(1) + " min"}</td></tr>`).join("");

    $("assignmentsBody").innerHTML = data.assignments.map((a, idx) => `
      <tr class="route-row" data-idx="${idx}" style="cursor:pointer" title="Click to show this route on the map">
        <td>${esc(a.incident)}</td><td>${esc(a.unit)}</td><td>${a.path.map(p => esc(name(p))).join(" &rarr; ")}</td>
        <td>${a.km.toFixed(1)} km</td><td>${a.minutes.toFixed(1)} min</td><td>t+${a.arrivalAt.toFixed(1)} min</td></tr>`).join("");
    document.querySelectorAll("#assignmentsBody .route-row").forEach(r => r.addEventListener("click", () => {
      SudarMap.setHighlight(data.assignments[Number(r.getAttribute("data-idx"))].path);
      $("mapContainer").scrollIntoView({ behavior: "smooth", block: "center" });
    }));

    if (!formReady) {
      $("incLoc").innerHTML = data.locations.slice().sort((a, b) => a.name.localeCompare(b.name))
        .map(l => `<option value="${l.id}">${esc(l.name)} (${esc(l.taluk || "-")})</option>`).join("");
      formReady = true;
    }
    renderRoads();
  }

  function renderRoads() {
    const body = $("roadsBody"); if (!body) return;
    const q = $("roadFilter").value.trim().toLowerCase();
    const rows = [];
    data.roads.forEach((r, idx) => {
      const label = name(r.from) + " <-> " + name(r.to);
      if (q && !(label.toLowerCase().includes(q) || r.from.toLowerCase() === q || r.to.toLowerCase() === q)) return;
      rows.push(`<tr><td>${esc(label)}</td><td>${r.km} km</td><td>x${r.traffic}</td>
        <td>${r.blocked ? '<span class="status-pill status-UNREACHABLE">CLOSED</span>' : '<span class="status-pill status-DISPATCHED">OPEN</span>'}</td>
        <td><button class="roadBtn" data-idx="${idx}">${r.blocked ? "Reopen" : "Close"}</button></td></tr>`);
    });
    body.innerHTML = rows.join("");
    body.querySelectorAll(".roadBtn").forEach(b => b.addEventListener("click", async () => {
      const r = data.roads[Number(b.getAttribute("data-idx"))];
      try { apply(await call("/api/road", { from: r.from, to: r.to, blocked: !r.blocked })); } catch (e) { alert(e.message); }
    }));
  }

  $("roadFilter").addEventListener("input", renderRoads);
  $("connectBtn").addEventListener("click", load);
  $("refreshBtn").addEventListener("click", load);
  $("incSubmit").addEventListener("click", async function () {
    const msg = $("incMsg"), type = $("incType").value.trim();
    const needs = Array.from(document.querySelectorAll(".needBox:checked")).map(b => b.value);
    if (!type) { msg.textContent = "Enter an incident type."; return; }
    if (!needs.length) { msg.textContent = "Select at least one unit type."; return; }
    try {
      const j = await call("/api/incident", { type, location: $("incLoc").value, severity: Number($("incSeverity").value), needs });
      apply(j);
      const inc = j.incidents.find(i => i.id === j.created);
      msg.textContent = j.created + ": " + inc.status + (inc.responseMinutes != null ? " -- first unit arrives in " + inc.responseMinutes.toFixed(1) + " min." : " -- no reachable unit.") +
        (j.warning ? "  Warning: " + j.warning : "");
      $("incType").value = "";
      const a = j.assignments.find(x => x.incident === j.created);
      if (a) SudarMap.setHighlight(a.path);
    } catch (e) { msg.textContent = "Error: " + e.message; }
  });

  load();
})();
