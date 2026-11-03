// Pine3 View - personal Home Assistant dashboard. Original code, no build step.
// Organises the home as Areas -> Devices -> Entities using hass.areas/devices/entities.

const SHOWN = new Set([
  "light", "switch", "fan", "cover", "lock", "climate", "media_player", "sensor",
  "binary_sensor", "scene", "script", "vacuum", "camera", "input_boolean", "button",
]);
const TOGGLE = new Set(["light", "switch", "fan", "input_boolean", "lock", "cover", "media_player"]);
const DETAIL = new Set(["light", "cover", "climate", "media_player", "fan"]);

const SECTIONS = [
  ["Luzes", ["light"]],
  ["Clima", ["climate", "fan"]],
  ["Cortinas e estores", ["cover"]],
  ["Multimédia", ["media_player"]],
  ["Segurança", ["lock", "camera"]],
  ["Tomadas e interruptores", ["switch", "input_boolean"]],
  ["Ações", ["script", "button", "vacuum"]],
  ["Sensores", ["sensor", "binary_sensor"]],
];

const DOMAIN_ICON = {
  light: "mdi:lightbulb", switch: "mdi:power-socket-eu", fan: "mdi:fan", cover: "mdi:window-shutter",
  lock: "mdi:lock", climate: "mdi:thermostat", media_player: "mdi:speaker", sensor: "mdi:gauge",
  binary_sensor: "mdi:checkbox-blank-circle-outline", scene: "mdi:palette", script: "mdi:script-text-play",
  vacuum: "mdi:robot-vacuum", camera: "mdi:cctv", input_boolean: "mdi:toggle-switch", button: "mdi:gesture-tap-button",
};
const DEVICE_CLASS_ICON = {
  temperature: "mdi:thermometer", humidity: "mdi:water-percent", power: "mdi:flash", energy: "mdi:lightning-bolt",
  door: "mdi:door", window: "mdi:window-closed-variant", motion: "mdi:motion-sensor", battery: "mdi:battery",
  illuminance: "mdi:brightness-5", garage_door: "mdi:garage", blind: "mdi:blinds", curtain: "mdi:curtains",
};
const ACCENT = {
  light: "#ffb340", switch: "#5ac8fa", fan: "#64d2ff", cover: "#5e9cff", lock: "#30d158", climate: "#ff9f0a",
  media_player: "#bf5af2", input_boolean: "#5ac8fa", vacuum: "#30d158", script: "#5e5ce6", button: "#5e5ce6",
  binary_sensor: "#ff453a", camera: "#8e8e93",
};
const STATE_PT = {
  on: "Ligado", off: "Desligado", open: "Aberto", closed: "Fechado", opening: "A abrir", closing: "A fechar",
  locked: "Trancado", unlocked: "Destrancado", playing: "A tocar", paused: "Em pausa", idle: "Inativo",
  standby: "Em espera", unavailable: "Indisponível", unknown: "Desconhecido", heat: "Aquecer", cool: "Arrefecer",
  auto: "Automático", heat_cool: "Aquecer/Arrefecer", dry: "Desumidificar", fan_only: "Ventilação",
  cleaning: "A limpar", docked: "Na base", returning: "A regressar",
};
const WEATHER_PT = {
  "clear-night": "Céu limpo", cloudy: "Nublado", exceptional: "Excecional", fog: "Nevoeiro", hail: "Granizo",
  lightning: "Trovoada", "lightning-rainy": "Trovoada e chuva", partlycloudy: "Pouco nublado", pouring: "Chuva forte",
  rainy: "Chuva", snowy: "Neve", "snowy-rainy": "Chuva e neve", sunny: "Sol", windy: "Vento", "windy-variant": "Vento",
};
const WEATHER_ICON = {
  "clear-night": "mdi:weather-night", cloudy: "mdi:weather-cloudy", fog: "mdi:weather-fog", hail: "mdi:weather-hail",
  lightning: "mdi:weather-lightning", "lightning-rainy": "mdi:weather-lightning-rainy", partlycloudy: "mdi:weather-partly-cloudy",
  pouring: "mdi:weather-pouring", rainy: "mdi:weather-rainy", snowy: "mdi:weather-snowy", "snowy-rainy": "mdi:weather-snowy-rainy",
  sunny: "mdi:weather-sunny", windy: "mdi:weather-windy", "windy-variant": "mdi:weather-windy-variant",
};
const num = (n) => String(n).replace(".", ",");
const BIN_PT = {
  door: ["Fechada", "Aberta"], window: ["Fechada", "Aberta"], garage_door: ["Fechada", "Aberta"],
  motion: ["Sem movimento", "Movimento"], occupancy: ["Livre", "Ocupado"], moisture: ["Seco", "Fuga"],
};

