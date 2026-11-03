// Pine3 View - personal Home Assistant dashboard (original code, no build step).
// Organises the home as Areas -> Devices -> Entities using hass.areas/devices/entities.

const TOGGLE = new Set(["light", "switch", "fan", "input_boolean", "lock", "cover"]);
const SHOWN = new Set(["light", "switch", "fan", "cover", "lock", "climate", "media_player", "sensor", "binary_sensor", "scene", "script", "vacuum", "camera"]);
const ICONS = { light: "💡", switch: "🔌", fan: "🌀", cover: "🪟", lock: "🔒", climate: "🌡️", media_player: "🎵", sensor: "📊", binary_sensor: "👁️", scene: "🎬", script: "📜", vacuum: "🤖", camera: "📷" };

const css = `
:host{display:block;min-height:100vh;background:#0f1a14;color:#e8f1ec;font-family:system-ui,sans-serif;--accent:#5fd18a;--card:#1a2a21}
header{display:flex;align-items:center;gap:12px;padding:16px 20px;position:sticky;top:0;background:#0f1a14ee;backdrop-filter:blur(8px);z-index:2}
header h1{font-size:20px;margin:0;flex:1}
button.back,button.menu{background:var(--card);color:inherit;border:0;border-radius:10px;padding:8px 12px;font-size:16px;cursor:pointer}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:12px;padding:0 20px 24px}
.card{background:var(--card);border-radius:16px;padding:14px;cursor:pointer;border:2px solid transparent;transition:.15s;user-select:none}
.card.on{border-color:var(--accent);background:#21382b}
.card .ic{font-size:26px}.card .nm{margin-top:8px;font-weight:600;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.card .st{opacity:.7;font-size:12px;margin-top:2px}
.room .cnt{opacity:.7;font-size:12px;margin-top:2px}
.empty{padding:40px 20px;opacity:.6}
`;

class Pine3ViewPanel extends HTMLElement {
  constructor() { super(); this.attachShadow({ mode: "open" }); this._room = null; }
  set hass(h) { this._hass = h; this._render(); }
  set narrow(n) { this._narrow = n; }

  _rooms() {
    const h = this._hass, rooms = new Map();
    for (const a of Object.values(h.areas || {})) rooms.set(a.area_id, { id: a.area_id, name: a.name, ents: [] });
    rooms.set("_none", { id: "_none", name: "Sem divisão", ents: [] });
    for (const e of Object.values(h.entities || {})) {
      const domain = e.entity_id.split(".")[0];
      if (!SHOWN.has(domain) || e.hidden || e.entity_category) continue;
      const area = e.area_id || (h.devices?.[e.device_id]?.area_id) || "_none";
      (rooms.get(area) || rooms.get("_none")).ents.push(e.entity_id);
    }
    return [...rooms.values()].filter((r) => r.ents.length).sort((a, b) => a.name.localeCompare(b.name));
  }

  _isOn(s) { return s && !["off", "closed", "locked", "unavailable", "idle", "standby", "docked"].includes(s.state); }

  _toggle(id) {
    const d = id.split(".")[0], s = this._hass.states[id];
    if (!TOGGLE.has(d) && !["scene", "script"].includes(d)) return;
    if (d === "scene" || d === "script") return this._hass.callService(d, "turn_on", { entity_id: id });
    if (d === "cover") return this._hass.callService("cover", s.state === "open" ? "close_cover" : "open_cover", { entity_id: id });
    if (d === "lock") return this._hass.callService("lock", s.state === "locked" ? "unlock" : "lock", { entity_id: id });
    this._hass.callService("homeassistant", "toggle", { entity_id: id });
  }

  _render() {
    if (!this._hass) return;
    const rooms = this._rooms(), room = rooms.find((r) => r.id === this._room);
    const root = this.shadowRoot;
    let body;
    if (room) {
      const cards = room.ents.map((id) => {
        const s = this._hass.states[id]; if (!s) return "";
        const d = id.split(".")[0], unit = s.attributes.unit_of_measurement || "";
        return `<div class="card ${this._isOn(s) ? "on" : ""}" data-ent="${id}"><div class="ic">${ICONS[d] || "•"}</div><div class="nm">${s.attributes.friendly_name || id}</div><div class="st">${s.state} ${unit}</div></div>`;
      }).join("");
      body = `<div class="grid">${cards}</div>`;
    } else {
      body = rooms.length ? `<div class="grid">${rooms.map((r) => {
        const on = r.ents.filter((i) => this._isOn(this._hass.states[i]) && TOGGLE.has(i.split(".")[0])).length;
        return `<div class="card room ${on ? "on" : ""}" data-room="${r.id}"><div class="ic">🏠</div><div class="nm">${r.name}</div><div class="cnt">${r.ents.length} dispositivos${on ? ` · ${on} ligados` : ""}</div></div>`;
      }).join("")}</div>` : `<div class="empty">Sem divisões ou entidades encontradas.</div>`;
    }
    root.innerHTML = `<style>${css}</style><header>${this._narrow ? '<button class="menu">☰</button>' : ""}${room ? '<button class="back">←</button>' : ""}<h1>${room ? room.name : "Pine3 View"}</h1></header>${body}`;
    root.querySelector(".back")?.addEventListener("click", () => { this._room = null; this._render(); });
    root.querySelector(".menu")?.addEventListener("click", () => this.dispatchEvent(new Event("hass-toggle-menu", { bubbles: true, composed: true })));
    root.querySelectorAll("[data-room]").forEach((el) => el.addEventListener("click", () => { this._room = el.dataset.room; this._render(); }));
    root.querySelectorAll("[data-ent]").forEach((el) => el.addEventListener("click", () => this._toggle(el.dataset.ent)));
  }
}
customElements.define("pine3-view-panel", Pine3ViewPanel);
