(function () {
  "use strict";

  const session = SudarAuth.requireSession();
  if (!session) return; // requireSession already redirected to login.html

  // Work on a deep clone so admin road toggles don't mutate the loaded dataset module.
  const data = JSON.parse(JSON.stringify(SUDAR_DATA));

  const byId = {};
  data.locations.forEach(l => byId[l.id] = l);
  SudarDispatch.run(data);   // compute assignments, statuses and log live (Dijkstra)

  // ---------------- Theme ----------------
  const themeSelect = document.getElementById("themeSelect");
  const savedTheme = localStorage.getItem("sudar_theme") || "govt-light";
  document.documentElement.setAttribute("data-theme", savedTheme);
  themeSelect.value = savedTheme;
  themeSelect.addEventListener("change", function () {
    document.documentElement.setAttribute("data-theme", themeSelect.value);
    localStorage.setItem("sudar_theme", themeSelect.value);
  });

  // ---------------- Session chip / sign out ----------------
  document.getElementById("sessionName").textContent = session.fullName || session.username;
  document.getElementById("sessionRole").textContent = session.role;
  document.getElementById("logoutBtn").addEventListener("click", function () {
    SudarAuth.logout();
    window.location.href = "login.html";
  });
  document.getElementById("townLabel").textContent = data.town + " \u00b7 Prototype / Academic Demo";

  if (session.role === "ADMINISTRATOR") {
    document.getElementById("adminPanel").style.display = "block";
  }

  // ---------------- KPIs ----------------
  function renderKpis() {
    const openRoads = data.roads.filter(r => !r.blocked).length;
    const blockedRoads = data.roads.length - openRoads;
    const kpis = [
      { label: "Places Mapped", value: data.locations.length },
      { label: "Total Incidents", value: data.incidents.length },
      { label: "Units in Fleet", value: data.units.length },
      { label: "Avg. Response Time", value: data.averageResponseMinutes.toFixed(1) + " min" },
      { label: "Roads Open / Closed", value: openRoads + " / " + blockedRoads }
    ];
    const row = document.getElementById("kpiRow");
    row.innerHTML = kpis.map(k =>
      `<div class="kpi"><div class="label">${k.label}</div><div class="value">${k.value}</div></div>`
    ).join("");
  }

  // ---------------- Map + legend ----------------
  function renderMap() {
    SudarMap.render("mapContainer", data);
    const legend = document.getElementById("mapLegend");
    legend.innerHTML = `
      <span><span class="dot" style="background:#0b7a3b"></span>Light traffic</span>
      <span><span class="dot" style="background:#b8860b"></span>Moderate traffic</span>
      <span><span class="dot" style="background:#b3261e"></span>Heavy traffic / closed road</span>
      <span>\u25B2 Station &nbsp; + Hospital &nbsp; \u25C6 Market &nbsp; \u25A0 Industrial &nbsp; \u2022 Junction</span>
    `;
  }

  // ---------------- Units ----------------
  function renderUnits() {
    const body = document.getElementById("unitsBody");
    body.innerHTML = data.units.map(u => `
      <tr>
        <td>${u.id}</td>
        <td><span class="badge badge-${u.type}">${u.type}</span></td>
        <td>${byId[u.station] ? byId[u.station].name : u.station}</td>
      </tr>
    `).join("");
  }

  // ---------------- Incidents ----------------
  function renderIncidents() {
    const body = document.getElementById("incidentsBody");
    body.innerHTML = data.incidents.map(i => `
      <tr>
        <td>${i.id}</td>
        <td>${i.type}</td>
        <td>${byId[i.location] ? byId[i.location].name : i.location}</td>
        <td>${i.severity}</td>
        <td>${i.needs.map(n => `<span class="badge badge-${n}">${n}</span>`).join(" ")}</td>
        <td><span class="status-pill status-${i.status}">${i.status}</span></td>
        <td>${i.responseMinutes == null ? "&mdash;" : i.responseMinutes.toFixed(1) + " min"}</td>
      </tr>
    `).join("");
  }

  // ---------------- Assignments ----------------
  function renderAssignments() {
    const body = document.getElementById("assignmentsBody");
    body.innerHTML = data.assignments.map(a => `
      <tr class="route-row" data-idx="${data.assignments.indexOf(a)}" style="cursor:pointer" title="Click to show this route on the map">
        <td>${a.incident}</td>
        <td>${a.unit}</td>
        <td>${a.path.map(id => byId[id] ? byId[id].name : id).join(" &rarr; ")}</td>
        <td>${a.km.toFixed(1)} km</td>
        <td>${a.minutes.toFixed(1)} min</td>
        <td>t+${a.arrivalAt.toFixed(1)} min</td>
      </tr>
    `).join("");
    body.querySelectorAll(".route-row").forEach(row => row.addEventListener("click", function () {
      const a = data.assignments[Number(row.getAttribute("data-idx"))];
      SudarMap.setHighlight(a.path);
      document.getElementById("mapContainer").scrollIntoView({ behavior: "smooth", block: "center" });
    }));
  }

  // ---------------- Log ----------------
  function renderLog() {
    const feed = document.getElementById("logFeed");
    feed.innerHTML = data.log.map(line => `<div>${line}</div>`).join("");
  }

  // ---------------- Admin: road controls ----------------
  function renderRoadsAdmin() {
    const body = document.getElementById("roadsBody");
    if (!body) return;
    body.innerHTML = data.roads.map((r, idx) => `
      <tr>
        <td>${byId[r.from] ? byId[r.from].name : r.from} \u2194 ${byId[r.to] ? byId[r.to].name : r.to}</td>
        <td>${r.km} km</td>
        <td>x${r.traffic}</td>
        <td>${r.blocked
          ? '<span class="status-pill status-blocked" style="background:#fdeaea;color:#b3261e;">CLOSED</span>'
          : '<span class="status-pill status-DISPATCHED">OPEN</span>'}
        </td>
        <td><button data-idx="${idx}" class="toggleRoadBtn">${r.blocked ? "Reopen" : "Close"}</button></td>
      </tr>
    `).join("");

    body.querySelectorAll(".toggleRoadBtn").forEach(btn => {
      btn.addEventListener("click", function () {
        const idx = Number(btn.getAttribute("data-idx"));
        data.roads[idx].blocked = !data.roads[idx].blocked;
        refresh();
      });
    });
  }

  // ---------------- Places directory + search + taluk filter ----------------
  function renderPlaces() {
    document.getElementById("placeCount").textContent = "(" + data.locations.length + " places)";
    document.getElementById("placesBody").innerHTML = data.locations.map(l => `
      <tr class="place-row" data-id="${l.id}" style="cursor:pointer">
        <td>${l.id}</td><td>${l.name}</td><td>${l.type}</td><td>${l.taluk || "-"}</td>
        <td>${l.lat != null ? l.lat.toFixed(4) + ", " + l.lon.toFixed(4) : "-"}</td>
      </tr>`).join("");
    document.querySelectorAll(".place-row").forEach(r => r.addEventListener("click", () => {
      SudarMap.focus(r.getAttribute("data-id"), 3.5);
      document.getElementById("mapContainer").scrollIntoView({ behavior: "smooth", block: "center" });
    }));
  }

  function setupMapTools() {
    const list = document.getElementById("placeList");
    list.innerHTML = data.locations.map(l => `<option value="${l.name}"></option>`).join("");
    const taluks = Array.from(new Set(data.locations.map(l => l.taluk).filter(Boolean))).sort();
    const tf = document.getElementById("talukFilter");
    tf.innerHTML += taluks.map(t => `<option>${t}</option>`).join("");
    tf.addEventListener("change", () => SudarMap.setTaluk(tf.value));
    document.getElementById("placeSearch").addEventListener("change", function () {
      const q = this.value.trim().toLowerCase();
      const hit = data.locations.find(l => l.name.toLowerCase() === q) ||
                  data.locations.find(l => l.name.toLowerCase().includes(q));
      if (hit) SudarMap.focus(hit.id, 3.5);
    });
    document.getElementById("zoomIn").addEventListener("click", () => SudarMap.zoomIn());
    document.getElementById("zoomOut").addEventListener("click", () => SudarMap.zoomOut());
    document.getElementById("zoomReset").addEventListener("click", () => SudarMap.reset());
  }

  // ---------------- Report new incident ----------------
  function setupIncidentForm() {
    const sel = document.getElementById("incLoc");
    sel.innerHTML = data.locations.slice().sort((a, b) => a.name.localeCompare(b.name))
      .map(l => `<option value="${l.id}">${l.name} (${l.taluk || "-"})</option>`).join("");
    document.getElementById("incSubmit").addEventListener("click", function () {
      const type = document.getElementById("incType").value.trim();
      const needs = Array.from(document.querySelectorAll(".needBox:checked")).map(b => b.value);
      const msg = document.getElementById("incMsg");
      if (!type) { msg.textContent = "Enter an incident type."; return; }
      if (!needs.length) { msg.textContent = "Select at least one unit type."; return; }
      const last = data.incidents.reduce((m, i) => Math.max(m, i.reportedAt), 0);
      const id = "INC-" + String(data.incidents.length + 1).padStart(2, "0");
      data.incidents.push({ id, type, location: sel.value, severity: Number(document.getElementById("incSeverity").value),
        reportedAt: last + 3, needs });
      refresh();
      const inc = data.incidents.find(i => i.id === id);
      msg.textContent = id + ": " + inc.status + (inc.responseMinutes != null ? " -- first unit arrives in " + inc.responseMinutes.toFixed(1) + " min." : " -- no reachable unit.");
      document.getElementById("incType").value = "";
      const a = data.assignments.filter(x => x.incident === id)[0];
      if (a) SudarMap.setHighlight(a.path);
    });
  }

  // Re-run dispatch (e.g. after a road closure or new incident) and redraw everything.
  function refresh() {
    SudarDispatch.run(data);
    renderKpis(); renderMap(); renderUnits(); renderIncidents();
    renderAssignments(); renderLog(); renderRoadsAdmin();
  }

  refresh();
  renderPlaces();
  setupMapTools();
  setupIncidentForm();
})();
