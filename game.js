// Frontend logic. Every action is sent to the Java backend with fetch().
const $ = (sel) => document.querySelector(sel);

async function api(path, options) {
  const res = await fetch("/api/game" + path, options);
  let body = null;
  try { body = await res.json(); } catch (e) { /* empty body */ }
  if (!res.ok) throw new Error((body && body.message) || "Request failed (" + res.status + ")");
  return body;
}

function esc(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function toast(msg, isError = false) {
  const t = document.createElement("div");
  t.className = "toast" + (isError ? " error" : "");
  t.setAttribute("role", "status");
  t.textContent = msg;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 4000);
}

// Wraps a handler so any error from the server shows up as a message instead of crashing.
const guard = (fn) => async (...args) => {
  try { await fn(...args); } catch (e) { toast(e.message, true); }
};

/* ---------- Screen 1: Case files ---------- */
async function initHome() {
  const c = await api("/case");
  $("#title").textContent = c.title;
  $("#tagline").textContent = c.tagline;
  $("#briefing").textContent = c.briefing;
  const p = c.progress;
  $("#stats").innerHTML =
    `<div><b>${p.cluesFound}/${p.totalClues}</b><span>clues found</span></div>` +
    `<div><b>${p.questionsAsked}</b><span>questions asked</span></div>` +
    `<div><b>${p.finished ? "Solved" : "Open"}</b><span>case status</span></div>`;
  $("#reset").onclick = guard(async () => {
    if (!confirm("Erase all progress and start the case again?")) return;
    await api("/reset", { method: "POST" });
    await initHome();
    toast("Case reset. Good luck, detective.");
  });
}

/* ---------- Screen 2: Suspect room ---------- */
async function renderSuspects() {
  const list = await api("/suspects");
  $("#suspects").innerHTML = list.map((s) => `
    <article class="card">
      <h3>${esc(s.name)}</h3>
      <span class="role">${esc(s.role)}</span>
      <p>${esc(s.bio)}</p>
      <p><b>Motive:</b> ${esc(s.motive)}</p>
      <p><b>Alibi:</b> ${esc(s.alibi)}</p>
      <div class="qa">
        ${s.questions.map((q) => `
          <button class="q ${q.asked ? "asked" : ""}" data-s="${esc(s.id)}" data-q="${esc(q.id)}"
                  ${q.asked ? 'aria-disabled="true"' : ""}>${esc(q.text)}</button>
          ${q.asked ? `<p class="answer">${esc(q.answer)}</p>` : ""}`).join("")}
      </div>
    </article>`).join("");
}

function initSuspects() {
  $("#suspects").addEventListener("click", guard(async (e) => {
    const btn = e.target.closest(".q");
    if (!btn || btn.classList.contains("asked")) return;
    const r = await api(`/suspects/${btn.dataset.s}/questions/${btn.dataset.q}`, { method: "POST" });
    if (r.newLeads.length) toast("New evidence available: " + r.newLeads.join(", "));
    await renderSuspects();
  }));
  return renderSuspects();
}

/* ---------- Screen 3: Evidence lab ---------- */
async function renderEvidence() {
  const [clues, suspects] = await Promise.all([api("/clues"), api("/suspects")]);
  $("#clues").innerHTML = clues.map((c) => `
    <article class="card ${c.status.toLowerCase()}">
      <span class="status">${c.status === "DISCOVERED" ? "Inspected" : c.status === "AVAILABLE" ? "Ready to inspect" : "Locked"}</span>
      <h3>${esc(c.title)}</h3>
      <span class="meta">${esc(c.location)}</span>
      ${c.description ? `<p>${esc(c.description)}</p>` : ""}
      ${c.status === "LOCKED" ? `<p>${esc(c.lockedReason)}</p>` : ""}
      ${c.status === "AVAILABLE" ? `<button class="btn" data-clue="${esc(c.id)}" type="button">Inspect clue</button>` : ""}
    </article>`).join("");

  const found = clues.filter((c) => c.status === "DISCOVERED");
  $("#cmp-clue").innerHTML = found.length
    ? found.map((c) => `<option value="${esc(c.id)}">${esc(c.title)}</option>`).join("")
    : `<option value="">No clues inspected yet</option>`;
  $("#cmp-suspect").innerHTML = suspects.map((s) => `<option value="${esc(s.id)}">${esc(s.name)}</option>`).join("");
}

function initEvidence() {
  $("#clues").addEventListener("click", guard(async (e) => {
    const btn = e.target.closest("[data-clue]");
    if (!btn) return;
    await api(`/clues/${btn.dataset.clue}/inspect`, { method: "POST" });
    await renderEvidence();
  }));
  $("#cmp-go").addEventListener("click", guard(async () => {
    const clueId = $("#cmp-clue").value;
    if (!clueId) { toast("Inspect a clue first.", true); return; }
    const r = await api(`/compare?clueId=${encodeURIComponent(clueId)}&suspectId=${encodeURIComponent($("#cmp-suspect").value)}`);
    const out = $("#cmp-result");
    out.hidden = false;
    out.className = "result " + r.result;
    out.textContent = r.message;
  }));
  return renderEvidence();
}

/* ---------- Screen 4: Final verdict ---------- */
async function initVerdict() {
  const [clues, suspects] = await Promise.all([api("/clues"), api("/suspects")]);
  $("#v-suspects").innerHTML = suspects.map((s, i) =>
    `<label><input type="radio" name="suspect" value="${esc(s.id)}" ${i === 0 ? "checked" : ""}> ${esc(s.name)} (${esc(s.role)})</label>`).join("");
  const found = clues.filter((c) => c.status === "DISCOVERED");
  $("#v-clues").innerHTML = found.length
    ? found.map((c) => `<label><input type="checkbox" name="clue" value="${esc(c.id)}"> ${esc(c.title)}</label>`).join("")
    : `<p>You haven't inspected any evidence yet. Visit the evidence lab first.</p>`;

  $("#v-go").onclick = guard(async () => {
    const suspectId = document.querySelector('input[name="suspect"]:checked').value;
    const clueIds = [...document.querySelectorAll('input[name="clue"]:checked')].map((x) => x.value);
    const r = await api("/accuse", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ suspectId, clueIds }),
    });
    const box = $("#v-result");
    box.hidden = false;
    box.className = "folder verdict " + (r.correct ? "win" : "lose");
    box.innerHTML = `<h2>${r.correct ? "Case closed!" : "Wrong suspect"}</h2>
      <p class="score">${esc(r.score)}/100</p><h3>${esc(r.rank)}</h3><p>${esc(r.explanation)}</p>
      ${r.correct ? `<a class="btn" href="index.html">Back to case files</a>` : ""}`;
    box.scrollIntoView({ behavior: "smooth" });
  });
}

/* ---------- start ---------- */
const page = document.body.dataset.page;
document.querySelectorAll("nav a").forEach((a) => {
  if (location.pathname.endsWith(a.getAttribute("href"))) a.classList.add("active");
});
if (location.pathname === "/") document.querySelector('nav a[href="index.html"]').classList.add("active");
const starters = { home: initHome, suspects: initSuspects, evidence: initEvidence, verdict: initVerdict };
if (starters[page]) guard(starters[page])();