const THEMES = {
  pinheiro: "radial-gradient(120% 90% at 10% 0%, #2f6b4a 0%, transparent 55%), radial-gradient(90% 80% at 100% 100%, #1d3f5e 0%, transparent 60%), linear-gradient(160deg, #11261b, #0b1520)",
  noite: "radial-gradient(100% 80% at 80% 0%, #3a2d6b 0%, transparent 55%), radial-gradient(90% 70% at 0% 100%, #1a3a5c 0%, transparent 60%), linear-gradient(170deg, #121026, #070912)",
  aurora: "radial-gradient(90% 70% at 0% 0%, #1f8a7a 0%, transparent 55%), radial-gradient(80% 70% at 100% 30%, #6a3fa0 0%, transparent 60%), linear-gradient(180deg, #0e1e2a, #120c22)",
  areia: "radial-gradient(100% 80% at 20% 0%, #a0724a 0%, transparent 55%), radial-gradient(80% 70% at 100% 100%, #5a4a7a 0%, transparent 60%), linear-gradient(160deg, #3a2a20, #1a1420)",
  oceano: "radial-gradient(100% 80% at 90% 0%, #1e6fa8 0%, transparent 55%), radial-gradient(80% 70% at 0% 100%, #0f8a8a 0%, transparent 60%), linear-gradient(170deg, #0a1f33, #06121c)",
};
const THEME_NAMES = { pinheiro: "Pinheiro", noite: "Noite", aurora: "Aurora", areia: "Areia", oceano: "Oceano" };

const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const dom = (id) => id.split(".")[0];
const store = {
  get(k, d) { try { const v = localStorage.getItem("pine3." + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem("pine3." + k, JSON.stringify(v)); } catch { /* ignore */ } },
};

const css = `
*,*::before,*::after{box-sizing:border-box}
:host{display:block;position:relative;min-height:100vh;color:#fff;font-family:-apple-system,BlinkMacSystemFont,"SF Pro Text","Inter","Segoe UI",Roboto,sans-serif;-webkit-font-smoothing:antialiased;
  --glass:rgba(40,44,52,.42);--glass-strong:rgba(30,32,38,.72);--line:rgba(255,255,255,.08);--muted:rgba(255,255,255,.62);--tile-on:rgba(255,255,255,.92);--r:22px}
.bg{position:fixed;inset:0;z-index:0;transition:background .6s}
.bg::after{content:"";position:absolute;inset:0;background:radial-gradient(transparent 40%,rgba(0,0,0,.35))}
.wrap{position:relative;z-index:1;max-width:1280px;margin:0 auto;padding:0 20px 120px}
.glass{background:var(--glass);backdrop-filter:blur(28px) saturate(160%);-webkit-backdrop-filter:blur(28px) saturate(160%);border:1px solid var(--line)}
header{display:flex;align-items:center;gap:10px;padding:18px 0 6px}
header .sp{flex:1}
.iconbtn{width:38px;height:38px;border-radius:50%;display:grid;place-items:center;color:#fff;cursor:pointer;border:1px solid var(--line);background:var(--glass);backdrop-filter:blur(20px);-webkit-backdrop-filter:blur(20px)}
.iconbtn ha-icon{--mdc-icon-size:20px}
.hero{padding:8px 2px 18px}
.hero .t{font-size:clamp(30px,5vw,40px);font-weight:700;letter-spacing:-.02em;line-height:1.1}
.hero .s{color:var(--muted);font-size:15px;margin-top:6px}
.clock{font-variant-numeric:tabular-nums;font-weight:300;font-size:clamp(40px,7vw,64px);letter-spacing:-.03em;line-height:1}
.toprow{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;flex-wrap:wrap}
.chips{display:flex;gap:8px;overflow-x:auto;padding:2px 0 14px;scrollbar-width:none}
.chips::-webkit-scrollbar{display:none}
.chip{flex:none;display:flex;align-items:center;gap:8px;padding:8px 14px 8px 10px;border-radius:999px;font-size:13px;font-weight:600;cursor:default;white-space:nowrap}
.chip .dot{width:26px;height:26px;border-radius:50%;display:grid;place-items:center;background:rgba(255,255,255,.12)}
.chip .dot ha-icon{--mdc-icon-size:16px}
.chip small{display:block;font-weight:500;color:var(--muted);font-size:11px}
h2{font-size:20px;font-weight:700;letter-spacing:-.01em;margin:22px 2px 10px}
.scenes{display:flex;gap:10px;overflow-x:auto;padding-bottom:4px;scrollbar-width:none}
.scenes::-webkit-scrollbar{display:none}
.scene{flex:none;display:flex;align-items:center;gap:10px;padding:10px 16px 10px 10px;border-radius:16px;cursor:pointer;font-weight:600;font-size:14px;transition:transform .12s}
.scene:active{transform:scale(.96)}
.scene .dot{width:32px;height:32px;border-radius:50%;display:grid;place-items:center;background:rgba(255,255,255,.14)}
.scene.fired{background:var(--tile-on);color:#111}
.rooms{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:14px}
.room{border-radius:var(--r);padding:16px;cursor:pointer;min-height:118px;display:flex;flex-direction:column;transition:transform .15s}
.room:active{transform:scale(.98)}
.room .top{display:flex;align-items:flex-start;justify-content:space-between}
.room .nm{font-size:18px;font-weight:700;letter-spacing:-.01em}
.room .meta{color:var(--muted);font-size:13px;margin-top:3px}
.room .bulb{width:36px;height:36px;border-radius:50%;display:grid;place-items:center;background:rgba(255,255,255,.12);cursor:pointer;flex:none}
.room .bulb.on{background:#ffb340;color:#2a1a00}
.room .bulb ha-icon{--mdc-icon-size:18px}
.room .mini{display:flex;gap:6px;margin-top:auto;padding-top:14px;flex-wrap:wrap}
.room .mini span{font-size:12px;font-weight:600;background:rgba(255,255,255,.1);padding:4px 9px;border-radius:999px}
.tiles{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:12px}
.tile{border-radius:20px;padding:13px 14px;cursor:pointer;min-height:96px;display:flex;flex-direction:column;justify-content:space-between;transition:background .2s,color .2s,transform .12s;user-select:none;-webkit-user-select:none;touch-action:manipulation}
.tile:active{transform:scale(.97)}
.tile.big{grid-column:span 2;min-height:120px}
.tile .ic{width:38px;height:38px;border-radius:50%;display:grid;place-items:center;background:rgba(255,255,255,.12);transition:background .2s}
.tile .ic ha-icon{--mdc-icon-size:21px}
.tile .nm{font-weight:600;font-size:14px;line-height:1.2;margin-top:10px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.tile .st{font-size:13px;color:var(--muted);margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.tile.on{background:var(--tile-on);color:#111;border-color:transparent}
.tile.on .st{color:rgba(0,0,0,.55)}
.tile.on .ic{background:var(--acc);color:#fff}
.tile.sensor{cursor:default}
.tile.sensor .val{font-size:22px;font-weight:600;letter-spacing:-.02em;margin-top:8px}
.tile.na{opacity:.45}
.dock{position:fixed;left:50%;bottom:18px;transform:translateX(-50%);z-index:5;display:flex;gap:4px;padding:6px;border-radius:999px;background:var(--glass-strong)}
.dock button{all:unset;cursor:pointer;display:flex;align-items:center;gap:8px;padding:10px 16px;border-radius:999px;font-size:14px;font-weight:600;color:var(--muted)}
.dock button.act{background:rgba(255,255,255,.16);color:#fff}
.dock ha-icon{--mdc-icon-size:20px}
.empty{color:var(--muted);padding:40px 4px}
.ov{position:fixed;inset:0;z-index:10;background:rgba(0,0,0,.45);backdrop-filter:blur(14px);-webkit-backdrop-filter:blur(14px);display:grid;place-items:center;animation:fade .18s}
@keyframes fade{from{opacity:0}}
@keyframes pop{from{transform:scale(.94);opacity:0}}
.sheet{width:min(380px,calc(100vw - 32px));border-radius:30px;padding:22px;background:var(--glass-strong);border:1px solid var(--line);animation:pop .22s cubic-bezier(.2,.9,.3,1.2)}
.sheet .hd{display:flex;align-items:center;gap:12px}
.sheet .hd .ic{width:44px;height:44px;border-radius:50%;display:grid;place-items:center;background:var(--acc,rgba(255,255,255,.15))}
.sheet .hd .nm{font-size:18px;font-weight:700;flex:1}
.sheet .hd .st{font-size:13px;color:var(--muted)}
.vslider{position:relative;width:120px;height:300px;margin:26px auto 10px;border-radius:34px;background:rgba(255,255,255,.14);overflow:hidden;cursor:ns-resize;touch-action:none}
.vslider .fill{position:absolute;left:0;right:0;bottom:0;background:var(--tile-on)}
.vslider .lbl{position:absolute;left:0;right:0;bottom:14px;text-align:center;font-weight:700;color:#111;mix-blend-mode:difference;filter:invert(1)}
.row{display:flex;gap:10px;justify-content:center;margin-top:16px;flex-wrap:wrap}
.pill{all:unset;cursor:pointer;padding:10px 16px;border-radius:999px;background:rgba(255,255,255,.12);font-weight:600;font-size:14px;display:flex;align-items:center;gap:6px}
.pill.act{background:var(--tile-on);color:#111}
.swatches{display:flex;gap:10px;justify-content:center;margin-top:14px}
.sw{width:30px;height:30px;border-radius:50%;cursor:pointer;border:2px solid rgba(255,255,255,.4)}
.ring{width:220px;height:220px;margin:22px auto 6px;border-radius:50%;display:grid;place-items:center;position:relative}
.ring .tgt{font-size:52px;font-weight:300;letter-spacing:-.03em}
.ring .cur{font-size:13px;color:var(--muted);text-align:center}
.round{all:unset;cursor:pointer;width:54px;height:54px;border-radius:50%;display:grid;place-items:center;background:rgba(255,255,255,.14);font-size:26px}
.attrs{margin-top:14px;font-size:13px;color:var(--muted);display:grid;grid-template-columns:auto 1fr;gap:6px 14px}
.attrs b{color:#fff;font-weight:500;text-align:right;overflow:hidden;text-overflow:ellipsis}
.more{all:unset;cursor:pointer;display:block;width:100%;text-align:center;margin-top:18px;color:var(--muted);font-size:13px}
.themes{display:grid;grid-template-columns:repeat(auto-fill,minmax(96px,1fr));gap:10px;margin-top:16px}
.themes .th{height:70px;border-radius:16px;cursor:pointer;display:flex;align-items:flex-end;padding:8px;font-size:12px;font-weight:600;border:2px solid transparent}
.themes .th.act{border-color:#fff}
@media (max-width:520px){.wrap{padding:0 16px 110px}.rooms{grid-template-columns:1fr 1fr}.room{min-height:110px;padding:14px}.room .nm{font-size:16px}.tiles{grid-template-columns:1fr 1fr}.dock button span{display:none}}
`;

class Pine3ViewPanel extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this._view = "home";
    this._room = null;
    this._modal = null;
    this._drag = null;
    this._theme = store.get("theme", "pinheiro");
    this._fired = null;
  }

  connectedCallback() { this._tick = setInterval(() => this._updateClock(), 15000); }
  disconnectedCallback() { clearInterval(this._tick); }
  set narrow(n) { this._narrow = n; }
  set hass(h) {
    this._hass = h;
    if (this._drag) return; // don't redraw under a finger
    this._render();
  }

  // ---------- data ----------
  _rooms() {
    const h = this._hass, rooms = new Map();
    for (const a of Object.values(h.areas || {})) rooms.set(a.area_id, { id: a.area_id, name: a.name, icon: a.icon, ents: [] });
    const none = { id: "_none", name: "Outros", icon: "mdi:home-outline", ents: [] };
    for (const e of Object.values(h.entities || {})) {
      if (!SHOWN.has(dom(e.entity_id)) || e.hidden || e.entity_category || !h.states[e.entity_id]) continue;
      const area = e.area_id || h.devices?.[e.device_id]?.area_id;
      const r = rooms.get(area);
      if (r) r.ents.push(e.entity_id);
      else if (!["scene", "script"].includes(dom(e.entity_id))) none.ents.push(e.entity_id);
    }
    const list = [...rooms.values()].filter((r) => r.ents.length).sort((a, b) => a.name.localeCompare(b.name, "pt"));
    if (none.ents.length) list.push(none);
    return list;
  }

  _s(id) { return this._hass.states[id]; }
  _name(id) { const s = this._s(id); return s?.attributes.friendly_name || id; }
  _isOn(id) {
    const s = this._s(id); if (!s) return false;
    const d = dom(id);
    if (d === "lock") return s.state === "unlocked";
    if (d === "climate") return s.state !== "off" && s.state !== "unavailable";
    if (d === "media_player") return ["playing", "on", "paused", "buffering"].includes(s.state);
    if (d === "vacuum") return ["cleaning", "returning"].includes(s.state);
    return ["on", "open", "opening", "closing", "home"].includes(s.state);
  }
  _icon(id) {
    const s = this._s(id), d = dom(id), dc = s?.attributes.device_class;
    if (s?.attributes.icon) return s.attributes.icon;
    if (d === "light") return this._isOn(id) ? "mdi:lightbulb-on" : "mdi:lightbulb-outline";
    if (d === "lock") return s?.state === "locked" ? "mdi:lock" : "mdi:lock-open-variant";
    if (d === "cover") return s?.state === "closed" ? "mdi:window-shutter" : "mdi:window-shutter-open";
    return DEVICE_CLASS_ICON[dc] || DOMAIN_ICON[d] || "mdi:circle-outline";
  }
  _accent(id) {
    const s = this._s(id);
    if (dom(id) === "light" && s?.attributes.rgb_color) return `rgb(${s.attributes.rgb_color.join(",")})`;
    return ACCENT[dom(id)] || "#5ac8fa";
  }
  _stateText(id) {
    const s = this._s(id); if (!s) return "";
    const d = dom(id), a = s.attributes;
    if (s.state === "unavailable") return "Indisponível";
    if (d === "light" && s.state === "on" && a.brightness != null) return `${Math.round((a.brightness / 255) * 100)}%`;
    if (d === "cover" && a.current_position != null && s.state !== "closed") return `${a.current_position}% aberto`;
    if (d === "climate") return `${STATE_PT[s.state] || s.state}${a.current_temperature != null ? ` · ${num(a.current_temperature)}°` : ""}`;
    if (d === "media_player" && s.state === "playing" && a.media_title) return a.media_title;
    if (d === "binary_sensor") { const m = BIN_PT[a.device_class]; if (m) return m[s.state === "on" ? 1 : 0]; }
    if (d === "sensor") return this._sensorVal(id);
    if (d === "scene" || d === "script" || d === "button") return "Tocar para executar";
    return STATE_PT[s.state] || s.state;
  }
  _sensorVal(id) {
    const s = this._s(id), u = s.attributes.unit_of_measurement || "";
    const n = Number(s.state);
    const v = Number.isFinite(n) && s.state !== "" ? (Math.abs(n) >= 100 ? Math.round(n) : Math.round(n * 10) / 10).toLocaleString("pt-PT") : (STATE_PT[s.state] || s.state);
    return `${v}${u ? (u === "°C" || u === "%" ? u : " " + u) : ""}`;
  }
  _roomClimate(r) {
    const by = (dc) => r.ents.find((i) => dom(i) === "sensor" && this._s(i)?.attributes.device_class === dc && Number.isFinite(Number(this._s(i).state)));
    const t = by("temperature"), hm = by("humidity");
    const c = r.ents.find((i) => dom(i) === "climate" && this._s(i)?.attributes.current_temperature != null);
    return {
      temp: t ? this._sensorVal(t) : c ? `${num(this._s(c).attributes.current_temperature)}°` : null,
      hum: hm ? this._sensorVal(hm) : null,
    };
  }

  // ---------- actions ----------
  _call(d, svc, data) { return this._hass.callService(d, svc, data); }
  _tap(id) {
    const d = dom(id), s = this._s(id);
    if (!s || s.state === "unavailable") return;
    if (d === "scene" || d === "script") return this._call(d, "turn_on", { entity_id: id });
    if (d === "button") return this._call("button", "press", { entity_id: id });
    if (d === "cover") return this._call("cover", s.state === "closed" || s.state === "closing" ? "open_cover" : "close_cover", { entity_id: id });
    if (d === "lock") return this._call("lock", s.state === "locked" ? "unlock" : "lock", { entity_id: id });
    if (d === "media_player") return this._call("media_player", "media_play_pause", { entity_id: id });
    if (d === "vacuum") return this._call("vacuum", s.state === "cleaning" ? "return_to_base" : "start", { entity_id: id });
    if (d === "climate" || d === "camera" || d === "sensor" || d === "binary_sensor") return this._openModal(id);
    if (TOGGLE.has(d)) return this._call("homeassistant", "toggle", { entity_id: id });
  }
  _openModal(id) { this._modal = id; this._render(); }
  _moreInfo(id) {
    this.dispatchEvent(new CustomEvent("hass-more-info", { detail: { entityId: id }, bubbles: true, composed: true }));
  }
  _roomLights(r) { return r.ents.filter((i) => dom(i) === "light"); }

  // ---------- render ----------
  _updateClock() {
    const el = this.shadowRoot.querySelector(".clock");
    if (el) el.textContent = new Date().toLocaleTimeString("pt-PT", { hour: "2-digit", minute: "2-digit" });
  }

  _render() {
    if (!this._hass) return;
    const rooms = this._rooms();
    const room = this._view === "room" ? rooms.find((r) => r.id === this._room) : null;
    if (this._view === "room" && !room) this._view = "home";
    let body;
    if (this._view === "room") body = this._roomView(room);
    else if (this._view === "scenes") body = this._scenesView(rooms);
    else body = this._homeView(rooms);

    const header = `<header>
      ${this._narrow ? `<div class="iconbtn" data-act="menu"><ha-icon icon="mdi:menu"></ha-icon></div>` : ""}
      ${this._view === "room" ? `<div class="iconbtn" data-act="back"><ha-icon icon="mdi:chevron-left"></ha-icon></div>` : ""}
      <div class="sp"></div>
      <div class="iconbtn" data-act="themes" title="Fundo"><ha-icon icon="mdi:palette-outline"></ha-icon></div>
    </header>`;
    const dock = `<nav class="dock glass">
      <button class="${this._view !== "scenes" ? "act" : ""}" data-nav="home"><ha-icon icon="mdi:home"></ha-icon><span>Casa</span></button>
      <button class="${this._view === "scenes" ? "act" : ""}" data-nav="scenes"><ha-icon icon="mdi:palette"></ha-icon><span>Cenas</span></button>
    </nav>`;

    this.shadowRoot.innerHTML = `<style>${css}</style>
      <div class="bg" style="background:${THEMES[this._theme] || THEMES.pinheiro}"></div>
      <div class="wrap">${header}${body}</div>${dock}${this._modalView()}`;
    this._bind(rooms);
  }

  _homeView(rooms) {
    const h = this._hass, now = new Date(), hr = now.getHours();
    const greet = hr < 6 ? "Boa noite" : hr < 13 ? "Bom dia" : hr < 20 ? "Boa tarde" : "Boa noite";
    const all = rooms.flatMap((r) => r.ents);
    const lightsOn = all.filter((i) => dom(i) === "light" && this._isOn(i));
    const coversOpen = all.filter((i) => dom(i) === "cover" && this._isOn(i));
    const openings = all.filter((i) => dom(i) === "binary_sensor" && ["door", "window", "garage_door"].includes(this._s(i).attributes.device_class) && this._s(i).state === "on");
    const unlocked = all.filter((i) => dom(i) === "lock" && this._s(i).state === "unlocked");
    const playing = all.filter((i) => dom(i) === "media_player" && this._s(i).state === "playing");
    const temps = all.filter((i) => dom(i) === "sensor" && this._s(i).attributes.device_class === "temperature").map((i) => Number(this._s(i).state)).filter(Number.isFinite);
    const weather = Object.keys(h.states).find((i) => dom(i) === "weather");

    const chip = (icon, title, sub, color) => `<div class="chip glass"><div class="dot" style="${color ? `background:${color};color:#111` : ""}"><ha-icon icon="${icon}"></ha-icon></div><div>${title}<small>${sub}</small></div></div>`;
    const chips = [];
    if (weather) { const w = this._s(weather); chips.push(chip(WEATHER_ICON[w.state] || "mdi:weather-partly-cloudy", `${num(w.attributes.temperature ?? "–")}${w.attributes.temperature_unit || "°"}`, WEATHER_PT[w.state] || w.state)); }
    if (temps.length) chips.push(chip("mdi:home-thermometer", `${(temps.reduce((a, b) => a + b, 0) / temps.length).toFixed(1).replace(".", ",")}°`, "Interior"));
    chips.push(chip("mdi:lightbulb-group", lightsOn.length ? `${lightsOn.length} ligada${lightsOn.length > 1 ? "s" : ""}` : "Apagadas", "Luzes", lightsOn.length ? "#ffb340" : ""));
    if (coversOpen.length) chips.push(chip("mdi:window-shutter-open", `${coversOpen.length} aberta${coversOpen.length > 1 ? "s" : ""}`, "Estores", "#5e9cff"));
    if (openings.length) chips.push(chip("mdi:door-open", `${openings.length} aberta${openings.length > 1 ? "s" : ""}`, "Portas e janelas", "#ff9f0a"));
    if (unlocked.length) chips.push(chip("mdi:lock-open-variant", `${unlocked.length} destrancada${unlocked.length > 1 ? "s" : ""}`, "Fechaduras", "#ff453a"));
    if (playing.length) chips.push(chip("mdi:music", this._s(playing[0]).attributes.media_title || "A tocar", "Multimédia", "#bf5af2"));

    const scenes = Object.keys(h.states).filter((i) => dom(i) === "scene" && !h.entities?.[i]?.hidden).slice(0, 12);
    const scenesHtml = scenes.length ? `<h2>Cenas</h2><div class="scenes">${scenes.map((i) => `<div class="scene glass ${this._fired === i ? "fired" : ""}" data-scene="${i}"><div class="dot"><ha-icon icon="${this._icon(i)}"></ha-icon></div>${esc(this._name(i))}</div>`).join("")}</div>` : "";

    const roomsHtml = rooms.length ? `<h2>Divisões</h2><div class="rooms">${rooms.map((r) => {
      const lights = this._roomLights(r), on = lights.filter((i) => this._isOn(i)).length;
      const active = r.ents.filter((i) => TOGGLE.has(dom(i)) && this._isOn(i)).length;
      const c = this._roomClimate(r);
      const mini = [c.temp && `<span>${c.temp}</span>`, c.hum && `<span>${c.hum}</span>`, active && `<span>${active} ativo${active > 1 ? "s" : ""}</span>`].filter(Boolean).join("");
      return `<div class="room glass" data-room="${esc(r.id)}">
        <div class="top"><div><div class="nm">${esc(r.name)}</div><div class="meta">${on ? `${on} luz${on > 1 ? "es" : ""} acesa${on > 1 ? "s" : ""}` : `${r.ents.length} dispositivo${r.ents.length > 1 ? "s" : ""}`}</div></div>
        ${lights.length ? `<div class="bulb ${on ? "on" : ""}" data-roomlights="${esc(r.id)}" title="Luzes"><ha-icon icon="mdi:lightbulb${on ? "" : "-outline"}"></ha-icon></div>` : ""}</div>
        ${mini ? `<div class="mini">${mini}</div>` : ""}
      </div>`;
    }).join("")}</div>` : `<div class="empty">Ainda não há divisões com dispositivos. Atribui áreas aos teus dispositivos no Home Assistant.</div>`;

    const name = h.config?.location_name || "Casa";
    return `<div class="hero toprow"><div><div class="t">${greet}</div><div class="s">${esc(name)} · ${(() => { const t = now.toLocaleDateString("pt-PT", { weekday: "long", day: "numeric", month: "long" }); return t[0].toUpperCase() + t.slice(1); })()}</div></div>
      <div class="clock">${now.toLocaleTimeString("pt-PT", { hour: "2-digit", minute: "2-digit" })}</div></div>
      <div class="chips">${chips.join("")}</div>${scenesHtml}${roomsHtml}`;
  }

  _tile(id) {
    const s = this._s(id), d = dom(id), on = this._isOn(id);
    const sensor = d === "sensor";
    const big = d === "media_player" && s.state === "playing" && this._narrow === false;
    return `<div class="tile glass ${on ? "on" : ""} ${sensor ? "sensor" : ""} ${big ? "big" : ""} ${s.state === "unavailable" ? "na" : ""}" data-ent="${esc(id)}" style="--acc:${this._accent(id)}">
      <div class="ic"><ha-icon icon="${this._icon(id)}"></ha-icon></div>
      <div><div class="nm">${esc(this._name(id))}</div>${sensor ? `<div class="val">${esc(this._sensorVal(id))}</div>` : `<div class="st">${esc(this._stateText(id))}</div>`}</div>
    </div>`;
  }

  _roomView(room) {
    const c = this._roomClimate(room);
    const sub = [c.temp, c.hum].filter(Boolean).join(" · ");
    const sections = SECTIONS.map(([title, domains]) => {
      const ents = room.ents.filter((i) => domains.includes(dom(i)));
      return ents.length ? `<h2>${title}</h2><div class="tiles">${ents.map((i) => this._tile(i)).join("")}</div>` : "";
    }).join("");
    const scenes = room.ents.filter((i) => dom(i) === "scene");
    const sc = scenes.length ? `<h2>Cenas</h2><div class="scenes">${scenes.map((i) => `<div class="scene glass ${this._fired === i ? "fired" : ""}" data-scene="${i}"><div class="dot"><ha-icon icon="${this._icon(i)}"></ha-icon></div>${esc(this._name(i))}</div>`).join("")}</div>` : "";
    return `<div class="hero"><div class="t">${esc(room.name)}</div>${sub ? `<div class="s">${sub}</div>` : ""}</div>${sc}${sections}`;
  }

  _scenesView() {
    const h = this._hass;
    const list = (d) => Object.keys(h.states).filter((i) => dom(i) === d && !h.entities?.[i]?.hidden);
    const sc = list("scene"), sp = list("script");
    const grid = (ids) => `<div class="tiles">${ids.map((i) => this._tile(i)).join("")}</div>`;
    return `<div class="hero"><div class="t">Cenas e scripts</div><div class="s">Toca para executar</div></div>
      ${sc.length ? `<h2>Cenas</h2>${grid(sc)}` : ""}${sp.length ? `<h2>Scripts</h2>${grid(sp)}` : ""}
      ${!sc.length && !sp.length ? `<div class="empty">Não há cenas nem scripts.</div>` : ""}`;
  }

  _modalView() {
    if (this._modal === "_themes") {
      return `<div class="ov" data-close><div class="sheet"><div class="hd"><div class="nm">Fundo</div></div>
        <div class="themes">${Object.keys(THEMES).map((k) => `<div class="th ${k === this._theme ? "act" : ""}" data-theme="${k}" style="background:${THEMES[k]}">${THEME_NAMES[k]}</div>`).join("")}</div></div></div>`;
    }
    const id = this._modal, s = id && this._s(id);
    if (!s) return "";
    const d = dom(id), a = s.attributes, on = this._isOn(id);
    let content = "";
    if (d === "light" && (a.supported_color_modes || []).some((m) => m !== "onoff")) {
      const pct = on ? Math.round(((a.brightness ?? 255) / 255) * 100) : 0;
      const colors = (a.supported_color_modes || []).some((m) => ["hs", "rgb", "rgbw", "rgbww", "xy"].includes(m));
      content = `<div class="vslider" data-slider="brightness" data-val="${pct}"><div class="fill" style="height:${pct}%"></div><div class="lbl">${pct}%</div></div>
        ${colors ? `<div class="swatches">${["#ffffff", "#ffd28a", "#ff8a5c", "#ff5ca8", "#8a7cff", "#5cc8ff", "#5cff9d"].map((c) => `<div class="sw" data-color="${c}" style="background:${c}"></div>`).join("")}</div>` : ""}`;
    } else if (d === "cover" && a.current_position != null) {
      const pct = a.current_position;
      content = `<div class="vslider" data-slider="position" data-val="${pct}"><div class="fill" style="height:${pct}%"></div><div class="lbl">${pct}%</div></div>
        <div class="row"><button class="pill" data-svc="cover.open_cover">Abrir</button><button class="pill" data-svc="cover.stop_cover">Parar</button><button class="pill" data-svc="cover.close_cover">Fechar</button></div>`;
    } else if (d === "climate") {
      const t = a.temperature, step = a.target_temp_step || 0.5;
      const modes = a.hvac_modes || [];
      const col = s.state === "cool" ? "#5ac8fa" : s.state === "off" ? "rgba(255,255,255,.2)" : "#ff9f0a";
      content = `<div class="ring" style="background:conic-gradient(${col} 0 ${t != null ? Math.min(100, Math.max(0, ((t - (a.min_temp ?? 7)) / ((a.max_temp ?? 35) - (a.min_temp ?? 7))) * 100)) : 0}%, rgba(255,255,255,.12) 0)">
        <div style="position:absolute;inset:14px;border-radius:50%;background:#202329"></div>
        <div style="position:relative;text-align:center"><div class="tgt">${t != null ? String(t).replace(".", ",") + "°" : "–"}</div><div class="cur">Atual ${num(a.current_temperature ?? "–")}°</div></div></div>
        ${t != null ? `<div class="row"><button class="round" data-temp="${-step}">−</button><button class="round" data-temp="${step}">+</button></div>` : ""}
        <div class="row">${modes.map((m) => `<button class="pill ${m === s.state ? "act" : ""}" data-hvac="${m}">${STATE_PT[m] || m}</button>`).join("")}</div>`;
    } else if (d === "media_player") {
      const vol = Math.round((a.volume_level ?? 0) * 100);
      content = `${a.entity_picture ? `<img src="${esc(a.entity_picture)}" style="width:100%;aspect-ratio:1;object-fit:cover;border-radius:20px;margin-top:18px">` : ""}
        ${a.media_title ? `<div style="text-align:center;margin-top:14px;font-weight:700">${esc(a.media_title)}</div><div style="text-align:center;color:var(--muted);font-size:13px">${esc(a.media_artist || "")}</div>` : ""}
        <div class="row"><button class="round" data-svc="media_player.media_previous_track">⏮</button><button class="round" data-svc="media_player.media_play_pause">${s.state === "playing" ? "⏸" : "▶"}</button><button class="round" data-svc="media_player.media_next_track">⏭</button></div>
        ${a.volume_level != null ? `<div class="vslider" data-slider="volume" data-val="${vol}" style="height:200px;width:100px"><div class="fill" style="height:${vol}%"></div><div class="lbl">${vol}%</div></div>` : ""}`;
    } else if (d === "fan" && a.percentage != null) {
      content = `<div class="vslider" data-slider="fan" data-val="${on ? a.percentage : 0}"><div class="fill" style="height:${on ? a.percentage : 0}%"></div><div class="lbl">${on ? a.percentage : 0}%</div></div>`;
    } else if (d === "camera") {
      content = `<img src="/api/camera_proxy/${esc(id)}?token=${esc(a.access_token || "")}&t=${Date.now()}" style="width:100%;border-radius:18px;margin-top:18px;background:#000">`;
    } else {
      const skip = new Set(["friendly_name", "icon", "entity_picture", "supported_features", "attribution"]);
      const rows = Object.entries(a).filter(([k, v]) => !skip.has(k) && typeof v !== "object").slice(0, 8);
      content = `${d === "sensor" ? `<div style="font-size:48px;font-weight:300;text-align:center;margin:24px 0 6px">${esc(this._sensorVal(id))}</div>` : ""}
        ${rows.length ? `<div class="attrs">${rows.map(([k, v]) => `<span>${esc(k.replace(/_/g, " "))}</span><b>${esc(v)}</b>`).join("")}</div>` : ""}`;
    }
    const canToggle = TOGGLE.has(d) && d !== "media_player";
    return `<div class="ov" data-close><div class="sheet" style="--acc:${this._accent(id)}">
      <div class="hd"><div class="ic"><ha-icon icon="${this._icon(id)}"></ha-icon></div><div style="flex:1;min-width:0"><div class="nm">${esc(this._name(id))}</div><div class="st">${esc(this._stateText(id))}</div></div>
      ${canToggle ? `<button class="pill ${on ? "act" : ""}" data-toggle>${on ? "Desligar" : "Ligar"}</button>` : ""}</div>
      ${content}<button class="more" data-more>Mais definições</button></div></div>`;
  }

  _bind() {
    const root = this.shadowRoot;
    const on = (sel, fn) => root.querySelectorAll(sel).forEach((el) => el.addEventListener("click", (ev) => fn(el, ev)));
    on('[data-act="back"]', () => { this._view = "home"; this._render(); });
    on('[data-act="menu"]', () => this.dispatchEvent(new Event("hass-toggle-menu", { bubbles: true, composed: true })));
    on('[data-act="themes"]', () => this._openModal("_themes"));
    on("[data-nav]", (el) => { this._view = el.dataset.nav; this._render(); window.scrollTo(0, 0); });
    on("[data-room]", (el) => { this._view = "room"; this._room = el.dataset.room; this._render(); window.scrollTo(0, 0); });
    on("[data-roomlights]", (el, ev) => {
      ev.stopPropagation();
      const r = this._rooms().find((x) => x.id === el.dataset.roomlights);
      const lights = this._roomLights(r), anyOn = lights.some((i) => this._isOn(i));
      this._call("light", anyOn ? "turn_off" : "turn_on", { entity_id: lights });
    });
    on("[data-scene]", (el) => {
      this._call("scene", "turn_on", { entity_id: el.dataset.scene });
      this._fired = el.dataset.scene; this._render();
      setTimeout(() => { this._fired = null; this._render(); }, 1200);
    });
    on("[data-theme]", (el) => { this._theme = el.dataset.theme; store.set("theme", this._theme); this._render(); });

    // tiles: tap = action, long-press / right-click = details
    root.querySelectorAll("[data-ent]").forEach((el) => {
      const id = el.dataset.ent;
      let timer, long = false;
      const start = () => { long = false; timer = setTimeout(() => { long = true; if (DETAIL.has(dom(id)) || dom(id) === "sensor") this._openModal(id); }, 450); };
      const cancel = () => clearTimeout(timer);
      el.addEventListener("pointerdown", start);
      el.addEventListener("pointerup", cancel);
      el.addEventListener("pointerleave", cancel);
      el.addEventListener("contextmenu", (ev) => { ev.preventDefault(); cancel(); this._openModal(id); });
      el.addEventListener("click", () => { if (!long) this._tap(id); });
    });

    // modal
    const ov = root.querySelector(".ov");
    if (!ov) return;
    ov.addEventListener("click", (ev) => { if (ev.target === ov) { this._modal = null; this._render(); } });
    const id = this._modal;
    on("[data-toggle]", () => this._tap(id));
    on("[data-more]", () => { this._modal = null; this._render(); this._moreInfo(id); });
    on("[data-svc]", (el) => { const [d, s] = el.dataset.svc.split("."); this._call(d, s, { entity_id: id }); });
    on("[data-color]", (el) => { const h = el.dataset.color; this._call("light", "turn_on", { entity_id: id, rgb_color: [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16)) }); });
    on("[data-hvac]", (el) => this._call("climate", "set_hvac_mode", { entity_id: id, hvac_mode: el.dataset.hvac }));
    on("[data-temp]", (el) => {
      const a = this._s(id).attributes;
      const t = Math.min(a.max_temp ?? 35, Math.max(a.min_temp ?? 7, Math.round(((a.temperature ?? 20) + Number(el.dataset.temp)) * 10) / 10));
      this._call("climate", "set_temperature", { entity_id: id, temperature: t });
    });
    root.querySelectorAll("[data-slider]").forEach((el) => this._bindSlider(el, id));
  }

  _bindSlider(el, id) {
    const fill = el.querySelector(".fill"), lbl = el.querySelector(".lbl");
    const kind = el.dataset.slider;
    let val = Number(el.dataset.val);
    const at = (ev) => { const r = el.getBoundingClientRect(); return Math.round(Math.min(100, Math.max(0, ((r.bottom - ev.clientY) / r.height) * 100))); };
    const show = (v) => { fill.style.height = v + "%"; lbl.textContent = v + "%"; };
    el.addEventListener("pointerdown", (ev) => { el.setPointerCapture(ev.pointerId); this._drag = true; val = at(ev); show(val); });
    el.addEventListener("pointermove", (ev) => { if (this._drag) { val = at(ev); show(val); } });
    const end = () => {
      if (!this._drag) return;
      this._drag = null;
      if (kind === "brightness") this._call("light", val ? "turn_on" : "turn_off", val ? { entity_id: id, brightness_pct: val } : { entity_id: id });
      else if (kind === "position") this._call("cover", "set_cover_position", { entity_id: id, position: val });
      else if (kind === "volume") this._call("media_player", "volume_set", { entity_id: id, volume_level: val / 100 });
      else if (kind === "fan") this._call("fan", val ? "set_percentage" : "turn_off", val ? { entity_id: id, percentage: val } : { entity_id: id });
    };
    el.addEventListener("pointerup", end);
    el.addEventListener("pointercancel", end);
  }
}

if (!customElements.get("pine3-view-panel")) customElements.define("pine3-view-panel", Pine3ViewPanel);
