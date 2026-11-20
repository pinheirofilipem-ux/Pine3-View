// Pine3 View - personal Home Assistant dashboard. Original code, no build step.
// Organises the home as Areas -> Devices -> Entities, built server-side by discovery.py.

import {
  SHOWN, TOGGLE, DETAIL, SECTIONS, SECTION_ICON, DOMAIN_ICON, DEVICE_CLASS_ICON, ACCENT, STATE_PT, WEATHER_PT, WEATHER_ICON,
  BIN_PT, NAME_ICON, THEMES, LIGHT_THEMES, VIVID_THEMES, AMBIENT_THEMES, CARD_COLORS, ACCENTS, DOCK_ITEMS, DEFAULTS, THEME_NAMES, esc, dom, store, loadFonts,
} from "./const.js";
import { css } from "./styles.js";
import { t, tp, num, locale, setLang, lang } from "./i18n.js";
import { Weather } from "./weather.js";
import { toLight, currentTheme, isLight } from "./theme.js";
import { houseView, bindHouse, css3d } from "./house3d.js";
import { planView, bindPlan, cssPlan } from "./plan2d.js";

// the version of this file, from its URL (/pine3_view/<version>/…): differs from the integration's when the browser kept an old copy
const FRONT_VERSION = (import.meta.url.match(/\/(\d+\.\d+\.\d+)\//) || [])[1] || null;

loadFonts();

const clone = (o) => JSON.parse(JSON.stringify(o));
const cap = (s) => (s ? s[0].toUpperCase() + s.slice(1) : s);
const SAVER_STEPS = [0, 1, 5, 10, 30];
const EDIT_HINT = "Arrasta para mover. Toca em − para esconder e nas setas para duplicar o tamanho.";

/** A colour for a temperature, cold blue to hot orange (the forecast's range bars). */
function tempColor(v) {
  const stops = [[0, [90, 200, 250]], [10, [100, 210, 255]], [16, [130, 214, 190]], [20, [246, 201, 74]], [26, [255, 159, 67]], [33, [255, 94, 58]]];
  if (v <= stops[0][0]) return `rgb(${stops[0][1]})`;
  for (let k = 1; k < stops.length; k++) {
    const [t1, c1] = stops[k], [t0, c0] = stops[k - 1];
    if (v <= t1) { const f = (v - t0) / (t1 - t0); return `rgb(${c0.map((c, n) => Math.round(c + (c1[n] - c) * f))})`; }
  }
  return `rgb(${stops[stops.length - 1][1]})`;
}

class Pine3ViewPanel extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this._view = "home";
    this._room = null;
    this._modal = null;
    this._drag = null;
    this._theme = store.get("theme", "ameixa");
    this._fired = null;
    this._edit = null; // working copy of layout settings while editing
    this._weather = new Weather(() => { if (!this._drag) this._render(); });
    this._lastActivity = Date.now();
    const wake = () => { this._lastActivity = Date.now(); };
    this.addEventListener("pointerdown", wake, true);
    this.addEventListener("keydown", wake, true);
    this.addEventListener("wheel", wake, { capture: true, passive: true });
  }

  connectedCallback() {
    this._tick = setInterval(() => this._onTick(), 15000);
    this._onFs = () => this._render();
    document.addEventListener("fullscreenchange", this._onFs);
    this._onVis = () => { if (document.visibilityState === "visible") this._applyWakeLock(); };
    document.addEventListener("visibilitychange", this._onVis);
    // per-device kiosk options (and ?kiosk in the URL) hide the Home Assistant sidebar
    const kiosk = /[?&#]kiosk\b/.test(location.search + location.hash);
    if (kiosk || store.get("hide_sidebar", false)) this._dockSidebar(true);
    this._applyWakeLock();
    this._onResize = () => { this._fit3d?.(); this._fitDock(); this._dockSpace(); this._placeBlob(false); this._packSections(); this._fitRows(); this._fitDays(); this._fillHome(); };
    window.addEventListener("resize", this._onResize);
    if (this._hass) this._connect();
  }
  disconnectedCallback() {
    clearInterval(this._tick);
    document.removeEventListener("fullscreenchange", this._onFs);
    document.removeEventListener("visibilitychange", this._onVis);
    this._wake?.release?.().catch(() => {});
    this._wake = null;
    window.removeEventListener("resize", this._onResize);
    this._unsub?.then?.((u) => u()).catch(() => {});
    this._unsub = null;
    this._connecting = false;
  }
  set narrow(n) { this._narrow = n; }
  set hass(h) {
    const prev = this._hass;
    this._hass = h;
    if (!this._connecting) this._connect();
    if (this._camOv) this._camUpdate();
    if (this._drag) return; // don't redraw under a finger
    // Home Assistant pushes a new hass object whenever *any* entity changes; only redraw
    // when something this screen shows has changed (redraws restart animations and flicker).
    if (prev && !this._affects(prev, h)) return;
    this._render();
  }

  /** Did anything the current screen depends on change between two hass objects? */
  _affects(prev, h) {
    if (!this._watch || prev.language !== h.language || prev.entities !== h.entities || prev.areas !== h.areas) return true;
    for (const id of this._watch) if (prev.states[id] !== h.states[id]) return true;
    return false;
  }

  /** Every entity the last render read, so state changes elsewhere don't trigger a redraw. */
  _collectWatch(rooms) {
    if (!this._model) { this._watch = null; return; } // local discovery reads registries too: always redraw
    const w = new Set();
    const add = (ids) => (ids || []).forEach((i) => w.add(i));
    for (const r of rooms) { add(r.ents); add(r.temperature); add(r.humidity); }
    for (const m of Object.values(this._meta || {})) { add(m.extras); add(m.members); }
    add(this._model.persons); add(this._model.scenes); add(this._model.scripts);
    if (this._cfg().appearance === "auto") w.add("sun.sun"); // day / night theme
    for (const sc of this._model.scenes || []) { const l = this._s(sc)?.attributes.entity_id; if (Array.isArray(l)) add(l); } // a scene lights up while its devices are on
    add(Object.keys(this._hass.states).filter((i) => i.startsWith("weather.")));
    { const ip = this._ipma(); add([ip.alert, ip.uv, ip.fire].filter(Boolean)); } // IPMA warnings
    if (this._modal && !this._modal.startsWith("_")) w.add(this._modal);
    this._watch = w;
  }

  /** Overlay class: the opening animation runs once, not on every redraw. */
  _ovEnter(key) {
    const enter = this._ovShown !== key;
    this._ovShown = key;
    return enter ? "enter" : "";
  }

  _onTick() {
    const mins = this._cfg().screensaver;
    if (mins && !this._saver && !this._camOv && this._view !== "settings" && !this._edit && Date.now() - this._lastActivity > mins * 60000) {
      this._saver = true;
      this._modal = null;
      this._render();
      return;
    }
    this._updateClock();
  }

  // ---------- data ----------
  // The home model is built server-side (discovery.py) and pushed on registry changes.
  async _connect() {
    this._connecting = true;
    try {
      this._model = await this._hass.callWS({ type: "pine3_view/home" });
      if (this._model.settings?.theme) this._theme = this._model.settings.theme;
      this._unsub = this._hass.connection.subscribeMessage((m) => { this._model = m; if (!this._drag) this._render(); }, { type: "pine3_view/subscribe" });
    } catch (err) {
      console.warn("Pine3 View: API unavailable, using local discovery", err);
      this._model = null;
    }
    this._render();
  }

  _saved() { return { ...clone(DEFAULTS), ...(this._model?.settings || store.get("settings", {})) }; }

  _cfg() {
    if (this._view === "settings" && this._draft) return this._draft;
    const c = this._saved();
    return this._edit ? { ...c, ...this._edit } : c;
  }

  async _persist(patch) {
    if (this._model) {
      const res = await this._hass.callWS({ type: "pine3_view/settings", ...patch });
      this._model.settings = res;
    } else store.set("settings", { ...this._saved(), ...patch });
  }

  _saveSettings(patch) {
    if (this._model) Object.assign(this._model.settings, patch);
    this._persist(patch).catch((e) => console.error("Pine3 View", e));
  }

  _rooms() {
    const cfg = this._cfg();
    const order = cfg.room_order || [], hid = cfg.hidden_rooms || [], hiddenDev = cfg.hidden || [];
    const pos = (list, id) => { const k = list.indexOf(id); return k < 0 ? 1e4 : k; };
    const sortBy = (list, items, key = (x) => x) => items.map((x, k) => [x, k]).sort((a, b) => pos(list, key(a[0])) - pos(list, key(b[0])) || a[1] - b[1]).map(([x]) => x);
    let out = [];
    this._meta = {};
    if (this._model) {
      for (const r of this._model.rooms) {
        for (const d of r.devices) this._meta[d.id] = d;
        const shown = cfg.shown || [];
        const extra = (r.entities || []).filter((e) => !e.primary && shown.includes(e.id) && !hiddenDev.includes(e.id) && this._s(e.id));
        for (const e of extra) this._meta[e.id] = { ...e, extras: [], extra: true };
        let ents = [...r.devices.filter((d) => !hiddenDev.includes(d.id) && this._s(d.id)).map((d) => d.id), ...extra.map((e) => e.id)];
        ents = sortBy(cfg.device_order?.[r.id] || [], ents);
        const scenes = r.scenes.filter((i) => this._s(i));
        if (!ents.length && !scenes.length) continue;
        out.push({ ...r, ents: [...ents, ...scenes], count: ents.length });
      }
    } else out = this._localRooms();
    this._hiddenRooms = out.filter((r) => hid.includes(r.id));
    return sortBy(order, out.filter((r) => !hid.includes(r.id)), (r) => r.id);
  }

  _localRooms() {
    const h = this._hass, rooms = new Map();
    for (const a of Object.values(h.areas || {})) rooms.set(a.area_id, { id: a.area_id, name: a.name, icon: a.icon, floor_level: h.floors?.[a.floor_id]?.level ?? 0, ents: [] });
    const none = { id: "_none", name: "Outros", icon: "mdi:home-outline", ents: [] };
    for (const e of Object.values(h.entities || {})) {
      if (!SHOWN.has(dom(e.entity_id)) || e.hidden || e.entity_category || !h.states[e.entity_id]) continue;
      const area = e.area_id || h.devices?.[e.device_id]?.area_id;
      const r = rooms.get(area);
      if (r) r.ents.push(e.entity_id);
      else if (!["scene", "script"].includes(dom(e.entity_id))) none.ents.push(e.entity_id);
    }
    const list = [...rooms.values()].filter((r) => r.ents.length).sort((a, b) => a.name.localeCompare(b.name, locale()));
    if (none.ents.length) list.push(none);
    for (const r of list) r.count = r.ents.filter((i) => dom(i) !== "scene").length;
    return list;
  }

  _s(id) { return this._hass.states[id]; }
  _name(id) { return this._meta?.[id]?.name || this._s(id)?.attributes.friendly_name || id; }
  _roomName(r) { return r.id === "_none" ? t("Outros") : r.name; }
  _isOn(id) {
    const s = this._s(id); if (!s) return false;
    const d = dom(id);
    if (d === "lock") return s.state === "unlocked";
    if (d === "climate") return s.state !== "off" && s.state !== "unavailable";
    if (d === "media_player") return this._isTv(id) ? !["off", "standby", "unavailable", "unknown"].includes(s.state) : ["playing", "on", "paused", "buffering"].includes(s.state);
    if (d === "vacuum") return ["cleaning", "returning"].includes(s.state);
    return ["on", "open", "opening", "closing", "home"].includes(s.state);
  }
  /** A TV (device class "tv", e.g. HomeKit / Samsung / LG, or "TV" in its name), as opposed to a speaker. */
  _isTv(id) {
    const s = this._s(id);
    if (dom(id) !== "media_player" || !s) return false;
    return s.attributes.device_class === "tv" || /\btv\b|televis/i.test(`${this._meta?.[id]?.name || ""} ${s.attributes.friendly_name || ""}`);
  }
  _icon(id) {
    const s = this._s(id), d = dom(id), dc = s?.attributes.device_class;
    // integration icon sets (e.g. the Philips purifier's "pap:") may not be loaded here: use mdi/hass only
    if (/^(mdi|hass):/.test(s?.attributes.icon || "")) return s.attributes.icon;
    const m = this._meta?.[id];
    if (d === "media_player" && /apple/i.test(m?.manufacturer || "")) {
      if (/homepod/i.test(m.model || "")) return "mdi:speaker";
      if (/tv/i.test(m.model || "")) return "mdi:television-box";
    }
    if (d === "media_player") {
      if (this._isTv(id)) return "mdi:television";
      if (dc === "receiver") return "mdi:audio-video";
    }
    if (d === "light") return this._isOn(id) ? "mdi:lightbulb-on" : "mdi:lightbulb-outline";
    if (d === "lock") return s?.state === "locked" ? "mdi:lock" : "mdi:lock-open-variant";
    if (d === "cover") return s?.state === "closed" ? "mdi:window-shutter" : "mdi:window-shutter-open";
    if (d === "climate") return (s?.attributes.hvac_modes || []).includes("cool") ? "mdi:air-conditioner" : "mdi:thermostat";
    if (DEVICE_CLASS_ICON[dc]) return DEVICE_CLASS_ICON[dc];
    if (["fan", "switch", "select", "number", "sensor", "button"].includes(d)) {
      const n = `${m?.name || ""} ${s?.attributes.friendly_name || ""}`;
      // the purifier hint is for the fan itself; its other entities ("Purifier filter") use the rest
      const hit = NAME_ICON.find(([re, icon]) => (icon === "mdi:air-purifier") === (d === "fan") && re.test(n));
      if (hit) return hit[1];
    }
    return DOMAIN_ICON[d] || "mdi:circle-outline";
  }
  _accent(id) {
    const s = this._s(id);
    if (dom(id) === "light" && s?.attributes.rgb_color) return `rgb(${s.attributes.rgb_color.join(",")})`;
    if (dom(id) === "lock" && s && !["locked", "unavailable", "unknown"].includes(s.state)) return "#ff9f0a"; // unlocked / open / jammed
    return ACCENT[dom(id)] || "#5ac8fa";
  }
  _st(state) { return STATE_PT[state] ? t(STATE_PT[state]) : state; }
  _stateText(id) {
    const s = this._s(id); if (!s) return "";
    const d = dom(id), a = s.attributes;
    if (s.state === "unavailable") return t("Indisponível");
    if (d === "light" && s.state === "on" && a.brightness != null) return `${Math.round((a.brightness / 255) * 100)}%`;
    if (d === "cover" && a.current_position != null && s.state !== "closed") return t("{n}% aberto", { n: a.current_position });
    if (d === "climate") {
      const act = s.state !== "off" && a.hvac_action && !["off", "idle"].includes(a.hvac_action) ? a.hvac_action : null;
      const tgt = a.temperature ?? (a.target_temp_low != null && a.target_temp_high != null ? `${num(a.target_temp_low)}–${num(a.target_temp_high)}` : null);
      return `${this._st(act || s.state)}${a.current_temperature != null ? ` · ${num(a.current_temperature)}°` : ""}${s.state !== "off" && tgt != null ? ` → ${typeof tgt === "number" ? num(tgt) : tgt}°` : ""}`;
    }
    if (d === "media_player") {
      const title = a.media_title ? (a.media_artist ? `${a.media_title} · ${a.media_artist}` : a.media_title) : null;
      if (["playing", "paused"].includes(s.state) && (title || a.app_name)) return title || a.app_name;
      if (a.app_name && !["off", "standby"].includes(s.state)) return `${this._st(s.state)} · ${a.app_name}`;
      if (a.source && this._isTv(id) && !["off", "standby"].includes(s.state)) return `${this._st(s.state)} · ${a.source}`;
    }
    if (d === "binary_sensor") { const m = BIN_PT[a.device_class]; if (m) return t(m[s.state === "on" ? 1 : 0]); }
    if (d === "sensor") return this._sensorVal(id);
    if (d === "scene" || d === "script" || d === "button") return t("Tocar para executar");
    return this._st(s.state);
  }
  _sensorVal(id) {
    const s = this._s(id), u = s.attributes.unit_of_measurement || "";
    const n = Number(s.state);
    const v = Number.isFinite(n) && s.state !== "" ? (Math.abs(n) >= 100 ? Math.round(n) : Math.round(n * 10) / 10).toLocaleString(locale()) : this._st(s.state);
    return `${v}${u ? (u === "°C" || u === "%" ? u : " " + u) : ""}`;
  }
  _roomClimate(r) {
    const by = (dc) => [...(r[dc] || []), ...r.ents].find((i) => dom(i) === "sensor" && this._s(i)?.attributes.device_class === dc && Number.isFinite(Number(this._s(i).state)));
    const tt = by("temperature"), hm = by("humidity");
    const c = r.ents.find((i) => dom(i) === "climate" && this._s(i)?.attributes.current_temperature != null);
    return {
      temp: tt ? this._sensorVal(tt) : c ? `${num(this._s(c).attributes.current_temperature)}°` : null,
      hum: hm ? this._sensorVal(hm) : null,
    };
  }
  _switchables(r) { return r.ents.filter((i) => ["light", "switch", "fan", "input_boolean"].includes(dom(i))); }

  // ---------- actions ----------
  _call(d, svc, data) { return this._hass.callService(d, svc, data); }
  /** Dock buttons: rebuilt only when the items or the language change; otherwise just the active one moves. */
  _updateDock(cfg) {
    const nav = this._dockEl, show = this._view !== "settings" && !this._edit;
    const items = DOCK_ITEMS.filter(([k]) => k === "home" || cfg.dock.includes(k));
    const key = items.map(([k]) => k).join() + lang();
    if (nav._key !== key) {
      nav.querySelectorAll("button").forEach((b) => b.remove());
      nav.insertAdjacentHTML("beforeend", items.map(([k, icon, label]) => `<button data-dock="${k}"><ha-icon icon="${icon}"></ha-icon><span>${t(label)}</span></button>`).join(""));
      nav.querySelectorAll("button").forEach((b) => b.addEventListener("click", () => { this._edit = null; this._go(b.dataset.dock); }));
      nav._key = key;
      this._blobAt = null;
    }
    nav.hidden = !show;
    const active = this._view === "room" ? "rooms" : this._view;
    nav.querySelectorAll("button").forEach((b) => b.classList.toggle("act", b.dataset.dock === active));
    if (!show) { this._blobAt = null; return; }
    this._fitDock(active);
    this._dockSpace();
    this._placeBlob(this._blobAt != null && this._blobAt !== active);
    this._blobAt = active;
  }

  /** Smaller dock when it doesn't fit: first only the active tab keeps its name, then icons only. */
  _fitDock(active = this._blobAt) {
    const nav = this._dockEl;
    if (!nav || nav.hidden) return;
    const avail = Math.min(window.innerWidth, this.clientWidth || window.innerWidth) - 24;
    const sig = `${nav._key}|${active}|${avail}`;
    if (nav._fit === sig) return;
    nav._fit = sig;
    nav.classList.remove("compact", "icons");
    if (nav.scrollWidth > avail) nav.classList.add("compact");
    if (nav.scrollWidth > avail) nav.classList.add("icons");
  }

  /** The glass lens under the active tab; with `animate` it slides there and stretches like a drop on the way. */
  /** The space the floating dock takes at the bottom, so the content stops right above it (no needless scroll). */
  _dockSpace() {
    const nav = this._dockEl;
    if (!nav || nav.hidden) { this.style.removeProperty("--dock-space"); return; }
    const top = nav.getBoundingClientRect().top;
    if (top > 0) this.style.setProperty("--dock-space", `${Math.ceil(window.innerHeight - top)}px`);
  }
  _placeBlob(animate) {
    const nav = this._dockEl, blob = nav?.querySelector(".blob"), act = nav?.querySelector("button.act");
    if (!blob) return;
    if (!act || nav.hidden) { blob.style.opacity = "0"; return; }
    const nr = nav.getBoundingClientRect(), ar = act.getBoundingClientRect();
    if (!nr.width) return;
    blob.style.opacity = "";
    const anim = animate && !this.classList.contains("noanim");
    if (anim) { blob.classList.remove("go"); void blob.offsetWidth; blob.classList.add("go"); }
    else blob.style.transition = "none";
    blob.style.transform = `translateX(${ar.left - nr.left - nav.clientLeft}px)`;
    blob.style.width = `${ar.width}px`;
    if (!anim) { void blob.offsetWidth; blob.style.transition = ""; }
  }

  _go(view, room = null) {
    this._view = view;
    if (room) this._room = room;
    this._render();
    window.scrollTo(0, 0);
  }
  _tap(id) {
    const d = dom(id), s = this._s(id);
    if (!s || s.state === "unavailable") return;
    if (d === "scene") return this._sceneTap(id);
    if (d === "script") return this._call(d, "turn_on", { entity_id: id });
    if (d === "button") return this._call("button", "press", { entity_id: id });
    if (d === "cover") return this._call("cover", s.state === "closed" || s.state === "closing" ? "open_cover" : "close_cover", { entity_id: id });
    if (d === "lock") return this._lockTap(id);
    if (d === "camera") return this._openCam(id);
    if (d === "media_player") return this._isTv(id) ? this._openModal(id) : this._call("media_player", "media_play_pause", { entity_id: id });
    if (d === "climate" || d === "vacuum" || d === "camera" || d === "sensor" || d === "binary_sensor") return this._openModal(id);
    if (TOGGLE.has(d)) return this._call("homeassistant", "toggle", { entity_id: id });
  }
  /** Two-tap actions (unlock, open a door): the first tap arms for 3 s, the second one acts. */
  _armed(key) { return this._arm?.key === key && Date.now() < this._arm.until; }
  _twoTap(key, fn, redraw = () => this._render()) {
    clearTimeout(this._armT);
    if (this._armed(key)) { this._arm = null; fn(); redraw(); return; }
    this._arm = { key, until: Date.now() + 3000 };
    this._armT = setTimeout(() => { this._arm = null; redraw(); }, 3000);
    redraw();
  }
  /** A lock locks with one tap; unlocking takes a second tap, so a stray touch never opens a door. */
  _lockTap(id, redraw) {
    const s = this._s(id);
    if (!s || s.state === "unavailable") return;
    if (s.state === "locked") this._twoTap(id, () => this._call("lock", "unlock", { entity_id: id }), redraw);
    else this._call("lock", "lock", { entity_id: id });
  }

  // ---------- live camera ----------
  /** The last picture this device saw of a camera (kept in this browser only; a thumbnail request would wake some cameras). */
  _camShot(id) {
    this._camShots ||= {};
    if (!(id in this._camShots)) {
      try { this._camShots[id] = JSON.parse(localStorage.getItem(`pine3_cam_${id}`) || "null"); } catch { this._camShots[id] = null; }
    }
    return this._camShots[id];
  }
  _camKeep(id) {
    const tok = this._s(id)?.attributes.access_token;
    if (!tok) return;
    fetch(`/api/camera_proxy/${id}?token=${encodeURIComponent(tok)}`).then((r) => (r.ok ? r.blob() : null)).then((b) => {
      if (!b || !b.size) return;
      const fr = new FileReader();
      fr.onload = () => {
        const shot = { t: Date.now(), d: fr.result };
        (this._camShots ||= {})[id] = shot;
        try { localStorage.setItem(`pine3_cam_${id}`, JSON.stringify(shot)); } catch { /* full or blocked: memory only */ }
        if (!this._camOv && !this._modal) this._render();
      };
      fr.readAsDataURL(b);
    }).catch(() => {});
  }
  /** Door-like entities anywhere in the house: locks and buttons that open something (an intercom's "Activate"). */
  _doorLike(i) { return dom(i) === "lock" || (dom(i) === "button" && /lock|door|gate|entrance|porta|port[aã]o|fechadura|trinco|entrada/i.test(`${i} ${this._name(i)}`)); }
  /**
   * Doors to open from a camera: the ones chosen for it, else the locks of its room, else its door-like buttons.
   * (An apartment door in the same hall must not show up beside the building's camera: hence the choice.)
   */
  _camDoors(id) {
    const chosen = this._cfg().cam_doors?.[id];
    if (Array.isArray(chosen)) return chosen.filter((e) => this._s(e));
    const room = this._rooms().find((r) => r.ents.includes(id));
    const ents = (room?.ents || []).filter((i) => this._s(i));
    const locks = ents.filter((i) => dom(i) === "lock");
    if (locks.length) return locks;
    return ents.filter((i) => dom(i) === "button" && this._doorLike(i));
  }
  _camChoose(e) {
    const cfg = this._cfg(), cur = this._camDoors(this._camId);
    const list = cur.includes(e) ? cur.filter((x) => x !== e) : [...cur, e];
    this._saveSettings({ cam_doors: { ...(cfg.cam_doors || {}), [this._camId]: list } });
    this._camSide();
  }
  /** A camera's live picture in its own window, outside the redrawn part: redraws don't restart the video. */
  _openCam(id) {
    if (!this._s(id)) return;
    this._closeCam(false);
    this._camId = id;
    this._camPicking = false;
    this._camT0 = Date.now();
    this._camEnd = Date.now() + 120000;
    const ov = document.createElement("div");
    ov.className = "ov enter camov";
    ov.innerHTML = `<div class="sheet cam">
      <div class="hd"><div class="ic"><ha-icon icon="${this._icon(id)}"></ha-icon></div><div style="flex:1;min-width:0"><div class="nm">${esc(this._name(id))}</div>
        <div class="st"><span class="live"><i></i>${t("Ao vivo")}</span><span class="cam-el">0:00</span></div></div>
        <button class="sh-x" data-camx title="${t("Fechar")}" aria-label="${t("Fechar")}"><ha-icon icon="mdi:close"></ha-icon></button></div>
      <div class="cam-v"><div class="cam-wait"><ha-icon icon="mdi:cctv"></ha-icon>${t("A ligar à câmara…")}</div><div class="cam-bar"><span><ha-icon icon="mdi:timer-outline"></ha-icon><b class="cam-left"></b></span></div></div>
      <div class="cam-side"></div></div>`;
    const v = ov.querySelector(".cam-v");
    let el;
    if (customElements.get("ha-camera-stream")) {
      el = document.createElement("ha-camera-stream");
      el.hass = this._hass; el.stateObj = this._s(id); el.muted = true; el.controls = false;
    } else {
      el = document.createElement("img");
      el.alt = "";
      el.src = `/api/camera_proxy_stream/${id}?token=${encodeURIComponent(this._s(id).attributes.access_token || "")}`;
    }
    el.className = "cam-s";
    v.prepend(el);
    ov.addEventListener("click", (ev) => {
      if (ev.target === ov || ev.target.closest("[data-camx]")) return this._closeCam();
      if (ev.target.closest("[data-camplus]")) { this._camEnd = Math.min(Date.now() + 600000, Math.max(this._camEnd, Date.now()) + 120000); return this._camTick(); }
      if (ev.target.closest("[data-campick]")) { this._camPicking = !this._camPicking; return this._camSide(); }
      const c = ev.target.closest("[data-camsel]")?.dataset.camsel;
      if (c) return this._camChoose(c);
      const d = ev.target.closest("[data-camdoor]")?.dataset.camdoor;
      if (d) this._camDoor(d);
    });
    this._camKeyFn = (ev) => { if (ev.key === "Escape") this._closeCam(); };
    window.addEventListener("keydown", this._camKeyFn);
    this.shadowRoot.appendChild(ov);
    this._camOv = ov;
    this._camSide();
    this._camTick();
    this._camTimer = setInterval(() => this._camTick(), 1000);
  }
  _camDoor(d) {
    if (dom(d) === "lock" && this._s(d)?.state !== "locked") return;
    this._twoTap(`cam:${d}`, () => {
      if (dom(d) === "lock") this._call("lock", "unlock", { entity_id: d });
      else this._call("button", "press", { entity_id: d });
      this._camDone = { id: d, until: Date.now() + 5000 };
      this._camEnd = Math.min(this._camEnd, Date.now() + 5000); // opened: the window closes by itself
    }, () => this._camSide());
  }
  _camTick() {
    if (!this._camOv) return;
    const now = Date.now();
    if (now >= this._camEnd) return this._closeCam();
    const mmss = (ms) => { const x = Math.max(0, Math.round(ms / 1000)); return `${Math.floor(x / 60)}:${String(x % 60).padStart(2, "0")}`; };
    this._camOv.querySelector(".cam-el").textContent = mmss(now - this._camT0);
    this._camOv.querySelector(".cam-left").textContent = t("fecha em {t}", { t: mmss(this._camEnd - now) });
    if (this._camDone && now > this._camDone.until) { this._camDone = null; this._camSide(); }
  }
  _camSide() {
    if (!this._camOv) return;
    const doors = this._camDoors(this._camId);
    if (this._camPicking) {
      const all = [...new Set(this._rooms().flatMap((r) => r.ents))].filter((i) => this._s(i) && this._doorLike(i));
      const roomOf = (i) => this._rooms().find((r) => r.ents.includes(i))?.name || "";
      this._camOv.querySelector(".cam-side").innerHTML = `<div class="cam-pk-h">${t("Portas ao lado desta câmara")}</div>
        ${all.map((i) => `<button class="cam-pk ${doors.includes(i) ? "on" : ""}" data-camsel="${esc(i)}"><ha-icon icon="mdi:${doors.includes(i) ? "checkbox-marked-circle" : "checkbox-blank-circle-outline"}"></ha-icon><span>${esc(this._name(i))}<small>${esc(roomOf(i))}</small></span></button>`).join("") || `<div class="cam-note">${t("Não há fechaduras nem botões de porta.")}</div>`}
        <button class="pill solid cam-pk-ok" data-campick><ha-icon icon="mdi:check"></ha-icon>${t("Concluído")}</button>`;
      return;
    }
    const btn = (d) => {
      const open = (this._camDone?.id === d) || (dom(d) === "lock" && this._s(d).state !== "locked");
      const armed = !open && this._armed(`cam:${d}`), na = this._s(d).state === "unavailable";
      return `<button class="cam-b door ${armed ? "arm" : ""} ${open ? "ok" : ""}" data-camdoor="${esc(d)}" ${na ? "disabled" : ""}>
        <ha-icon icon="mdi:${open ? "check" : "door-open"}"></ha-icon><b>${open ? t("Aberta") : armed ? t("Toca outra vez") : t("Abrir")}</b><small>${esc(this._name(d))}${open || armed ? "" : ` · ${t("toca 2 vezes")}`}</small></button>`;
    };
    this._camOv.querySelector(".cam-side").innerHTML = `${doors.map(btn).join("")}
      <button class="cam-b" data-camplus><ha-icon icon="mdi:timer-plus-outline"></ha-icon><b>+2 min</b><small>${t("manter ao vivo")}</small></button>
      <div class="cam-note">${t("Ao fechar, o vídeo para.")}<button class="cam-edit" data-campick><ha-icon icon="mdi:pencil-outline"></ha-icon>${t("Escolher portas")}</button></div>`;
  }
  _camUpdate() {
    const el = this._camOv?.querySelector("ha-camera-stream");
    if (el) { el.hass = this._hass; el.stateObj = this._s(this._camId); }
    this._camSide();
  }
  _closeCam(keep = true) {
    if (!this._camOv) return;
    const id = this._camId;
    clearInterval(this._camTimer);
    window.removeEventListener("keydown", this._camKeyFn);
    this._camOv.remove();
    this._camOv = null;
    this._camDone = null;
    // the session is still open for a few seconds: keep one picture for the camera's card
    if (keep) this._camKeep(id);
  }
  /** A camera card: the last picture seen on this device (or its icon) and a play button; it never streams by itself. */
  _camTile(id) {
    const s = this._s(id), shot = this._camShot(id);
    const lang = this._hass?.locale?.language || this._hass?.language;
    const when = shot ? new Date(shot.t) : null;
    const whenTxt = when ? (when.toDateString() === new Date().toDateString() ? when.toLocaleTimeString(lang, { hour: "2-digit", minute: "2-digit" }) : when.toLocaleDateString(lang, { day: "numeric", month: "short" })) : "";
    return `<div class="tile camt big glass ${shot ? "pic" : ""} ${s.state === "unavailable" ? "na" : ""}" data-cam="${esc(id)}" data-key="${esc(id)}">
      ${shot ? `<div class="camt-bg" style="background-image:url('${shot.d}')"></div>` : ""}<div class="camt-sh"></div>
      ${this._meta?.[id] ? this._editChrome("dev", id) : ""}
      <div class="row1"><div class="ic"><ha-icon icon="${this._icon(id)}"></ha-icon></div></div>
      <div class="camt-play"><ha-icon icon="mdi:play"></ha-icon></div>
      <div class="tx"><div class="nm">${esc(this._name(id))}</div><div class="st">${shot ? `${esc(t("Última imagem · {t}", { t: whenTxt }))} · ` : ""}${t("Toca para ver ao vivo")}</div></div>
    </div>`;
  }

  _openModal(id) { if (dom(id) === "camera") return this._openCam(id); this._modal = id; this._modalAt = Date.now(); this._render(); }
  /** A click that lands on a card just opened: the end of the press that opened it (a long press on a tablet), not a choice. */
  _ghost(el) { return !!el.closest?.(".ov") && Date.now() - (this._modalAt || 0) < 600; }
  _moreInfo(id) {
    this.dispatchEvent(new CustomEvent("hass-more-info", { detail: { entityId: id }, bubbles: true, composed: true }));
  }

  // ---------- full screen & kiosk (per device) ----------
  _fsSupported() { return !!(document.fullscreenEnabled && document.documentElement.requestFullscreen); }
  _toggleFullscreen() {
    if (document.fullscreenElement) document.exitFullscreen?.();
    else document.documentElement.requestFullscreen?.({ navigationUI: "hide" }).catch((e) => console.warn("Pine3 View: fullscreen", e));
  }
  /** Ask the Home Assistant frontend to hide (or dock again) its sidebar. */
  _dockSidebar(hide) {
    this.dispatchEvent(new CustomEvent("hass-dock-sidebar", { detail: { dock: hide ? "always_hidden" : "docked" }, bubbles: true, composed: true }));
  }
  async _applyWakeLock() {
    if (!store.get("wake_lock", false) || !navigator.wakeLock || this._wake) return;
    try {
      this._wake = await navigator.wakeLock.request("screen");
      this._wake.addEventListener("release", () => { this._wake = null; });
    } catch (e) { console.warn("Pine3 View: wake lock", e); }
  }
  // ---------- per-device performance (stored in this browser only) ----------
  /** A weak device: little memory (Chrome reports ≤2 GB) or an old engine without color-mix(). */
  _weakDevice() {
    const mem = navigator.deviceMemory;
    return (mem != null && mem <= 2) || !window.CSS?.supports?.("color", "color-mix(in srgb, red, blue)");
  }
  /** How this device shows the house: full | lite | image | plan. */
  _houseMode() {
    const v = store.get("house_mode", "auto");
    if (v !== "auto") return v;
    const mem = navigator.deviceMemory;
    return this._weakDevice() ? "image" : mem != null && mem <= 4 ? "lite" : "full";
  }
  /** Light effects: no frosted-glass blur (backdrop-filter), the costliest thing for a weak GPU. */
  _liteFx() { const v = store.get("lite_fx", "auto"); return v === "auto" ? this._weakDevice() : v === "on"; }
  /** Animations: this device's choice, or the panel-wide setting. */
  _animOn(cfg) { const v = store.get("anim", "auto"); return v === "auto" ? cfg.animations !== false : v === "on"; }
  _localChoice(key, value, options, label, sub) {
    return `<div class="opt col"><div><b>${label}</b>${sub ? `<small>${sub}</small>` : ""}</div><div class="seg">${options.map(([v, l]) =>
      `<button class="pill ${v === value ? "solid" : ""}" data-lchoice="${key}" data-val="${v}">${l}</button>`).join("")}</div></div>`;
  }

  _localToggle(key, label, sub, disabled) {
    const on = store.get(key, false);
    return `<div class="opt ${disabled ? "dis" : ""}"><div><b>${label}</b>${sub ? `<small>${sub}</small>` : ""}</div><div class="tg ${on ? "on" : ""}" data-local="${key}"><i></i></div></div>`;
  }

  // ---------- clock & weather ----------
  _clockText() {
    const h12 = !this._cfg().clock_24h;
    return new Date().toLocaleTimeString(h12 ? "en-US" : locale(), { hour: "2-digit", minute: "2-digit", hour12: h12 });
  }
  _dateText(opts = { weekday: "long", day: "numeric", month: "long" }) { return cap(new Date().toLocaleDateString(locale(), opts)); }
  _updateClock() {
    this.shadowRoot.querySelectorAll(".clock").forEach((el) => { el.textContent = this._clockText(); });
  }
  _wx() { return this._weather.current(this._hass, this._cfg().weather_entity); }
  _wxLabel(cond) { return WEATHER_PT[cond] ? t(WEATHER_PT[cond]) : cond; }
  _wxPill() {
    const w = this._wx();
    if (!w) return `<div class="wpill glass" data-wx><ha-icon icon="mdi:weather-cloudy-clock"></ha-icon><span>${t("A carregar o tempo…")}</span></div>`;
    const al = this._ipmaAlert();
    return `<div class="wpill glass" data-wx title="${t("Previsão")}"><ha-icon icon="${WEATHER_ICON[w.cond] || "mdi:weather-partly-cloudy"}"></ha-icon><b>${num(Math.round(w.temp ?? 0))}°</b><span>${esc(this._wxLabel(w.cond))}</span>${al ? `<ha-icon class="wx-al lv-${al.level}" icon="mdi:alert"></ha-icon>` : ""}</div>`;
  }
  async _openForecast() {
    this._modal = "_wx";
    this._fc = null;
    this._render();
    this._fc = await this._weather.forecast(this._hass, this._cfg().weather_entity);
    if (this._modal === "_wx") this._render();
  }
  // ---------- IPMA (Portuguese weather service): warnings, UV index and fire risk ----------
  _ipma() {
    const h = this._hass, st = h.states;
    const ids = Object.keys(st).filter((i) => dom(i) === "sensor" && (h.entities?.[i]?.platform === "ipma" || /ipma/.test(i)));
    const levels = ["green", "yellow", "orange", "red"];
    return {
      alert: ids.find((i) => st[i].attributes.awarenessTypeName != null || (levels.includes(st[i].state) && /alert|aviso|warning/.test(i))),
      uv: ids.find((i) => /uv/.test(i)),
      fire: ids.find((i) => /rcm|fire|incend/.test(i)),
    };
  }
  /**
   * The active IPMA warnings (yellow / orange / red), most severe first. The integration's sensor holds only
   * the first one; when it names its area (idAreaAviso), all of them are asked of the integration's backend
   * (pine3_view/ipma_warnings, every 10 minutes) and that list wins.
   */
  /** IPMA warnings, most severe first; never breaks the panel if the IPMA entity changes shape. */
  _ipmaAlerts() {
    try { return this._ipmaList(); } catch (e) { console.warn("Pine3 View: IPMA warnings", e); return []; }
  }
  _ipmaList() {
    const id = this._ipma().alert, s = id && this._s(id);
    if (!s) return [];
    const a = s.attributes, when = (v) => (v ? new Date(String(v).replace(" ", "T")) : null);
    const norm = (w) => ({ level: w.awarenessLevelID, type: w.awarenessTypeName || "", text: w.text || "", start: when(w.startTime), end: when(w.endTime) });
    const area = a.idAreaAviso;
    if (area && this._model) {
      const c = this._ipmaW;
      if ((!c || c.area !== area || Date.now() - c.at > 10 * 60e3) && !this._ipmaLoading) {
        this._ipmaLoading = true;
        this._hass.callWS({ type: "pine3_view/ipma_warnings", area })
          .then((r) => { this._ipmaW = { area, at: Date.now(), list: r.warnings || [] }; })
          .catch(() => { this._ipmaW = { area, at: Date.now(), list: null }; })
          .finally(() => { this._ipmaLoading = false; if (this._view === "home" || this._modal === "_wx") this._render(); });
      }
    }
    const rank = { red: 3, orange: 2, yellow: 1 };
    const fromApi = area && this._ipmaW?.area === area && Array.isArray(this._ipmaW.list) ? this._ipmaW.list.map(norm) : null;
    const list = fromApi ?? (["yellow", "orange", "red"].includes(s.state) ? [norm({ ...a, awarenessLevelID: s.state })] : []);
    return list.filter((w) => rank[w.level] && !(w.end && w.end < new Date()))
      .sort((x, y) => rank[y.level] - rank[x.level] || (x.start || 0) - (y.start || 0));
  }
  _ipmaAlert() { return this._ipmaAlerts()[0] || null; }
  _alertFmt(d) {
    const today = d.toDateString() === new Date().toDateString();
    const hm = d.toLocaleTimeString(locale(), { hour: "2-digit", minute: "2-digit" });
    return today ? hm : `${d.toLocaleDateString(locale(), { weekday: "short" })} ${hm}`;
  }
  _alertWhen(al) {
    if (al.start && al.start > new Date()) return t("a partir de {t}", { t: this._alertFmt(al.start) });
    return al.end && !isNaN(al.end) ? t("até {t}", { t: this._alertFmt(al.end) }) : "";
  }
  /** A warning as a strip ("+N" for the others), or in full with its start, end and text. */
  _alertBanner(al, full = false, more = 0) {
    const lv = { yellow: t("Aviso amarelo"), orange: t("Aviso laranja"), red: t("Aviso vermelho") }[al.level];
    const period = full ? [al.start && !isNaN(al.start) ? `${t("Começa")} ${this._alertFmt(al.start)}` : "", al.end && !isNaN(al.end) ? `${t("Termina")} ${this._alertFmt(al.end)}` : ""].filter(Boolean).join(" · ") : this._alertWhen(al);
    return `<div class="wx-alert lv-${al.level} ${full ? "full" : ""}"><ha-icon icon="mdi:alert"></ha-icon><div class="wx-at"><b>${esc(lv)}${al.type ? ` · ${esc(al.type)}` : ""}</b>${period ? `<small>${esc(period)}</small>` : ""}${full && al.text ? `<p>${esc(al.text)}</p>` : ""}</div>${more ? `<span class="wx-more" data-alertmore>+${more}</span>` : ""}</div>`;
  }

  /** The forecast for the home weather card, refreshed every 15 minutes (loads in the background, then redraws). */
  _homeForecast() {
    const c = this._hfc;
    if ((!c || Date.now() - c.at > 15 * 60e3) && !this._hfcLoading) {
      this._hfcLoading = true;
      this._weather.forecast(this._hass, this._cfg().weather_entity)
        .then((fc) => { this._hfc = { fc, at: Date.now() }; })
        .catch(() => { this._hfc = { fc: { hourly: [], daily: [] }, at: Date.now() }; })
        .finally(() => { this._hfcLoading = false; if (this._view === "home" && !this._drag) this._render(); });
    }
    return c?.fc || null;
  }
  /**
   * Weather card for the home screen: now (big temperature, condition, today's high/low and rain),
   * the next hours and tomorrow. "wide" lays them side by side, "tall" stacks them. A tap opens the full forecast.
   */
  _wxCard(kind) {
    const w = this._wx(), fc = this._homeForecast();
    const big = kind === "big", side = kind.includes("side");
    const icon = (c) => WEATHER_ICON[c] || "mdi:weather-cloudy";
    const deg = (v) => (v == null ? "–" : `${num(Math.round(v))}°`);
    const rain = (p) => (p != null && p >= 10 ? `<small class="pp"><ha-icon icon="mdi:water-outline"></ha-icon>${Math.round(p)}%</small>` : "");
    const daily = fc?.daily || [];
    const isToday = (d) => d && new Date(d.time).toDateString() === new Date().toDateString();
    const today = isToday(daily[0]) ? daily[0] : null;
    const hrs = (fc?.hourly || []).filter((f) => f.time >= Date.now() - 30 * 60e3).slice(0, big ? 4 : 5);
    const hh = (d) => d.toLocaleTimeString(locale(), { hour: "2-digit", minute: "2-digit" });
    const now = w ? `<div class="wx-now"><ha-icon icon="${icon(w.cond)}"></ha-icon><div class="wx-t"><b>${deg(w.temp)}</b><span>${esc(this._wxLabel(w.cond))}</span>
        ${today ? `<small>${t("Máx.")} ${deg(today.temp)} · ${t("Mín.")} ${deg(today.low)}</small>` : ""}</div></div>`
      : `<div class="wx-now"><ha-icon icon="mdi:weather-cloudy-clock"></ha-icon><div class="wx-t"><span>${t("A carregar o tempo…")}</span></div></div>`;
    const hours = hrs.length ? `<div class="wx-hrs">${hrs.map((f) => `<div><small>${hh(f.time)}</small><ha-icon icon="${icon(f.cond)}"></ha-icon><b>${deg(f.temp)}</b>${rain(f.precip)}</div>`).join("")}</div>`
      : fc ? "" : `<div class="wx-hrs wx-wait">${t("A carregar a previsão…")}</div>`;
    // the next days (3, or 5 in the sidebar), each with a range bar on the scale of the days shown
    const days = daily.slice(today ? 1 : 0).slice(0, big ? 3 : 7); // tall cards show as many as fit (see _fitDays)
    const lo = (d) => d.low ?? d.temp, L = Math.min(...days.map(lo)), H = Math.max(...days.map((d) => d.temp)), span = H - L || 1;
    const pos = (v) => Math.max(0, Math.min(100, ((v - L) / span) * 100));
    const dname = (d, k) => (k === 0 && today ? t("Amanhã") : cap(d.time.toLocaleDateString(locale(), { weekday: "short" }).replace(".", "").slice(0, 3)));
    const next = days.length ? `<div class="wx-days">${days.map((d, k) => `<div><span>${esc(dname(d, k))}</span><ha-icon icon="${icon(d.cond)}"></ha-icon>${big ? "" : `<small class="pp">${d.precip != null && d.precip >= 10 ? `${Math.round(d.precip)}%` : ""}</small>`}<small>${deg(lo(d))}</small>
        <i class="frng"><i style="left:${pos(lo(d))}%;width:${Math.max(4, pos(d.temp) - pos(lo(d)))}%;background:linear-gradient(90deg,${tempColor(lo(d))},${tempColor(d.temp)})"></i></i><b>${deg(d.temp)}</b></div>`).join("")}</div>` : "";
    const als = this._ipmaAlerts(), al = als[0];
    return `<div class="wxc ${kind} glass ${al ? "has-alert" : ""}" data-wx title="${t("Previsão")}">${now}${big ? next + hours : hours + next}${al ? this._alertBanner(al, false, als.length - 1) : ""}</div>`;
  }

  _forecastSheet() {
    const w = this._wx(), fc = this._fc;
    const src = w?.source.kind === "entity" ? this._s(w.source.id)?.attributes.friendly_name || w.source.id : "Open-Meteo";
    const icon = (c) => WEATHER_ICON[c] || "mdi:weather-cloudy";
    const deg = (v) => (v == null ? "–" : `${num(Math.round(v))}°`);
    const hm = (d) => d.toLocaleTimeString(locale(), { hour: "2-digit", minute: "2-digit" });
    const daily = fc?.daily || [], hourly = fc?.hourly || [];
    const today = daily[0] && new Date(daily[0].time).toDateString() === new Date().toDateString() ? daily[0] : null;
    // now: big temperature, condition, today's high / low, then chips (rain, sunrise, sunset, humidity, wind)
    const sun = this._s("sun.sun")?.attributes || {}, at = (v) => (v ? new Date(v) : null);
    const rise = at(sun.next_rising), set = at(sun.next_setting);
    const chip = (ic, txt) => `<span><ha-icon icon="${ic}"></ha-icon>${txt}</span>`;
    const chips = [today?.precip != null ? chip("mdi:water-outline", `${t("Chuva")} <b>${Math.round(today.precip)}%</b>`) : "",
      rise ? chip("mdi:weather-sunset-up", `${t("Nascer")} <b>${hm(rise)}</b>`) : "", set ? chip("mdi:weather-sunset-down", `${t("Pôr")} <b>${hm(set)}</b>`) : "",
      w?.humidity != null ? chip("mdi:water-percent", `${t("Humidade")} <b>${Math.round(w.humidity)}%</b>`) : "",
      w?.wind != null ? chip("mdi:weather-windy", `${t("Vento")} <b>${Math.round(w.wind)} ${esc(w.windUnit)}</b>`) : ""].join("");
    const now = w ? `<div class="fnow"><ha-icon icon="${icon(w.cond)}"></ha-icon><b>${num(Math.round(w.temp))}°</b><div><span>${esc(this._wxLabel(w.cond))}</span>
        ${today ? `<small>${t("Máx.")} ${deg(today.temp)} · ${t("Mín.")} ${deg(today.low)}</small>` : ""}</div></div>` : "";
    // warnings (most severe first), "no warnings", UV and fire risk
    const als = this._ipmaAlerts(), ip = this._ipma();
    const uv = ip.uv && this._s(ip.uv), fire = ip.fire && this._s(ip.fire);
    const extra = [uv && uv.state !== "unknown" ? chip("mdi:sun-wireless-outline", `${t("Índice UV")} <b>${esc(uv.state)}</b>`) : "",
      fire && fire.state !== "unknown" ? chip("mdi:fire", `${t("Risco de incêndio")} <b>${esc(this._fmtState(ip.fire))}</b>`) : ""].join("");
    const warn = als.length ? `<div class="sub">${t("Avisos")} <span class="badge">${als.length}</span></div>${als.map((x) => this._alertBanner(x, true)).join("")}`
      : ip.alert ? `<div class="wx-ok"><ha-icon icon="mdi:check-circle-outline"></ha-icon>${t("Sem avisos do IPMA")}</div>` : "";
    const left = `${now}${chips ? `<div class="wx-chips">${chips}</div>` : ""}${warn}${extra ? `<div class="wx-chips">${extra}</div>` : ""}
      <div class="fsrc">${t("Fonte")}: ${esc(src)}</div>`;
    // next hours as small cards, next days with a temperature range bar on the week's scale
    const hour = (f, k) => `<div class="fh"><small>${k === 0 && f.time - Date.now() < 3600e3 ? t("Agora") : hm(f.time)}</small><ha-icon icon="${icon(f.cond)}"></ha-icon><b>${deg(f.temp)}</b><small class="pp">${f.precip != null ? `${Math.round(f.precip)}%` : ""}</small></div>`;
    const lows = daily.map((d) => d.low ?? d.temp).filter((v) => v != null), highs = daily.map((d) => d.temp).filter((v) => v != null);
    const L = Math.min(...lows), H = Math.max(...highs), span = H - L || 1;
    const pos = (v) => Math.max(0, Math.min(100, ((v - L) / span) * 100));
    const day = (f, k) => {
      const lo = f.low ?? f.temp, hi = f.temp;
      const dot = k === 0 && today && w?.temp != null ? `<i class="fdot" style="left:${pos(w.temp)}%"></i>` : "";
      return `<div class="fd"><span>${k === 0 && today ? t("Hoje") : cap(f.time.toLocaleDateString(locale(), { weekday: "short" }).replace(".", "").slice(0, 3))}</span><ha-icon icon="${icon(f.cond)}"></ha-icon>
        <small class="pp">${f.precip != null ? Math.round(f.precip) + "%" : ""}</small><small>${deg(lo)}</small>
        <div class="frng"><i style="left:${pos(lo)}%;width:${Math.max(4, pos(hi) - pos(lo))}%;background:linear-gradient(90deg,${tempColor(lo)},${tempColor(hi)})"></i>${dot}</div><b>${deg(hi)}</b></div>`;
    };
    const main = !fc ? `<div class="empty" style="text-align:center">${t("A carregar a previsão…")}</div>` : `
        ${hourly.length ? `<div class="sub">${t("Próximas horas")}</div><div class="fhs">${hourly.map(hour).join("")}</div>` : ""}
        ${daily.length ? `<div class="sub">${t("Próximos dias")}</div><div class="fds">${daily.map(day).join("")}</div>` : ""}
        ${!hourly.length && !daily.length ? `<div class="empty">${t("Sem previsão disponível.")}</div>` : ""}`;
    // wide screens: now, warnings, UV and fire risk on the left, the forecast on the right
    return `<div class="ov ${this._ovEnter("_wx")}" data-close><div class="sheet wide cols fcst">
      <div class="hd"><div style="flex:1;min-width:0"><div class="nm">${t("Previsão do tempo")}</div></div><button class="sh-x" data-closex title="${t("Fechar")}" aria-label="${t("Fechar")}"><ha-icon icon="mdi:close"></ha-icon></button></div>
      <div class="sh-main">${left}</div><div class="sh-side">${main}</div>
    </div></div>`;
  }

  // ---------- settings ----------
  _openSettings(section) {
    if (section) this._section = section;
    const c = this._saved();
    if (!c.room_order?.length) c.room_order = this._rooms().map((r) => r.id);
    this._draft = clone(c);
    this._savedJson = JSON.stringify(this._draft);
    this._prevView = this._view === "settings" ? "home" : this._view;
    this._edit = null;
    this._modal = null;
    this._go("settings");
  }
  _closeSettings() { this._draft = null; this._go(this._prevView || "home"); }

  async _saveDraft() {
    const d = this._draft;
    this._theme = d.theme;
    store.set("theme", d.theme);
    try {
      await this._persist(d);
      if (this._model) this._model = await this._hass.callWS({ type: "pine3_view/home" });
      this._toast = t("Alterações guardadas");
    } catch (e) { console.error("Pine3 View", e); this._toast = t("Não foi possível guardar"); }
    this._savedJson = JSON.stringify(d);
    this._render();
    setTimeout(() => { this._toast = null; this._render(); }, 1800);
  }

  _toggle(key, on, label, sub) {
    return `<div class="opt"><div><b>${label}</b>${sub ? `<small>${sub}</small>` : ""}</div><div class="tg ${on ? "on" : ""}" data-tg="${key}"><i></i></div></div>`;
  }
  _choice(key, value, options, label, sub) {
    return `<div class="opt col"><div><b>${label}</b>${sub ? `<small>${sub}</small>` : ""}</div><div class="seg">${options.map(([v, l]) =>
      `<button class="pill ${String(v) === String(value) ? "solid" : ""}" data-choice="${key}" data-val="${esc(v)}" data-type="${typeof v}">${l}</button>`).join("")}</div></div>`;
  }

  _settingsView() {
    const d = this._draft, sec = this._section || "look";
    const dirty = JSON.stringify(d) !== this._savedJson;
    const navItems = [
      ["look", "mdi:palette-outline", t("Aparência")], ["home", "mdi:home-outline", t("Início")], ["rooms", "mdi:view-grid-outline", t("Divisões")],
      ["entities", "mdi:format-list-checks", t("Entidades")], ["plan", "mdi:floor-plan", t("Planta 3D")],
      ["nav", "mdi:dock-bottom", t("Navegação")], ["system", "mdi:cog-outline", t("Sistema")],
    ];
    let main = "";
    if (sec === "look") {
      main = `<div class="panel glass"><div class="opts">
          ${this._choice("language", d.language, [["pt", "Português"], ["en", "English"]], t("Idioma"), t("Idioma de todo o painel."))}
          ${this._choice("layout", d.layout, [["grid", t("Grelha")], ["list", t("Lista")]], t("Disposição"), t("Como as divisões e os dispositivos aparecem."))}
          ${this._toggle("animations", d.animations !== false, t("Animações"), t("Transições ao mudar de ecrã, a lente da dock e a abertura dos cartões. Desliga para um painel mais leve."))}
        </div></div>
        <div class="panel glass">
          <div class="preview">
            <div class="rc on pv"><span class="pv-badge">2</span><div class="ri"><ha-icon icon="mdi:lightbulb-outline"></ha-icon></div>
              <div class="foot"><div class="nm">${t("Sala")}</div><div class="meta">75%</div><div class="pv-bar"><i></i></div></div></div>
            <div class="rc glass pv off"><div class="ri"><ha-icon icon="mdi:bed"></ha-icon></div>
              <div class="foot"><div class="nm">${t("Quarto")}</div><div class="meta">${t("Tudo desligado")}</div><div class="dots"><i></i><i class="on"></i><i></i></div></div></div>
          </div>
          <h3>${t("Cor dos cartões")}</h3><p>${t("Superfície suave dos cartões ligados.")}</p>
          <div class="swr">${CARD_COLORS.map((c) => `<div class="swc ${c === d.card_color ? "act" : ""}" data-card="${c}" style="background:${c}"></div>`).join("")}</div>
          <h3>${t("Cor de destaque")}</h3><p>${t("Ícones acesos, pontos das luzes, sliders e contadores.")}</p>
          <div class="swr">${ACCENTS.map((c) => `<div class="swc ${c === d.accent_color ? "act" : ""}" data-accent="${c}" style="background:${c}"></div>`).join("")}</div>
        </div>
        ${(() => {
          // themes: dark and light; automatic = one for the day and one for the night, by sunrise and sunset
          const keys = Object.keys(THEMES), dark = keys.filter((k) => !LIGHT_THEMES.has(k) && !VIVID_THEMES.has(k) && !AMBIENT_THEMES.has(k)), amb = keys.filter((k) => AMBIENT_THEMES.has(k)), vivid = keys.filter((k) => VIVID_THEMES.has(k)), lightK = keys.filter((k) => LIGHT_THEMES.has(k));
          const grid = (attr, cur, list) => `<div class="bgs">${list.map((k) => `<div class="bgc ${k === cur ? "act" : ""}" data-${attr}="${k}"><div style="background:${THEMES[k]}"></div><span>${t(THEME_NAMES[k])}</span></div>`).join("")}</div>`;
          const auto = d.appearance === "auto";
          const sun = this._s("sun.sun");
          return `<div class="panel glass"><h3 style="margin-top:0">${t("Fundo")}</h3><p>${t("O ambiente por trás dos cartões. Os fundos claros põem todo o painel em modo claro.")}</p>
            <div class="opts">${this._choice("appearance", auto ? "auto" : "fixed", [["fixed", t("Fixo")], ["auto", t("Automático")]], t("Modo"),
              auto ? (sun ? t("Usa o fundo de dia entre o nascer e o pôr do sol (sun.sun) e o de noite no resto do tempo.") : t("Sem a entidade sun.sun: usa o fundo de dia das 7h às 19h.")) : t("Um fundo sempre igual, ou um para o dia e outro para a noite, ao nascer e ao pôr do sol."))}</div>
            ${auto ? `<h3>${t("De dia")}</h3>${grid("themeday", d.theme_day || "linho", [...lightK, ...vivid, ...amb, ...dark])}<h3>${t("À noite")}</h3>${grid("themenight", d.theme_night || "ameixa", [...amb, ...dark, ...vivid, ...lightK])}`
              : `<h3>${t("Ambiente")}</h3>${grid("theme", d.theme, amb)}<h3>${t("Vivos")}</h3>${grid("theme", d.theme, vivid)}<h3>${t("Escuros")}</h3>${grid("theme", d.theme, dark)}<h3>${t("Claros")}</h3>${grid("theme", d.theme, lightK)}`}</div>`;
        })()}`;
    } else if (sec === "home") {
      const wxs = this._weather.entities(this._hass);
      main = `<div class="panel glass"><h3 style="margin-top:0">${t("Ecrã inicial")}</h3><p>${t("O que aparece no início.")}</p><div class="opts">
        ${this._toggle("show_weather", d.show_weather, t("Meteorologia"), t("Pílula com a temperatura e o estado do tempo. Toca nela para ver a previsão."))}
        ${this._choice("weather_entity", d.weather_entity, [["", t("Automático")], ...wxs.map((i) => [i, esc(this._s(i).attributes.friendly_name || i)]), ["open-meteo", "Open-Meteo"]],
          t("Fonte do tempo"), t("Automático usa a primeira entidade de tempo do Home Assistant ou, se não houver, a Open-Meteo para a localização da casa."))}
        ${this._toggle("home_people", !!d.home_people, t("Pessoas"), t("Quem está em casa, ao lado da data."))}
        ${this._choice("home_layout", d.home_layout || "top", [["top", t("Relógio grande")], ["columns", t("Duas colunas")], ["sidebar", t("Barra lateral")], ["classic", t("Clássica")]], t("Disposição do início"), t("Relógio grande com o estado da casa ao lado; duas colunas, com o relógio, o tempo e o que está ligado à esquerda e os cartões à direita; uma barra lateral de vidro com o relógio, o tempo e o estado, e as cenas e os cartões ao lado; ou a disposição original."))}
        ${this._toggle("show_status", d.show_status, t("Estado da casa"), t("Luzes acesas, outros aparelhos ligados e botões Acender/Apagar luzes."))}
        ${this._toggle("show_scenes", d.show_scenes, t("Cenas"), t("Faixa de cenas no início."))}
        ${this._toggle("show_summary", d.show_summary, t("Cartões de resumo"), t("Clima, Segurança e A tocar."))}
        ${this._toggle("clock_24h", d.clock_24h, t("Relógio de 24 horas"), t("Desliga para mostrar AM/PM."))}
      </div></div>
      <div class="panel glass"><h3 style="margin-top:0">${t("Este dispositivo")}</h3><p>${t("Opções guardadas só neste browser, para um tablet na parede ficar diferente do telemóvel.")}</p><div class="opts">
        ${this._localToggle("hide_sidebar", t("Esconder a barra lateral do Home Assistant"), t("O painel ocupa a largura toda. Também podes abrir o painel com ?kiosk no endereço."))}
        ${this._localToggle("wake_lock", t("Manter o ecrã ligado"), navigator.wakeLock ? t("Impede o ecrã de adormecer enquanto o painel está aberto.") : t("Este browser não suporta manter o ecrã ligado."), !navigator.wakeLock)}
        ${(() => {
          const modes = { full: t("3D completo"), lite: t("3D leve"), image: t("Imagem 3D"), plan: t("Planta 2D") };
          const hm = store.get("house_mode", "auto"), fx = store.get("lite_fx", "auto"), an = store.get("anim", "auto");
          return this._localChoice("house_mode", hm, [["auto", hm === "auto" ? t("Automático ({m})", { m: modes[this._houseMode()] }) : t("Automático")], ...Object.entries(modes)],
              t("Vista da Casa 3D"), t("Em tablets mais fracos, a Imagem 3D desenha a casa como uma imagem (leve e fluida) e a Planta 2D mostra-a vista de cima. O 3D leve mantém a vista atual sem os efeitos mais pesados."))
            + this._localChoice("lite_fx", fx, [["auto", fx === "auto" ? t("Automático ({m})", { m: this._liteFx() ? t("Ligado") : t("Desligado") }) : t("Automático")], ["on", t("Ligado")], ["off", t("Desligado")]],
              t("Efeitos leves"), t("Tira o desfoque do vidro (o mais pesado para a placa gráfica). Usa se vires zonas brancas, vazias ou a piscar."))
            + this._localChoice("anim", an, [["auto", t("Como no painel")], ["on", t("Ligadas")], ["off", t("Desligadas")]],
              t("Animações neste dispositivo"), t("Sobrepõe a opção Animações da Aparência só neste dispositivo."));
        })()}
        ${this._fsSupported() ? `<div class="opt"><div><b>${t("Ecrã inteiro")}</b><small>${t("Esconde as barras do browser.")}</small></div><button class="pill" data-fs><ha-icon icon="mdi:fullscreen"></ha-icon>${document.fullscreenElement ? t("Sair do ecrã inteiro") : t("Ecrã inteiro")}</button></div>` : ""}
      </div></div>
      <div class="panel glass"><div class="opts">
        ${this._choice("screensaver", d.screensaver, SAVER_STEPS.map((m) => [m, m ? t("{n} min", { n: m }) : t("Desligado")]), t("Ecrã de descanso"), t("Relógio grande e escurecido depois de algum tempo sem toques. Ideal para tablets na parede."))}
      </div></div>`;
    } else if (sec === "rooms") {
      const all = this._model?.rooms || this._localRooms();
      const byId = Object.fromEntries(all.map((r) => [r.id, r]));
      const order = [...d.room_order.filter((i) => byId[i]), ...all.map((r) => r.id).filter((i) => !d.room_order.includes(i))];
      const hiddenDev = this._model ? all.flatMap((r) => r.devices.filter((x) => d.hidden.includes(x.id)).map((x) => ({ ...x, room: this._roomName(r) }))) : [];
      const size = (id) => d.house_size?.[id] || 0;
      main = `<div class="panel glass"><h3 style="margin-top:0">${t("Divisões")}</h3><p>${t("Criadas automaticamente a partir das áreas do Home Assistant. Ordena, esconde e escolhe o tamanho de cada uma na Casa 3D.")}</p>
        <div class="list big">${order.map((id, k) => { const r = byId[id], vis = !d.hidden_rooms.includes(id); return `<div class="it">
          <div class="ri sm"><ha-icon icon="${r.icon || "mdi:home-outline"}"></ha-icon></div><span>${esc(this._roomName(r))} <small>${tp((r.devices || r.ents).length, "{n} dispositivo", "{n} dispositivos")}</small></span>
          <div class="seg sm" title="${t("Tamanho na Casa 3D")}">${[[0, "A"], [1, "P"], [2, "M"], [3, "G"]].map(([v, l]) => `<button class="mini ${size(id) === v ? "on" : ""}" data-size="${esc(id)}:${v}">${t(l)}</button>`).join("")}</div>
          <button class="mini" data-move="${k}:-1" ${k === 0 ? "disabled" : ""}><ha-icon icon="mdi:chevron-up"></ha-icon></button>
          <button class="mini" data-move="${k}:1" ${k === order.length - 1 ? "disabled" : ""}><ha-icon icon="mdi:chevron-down"></ha-icon></button>
          <div class="tg ${vis ? "on" : ""}" data-roomvis="${esc(id)}"><i></i></div></div>`; }).join("")}</div>
        <p style="margin:14px 0 0;font-size:14px">${t("Tamanho na Casa 3D: A = automático, P = pequeno, M = médio, G = grande. Os pisos vêm dos pisos do Home Assistant.")}</p></div>
        ${hiddenDev.length ? `<div class="panel glass"><h3 style="margin-top:0">${t("Dispositivos escondidos")}</h3><p>${t("Escondidos no modo de edição ou na folha de detalhe.")}</p><div class="list big">${hiddenDev.map((x) => `<div class="it"><span>${esc(x.name)} <small>${esc(x.room)}</small></span><button class="pill" data-unhide="${esc(x.id)}">${t("Mostrar")}</button></div>`).join("")}</div></div>` : ""}`;
      this._order = order;
    } else if (sec === "entities") {
      main = this._entitiesSection(d);
    } else if (sec === "plan") {
      main = planView(this, d, this._planRooms());
    } else if (sec === "nav") {
      main = `<div class="panel glass"><h3 style="margin-top:0">${t("Dock")}</h3><p>${t("Botões da barra de navegação. A Casa está sempre presente.")}</p><div class="opts">
        ${DOCK_ITEMS.filter(([k]) => k !== "home").map(([k, , label, sub]) => this._toggle("dock:" + k, d.dock.includes(k), t(label), t(sub))).join("")}
      </div></div>`;
    } else {
      const rooms = this._model?.rooms || [];
      const devs = rooms.reduce((a, r) => a + r.devices.length, 0);
      main = `<div class="panel glass"><h3 style="margin-top:0">${t("Sistema")}</h3><div class="facts">
          <div><small>${t("Versão")}</small><b>${esc(this._model?.version || "—")}${FRONT_VERSION && this._model?.version && FRONT_VERSION !== this._model.version ? ` · ${esc(t("painel {v}", { v: FRONT_VERSION }))}` : ""}</b></div>
          <div><small>${t("Modo")}</small><b>${this._model ? t("Integração") : t("Local (sem API)")}</b></div>
          <div><small>${t("Divisões")}</small><b>${rooms.length || this._localRooms().length}</b></div>
          <div><small>${t("Dispositivos")}</small><b>${devs || "—"}</b></div>
        </div>
        <div class="row" style="justify-content:flex-start;margin-top:22px">
          <button class="pill" data-rebuild><ha-icon icon="mdi:refresh"></ha-icon>${t("Reconstruir agora")}</button>
          <button class="pill" data-reset><ha-icon icon="mdi:backup-restore"></ha-icon>${t("Repor predefinições")}</button>
          <a class="pill" href="/config/integrations/integration/pine3_view" target="_top"><ha-icon icon="mdi:open-in-new"></ha-icon>${t("Integração no HA")}</a>
        </div></div>`;
    }
    return `<div class="set">
      <div class="set-hd"><div><div class="eyebrow">Pine3 View</div><div class="t">${t("Definições")}</div><div class="s">${t("Aparência, ecrã inicial, divisões e navegação.")}</div></div>
        <div class="actions"><button class="pill ${dirty ? "solid" : "dim"}" data-save ${dirty ? "" : "disabled"}><ha-icon icon="mdi:check"></ha-icon>${t("Guardar alterações")}</button>
        <div class="iconbtn" data-closeset title="${t("Fechar")}"><ha-icon icon="mdi:close"></ha-icon></div></div></div>
      <div class="set-body"><nav class="set-nav">${navItems.map(([k, icon, label]) => `<button class="${k === sec ? "act" : ""}" data-sec="${k}"><span class="ni"><ha-icon icon="${icon}"></ha-icon></span>${label}</button>`).join("")}</nav>
        <div class="set-main">${main}</div></div>
      ${this._toast ? `<div class="toast">${esc(this._toast)}</div>` : ""}
    </div>`;
  }

  _planRooms() {
    if (this._model) return this._model.rooms.map((r) => ({ ...r, count: r.devices.length }));
    return this._localRooms();
  }

  _entShown(d, e) { return e.primary ? !d.hidden.includes(e.id) : d.shown.includes(e.id) && !d.hidden.includes(e.id); }

  _entitiesSection(d) {
    if (!this._model) return `<div class="panel glass"><p>${t("Esta secção precisa da integração Pine3 View instalada.")}</p></div>`;
    const q = (this._entQ || "").trim().toLowerCase();
    const tag = (e) => (e.primary ? t("Principal") : e.category ? t("Diagnóstico") : e.ha_hidden ? t("Escondida no HA") : t("Secundária"));
    const groups = this._model.rooms.map((r) => {
      const all = r.entities || [];
      const list = all.filter((e) => (this._entAll || (!e.category && !e.ha_hidden) || this._entShown(d, e))
        && (!q || e.name.toLowerCase().includes(q) || e.id.includes(q)));
      if (!list.length) return "";
      const n = all.filter((e) => this._entShown(d, e)).length;
      return `<div class="panel glass"><h3 style="margin-top:0;display:flex;align-items:center;gap:10px"><ha-icon icon="${r.icon || "mdi:home-outline"}"></ha-icon>${esc(this._roomName(r))}
          <small style="font-size:14px;color:var(--muted);font-family:inherit">${t("{a} de {b} visíveis", { a: n, b: all.length })}</small></h3>
        <div class="list big">${list.map((e) => `<div class="it ${this._entShown(d, e) ? "" : "off"}">
          <ha-icon icon="${this._s(e.id) ? this._icon(e.id) : "mdi:help-circle-outline"}"></ha-icon>
          <span>${esc(e.name)} <small>${esc(e.id)}</small></span><em class="tag ${e.primary ? "pri" : ""}">${tag(e)}</em>
          <div class="tg ${this._entShown(d, e) ? "on" : ""}" data-enttg="${esc(e.id)}" data-primary="${e.primary ? 1 : 0}"><i></i></div></div>`).join("")}</div></div>`;
    }).join("");
    return `<div class="panel glass"><h3 style="margin-top:0">${t("Entidades")}</h3>
        <p>${t("Escolhe o que aparece no painel. As entidades principais são os mosaicos criados automaticamente; as secundárias (por exemplo, o interruptor de uma TV ou o sensor de potência de uma tomada) podes mostrá-las como mosaicos próprios.")}</p>
        <input class="search" data-entq type="search" placeholder="${t("Procurar entidades…")}" value="${esc(this._entQ || "")}">
        <div class="opts" style="margin-top:8px">${this._toggle("_entall", !!this._entAll, t("Mostrar diagnóstico e escondidas no HA"), t("Inclui entidades de diagnóstico, de configuração e as escondidas no Home Assistant."))}</div>
      </div>${groups || `<div class="empty">${t("Nenhuma entidade encontrada.")}</div>`}`;
  }

  _bindSettings() {
    const root = this.shadowRoot, d = this._draft;
    if (!d) return;
    if (this._section === "plan") bindPlan(this, root, d, this._planRooms());
    const q = root.querySelector("[data-entq]");
    if (q) {
      q.addEventListener("input", () => { this._entQ = q.value; this._entFocus = true; this._render(); });
      q.addEventListener("blur", () => { this._entFocus = false; });
    }
    root.querySelectorAll("[data-enttg]").forEach((el) => el.addEventListener("click", () => {
      const id = el.dataset.enttg, primary = el.dataset.primary === "1";
      const e = this._model.rooms.flatMap((r) => r.entities || []).find((x) => x.id === id);
      const on = this._entShown(d, e);
      if (primary) d.hidden = on ? [...d.hidden, id] : d.hidden.filter((x) => x !== id);
      else if (on) d.shown = d.shown.filter((x) => x !== id);
      else { d.shown = [...new Set([...d.shown, id])]; d.hidden = d.hidden.filter((x) => x !== id); }
      this._render();
    }));
    const on = (sel, fn) => root.querySelectorAll(sel).forEach((el) => el.addEventListener("click", (ev) => fn(el, ev)));
    const upd = () => this._render();
    on("[data-sec]", (el) => { this._section = el.dataset.sec; upd(); });
    on("[data-fs]", () => this._toggleFullscreen());
    on("[data-local]", (el) => {
      const k = el.dataset.local, v = !store.get(k, false);
      store.set(k, v);
      if (k === "hide_sidebar") this._dockSidebar(v);
      if (k === "wake_lock") { if (v) this._applyWakeLock(); else { this._wake?.release?.().catch(() => {}); this._wake = null; } }
      upd();
    });
    on("[data-lchoice]", (el) => { store.set(el.dataset.lchoice, el.dataset.val); upd(); });
    on("[data-closeset]", () => this._closeSettings());
    on("[data-save]", () => this._saveDraft());
    on("[data-card]", (el) => { d.card_color = el.dataset.card; upd(); });
    on("[data-accent]", (el) => { d.accent_color = el.dataset.accent; upd(); });
    on("[data-theme]", (el) => { d.theme = el.dataset.theme; upd(); });
    on("[data-themeday]", (el) => { d.theme_day = el.dataset.themeday; upd(); });
    on("[data-themenight]", (el) => { d.theme_night = el.dataset.themenight; upd(); });
    on("[data-choice]", (el) => { d[el.dataset.choice] = el.dataset.type === "number" ? Number(el.dataset.val) : el.dataset.val; upd(); });
    on("[data-tg]", (el) => {
      const k = el.dataset.tg;
      if (k === "_entall") { this._entAll = !this._entAll; upd(); return; }
      if (k.startsWith("dock:")) { const it = k.slice(5); d.dock = d.dock.includes(it) ? d.dock.filter((x) => x !== it) : DOCK_ITEMS.map(([x]) => x).filter((x) => x === it || d.dock.includes(x)); }
      else d[k] = !d[k];
      upd();
    });
    on("[data-roomvis]", (el) => { const id = el.dataset.roomvis; d.hidden_rooms = d.hidden_rooms.includes(id) ? d.hidden_rooms.filter((x) => x !== id) : [...d.hidden_rooms, id]; upd(); });
    on("[data-size]", (el) => {
      const [id, v] = el.dataset.size.split(/:(?=\d$)/);
      d.house_size = { ...(d.house_size || {}) };
      if (Number(v)) d.house_size[id] = Number(v); else delete d.house_size[id];
      upd();
    });
    on("[data-move]", (el) => {
      const [k, dir] = el.dataset.move.split(":").map(Number), o = [...this._order], j = k + dir;
      if (j < 0 || j >= o.length) return;
      [o[k], o[j]] = [o[j], o[k]];
      // keep the summary cards (keys "@…") where they were in the home order
      const full = [...d.room_order];
      let n = 0;
      d.room_order = full.map((x) => (x.startsWith("@") ? x : o[n++])).filter(Boolean).concat(o.slice(n));
      upd();
    });
    on("[data-unhide]", (el) => { d.hidden = d.hidden.filter((x) => x !== el.dataset.unhide); upd(); });
    on("[data-reset]", () => { const keepLang = d.language; Object.assign(d, clone(DEFAULTS)); d.language = keepLang; d.room_order = (this._model?.rooms || []).map((r) => r.id); upd(); });
    on("[data-rebuild]", async () => { if (this._model) { this._model = await this._hass.callWS({ type: "pine3_view/home" }); this._toast = t("Casa reconstruída"); upd(); setTimeout(() => { this._toast = null; upd(); }, 1500); } });
  }

  // ---------- edit mode ----------
  _editBtn() { return ""; } // editing starts from the "⋮" menu
  _startEdit() {
    const c = this._saved();
    const rooms = this._rooms();
    this._edit = {
      room_order: c.room_order?.length ? [...c.room_order] : rooms.map((r) => r.id),
      hidden_rooms: [...c.hidden_rooms], big_rooms: [...c.big_rooms], hidden: [...c.hidden],
      big_devices: [...c.big_devices], device_order: clone(c.device_order || {}),
    };
    this._modal = null;
    this._render();
  }
  async _finishEdit(save = true) {
    const e = this._edit;
    this._edit = null;
    if (save && e) {
      if (this._model) Object.assign(this._model.settings, e);
      try { await this._persist(e); } catch (err) { console.error("Pine3 View", err); }
    }
    this._render();
  }
  _editChrome(kind, key) {
    if (!this._edit) return "";
    const big = kind === "room" ? this._edit.big_rooms.includes(key) : this._edit.big_devices.includes(key);
    return `<button class="ed-x" data-edhide="${kind}|${esc(key)}" title="${t("Esconder")}">−</button>
      <button class="ed-big" data-edbig="${kind}|${esc(key)}" title="${big ? t("Tamanho normal") : t("Tamanho duplo")}"><ha-icon icon="mdi:arrow-${big ? "collapse" : "expand"}"></ha-icon></button>`;
  }
  _bindEdit(root) {
    const e = this._edit;
    const split = (v) => { const k = v.indexOf("|"); return [v.slice(0, k), v.slice(k + 1)]; };
    root.querySelectorAll("[data-edhide]").forEach((b) => b.addEventListener("click", (ev) => {
      ev.stopPropagation();
      const [kind, key] = split(b.dataset.edhide);
      if (kind === "room") e.hidden_rooms = [...new Set([...e.hidden_rooms, key])];
      else e.hidden = [...new Set([...e.hidden, key])];
      this._render();
    }));
    root.querySelectorAll("[data-edbig]").forEach((b) => b.addEventListener("click", (ev) => {
      ev.stopPropagation();
      const [kind, key] = split(b.dataset.edbig);
      const list = kind === "room" ? "big_rooms" : "big_devices";
      e[list] = e[list].includes(key) ? e[list].filter((x) => x !== key) : [...e[list], key];
      this._render();
    }));
    root.querySelectorAll("[data-edrestore]").forEach((b) => b.addEventListener("click", () => {
      if (b.dataset.edrestore === "rooms") e.hidden_rooms = [];
      else {
        const ids = new Set(this._model?.rooms.find((r) => r.id === this._room)?.devices.map((d) => d.id) || []);
        e.hidden = e.hidden.filter((x) => !ids.has(x));
      }
      this._render();
    }));
    root.querySelector("[data-eddone]")?.addEventListener("click", () => this._finishEdit(true));
    root.querySelector("[data-edcancel]")?.addEventListener("click", () => this._finishEdit(false));
    root.querySelectorAll("[data-sortable]").forEach((c) => this._bindSortable(c));
  }
  _bindSortable(container) {
    const items = () => [...container.children].filter((c) => c.dataset.key);
    items().forEach((item) => {
      item.addEventListener("pointerdown", (ev) => {
        if (ev.target.closest("button")) return;
        ev.preventDefault();
        // listen on window: moving the item in the DOM would drop a pointer capture
        this._drag = true;
        item.classList.add("dragging");
        const move = (mv) => {
          const over = items().find((o) => {
            if (o === item) return false;
            const r = o.getBoundingClientRect();
            return mv.clientX >= r.left && mv.clientX <= r.right && mv.clientY >= r.top && mv.clientY <= r.bottom;
          });
          if (!over) return;
          const list = items();
          container.insertBefore(item, list.indexOf(over) > list.indexOf(item) ? over.nextSibling : over);
        };
        const up = () => {
          window.removeEventListener("pointermove", move);
          window.removeEventListener("pointerup", up);
          window.removeEventListener("pointercancel", up);
          item.classList.remove("dragging");
          this._drag = null;
          const keys = items().map((c) => c.dataset.key);
          const scope = container.dataset.sortable;
          if (scope === "rooms") {
            this._edit.room_order = [...keys, ...this._edit.room_order.filter((k) => !keys.includes(k))];
          } else {
            // device order within one section of a room: slot the new order back into the room's full order
            const rid = scope.slice(4);
            const full = (this._rooms().find((r) => r.id === rid)?.ents || []).filter((k) => dom(k) !== "scene");
            const first = Math.min(...keys.map((k) => full.indexOf(k)).filter((n) => n >= 0));
            const rest = full.filter((k) => !keys.includes(k));
            const at = rest.filter((k) => full.indexOf(k) < first).length;
            this._edit.device_order = { ...this._edit.device_order, [rid]: [...rest.slice(0, at), ...keys, ...rest.slice(at)] };
          }
          this._render();
        };
        window.addEventListener("pointermove", move);
        window.addEventListener("pointerup", up);
        window.addEventListener("pointercancel", up);
      });
    });
  }


  // ---------- small menus ("⋮" and the "+N" ones) ----------
  /** A small glass menu under (or above) `anchor`; `items` are [icon, label, action, extra?] rows. */
  _popMenu(anchor, title, items) {
    this._closePop();
    const m = document.createElement("div");
    m.className = "pmenu";
    m.innerHTML = `${title ? `<div class="pm-h">${esc(title)}</div>` : ""}${items.map(([icon, label, , sub, color, power], k) => `<div class="pm-i">
        <button class="pm-b" data-pmk="${k}" ${color ? `style="--pmc:${color}"` : ""}><ha-icon icon="${icon}"></ha-icon><span>${esc(label)}${sub ? `<small>${esc(sub)}</small>` : ""}</span></button>
        ${power ? `<button class="pm-pw" data-pmp="${k}" title="${t("Desligar")}"><ha-icon icon="mdi:power"></ha-icon></button>` : ""}</div>`).join("")}`;
    this.shadowRoot.appendChild(m);
    const b = anchor.getBoundingClientRect(), w = m.offsetWidth, h = m.offsetHeight;
    const left = Math.max(10, Math.min(window.innerWidth - w - 10, b.left + w > window.innerWidth - 10 ? b.right - w : b.left));
    let top = b.bottom + 8;
    if (top + h > window.innerHeight - 10) top = Math.max(10, b.top - h - 8);
    m.style.left = `${left}px`;
    m.style.top = `${top}px`;
    m.addEventListener("click", (ev) => {
      const pk = ev.target.closest("[data-pmp]"), bk = ev.target.closest("[data-pmk]");
      if (pk) { items[+pk.dataset.pmp][5](); this._closePop(); }
      else if (bk) { this._closePop(); items[+bk.dataset.pmk][2](); }
    });
    this._pop = m;
    this._popAnchor = anchor;
    this._popOut = (ev) => { const path = ev.composedPath(); if (!path.includes(m) && !path.includes(anchor)) this._closePop(); };
    setTimeout(() => window.addEventListener("pointerdown", this._popOut, true));
  }
  _closePop() {
    if (!this._pop) return;
    this._pop.remove();
    this._pop = null;
    window.removeEventListener("pointerdown", this._popOut, true);
  }
  /** The "⋮" menu: edit this screen (where there is something to edit) and settings. */
  _mainMenu() {
    if (this._pop && this._popAnchor === this._moreEl) return this._closePop();
    const items = [];
    if (["home", "rooms", "room"].includes(this._view)) items.push(["mdi:pencil-outline", t("Editar ecrã"), () => this._startEdit()]);
    items.push(["mdi:cog-outline", t("Definições"), () => this._openSettings()]);
    this._popMenu(this._moreEl, "", items);
  }
  /** "+N" of a row that didn't fit: the hidden scenes, or the hidden devices that are on (with a power button). */
  _moreMenu(el) {
    const box = el.parentElement, ids = [...box.children].filter((x) => x.hidden && x.dataset.id).map((x) => x.dataset.id);
    if (box.dataset.kind === "scenes") {
      this._popMenu(el, t("Mais cenas"), ids.map((i) => [this._s(i)?.attributes.icon || "mdi:creation-outline", this._name(i), () => this._sceneTap(i)]));
    } else {
      const roomOf = (i) => this._rooms().find((r) => r.ents.includes(i))?.name || "";
      this._popMenu(el, t("Também ligados"), ids.map((i) => [this._icon(i), this._name(i), () => this._openModal(i), roomOf(i), "var(--amber)", () => this._turnOff(i)]));
    }
  }
  /** "+N" of a weather warning: every IPMA warning; a tap opens the forecast. */
  _alertsMenu(el) {
    const col = { red: "#ff453a", orange: "#ff9f0a", yellow: "#ffd60a" };
    const lv = { yellow: t("Aviso amarelo"), orange: t("Aviso laranja"), red: t("Aviso vermelho") };
    this._popMenu(el, t("Avisos do IPMA"), this._ipmaAlerts().map((al) => ["mdi:alert", `${lv[al.level]}${al.type ? ` · ${al.type}` : ""}`, () => this._openForecast(), this._alertWhen(al), col[al.level]]));
  }
  /** Turn a device off from a menu: AC and thermostats off, a vacuum back to its dock, anything else off. */
  _turnOff(id) {
    const d = dom(id);
    if (d === "vacuum") return this._call("vacuum", "return_to_base", { entity_id: id });
    if (["climate", "fan", "media_player", "switch", "input_boolean", "light"].includes(d)) return this._call(d, "turn_off", { entity_id: id });
    return this._call("homeassistant", "turn_off", { entity_id: id });
  }

  /** Rows that show only what fits in `data-fit` lines and gather the rest in their "+N". */
  _fitRows() {
    this.shadowRoot?.querySelectorAll("[data-fit]").forEach((c) => {
      const lines = +c.dataset.fit, more = c.querySelector(".fitmore");
      const items = [...c.children].filter((x) => x !== more);
      items.forEach((x) => { x.hidden = false; });
      if (more) more.hidden = true;
      if (!items.length || !more || !c.offsetParent) return;
      const rowH = items[0].offsetHeight, gap = parseFloat(getComputedStyle(c).rowGap) || 6;
      const limit = items[0].offsetTop + lines * (rowH + gap) - gap / 2;
      const fits = () => [...c.children].every((x) => x.hidden || x.offsetTop + x.offsetHeight <= limit);
      for (let n = 1; !fits() && n <= items.length; n++) {
        items[items.length - n].hidden = true;
        more.hidden = false;
        more.textContent = `+${n}`;
      }
    });
  }
  /** Tall weather cards: as many next days as fit above the dock (or inside the sidebar), at least three. */
  _fitDays() {
    this.shadowRoot?.querySelectorAll(".wxc.tall .wx-days").forEach((box) => {
      const rows = [...box.children];
      rows.forEach((r) => { r.hidden = false; });
      const card = box.closest(".wxc"), sb = card.closest(".sb");
      if (!card.offsetParent || window.innerWidth <= 720) { rows.slice(5).forEach((r) => { r.hidden = true; }); return; }
      const nav = this._dockEl;
      let limit = nav && !nav.hidden ? nav.getBoundingClientRect().top - 16 : window.innerHeight - 16;
      if (sb) {
        const st = sb.querySelector(".sbst");
        limit = sb.getBoundingClientRect().bottom - 18 - (st ? st.offsetHeight + 12 : 0);
      }
      for (let n = rows.length - 1; n >= 3 && card.getBoundingClientRect().bottom > limit; n--) rows[n].hidden = true;
    });
  }
  /** Home on a wide screen: the cards grow to fill the space down to the dock (no needless empty band, no scroll). */
  _fillHome() {
    const g = this.shadowRoot?.querySelector(".hgrid");
    if (!g) return;
    g.classList.remove("fill");
    if (this._view !== "home" || window.innerWidth <= 720) return;
    const cards = [...g.children].filter((x) => x.offsetParent);
    if (!cards.length) return;
    const rows = new Set(cards.map((x) => x.offsetTop)).size;
    const nav = this._dockEl, bottom = nav && !nav.hidden ? nav.getBoundingClientRect().top - 16 : window.innerHeight - 16;
    const gap = parseFloat(getComputedStyle(g).rowGap) || 14;
    const h = Math.floor((bottom - g.getBoundingClientRect().top - (rows - 1) * gap) / rows);
    if (h < 110) return; // too little room: keep the normal height and scroll
    g.style.setProperty("--rch", `${Math.min(h, 280)}px`);
    g.classList.add("fill");
  }

  // ---------- render ----------
  _render() {
    if (!this._hass) return;
    const cfg = this._cfg();
    setLang(cfg.language);
    const rooms = this._rooms();
    const room = this._view === "room" ? rooms.find((r) => r.id === this._room) : null;
    if (this._view === "room" && !room) this._view = "home";
    this.style.setProperty("--cream", cfg.card_color);
    this.style.setProperty("--amber", cfg.accent_color);
    this.setAttribute("lang", lang());
    let body;
    if (this._view === "settings" && this._draft) body = this._settingsView();
    else if (this._view === "room") body = this._roomView(room);
    else if (this._view === "scenes") body = this._scenesView(rooms);
    else if (this._view === "rooms") body = this._roomsView(rooms);
    else if (this._view === "security") body = this._securityView(rooms);
    else if (this._view === "climate") body = this._climateView(rooms);
    else if (this._view === "house") body = houseView(this, rooms);
    else body = this._homeView(rooms);

    const header = (this._narrow || this._view === "room") && this._view !== "settings" ? `<header>
      ${this._narrow ? `<div class="iconbtn" data-act="menu"><ha-icon icon="mdi:menu"></ha-icon></div>` : ""}
      ${this._view === "room" ? `<div class="iconbtn" data-act="back"><ha-icon icon="mdi:chevron-left"></ha-icon></div>` : ""}
      <div class="sp"></div>
    </header>` : "";
    const editBar = this._edit ? `<div class="edbar"><button class="pill" data-edcancel>${t("Cancelar")}</button><button class="pill solid" data-eddone><ha-icon icon="mdi:check"></ha-icon>${t("Concluído")}</button></div>` : "";

    const refocus = this._entFocus;
    // keep scroll positions of inner scrollers across the redraw
    const root = this.shadowRoot;
    const scrolls = [".sheet", ".sh-main", ".sh-side", ".scenes", ".fhs", ".set-nav"].map((sel) => [sel, [...root.querySelectorAll(sel)].map((e) => [e.scrollTop, e.scrollLeft])]);
    if (!this._modal && !this._saver) this._ovShown = null;
    this._collectWatch(rooms);
    // entering another screen: the content flows in (no blur on the 3D Home, a filter would flatten it)
    const viewKey = `${this._view}:${this._view === "room" ? this._room : ""}`;
    const anim = this._animOn(cfg);
    const vin = this._viewKey && this._viewKey !== viewKey && anim ? (this._view === "house" ? "vin flat" : "vin") : "";
    this.classList.toggle("noanim", !anim);
    this.classList.toggle("lite", this._liteFx());
    if (!this._app) {
      // the dock lives outside the redrawn part, so it doesn't flicker or replay its animation on every update
      root.innerHTML = `<style></style><div id="app"></div><nav class="dock" hidden><i class="blob"></i></nav><button class="cmore" hidden><ha-icon icon="mdi:dots-vertical"></ha-icon></button>`;
      this._moreEl = root.querySelector(".cmore");
      this._moreEl.addEventListener("click", () => this._mainMenu());
      this._styleEl = root.querySelector("style");
      this._app = root.getElementById("app");
      this._dockEl = root.querySelector(".dock");
      new ResizeObserver(() => this._placeBlob(false)).observe(this._dockEl); // fonts loading, window size…
      document.fonts?.ready?.then(() => { if (this._dockEl) { this._dockEl._fit = null; this._fitDock(); this._placeBlob(false); this._fitRows(); this._fitDays(); this._fillHome(); } });
    }
    // appearance: a fixed theme, or the day / night one by the sun; light themes get the light stylesheet
    const themeKey = currentTheme(cfg, this._hass, this._theme);
    const light = isLight(themeKey);
    this.classList.toggle("light", light);
    if (this._styleLight !== light) {
      const all = `${css}${css3d}${cssPlan}`;
      this._styleEl.textContent = light ? (this._lightCss ||= toLight(all)) : all;
      this._styleLight = light;
    }
    if (this._viewKey !== viewKey) this._closePop();
    this._viewKey = viewKey;
    this._app.innerHTML = `<div class="bg" style="background:${THEMES[themeKey] || THEMES.ameixa}"></div>
      <div class="wrap ${vin} ${this._view === "room" ? "roomv" : ""} ${cfg.layout === "list" ? "as-list" : ""} ${this._edit ? "editing" : ""}">${header}${body}</div>${editBar}${this._saver ? this._saverView(rooms) : this._modalView()}`;
    for (const [sel, pos] of scrolls) root.querySelectorAll(sel).forEach((e, k) => { if (pos[k]) { e.scrollTop = pos[k][0]; e.scrollLeft = pos[k][1]; } });
    this._updateDock(cfg);
    this._moreEl.hidden = this._view === "settings" || !!this._edit;
    this._moreEl.title = t("Menu");
    this._packSections();
    this._fitRows();
    this._fitDays();
    this._fillHome();
    if (this._view === "settings") this._bindSettings();
    else this._bind(rooms);
    if (refocus) {
      const q = this.shadowRoot.querySelector("[data-entq]");
      if (q) { q.focus(); q.setSelectionRange(q.value.length, q.value.length); this._entFocus = true; }
    }
  }

  _gear() { return ""; } // settings open from the "⋮" menu
  _hint() { return this._edit ? `<div class="edhint">${t(EDIT_HINT)}</div>` : ""; }

  _saverView(rooms) {
    const w = this._wx();
    const lit = rooms.flatMap((r) => r.ents).filter((i) => dom(i) === "light" && this._isOn(i)).length;
    return `<div class="saver ${this._ovEnter("_saver")}" data-saver><div class="sv-in">
      <div class="clock sv-clock">${this._clockText()}</div>
      <div class="sv-date">${esc(this._dateText())}</div>
      <div class="sv-meta">${w ? `<span><ha-icon icon="${WEATHER_ICON[w.cond] || "mdi:weather-partly-cloudy"}"></ha-icon>${num(Math.round(w.temp))}° ${esc(this._wxLabel(w.cond))}</span>` : ""}
        <span><ha-icon icon="mdi:lightbulb-outline"></ha-icon>${lit ? tp(lit, "{n} luz acesa", "{n} luzes acesas") : t("Luzes apagadas")}</span></div>
    </div></div>`;
  }

  /** The people at home, next to the date (Settings → Home → People, off by default). */
  _people() {
    if (!this._cfg().home_people) return "";
    const h = this._hass;
    const persons = (this._model?.persons || Object.keys(h.states).filter((i) => dom(i) === "person")).map((i) => this._s(i)).filter(Boolean).slice(0, 4);
    const tint = ["#f3a96b", "#f6cf6a", "#c9d3dc", "#b9e0c3"];
    return persons.length ? `<div class="people">${persons.map((p, k) => {
      const pic = p.attributes.entity_picture;
      const ini = (p.attributes.friendly_name || "?").trim()[0].toUpperCase();
      return `<div class="av ${p.state === "home" ? "home" : ""}" title="${esc(p.attributes.friendly_name)}" style="background-color:${tint[k % 4]};${pic ? `background-image:url('${esc(pic)}')` : ""}">${pic ? "" : esc(ini)}<i></i></div>`;
    }).join("")}</div>` : "";
  }
  /** The date, with the people beside it when they are shown. */
  _dateRow() {
    return `<div class="drow"><div class="eyebrow tdate">${esc(this._dateText())}</div>${this._people()}</div>`;
  }
  /** What else is on, as chips with an icon (a tap opens the device); what doesn't fit in `lines` rows goes in a "+N" menu. */
  _onChips(others, lines) {
    if (!others.length) return "";
    return `<div class="onchips" data-fit="${lines}" data-kind="on">${others.map((i) => `<button class="oc" data-open="${esc(i)}" data-id="${esc(i)}" title="${esc(this._name(i))}"><ha-icon icon="${this._icon(i)}"></ha-icon><span>${esc(this._name(i))}</span></button>`).join("")}<button class="oc fitmore" hidden></button></div>`;
  }

  /** What else is on besides lights (plugs, switches, fans/purifiers, TVs and speakers, AC, a cleaning vacuum). */
  _activeOthers(all) {
    return all.filter((i) => ["switch", "input_boolean", "fan", "media_player", "climate", "vacuum"].includes(dom(i)) && this._isOn(i));
  }


  /**
   * The home grid: rooms and the summary cards (Climate, Security, Vacuum, Now playing) in one order,
   * which the edit mode sorts by dragging. Summary cards are keyed "@climate", "@security", "@vacuum:<id>", "@media".
   */
  _homeItems(rooms, cards) {
    const order = (this._edit || this._cfg()).room_order || [];
    const keyOf = (html) => html.match(/data-key="([^"]+)"/)?.[1];
    const items = [...rooms.map((r) => [r.id, this._roomCard(r)]), ...cards.map((c) => [keyOf(c), c])];
    const pos = (k, i) => { const n = order.indexOf(k); return n < 0 ? 1e4 + i : n; };
    return items.map(([k, html], i) => [pos(k, i), html]).sort((a, b) => a[0] - b[0]).map(([, html]) => html);
  }

  // ---------- scenes ----------
  /** Devices a scene sets (HA scenes list them in attributes.entity_id), limited to those that can be turned off. */
  _sceneEnts(id) {
    const list = this._s(id)?.attributes.entity_id;
    return (Array.isArray(list) ? list : []).filter((e) => this._s(e) && ["light", "switch", "fan", "input_boolean", "media_player", "climate"].includes(dom(e)));
  }
  /**
   * A scene is "on" while some of its devices are on and no scene activated after it set any of the
   * same devices: several scenes for different rooms can be on at once, a later scene on the same
   * lights takes over.
   */
  _sceneOn(id) {
    const at = (x) => { const v = Date.parse(this._s(x)?.state); return Number.isFinite(v) ? v : 0; };
    const mine = at(id), ents = this._sceneEnts(id);
    if (!mine || !ents.some((e) => this._isOn(e))) return false;
    const own = new Set(ents);
    return !Object.keys(this._hass.states).some((x) => x !== id && dom(x) === "scene" && at(x) > mine && this._sceneEnts(x).some((e) => own.has(e)));
  }
  /** Tap on a scene: activate it, or turn its devices off if it is the one on. */
  _sceneTap(id) {
    if (this._sceneOn(id)) {
      const ents = this._sceneEnts(id).filter((e) => this._isOn(e));
      if (ents.length) this._call("homeassistant", "turn_off", { entity_id: ents });
      return;
    }
    this._call("scene", "turn_on", { entity_id: id });
    this._fired = id; this._render();
    setTimeout(() => { this._fired = null; this._render(); }, 1200);
  }

  _roomCard(r) {
    const sw = this._switchables(r), on = sw.filter((i) => this._isOn(i));
    const c = this._roomClimate(r);
    const active = on.length > 0;
    const big = this._cfg().big_rooms?.includes(r.id);
    const meta = sw.length ? (active ? t("{a} de {b} ligados", { a: on.length, b: sw.length }) : t("Tudo desligado")) : tp(r.count, "{n} dispositivo", "{n} dispositivos");
    const quick = big ? `<div class="quick">${sw.slice(0, 6).map((i) => `<button class="qk ${this._isOn(i) ? "on" : ""}" data-pw="${esc(i)}"><ha-icon icon="${this._icon(i)}"></ha-icon><span>${esc(this._name(i))}</span></button>`).join("")}</div>` : "";
    return `<div class="rc ${active ? "on" : "glass"} ${big ? "big" : ""}" data-room="${esc(r.id)}" data-key="${esc(r.id)}">
      ${this._editChrome("room", r.id)}
      <div class="row1"><div class="ri"><ha-icon icon="${r.icon || "mdi:home-outline"}"></ha-icon></div>
        ${sw.length ? `<div class="pw" data-roomlights="${esc(r.id)}" title="${active ? t("Apagar luzes") : t("Ligar luzes")}"><ha-icon icon="mdi:power"></ha-icon></div>` : ""}</div>
      ${quick}
      <div class="foot"><div class="nm">${esc(this._roomName(r))}</div><div class="meta">${meta}</div>
        <div class="dots">${sw.slice(0, 8).map((i) => `<i class="${this._isOn(i) ? "on" : ""}"></i>`).join("")}${c.temp ? `<span class="tmp">${c.temp.replace(/[,.]\d/, "").replace("°C", "°")}</span>` : ""}</div></div>
    </div>`;
  }

  _restoreCard(n) {
    if (!this._edit || !n) return "";
    return `<div class="rc add" data-edrestore="rooms"><ha-icon icon="mdi:plus"></ha-icon><span>${tp(n, "Mostrar {n} escondida", "Mostrar {n} escondidas")}</span></div>`;
  }

  _homeView(rooms) {
    const h = this._hass, cfg = this._cfg();
    const all = rooms.flatMap((r) => r.ents);
    const lights = all.filter((i) => dom(i) === "light");
    const lightsOn = lights.filter((i) => this._isOn(i));
    const status = lightsOn.length ? tp(lightsOn.length, "{n} luz acesa em casa", "{n} luzes acesas em casa") : lights.length ? t("Todas as luzes apagadas") : t("Tudo tranquilo em casa");

    const scenes = (this._model ? this._model.scenes.filter((i) => this._s(i)) : Object.keys(h.states).filter((i) => dom(i) === "scene" && !h.entities?.[i]?.hidden)).slice(0, 12);
    const scenesHtml = scenes.length && cfg.show_scenes ? `<div class="sect hsc"><div class="eyebrow">${t("Cenas")} <span class="badge">${scenes.length}</span></div>
      <div class="scenes" data-fit="1" data-kind="scenes">${scenes.map((i) => `<div class="scene glass ${this._fired === i ? "fired" : ""} ${this._sceneOn(i) ? "on" : ""}" data-scene="${i}" data-id="${i}" title="${this._sceneOn(i) ? t("Toca para desligar") : ""}"><ha-icon icon="${this._s(i).attributes.icon || "mdi:creation-outline"}"></ha-icon>${esc(this._name(i))}</div>`).join("")}<div class="scene glass fitmore" hidden></div></div></div>` : "";

    // Summary cards
    const cards = [];
    const temps = all.filter((i) => dom(i) === "sensor" && this._s(i).attributes.device_class === "temperature").map((i) => Number(this._s(i).state)).filter(Number.isFinite);
    const hums = rooms.flatMap((r) => r.humidity || []).map((i) => Number(this._s(i)?.state)).filter(Number.isFinite);
    const climates = all.filter((i) => dom(i) === "climate" && this._s(i));
    const climate = climates.find((i) => this._isOn(i)) || climates[0];
    if (temps.length || climate) {
      const tt = temps.length ? temps.reduce((a, b) => a + b, 0) / temps.length : this._s(climate).attributes.current_temperature;
      const heating = climate && this._isOn(climate);
      // one device opens it directly; several open the Climate screen
      const go = climates.length > 1 ? `data-nav="climate"` : climate ? `data-open="${climate}"` : "";
      cards.push(`<div data-key="@climate" class="rc ${heating ? "on" : "glass"}" ${go}>
        <div class="row1"><div class="ri" style="background:#34c759;color:#0d2a14"><ha-icon icon="mdi:thermometer"></ha-icon></div><div class="eyebrow" style="margin:14px 0 0 0;flex:1;padding-left:14px">${t("Clima")}</div></div>
        <div class="foot"><div class="big-num">${tt != null ? Math.round(tt) : "–"}°</div><div class="meta">${climates.length > 1 ? `${t("{a} de {b} ligados", { a: climates.filter((i) => this._isOn(i)).length, b: climates.length })} · ` : ""}${hums.length ? t("Humidade {n}%", { n: Math.round(hums.reduce((a, b) => a + b, 0) / hums.length) }) : climate ? esc(this._stateText(climate)) : t("Interior")}</div></div></div>`);
    }
    const secIds = all.filter((i) => dom(i) === "lock" || (dom(i) === "binary_sensor" && ["door", "window", "garage_door", "opening"].includes(this._s(i).attributes.device_class)));
    if (secIds.length) {
      const bad = secIds.filter((i) => (dom(i) === "lock" ? this._s(i).state === "unlocked" : this._s(i).state === "on"));
      cards.push(`<div data-key="@security" class="rc glass" data-nav="security">
        <div class="row1"><div class="ri" style="${bad.length ? "background:#ff9f0a;color:#2a1500" : "background:#30d158;color:#06210f"}"><ha-icon icon="mdi:shield-${bad.length ? "alert-outline" : "check-outline"}"></ha-icon></div><div class="eyebrow" style="margin:14px 0 0 0;flex:1;padding-left:14px">${t("Segurança")}</div></div>
        <div class="foot"><div class="big-txt">${bad.length ? tp(bad.length, "{n} item aberto", "{n} itens abertos") : t("Tudo seguro")}</div><div class="meta">${tp(secIds.length, "{n} item monitorizado", "{n} itens monitorizados")}</div></div></div>`);
    }
    // robot vacuums: status, battery and (while cleaning) progress; a tap opens its card, never starts it
    for (const v of all.filter((i) => dom(i) === "vacuum" && this._s(i))) {
      const on = this._isOn(v), level = this._vacBattery(v);
      const status = this._vacMember(v, "sensor", "status");
      const progress = on ? [this._vacMember(v, "sensor", "cleaning_area"), this._vacMember(v, "sensor", "clean_percent", "cleaning_progress")].filter(Boolean).map((e) => this._sensorVal(e)) : [];
      const err = this._vacMember(v, "sensor", "vacuum_error");
      const errOn = err && !["none", "ok", "unknown", "no_error"].includes(this._s(err).state);
      cards.push(`<div data-key="@vacuum:${esc(v)}" class="rc ${on ? "on" : "glass"}" data-open="${esc(v)}">
        <div class="row1"><div class="ri" style="background:${errOn ? "#ff9f0a;color:#2a1500" : "#30d158;color:#06210f"}"><ha-icon icon="mdi:robot-vacuum"></ha-icon></div><div class="eyebrow" style="margin:14px 0 0 0;flex:1;padding-left:14px">${t("Aspirador")}</div></div>
        <div class="foot"><div class="big-txt">${esc(errOn ? this._fmtState(err) : this._fmtState(status || v))}</div>
          <div class="meta">${esc([level != null ? `🔋 ${level}%` : "", ...progress, this._name(v)].filter(Boolean).join(" · "))}</div></div></div>`);
    }
    const media = all.find((i) => dom(i) === "media_player" && this._s(i).state === "playing") || all.find((i) => dom(i) === "media_player" && this._s(i).state === "paused");
    if (media) {
      const a = this._s(media).attributes, roomName = rooms.find((r) => r.ents.includes(media))?.name;
      const pct = a.media_duration ? Math.min(100, ((a.media_position || 0) / a.media_duration) * 100) : 0;
      cards.push(`<div data-key="@media" class="rc glass media ${a.entity_picture ? "pic" : ""}" data-open="${media}" style="${a.entity_picture ? `background-image:linear-gradient(rgba(30,16,32,.55),rgba(30,16,32,.85)),url('${esc(a.entity_picture)}');background-size:cover;background-position:center` : ""}">
        <div class="eyebrow" style="color:#5ee08a;letter-spacing:.14em"><ha-icon icon="mdi:music-note" style="--mdc-icon-size:16px"></ha-icon>${esc([t("A tocar"), roomName, a.app_name].filter(Boolean).join(" · "))}</div>
        <div class="foot"><div class="ttl">${esc(a.media_title || this._name(media))}</div><div class="art">${esc(a.media_artist || "")}</div>
          ${pct ? `<div class="bar"><i style="width:${pct}%"></i></div>` : ""}
          <div class="ctl"><span data-media="media_previous_track" data-ent-id="${media}"><ha-icon icon="mdi:skip-previous"></ha-icon></span><span class="pp" data-media="media_play_pause" data-ent-id="${media}"><ha-icon icon="mdi:${this._s(media).state === "playing" ? "pause" : "play"}"></ha-icon></span><span data-media="media_next_track" data-ent-id="${media}"><ha-icon icon="mdi:skip-next"></ha-icon></span></div></div></div>`);
    }

    const others = this._activeOthers(all);
    const grid = rooms.length || this._edit ? `<div class="grid hgrid" data-sortable="rooms">${this._homeItems(rooms, cfg.show_summary ? cards : []).join("")}${this._restoreCard(this._hiddenRooms.length)}</div>`
      : `<div class="empty">${t("Ainda não há divisões com dispositivos. Atribui áreas aos teus dispositivos no Home Assistant.")}</div>`;
    const showStatus = cfg.show_status && !this._edit;
    const layout = cfg.home_layout || "top";
    const sc = this._edit ? "" : scenesHtml;
    const lit = lightsOn.length ? tp(lightsOn.length, "{n} luz acesa", "{n} luzes acesas") : others.length ? tp(others.length, "{n} dispositivo ligado", "{n} dispositivos ligados") : status;
    const stat = (cls, lines, eyebrow = true) => (showStatus ? `<div class="${cls}">${eyebrow ? `<div class="eyebrow">${t("Estado da casa")}</div>` : ""}<div class="status">${lit}</div>${this._onChips(others, lines)}</div>` : "");

    if (layout === "columns") {
      // 1/3: date, a big clock, what is on and the weather · 2/3: scenes and the cards
      return `<div class="hcols ${this._edit ? "editing" : ""}">
        <aside class="hleft">
          ${this._dateRow()}<div class="clock">${this._clockText()}</div>${this._hint()}
          ${stat("hstat", 2)}
          ${cfg.show_weather ? this._wxCard("tall") : ""}
        </aside>
        <div class="hmain">${sc}${grid}</div>
      </div>`;
    }
    if (layout === "sidebar") {
      // a full-height glass sidebar (clock, weather, home status) and the scenes and cards beside it
      return `<div class="hside ${this._edit ? "editing" : ""} ${sc ? "has-sc" : ""}">
        <aside class="sb glass">
          ${this._dateRow()}<div class="clock">${this._clockText()}</div>${this._hint()}
          ${cfg.show_weather ? this._wxCard("tall side") : ""}
          ${stat("sbst", 2, false)}
        </aside>
        <div class="hmain">${sc}${grid}</div>
      </div>`;
    }
    if (layout === "top") {
      // a big clock with the home status beside it and the weather at the right, then scenes and cards
      return `<div class="top t1">
          <div class="tclock">${this._dateRow()}<div class="clock">${this._clockText()}</div>${this._hint()}</div>
          ${stat("tstat", 2) || `<div class="tstat"></div>`}
          <div class="tside">${cfg.show_weather ? this._wxCard("big") : ""}</div>
          <div class="tsc">${sc}</div>
        </div>
        ${grid}`;
    }
    // classic: clock, home status and the weather pill on one row, then scenes and cards
    return `<div class="top tcl">
        <div>${this._dateRow()}<div class="clock">${this._clockText()}</div>${this._hint()}</div>
        ${stat("clst", 1)}
        <div class="right">${cfg.show_weather ? this._wxPill() : ""}</div>
      </div>
      ${sc}
      ${grid}`;
  }

  _roomsView(rooms) {
    return `<div class="hero"><div><div class="eyebrow">${tp(rooms.length, "{n} divisão", "{n} divisões")}</div><div class="t" style="margin-top:8px">${t("Divisões")}</div>${this._hint()}</div>
      <div class="right">${this._editBtn()}${this._gear()}</div></div>
      <div class="grid" data-sortable="rooms">${rooms.map((r) => this._roomCard(r)).join("")}${this._restoreCard(this._hiddenRooms.length)}</div>`;
  }

  /** Climate screen: every air conditioner / thermostat with quick controls, then the temperature of each room. */
  _climateView(rooms) {
    const cl = rooms.flatMap((r) => r.ents.filter((i) => dom(i) === "climate" && this._s(i)).map((i) => [i, r]));
    const on = cl.filter(([i]) => this._isOn(i)).length;
    const readings = rooms.map((r) => [r, this._roomClimate(r)]).filter(([, c]) => c.temp || c.hum);
    return `<div class="hero"><div><div class="eyebrow">${cl.length ? t("{a} de {b} ligados", { a: on, b: cl.length }) : t("Clima")}</div><div class="t" style="margin-top:8px">${t("Clima")}</div></div><div class="right">${this._gear()}</div></div>
      ${cl.length ? `<div class="tiles clim">${cl.map(([i, r]) => this._climateCard(i, r)).join("")}</div>` : ""}
      ${readings.length ? `<div class="sect"><div class="eyebrow">${t("Temperatura por divisão")}</div>
        <div class="tiles rtemp">${readings.map(([r, c]) => `<div class="tile glass rt" data-room="${esc(r.id)}"><div class="nm">${esc(r.name)}</div>
          <div class="rtv"><b>${esc((c.temp || "–").replace("°C", "°"))}</b>${c.hum ? `<span><ha-icon icon="mdi:water-percent"></ha-icon>${esc(c.hum)}</span>` : ""}</div></div>`).join("")}</div></div>` : ""}
      ${cl.length || readings.length ? "" : `<div class="empty">${t("Nada para monitorizar.")}</div>`}`;
  }
  /** Target temperature shown on the climate card: the value just asked for until the device reports it. */
  _climateTarget(id) {
    const a = this._s(id).attributes, w = this._ctWant?.[id];
    if (w && Date.now() - w.t < 15000 && a.temperature !== w.v) return w.v;
    return a.temperature;
  }
  /**
   * Climate card with direct control (climate screen and room view): a ring with the target, the current
   * temperature, a few modes (cool / heat / off) and − / +. A tap elsewhere opens the full card.
   */
  _climateCard(id, r) {
    const s = this._s(id), a = s.attributes, on = this._isOn(id);
    const act = on && a.hvac_action && !["off", "idle"].includes(a.hvac_action) ? a.hvac_action : null;
    const tgt = this._climateTarget(id), range = tgt == null && a.target_temp_low != null && a.target_temp_high != null;
    const all = a.hvac_modes || [], short = { cool: t("Frio"), heat: t("Calor"), heat_cool: t("Auto"), auto: t("Auto"), dry: t("Seco"), fan_only: t("Ventilar"), off: t("Desligar") };
    let modes = ["cool", "heat"].filter((m) => all.includes(m));
    if (modes.length < 2) modes = [...modes, ...all.filter((m) => m !== "off" && !modes.includes(m))].slice(0, 2);
    if (all.includes("off")) modes.push("off");
    if (s.state !== "off" && all.includes(s.state) && !modes.includes(s.state)) modes.splice(modes.length - (all.includes("off") ? 1 : 0), 0, s.state);
    const mode = { heating: "heat", preheating: "heat", cooling: "cool", drying: "dry", fan: "fan_only" }[act] || s.state;
    const col = !on ? "var(--clx-off)" : { cool: "#4aa8ff", dry: "#64d2ff", fan_only: "#a8b0bd", heat: "#ff9f43", heat_cool: "#4ade80", auto: "#4ade80" }[mode] || "#ff9f43";
    const lo = a.min_temp ?? 7, hi = a.max_temp ?? 35, val = range ? a.target_temp_high : tgt ?? a.current_temperature;
    const frac = on && val != null ? Math.max(0.04, Math.min(1, (val - lo) / (hi - lo || 1))) : 0;
    const C = 2 * Math.PI * 44, arc = C * 0.75;
    const big = on && tgt != null ? num(tgt) : range && on ? `${num(a.target_temp_low)}–${num(a.target_temp_high)}` : a.current_temperature != null ? num(Math.round(a.current_temperature)) : "–";
    const cur = a.current_temperature != null ? `${num(a.current_temperature)}°` : "";
    const sub = [r && this._view !== "room" ? r.name : "", on && tgt != null ? cur : "", this._st(act || s.state)].filter(Boolean).join(" · ");
    const na = s.state === "unavailable";
    return `<div class="tile clx big ${on ? "on" : "glass"} ${na ? "na" : ""}" data-open="${esc(id)}" data-key="${esc(id)}">
      ${this._meta?.[id] ? this._editChrome("dev", id) : ""}
      <div class="clx-ring"><svg viewBox="0 0 100 100"><circle class="trk" cx="50" cy="50" r="44" stroke-dasharray="${arc.toFixed(1)} ${C.toFixed(1)}"/>
        <circle cx="50" cy="50" r="44" style="stroke:${col}" stroke-dasharray="${(arc * frac).toFixed(1)} ${C.toFixed(1)}"/></svg>
        <div class="clx-t ${range && on ? "rng" : String(big).length > 3 ? "long" : ""}"><b>${big}</b><sup>°</sup>${!on && cur ? `<small>${t("Atual")}</small>` : ""}</div></div>
      <div class="clx-r"><div class="nm">${esc(this._name(id))}</div><div class="st">${esc(sub)}</div>
        ${!na && modes.length > 1 ? `<div class="clx-seg">${modes.map((m) => `<button class="${m === s.state ? "act" : ""}" data-chvac="${esc(id)}" data-m="${m}">${short[m] || this._st(m)}</button>`).join("")}</div>` : ""}
        ${on && tgt != null ? `<div class="clx-pm"><button data-ctemp="${esc(id)}" data-d="-1" title="${t("Baixar")}">−</button><button data-ctemp="${esc(id)}" data-d="1" title="${t("Subir")}">+</button></div>` : ""}
      </div>
    </div>`;
  }

  _securityView(rooms) {
    const all = rooms.flatMap((r) => r.ents);
    const ids = all.filter((i) => dom(i) === "lock" || dom(i) === "camera" || (dom(i) === "binary_sensor" && ["door", "window", "garage_door", "opening", "motion", "smoke", "moisture", "gas"].includes(this._s(i).attributes.device_class)));
    // cameras first, then locks, then the door / window / alarm sensors (each kind in room order)
    const rank = (i) => ({ camera: 0, lock: 1 })[dom(i)] ?? 2;
    ids.sort((x, y) => rank(x) - rank(y));
    const watch = ids.filter((i) => ["lock", "binary_sensor"].includes(dom(i)) && this._s(i).state !== "unavailable");
    const isBad = (i) => (dom(i) === "lock" ? this._s(i).state !== "locked" : this._s(i).state === "on");
    const bad = watch.filter(isBad).length;
    const chip = watch.length ? `<div class="secst ${bad ? "bad" : "ok"}"><ha-icon icon="mdi:shield-${bad ? "alert-outline" : "check-outline"}"></ha-icon>${bad ? tp(bad, "{n} item aberto", "{n} itens abertos") : t("Tudo seguro")}</div>` : "";
    const tile = (i) => (dom(i) === "camera" ? this._camTile(i) : watch.includes(i) && !isBad(i) ? this._tile(i).replace('class="tile ', 'class="tile secok ') : this._tile(i));
    return `<div class="hero"><div><div class="eyebrow">${t("Segurança")}</div><div class="t" style="margin-top:8px">${t("Portas, janelas e fechaduras")}</div></div><div class="right">${chip}${this._gear()}</div></div>
      ${ids.length ? `<div class="tiles">${ids.map(tile).join("")}</div>` : `<div class="empty">${t("Nada para monitorizar.")}</div>`}`;
  }

  _tile(id) {
    const s = this._s(id), d = dom(id), on = this._isOn(id);
    const sensor = d === "sensor";
    const big = this._cfg().big_devices?.includes(id);
    const power = ["light", "switch", "fan", "input_boolean"].includes(d) || (this._isTv(id) && ((s.attributes.supported_features || 0) & 384) !== 0);
    const armed = d === "lock" && this._armed(id);
    return `<div class="tile ${on ? "on" : "glass"} ${armed ? "arm" : ""} ${sensor ? "sensor" : ""} ${big ? "big" : ""} ${s.state === "unavailable" ? "na" : ""}" data-ent="${esc(id)}" data-key="${esc(id)}" style="--acc:${this._accent(id)}">
      ${this._meta?.[id] ? this._editChrome("dev", id) : ""}
      <div class="row1"><div class="ic" ${d === "lock" ? `data-lk="${esc(id)}" title="${s.state === "locked" ? t("Destrancar") : t("Trancar")}"` : ""}><ha-icon icon="${this._icon(id)}"></ha-icon></div>${power ? `<div class="pw" data-pw="${esc(id)}"><ha-icon icon="mdi:power"></ha-icon></div>` : ""}</div>
      <div class="tx"><div class="nm">${esc(this._name(id))}</div>${sensor ? `<div class="val">${esc(this._sensorVal(id))}</div>` : `<div class="st">${armed ? t("Toca outra vez para destrancar") : esc(this._stateText(id))}</div>`}${this._extras(id)}</div>
    </div>`;
  }

  /** Name of a device's entity without the device/room prefix ("Purifier PM2.5" -> "PM2.5"). */
  _memberName(primary, e) {
    const n = this._s(e)?.attributes.friendly_name || e;
    const fold = (x) => (x || "").toLowerCase();
    for (const pre of [this._s(primary)?.attributes.friendly_name, this._meta?.[primary]?.name]) {
      if (pre && fold(n).startsWith(fold(pre) + " ") && n.length > pre.length + 1) {
        const rest = n.slice(pre.length + 1);
        return rest[0].toUpperCase() + rest.slice(1);
      }
    }
    return n;
  }

  /** Every other entity of the device behind a tile: readings in a grid, then its controls. */
  /** Readable label for a select option: our translations, then Home Assistant's, then "snake_case" -> "Snake case". */
  _optLabel(e, o) {
    const k = String(o).toLowerCase();
    if (STATE_PT[k]) return t(STATE_PT[k]);
    const ha = this._hass?.formatEntityState?.(this._s(e), o);
    if (ha && ha !== o) return ha;
    const txt = String(o).replace(/_/g, " ");
    return txt.charAt(0).toUpperCase() + txt.slice(1);
  }

  /** A select's value, showing the option just chosen until Home Assistant reports it (devices can lag a few seconds). */
  _selValue(e) {
    const st = this._s(e).state, want = this._selWant?.[e];
    if (!want) return st;
    if (st === want.o || Date.now() - want.t > 15000) { delete this._selWant[e]; return st; }
    return want.o;
  }

  _deviceSection(id, only = null) {
    const mem = (only || this._meta?.[id]?.members || []).filter((e) => this._s(e) && this._s(e).state !== "unavailable" && dom(e) !== "image"
      && !(dom(e) === "button" && this._s(e).attributes.device_class === "identify") // "Identify" only makes the device blink
      && !/power[_ ]?on[_ ]?(behaviou?r|state)|startup[_ ]?on[_ ]?off/i.test(`${e} ${this._s(e).attributes.friendly_name || ""}`.replace(/-/g, "_"))); // set once, not day to day
    if (!mem.length) return "";
    const readings = mem.filter((e) => ["sensor", "binary_sensor"].includes(dom(e)));
    const isToggle = (e) => ["switch", "input_boolean", "light", "fan"].includes(dom(e));
    const toggles = mem.filter(isToggle);
    const selects = mem.filter((e) => dom(e) === "select" || dom(e) === "input_select");
    const controls = mem.filter((e) => !["sensor", "binary_sensor"].includes(dom(e)) && !isToggle(e) && !selects.includes(e));
    // an option list is a tile with its current value; tapping it opens a row of chips right below
    const sel = (e) => {
      const opts = this._s(e).attributes.options || [], cur = this._selValue(e), open = this._selOpen === e;
      return `<button class="dv-t dv-sel ${open ? "open" : ""}" data-dvopensel="${esc(e)}"><ha-icon icon="${this._icon(e)}"></ha-icon><span class="dv-sv"><small>${esc(this._memberName(id, e))}</small><b>${opts.includes(cur) ? esc(this._optLabel(e, cur)) : "–"}</b></span><ha-icon class="chev" icon="mdi:chevron-down"></ha-icon></button>
        ${open ? `<div class="dv-opts ${this._selShown !== e ? "enter" : ""}"><div class="row presets">${opts.map((o) => `<button class="pill ${o === cur ? "act" : ""}" data-dvsel="${esc(e)}" data-opt="${esc(o)}">${esc(this._optLabel(e, o))}</button>`).join("")}</div></div>` : ""}`;
    };
    const value = (e) => (dom(e) === "sensor" ? this._sensorVal(e) : this._stateText(e));
    const ctl = (e) => {
      const s = this._s(e), a = s.attributes, d = dom(e), name = esc(this._memberName(id, e));
      if (["switch", "input_boolean", "light", "fan"].includes(d)) {
        return `<div class="dv-c"><ha-icon icon="${this._icon(e)}"></ha-icon><span>${name}</span><div class="tg ${this._isOn(e) ? "on" : ""}" data-dvtg="${esc(e)}"><i></i></div></div>`;
      }
      if (d === "select") {
        return `<div class="dv-c col"><div class="dv-h"><ha-icon icon="${this._icon(e)}"></ha-icon><span>${name}</span></div>
          <div class="seg">${(a.options || []).map((o) => `<button class="pill ${o === s.state ? "solid" : ""}" data-dvsel="${esc(e)}" data-opt="${esc(o)}">${esc(this._st(o))}</button>`).join("")}</div></div>`;
      }
      if (d === "number") {
        return `<div class="dv-c"><ha-icon icon="${this._icon(e)}"></ha-icon><span>${name}</span>
          <button class="mini" data-dvnum="${esc(e)}" data-step="-1">−</button><b class="dv-v">${esc(this._sensorVal(e))}</b><button class="mini" data-dvnum="${esc(e)}" data-step="1">+</button></div>`;
      }
      if (d === "button") return `<div class="dv-c"><ha-icon icon="${this._icon(e)}"></ha-icon><span>${name}</span><button class="pill" data-dvbtn="${esc(e)}">${t("Executar")}</button></div>`;
      return `<div class="dv-c"><ha-icon icon="${this._icon(e)}"></ha-icon><span>${name}</span><small>${esc(this._stateText(e))}</small><button class="mini" data-dvopen="${esc(e)}"><ha-icon icon="mdi:chevron-right"></ha-icon></button></div>`;
    };
    const html = `${readings.length ? `<div class="sub">${t("Leituras")}</div><div class="dv-grid">${readings.map((e) =>
        `<div class="dv-r"><div class="dv-rv"><ha-icon icon="${this._icon(e)}"></ha-icon><b>${esc(value(e))}</b></div><small>${esc(this._memberName(id, e))}</small></div>`).join("")}</div>` : ""}
      <!--side-->${toggles.length || selects.length || controls.length ? `<div class="sub">${t("Controlos")}</div>` : ""}
      ${toggles.length || selects.length ? `<div class="dv-tgrid" style="grid-auto-flow:row dense">${toggles.map((e) => `<button class="dv-t ${this._isOn(e) ? "on" : ""}" data-dvtg="${esc(e)}"><ha-icon icon="${this._icon(e)}"></ha-icon><span>${esc(this._memberName(id, e))}</span></button>`).join("")}${selects.map(sel).join("")}</div>` : ""}
      ${controls.length ? `<div class="dv-list">${controls.map(ctl).join("")}</div>` : ""}`;
    this._selShown = this._selOpen; // the open option row animates once, not on every redraw
    return html;
  }

  // ---------- robot vacuum (Roborock and others) ----------
  /** A member of the vacuum's device by its entity id suffix (Roborock: sensor.<robot>_battery, _status…). */
  _vacMember(id, d, ...suffixes) {
    const mem = this._meta?.[id]?.members || [];
    return mem.find((e) => dom(e) === d && this._s(e) && suffixes.some((x) => e.endsWith("_" + x)));
  }
  /** Rooms of the robot's maps (Roborock: roborock.get_maps), loaded once per card. */
  _vacLoadRooms(id) {
    this._vacRooms ||= {};
    if (this._vacRooms[id] !== undefined) return;
    const plat = this._hass.entities?.[id]?.platform;
    const roborock = plat === "roborock" || (!plat && /roborock/i.test(this._meta?.[id]?.manufacturer || ""));
    if (!roborock) { this._vacRooms[id] = null; return; }
    this._vacRooms[id] = "loading";
    this._hass.callWS({ type: "call_service", domain: "roborock", service: "get_maps", target: { entity_id: id }, return_response: true })
      .then((r) => { const res = r?.response?.[id] ?? r?.response; this._vacRooms[id] = res?.maps || []; })
      .catch((e) => { console.warn("Pine3 View: roborock.get_maps", e); this._vacRooms[id] = []; })
      .finally(() => { if (this._modal === id || this._view === "room") this._render(); });
  }
  /** The map the robot is using: the one named by its "selected map" select, else the first. */
  _vacMap(id, maps) {
    if (!maps?.length) return null;
    const names = maps.map((m) => m.name);
    const sel = (this._meta?.[id]?.members || []).find((e) => dom(e) === "select" && (this._s(e)?.attributes.options || []).some((o) => names.includes(o)));
    return maps.find((m) => m.name === this._s(sel)?.state) || maps[0];
  }
  /** The map segment named like this Home Assistant room ("Sala" ↔ "sala"), if the robot knows one. */
  _vacSegment(id, room) {
    const maps = this._vacRooms?.[id], map = Array.isArray(maps) ? this._vacMap(id, maps) : null;
    const norm = (x) => String(x || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim();
    const want = [room?.name, this._roomName?.(room)].map(norm).filter(Boolean);
    const hit = Object.entries(map?.rooms || {}).find(([, n]) => want.includes(norm(n)));
    return hit ? Number(hit[0]) : null;
  }
  /** Room view: a wide vacuum card with its status and four quick commands; a tap elsewhere opens the full card. */
  _vacCard(id, room) {
    const s = this._s(id), a = s.attributes, on = this._isOn(id), na = s.state === "unavailable";
    this._vacLoadRooms(id);
    const level = this._vacBattery(id);
    const status = this._vacMember(id, "sensor", "status");
    const err = this._vacMember(id, "sensor", "vacuum_error");
    const errOn = err && !["none", "ok", "unknown", "no_error"].includes(this._s(err).state);
    const area = this._vacMember(id, "sensor", "cleaning_area"), time = this._vacMember(id, "sensor", "cleaning_time");
    const pctE = this._vacMember(id, "sensor", "clean_percent", "cleaning_progress");
    const pct = on && pctE ? Number(this._s(pctE).state) : NaN;
    const last = this._vacMember(id, "sensor", "last_clean_end");
    const lastD = last ? new Date(this._s(last).state) : null;
    const lastTxt = lastD && !isNaN(lastD) && !on ? (() => {
      const lang = this._hass?.locale?.language || this._hass?.language;
      const hm = lastD.toLocaleTimeString(lang, { hour: "2-digit", minute: "2-digit" });
      return lastD.toDateString() === new Date().toDateString() ? t("hoje {h}", { h: hm }) : `${lastD.toLocaleDateString(lang, { day: "numeric", month: "short" })} ${hm}`;
    })() : "";
    const fans = a.fan_speed_list || [];
    const fanTxt = a.fan_speed ? (() => { try { const v = this._hass.formatEntityAttributeValue?.(s, "fan_speed", a.fan_speed); return v && v !== a.fan_speed ? v : this._st(a.fan_speed); } catch { return this._st(a.fan_speed); } })() : "";
    const meta = [level != null ? `<span><ha-icon icon="mdi:battery${level >= 95 ? "" : "-" + Math.max(10, Math.round(level / 10) * 10)}"></ha-icon>${level}%</span>` : "",
      on ? [area, time].filter(Boolean).map((e) => `<span>${esc(this._sensorVal(e))}</span>`).join("") : fanTxt ? `<span><ha-icon icon="mdi:fan"></ha-icon>${esc(fanTxt)}</span>` : "",
      lastTxt ? `<span><ha-icon icon="mdi:clock-outline"></ha-icon>${esc(t("Última: {t}", { t: lastTxt }))}</span>` : ""].join("");
    const seg = this._vacSegment(id, room);
    const q = (act, icon, label, main) => `<button class="vq ${main ? "main" : ""}" data-vq="${act}" data-vid="${esc(id)}" ${seg != null && act === "room" ? `data-seg="${seg}"` : ""}><ha-icon icon="mdi:${icon}"></ha-icon><span>${label}</span></button>`;
    const third = seg != null ? q("room", "broom", t("Esta divisão")) : fans.length > 1 ? q("fan", "fan", t("Potência")) : "";
    const btns = [s.state === "cleaning" ? q("pause", "pause", t("Pausar"), true) : q("start", "play", s.state === "paused" || (errOn && s.state !== "docked") ? t("Retomar") : t("Iniciar"), true),
      q("return_to_base", "home-import-outline", t("Base")), third, q("locate", "map-marker-radius-outline", t("Localizar"))].join("");
    return `<div class="tile vacw big ${on ? "on" : "glass"} ${errOn ? "err" : ""} ${na ? "na" : ""}" data-open="${esc(id)}" data-key="${esc(id)}">
      ${this._meta?.[id] ? this._editChrome("dev", id) : ""}
      <div class="vw-l"><div class="ic"><ha-icon icon="mdi:robot-vacuum${errOn ? "-alert" : ""}"></ha-icon></div>
        <div class="tx"><div class="nm">${esc(this._name(id))}</div><div class="st">${esc(this._fmtState(status || id))}</div>
          ${errOn ? `<div class="vw-err"><ha-icon icon="mdi:alert"></ha-icon>${esc(this._fmtState(err))}</div>` : ""}
          ${meta ? `<div class="vw-meta">${meta}</div>` : ""}
          ${Number.isFinite(pct) ? `<div class="vw-bar"><i style="width:${Math.max(2, Math.min(100, pct))}%"></i></div>` : ""}</div></div>
      ${na ? "" : `<div class="vw-q">${btns}</div>`}
    </div>`;
  }
  /**
   * The robot's routines (Roborock creates one button per routine of its app: no category and no
   * translated name, unlike its own buttons such as empty dustbin or the consumable resets).
   */
  _vacRoutines(id) {
    const reg = this._hass.entities || {};
    return (this._meta?.[id]?.members || []).filter((e) => dom(e) === "button" && this._s(e) && this._s(e).state !== "unavailable"
      && !reg[e]?.entity_category && !reg[e]?.translation_key && !/reset|consumable|empty_dustbin|dock_error/.test(e));
  }

  /** The robot's other entities, sorted: what you use when cleaning up front; maintenance, settings and diagnostics folded away. */
  _vacuumSections(id) {
    const cat = (e) => this._hass.entities?.[e]?.entity_category || null;
    const shown = new Set(["battery", "status", "cleaning_area", "cleaning_time", "clean_percent", "cleaning_progress", "vacuum_error", "dock_error"]
      .map((x) => this._vacMember(id, "sensor", x)).filter(Boolean));
    const routines = new Set(this._vacRoutines(id));
    const mem = (this._meta?.[id]?.members || []).filter((e) => this._s(e) && this._s(e).state !== "unavailable" && dom(e) !== "image" && !shown.has(e) && !routines.has(e));
    const isReading = (e) => ["sensor", "binary_sensor"].includes(dom(e));
    const maint = mem.filter((e) => (isReading(e) && /_(time_)?left$|_remaining$|consumable/.test(e)) || (dom(e) === "button" && /reset/.test(e)));
    const main = mem.filter((e) => !maint.includes(e) && !isReading(e) && (dom(e) === "select" || !cat(e)));
    const settings = mem.filter((e) => !maint.includes(e) && !main.includes(e) && !isReading(e));
    const diag = mem.filter((e) => !maint.includes(e) && isReading(e));
    this._vacOpen ||= {};
    const fold = (key, title, icon, list) => {
      if (!list.length) return "";
      const open = this._vacOpen[key];
      return `<button class="vac-sec ${open ? "open" : ""}" data-vsec="${key}"><ha-icon icon="mdi:${icon}"></ha-icon><span>${title}</span><small>${list.length}</small><ha-icon class="chev" icon="mdi:chevron-down"></ha-icon></button>
        ${open ? `<div class="vac-secb">${this._deviceSection(id, list)}</div>` : ""}`;
    };
    return `${main.length ? this._deviceSection(id, main) : ""}
      <div class="vac-folds">${fold("maint", t("Manutenção"), "wrench-outline", maint)}${fold("set", t("Definições do robô"), "tune-variant", settings)}${fold("diag", t("Diagnóstico"), "chart-box-outline", diag)}</div>`;
  }

  /** A state in the integration's own words (HA's translations, e.g. Roborock's "Charging"), else ours. */
  _fmtState(e) {
    try { return this._hass.formatEntityState?.(this._s(e)) || this._stateText(e); } catch { return this._stateText(e); }
  }
  /** Battery level of a vacuum: its attribute or its device's battery sensor. */
  _vacBattery(id) {
    const a = this._s(id)?.attributes || {}, bat = this._vacMember(id, "sensor", "battery");
    const v = a.battery_level ?? (bat ? Number(this._s(bat).state) : null);
    return v != null && Number.isFinite(Number(v)) ? Math.round(Number(v)) : null;
  }

  _vacuumContent(id) {
    const s = this._s(id), a = s.attributes, on = this._isOn(id);
    const bat = this._vacMember(id, "sensor", "battery");
    const level = a.battery_level ?? (bat ? Number(this._s(bat).state) : null);
    const status = this._vacMember(id, "sensor", "status");
    const area = this._vacMember(id, "sensor", "cleaning_area"), time = this._vacMember(id, "sensor", "cleaning_time"), pct = this._vacMember(id, "sensor", "clean_percent", "cleaning_progress");
    const err = this._vacMember(id, "sensor", "vacuum_error", "dock_error");
    const errOn = err && !["none", "ok", "unknown", "no_error"].includes(this._s(err).state);
    const now = on ? [area, time, pct].filter(Boolean).map((e) => this._sensorVal(e)).join(" · ") : "";
    // the integration's own translations (HA language) for its states and fan speeds, else ours
    const h = this._hass;
    const fmt = (e) => this._fmtState(e);
    const fanLabel = (f) => { try { const v = h.formatEntityAttributeValue?.(s, "fan_speed", f); return v && v !== f ? v : this._st(f); } catch { return this._st(f); } };
    const btn = (svc, icon, label, act) => `<button class="vac-b ${act ? "act" : ""}" data-svc="vacuum.${svc}"><ha-icon icon="mdi:${icon}"></ha-icon><span>${label}</span></button>`;
    const fans = a.fan_speed_list || [];
    // rooms (Roborock): pick some and clean only those
    this._vacLoadRooms(id);
    const maps = this._vacRooms?.[id], map = Array.isArray(maps) ? this._vacMap(id, maps) : null;
    const rooms = map ? Object.entries(map.rooms || {}) : [];
    const pick = (this._vacPick ||= {})[id] ||= [];
    // the map picture of the map in use (Roborock: one image entity per map)
    const imgs = (this._meta?.[id]?.members || []).filter((e) => dom(e) === "image" && this._s(e)?.attributes.entity_picture);
    const img = imgs.find((e) => map && (this._s(e).attributes.friendly_name || "").toLowerCase().includes(String(map.name).toLowerCase())) || imgs[0];
    return `<div class="vac-hd">
        ${level != null && Number.isFinite(level) ? `<div class="vac-bat"><ha-icon icon="mdi:battery${level >= 95 ? "" : "-" + Math.max(10, Math.round(level / 10) * 10)}"></ha-icon><b>${Math.round(level)}%</b></div>` : ""}
        <div class="vac-st"><b>${esc(fmt(status || id))}</b>${now ? `<small>${esc(now)}</small>` : ""}${errOn ? `<small class="vac-err">${esc(fmt(err))}</small>` : ""}</div>
      </div>
      ${(() => {
        const rs = this._vacRoutines(id);
        return rs.length ? `<div class="sub">${t("Rotinas")}</div><div class="row presets vac-rt">${rs.map((e) => `<button class="pill ${this._fired === e ? "act" : ""}" data-vroutine="${esc(e)}"><ha-icon icon="${this._s(e).attributes.icon || "mdi:play-circle-outline"}"></ha-icon>${esc(this._memberName(id, e))}</button>`).join("")}</div>` : "";
      })()}
      <div class="vac-btns">
        ${s.state === "cleaning" ? btn("pause", "pause", t("Pausar"), true) : btn("start", "play", s.state === "paused" ? t("Retomar") : t("Iniciar"))}
        ${btn("return_to_base", "home-import-outline", t("Voltar à base"), s.state === "returning")}
        ${btn("locate", "map-marker-radius-outline", t("Localizar"))}
      </div>
      ${fans.length > 1 ? `<div class="sub">${t("Potência")}</div><div class="row presets">${fans.map((f) => `<button class="pill ${f === a.fan_speed ? "act" : ""}" data-vfan="${esc(f)}">${esc(fanLabel(f))}</button>`).join("")}</div>` : ""}
      ${maps === "loading" ? `<div class="sub">${t("Divisões")}</div><div class="vac-note">${t("A carregar as divisões…")}</div>` : rooms.length ? `<div class="sub">${t("Limpar divisões")}</div>
        <div class="row presets">${rooms.map(([sid, name]) => `<button class="pill ${pick.includes(Number(sid)) ? "act" : ""}" data-vroom="${esc(sid)}">${esc(name)}</button>`).join("")}</div>
        <div class="row"><button class="pill solid" data-vclean ${pick.length ? "" : "disabled"}><ha-icon icon="mdi:broom"></ha-icon>${pick.length ? tp(pick.length, "Limpar {n} divisão", "Limpar {n} divisões") : t("Escolhe as divisões")}</button></div>` : ""}
      <!--side-->${img ? `<img class="vac-map" src="${esc(this._s(img).attributes.entity_picture)}" alt="">` : ""}`;
  }

  _extras(id) {
    const m = this._meta?.[id] || {};
    const big = this._cfg().big_devices?.includes(id);
    const pool = big ? (m.members || []).filter((e) => ["sensor", "binary_sensor"].includes(dom(e))).slice(0, 6) : m.extras;
    const ex = (pool || []).filter((e) => this._s(e) && this._s(e).state !== "unavailable");
    if (!ex.length) return "";
    const txt = ex.map((e) => {
      const dc = this._s(e).attributes.device_class;
      if (dom(e) === "binary_sensor") return dc === "battery" ? (this._s(e).state === "on" ? t("Bateria fraca") : "") : this._stateText(e);
      return this._sensorVal(e) + (dc === "battery" ? " 🔋" : "");
    }).filter(Boolean);
    return txt.length ? `<div class="ex">${esc(txt.join(" · "))}</div>` : "";
  }

  _roomView(room) {
    const c = this._roomClimate(room);
    const devs = room.ents.filter((i) => dom(i) !== "scene");
    // categories: one grid ordered by kind (lights first, sensors last) and filter chips on top, like a home app
    const cat = (i) => SECTIONS.findIndex(([, domains]) => domains.includes(dom(i)));
    const cats = SECTIONS.map(([title], k) => [k, title, devs.filter((i) => cat(i) === k)]).filter(([, , l]) => l.length);
    if (this._roomCatFor !== room.id) { this._roomCat = null; this._roomCatFor = room.id; }
    const sel = this._edit ? null : this._roomCat;
    const shown = devs.map((i, n) => [i, n]).sort((x, y) => ((cat(x[0]) + 99) % 99) - ((cat(y[0]) + 99) % 99) || x[1] - y[1]).map(([i]) => i)
      .filter((i) => sel == null || cat(i) === sel);
    const lights = devs.filter((i) => dom(i) === "light"), lit = lights.filter((i) => this._isOn(i));
    const others = this._activeOthers(devs);
    const status = lit.length ? tp(lit.length, "{n} luz acesa", "{n} luzes acesas") : others.length ? tp(others.length, "{n} dispositivo ligado", "{n} dispositivos ligados") : t("Tudo desligado");
    const sub = [c.temp, c.hum].filter(Boolean).join(" · ");
    const hiddenHere = this._edit && this._model ? this._model.rooms.find((r) => r.id === room.id)?.devices.filter((d) => this._edit.hidden.includes(d.id)).length : 0;
    const scenes = room.ents.filter((i) => dom(i) === "scene");
    const chips = (cats.length > 1 && !this._edit ? `<button class="pill rcat ${sel == null ? "solid" : ""}" data-rcat="">${t("Tudo")} <small>${devs.length}</small></button>${cats.map(([k, title, l]) => `<button class="pill rcat ${sel === k ? "solid" : ""}" data-rcat="${k}"><ha-icon icon="${SECTION_ICON[title] || "mdi:shape-outline"}"></ha-icon>${t(title)} <small>${l.length}</small></button>`).join("")}` : "")
      + (scenes.length && !this._edit ? `${cats.length > 1 ? `<span class="rsep"></span>` : ""}${scenes.map((i) => `<div class="scene glass ${this._fired === i ? "fired" : ""} ${this._sceneOn(i) ? "on" : ""}" data-scene="${i}" title="${this._sceneOn(i) ? t("Toca para desligar") : ""}"><ha-icon icon="${this._s(i).attributes.icon || "mdi:creation-outline"}"></ha-icon>${esc(this._name(i))}</div>`).join("")}` : "");
    return `<div class="rhead">
        <div class="rtitle"><div class="eyebrow">${tp(room.count, "{n} dispositivo", "{n} dispositivos")}${sub ? ` · ${esc(sub)}` : ""}</div><div class="t">${esc(this._roomName(room))}</div>${this._hint()}</div>
        ${this._edit ? "" : `<div class="rstat"><div class="status">${status}</div>${lit.length ? this._onChips(others, 1) : ""}</div>`}
        <div class="right">${lights.length && !this._edit ? `<button class="pill" data-rlights="on"><ha-icon icon="mdi:lightbulb-outline"></ha-icon>${t("Acender")}</button><button class="pill solid" data-rlights="off"><ha-icon icon="mdi:power"></ha-icon>${t("Apagar")}</button>` : ""}${this._editBtn()}${this._gear()}</div>
      </div>
      ${chips ? `<div class="rchips">${chips}</div>` : ""}
      <div class="tiles rgrid" data-sortable="dev:${esc(room.id)}">${shown.map((i) => (dom(i) === "climate" && this._s(i) ? this._climateCard(i, room) : dom(i) === "vacuum" && this._s(i) ? this._vacCard(i, room) : dom(i) === "camera" && this._s(i) ? this._camTile(i) : this._tile(i))).join("")}</div>
      ${hiddenHere ? `<div class="tiles" style="margin-top:18px"><div class="tile add" data-edrestore="devices"><ha-icon icon="mdi:plus"></ha-icon><span>${tp(hiddenHere, "Mostrar {n} escondido", "Mostrar {n} escondidos")}</span></div></div>` : ""}`;
  }

  /**
   * Room categories share rows: each spans as many grid columns as it has tiles (never more than the row).
   * A category with more tiles than a row continues in a section of its own (no title), so the next
   * categories can sit beside its last tiles instead of leaving the rest of that row empty.
   */
  _packSections() {
    const box = this.shadowRoot?.querySelector(".secs");
    if (!box) return;
    const cols = getComputedStyle(box).gridTemplateColumns.split(" ").filter(Boolean).length || 1;
    if (box.dataset.cols && Number(box.dataset.cols) !== cols) { this._render(); return; } // the row changed: lay out again
    box.dataset.cols = cols;
    const width = (el) => (el.classList.contains("big") ? 2 : 1);
    if (!this._edit) {
      for (const sec of [...box.querySelectorAll(":scope > .sec")]) {
        const chunks = [[]];
        let used = 0;
        for (const tile of sec.querySelectorAll(":scope > .tiles > .tile")) {
          if (used + width(tile) > cols && chunks.at(-1).length) { chunks.push([]); used = 0; }
          chunks.at(-1).push(tile);
          used += width(tile);
        }
        let after = sec;
        for (const chunk of chunks.slice(1)) {
          const cont = document.createElement("section");
          cont.className = "sec cont";
          cont.innerHTML = `<h2 aria-hidden="true">&nbsp;</h2><div class="tiles"></div>`;
          chunk.forEach((tile) => cont.lastChild.appendChild(tile));
          after.after(cont);
          after = cont;
        }
      }
    }
    box.querySelectorAll(":scope > .sec").forEach((sec) => {
      const cells = [...sec.querySelectorAll(":scope > .tiles > .tile")].reduce((a, el) => a + width(el), 0);
      sec.style.setProperty("--span", Math.max(1, Math.min(cols, cells || 1)));
    });
  }

  _scenesView() {
    const h = this._hass;
    const list = (d) => this._model ? (d === "scene" ? this._model.scenes : this._model.scripts).filter((i) => this._s(i)) : Object.keys(h.states).filter((i) => dom(i) === d && !h.entities?.[i]?.hidden);
    const sc = list("scene"), sp = list("script");
    const grid = (ids) => `<div class="tiles">${ids.map((i) => this._tile(i)).join("")}</div>`;
    return `<div class="hero"><div><div class="eyebrow">${t("Toca para executar")}</div><div class="t" style="margin-top:8px">${t("Cenas e scripts")}</div></div>${this._gear()}</div>
      ${sc.length ? `<h2>${t("Cenas")}</h2>${grid(sc)}` : ""}${sp.length ? `<h2>${t("Scripts")}</h2>${grid(sp)}` : ""}
      ${!sc.length && !sp.length ? `<div class="empty">${t("Não há cenas nem scripts.")}</div>` : ""}`;
  }

  _slider(kind, pct, compact) {
    return compact
      ? `<div class="hslider" data-slider="${kind}" data-val="${pct}"><div class="fill" style="width:${pct}%"></div><div class="lbl">${pct}%</div></div>`
      : `<div class="vslider" data-slider="${kind}" data-val="${pct}"><div class="fill" style="height:${pct}%"></div><div class="lbl">${pct}%</div></div>`;
  }

  _modalView() {
    if (this._modal === "_wx") return this._forecastSheet();
    const id = this._modal, s = id && this._s(id);
    if (!s) return "";
    const d = dom(id), a = s.attributes, on = this._isOn(id);
    const compact = !!this._meta?.[id]?.members?.some((e) => this._s(e)); // a whole-device card: keep it short
    let content = "";
    if (d === "light" && (a.supported_color_modes || []).some((m) => m !== "onoff")) {
      const pct = on ? Math.round(((a.brightness ?? 255) / 255) * 100) : 0;
      const colors = (a.supported_color_modes || []).some((m) => ["hs", "rgb", "rgbw", "rgbww", "xy"].includes(m));
      content = `${this._slider("brightness", pct, compact)}
        <!--side-->${colors ? `<div class="sub sh-only">${t("Cor")}</div>` : ""}${colors ? `<div class="swatches">${["#ffffff", "#ffd28a", "#ff8a5c", "#ff5ca8", "#8a7cff", "#5cc8ff", "#5cff9d"].map((c) => `<div class="sw" data-color="${c}" style="background:${c}"></div>`).join("")}</div>` : ""}`;
    } else if (d === "cover" && a.current_position != null) {
      const pct = a.current_position;
      content = `${this._slider("position", pct, compact)}
        <!--side--><div class="row"><button class="pill" data-svc="cover.open_cover">${t("Abrir")}</button><button class="pill" data-svc="cover.stop_cover">${t("Parar")}</button><button class="pill" data-svc="cover.close_cover">${t("Fechar")}</button></div>`;
    } else if (d === "climate") {
      // single target ("temperature") or a range (heat_cool / auto: target_temp_low + target_temp_high, e.g. Tado via HomeKit)
      const step = a.target_temp_step || 0.5, min = a.min_temp ?? 7, max = a.max_temp ?? 35;
      const lo = a.target_temp_low, hi = a.target_temp_high;
      const range = a.temperature == null && lo != null && hi != null;
      const tg = range ? null : a.temperature;
      const pos = (v) => Math.min(100, Math.max(0, ((v - min) / (max - min)) * 100));
      const off = s.state === "off" || s.state === "unavailable";
      const act = off ? null : a.hvac_action; // what it is doing right now: heating, cooling, idle…
      const mode = { heating: "heat", preheating: "heat", cooling: "cool", defrosting: "cool", drying: "dry", fan: "fan_only", idle: "idle" }[act] || s.state;
      const col = off ? "var(--ring-off)" : { cool: "#5ac8fa", dry: "#64d2ff", fan_only: "#a8b0bd", idle: "var(--ring-idle)" }[mode] || "#ff9f0a";
      const dim = "var(--ring-dim)";
      const grad = range ? `${dim} 0 ${pos(lo)}%, ${col} 0 ${pos(hi)}%, ${dim} 0` : `${col} 0 ${tg != null ? pos(tg) : 0}%, ${dim} 0`;
      const info = [a.current_temperature != null ? `${t("Atual")} ${num(a.current_temperature)}°` : "", a.current_humidity != null ? `${Math.round(a.current_humidity)}% ${t("hum.")}` : ""].filter(Boolean).join(" · ");
      const setter = (end, v, label) => `<div class="tset"><span>${label}</span><button class="round" data-temp="${-step}" data-end="${end}">−</button><b>${num(v)}°</b><button class="round" data-temp="${step}" data-end="${end}">+</button></div>`;
      const chips = (label, list, cur, attr) => list?.length > 1 ? `<div class="sub">${label}</div><div class="row presets">${list.map((m) => `<button class="pill ${m === cur ? "act" : ""}" data-cmode="${attr}" data-v="${esc(m)}">${esc(this._st(m))}</button>`).join("")}</div>` : "";
      const modes = a.hvac_modes || [];
      content = `<div class="ring ${compact ? "sm" : ""}" style="background:conic-gradient(${grad})">
        <div style="position:absolute;inset:14px;border-radius:50%;background:var(--ring-bg)"></div>
        <div style="position:relative;text-align:center"><div class="tgt ${range ? "rng" : ""}">${range ? `${num(lo)}–${num(hi)}°` : tg != null ? num(tg) + "°" : "–"}</div>
        ${act && act !== "off" ? `<div class="act" style="color:${col}">${esc(this._st(act))}</div>` : ""}${info ? `<div class="cur">${info}</div>` : ""}</div></div>
        ${range ? setter("low", lo, t("Mínima")) + setter("high", hi, t("Máxima")) : tg != null ? `<div class="row"><button class="round" data-temp="${-step}">−</button><button class="round" data-temp="${step}">+</button></div>` : ""}
        <!--side-->${modes.length ? `<div class="sub sh-only">${t("Modo")}</div>` : ""}
        ${modes.length ? `<div class="row ${modes.length > 4 || compact ? "presets" : ""}">${modes.map((m) => `<button class="pill ${m === s.state ? "act" : ""}" data-hvac="${m}">${this._st(m)}</button>`).join("")}</div>` : ""}
        ${chips(t("Velocidade"), a.fan_modes, a.fan_mode, "fan_mode")}${chips(t("Oscilação"), a.swing_modes, a.swing_mode, "swing_mode")}${chips(t("Perfil"), a.preset_modes, a.preset_mode, "preset_mode")}`;
    } else if (d === "media_player") {
      // only the controls the player supports (a HomeKit TV has power and inputs, no tracks)
      const f = a.supported_features || 0, has = (b) => (f & b) !== 0;
      const vol = Math.round((a.volume_level ?? 0) * 100);
      const sources = a.source_list || [];
      const playing = s.state === "playing";
      const transport = [has(16) ? `<button class="round" data-svc="media_player.media_previous_track">⏮</button>` : "",
        has(1) || has(16384) ? `<button class="round" data-svc="media_player.media_play_pause">${playing ? "⏸" : "▶"}</button>` : "",
        has(32) ? `<button class="round" data-svc="media_player.media_next_track">⏭</button>` : ""].join("");
      content = `${a.entity_picture && on ? `<img src="${esc(a.entity_picture)}" style="width:100%;max-height:260px;object-fit:cover;border-radius:20px;margin-top:18px">` : ""}
        ${a.media_title ? `<div style="text-align:center;margin-top:14px;font-weight:700">${esc(a.media_title)}</div><div style="text-align:center;color:var(--muted);font-size:13px">${esc(a.media_artist || a.app_name || "")}</div>` : ""}
        ${on && transport ? `<div class="row">${transport}</div>` : ""}
        <!--side-->${on && sources.length ? `<div class="sub">${t("Entrada")}</div><div class="row presets">${sources.map((src) => `<button class="pill ${src === a.source ? "act" : ""}" data-src="${esc(src)}">${esc(src)}</button>`).join("")}</div>` : ""}
        ${on && has(4) && a.volume_level != null ? `<div class="sub">${t("Volume")}</div>${this._slider("volume", vol, true)}`
          : on && has(1024) ? `<div class="sub">${t("Volume")}</div><div class="row"><button class="round" data-svc="media_player.volume_down">−</button>${has(8) ? `<button class="round" data-mute><ha-icon icon="mdi:volume-${a.is_volume_muted ? "off" : "high"}"></ha-icon></button>` : ""}<button class="round" data-svc="media_player.volume_up">+</button></div>` : ""}`;
    } else if (d === "fan" && (a.percentage != null || a.preset_modes?.length)) {
      content = `${a.percentage != null ? this._slider("fan", on ? a.percentage : 0, compact) : ""}
        <!--side-->${a.preset_modes?.length ? `<div class="sub sh-only">${t("Modo")}</div>` : ""}${a.preset_modes?.length ? `<div class="row ${compact ? "presets" : ""}">${a.preset_modes.map((m) => `<button class="pill ${on && m === a.preset_mode ? "act" : ""}" data-preset="${esc(m)}">${esc(this._st(m))}</button>`).join("")}</div>` : ""}`;
    } else if (d === "vacuum") {
      content = this._vacuumContent(id);
    } else if (d === "lock") {
      const locked = s.state === "locked", busy = ["locking", "unlocking", "opening"].includes(s.state);
      const armed = locked && this._armed(id);
      content = `<button class="lockbig ${locked ? "ok" : ""} ${armed ? "arm" : ""}" data-toggle ${busy || s.state === "unavailable" ? "disabled" : ""}><ha-icon icon="${this._icon(id)}"></ha-icon></button>
        <div class="lockcap">${busy ? esc(this._stateText(id)) : armed ? t("Toca outra vez para destrancar") : locked ? t("Toca 2 vezes para destrancar") : t("Toca para trancar")}</div>`;
    } else if (d === "camera") {
      content = `<img src="/api/camera_proxy/${esc(id)}?token=${esc(a.access_token || "")}&t=${Date.now()}" style="width:100%;border-radius:18px;margin-top:18px;background:#000">`;
    } else {
      const skip = new Set(["friendly_name", "icon", "entity_picture", "supported_features", "attribution", "color_mode", "supported_color_modes", "device_class"]);
      const rows = ["light", "switch", "input_boolean"].includes(d) ? [] : Object.entries(a).filter(([k, v]) => !skip.has(k) && typeof v !== "object" && v != null).slice(0, 8);
      content = `${d === "sensor" ? `<div style="font-size:48px;font-weight:300;text-align:center;margin:24px 0 6px">${esc(this._sensorVal(id))}</div>` : ""}
        <!--side-->${rows.length ? `<div class="attrs">${rows.map(([k, v]) => `<span>${esc(k.replace(/_/g, " "))}</span><b>${esc(v)}</b>`).join("")}</div>` : ""}`;
    }
    const canToggle = TOGGLE.has(d) && (d !== "media_player" || ((a.supported_features || 0) & 384) !== 0);
    const device = d === "vacuum" ? this._vacuumSections(id) : this._deviceSection(id);
    const more = `<div class="more"><button data-more title="${t("Mais definições")}" aria-label="${t("Mais definições")}"><ha-icon icon="mdi:cog-outline"></ha-icon></button></div>`;
    // wide screens: the main control on the left, everything else of the device on the right
    // landscape screens: two columns wherever there are two parts. Left: the main control (and, without a
    // second part of its own, the device's readings); right: the rest (modes, inputs, colours, controls...).
    const has = (h) => h.replace(/<!--side-->|\s/g, "") !== "";
    const [cm, cs = ""] = content.split("<!--side-->"), [dm, ds = ""] = device.split("<!--side-->");
    let left, right;
    const c = cm + cs;
    if (has(cm) && has(cs)) { left = cm; right = cs + device; } else if (has(ds) && has(c + dm)) { left = c + dm; right = ds; } else if (has(c) && has(dm)) { left = c; right = dm; }
    const cols = !!(left && right && has(left) && has(right));
    const layout = cols ? `wide dev cols ${device ? "" : "cols-sm"}` : device ? "wide dev" : "";
    return `<div class="ov ${this._ovEnter(id)} ${device ? "dev" : ""}" data-close><div class="sheet ${layout}" style="--acc:${this._accent(id)}">
      <div class="hd"><div class="ic"><ha-icon icon="${this._icon(id)}"></ha-icon></div><div style="flex:1;min-width:0"><div class="nm">${esc(this._name(id))}</div><div class="st">${esc(this._stateText(id))}</div></div>
      ${d === "lock" ? `<button class="pill act" data-toggle><ha-icon icon="mdi:lock${s.state === "locked" ? "-open-variant" : ""}"></ha-icon>${s.state === "locked" ? (this._armed(id) ? t("Confirmar") : t("Destrancar")) : t("Trancar")}</button>` : canToggle ? `<button class="pill ${on ? "act" : ""}" data-toggle>${on ? t("Desligar") : t("Ligar")}</button>` : ""}<button class="sh-x" data-closex title="${t("Fechar")}" aria-label="${t("Fechar")}"><ha-icon icon="mdi:close"></ha-icon></button></div>
      ${cols ? `<div class="sh-main">${left}${more}</div><div class="sh-side">${right}</div>` : `${content}${device}${more}`}</div></div>`;
  }

  _bind() {
    const root = this.shadowRoot;
    const on = (sel, fn) => root.querySelectorAll(sel).forEach((el) => el.addEventListener("click", (ev) => { if (!this._ghost(el)) fn(el, ev); }));
    if (this._saver) {
      root.querySelector("[data-saver]")?.addEventListener("click", () => { this._saver = false; this._lastActivity = Date.now(); this._render(); });
      return;
    }
    on('[data-act="back"]', () => { this._edit = null; this._go("home"); });
    on('[data-act="menu"]', () => this.dispatchEvent(new Event("hass-toggle-menu", { bubbles: true, composed: true })));
    on('[data-act="settings"]', () => this._openSettings());
    on("[data-editstart]", () => this._startEdit());
    on("[data-fs]", () => this._toggleFullscreen());
    on("[data-wx]", () => this._openForecast());
    on("[data-alertmore]", (el, ev) => { ev.stopPropagation(); this._alertsMenu(el); });
    on(".fitmore", (el, ev) => { ev.stopPropagation(); this._moreMenu(el); });
    if (this._edit) {
      this._bindEdit(root);
    } else {
      on("[data-nav]", (el) => this._go(el.dataset.nav));
      if (this._view !== "house") on("[data-room]", (el) => this._go("room", el.dataset.room));
      on("[data-roomlights]", (el, ev) => {
        ev.stopPropagation();
        const r = this._rooms().find((x) => x.id === el.dataset.roomlights);
        // the room's lights only (plugs and switches stay as they are); a room without lights switches its devices
        const sw = this._switchables(r), lights = sw.filter((i) => dom(i) === "light"), pool = lights.length ? lights : sw;
        const onNow = pool.filter((i) => this._isOn(i));
        if (onNow.length) this._call("homeassistant", "turn_off", { entity_id: onNow });
        else this._call("homeassistant", "turn_on", { entity_id: pool });
      });
      on("[data-all]", (el) => {
        const lights = this._rooms().flatMap((r) => r.ents).filter((i) => dom(i) === "light");
        if (lights.length) this._call("light", el.dataset.all === "on" ? "turn_on" : "turn_off", { entity_id: lights });
      });
      on("[data-pw]", (el, ev) => { ev.stopPropagation(); this._call("homeassistant", "toggle", { entity_id: el.dataset.pw }); });
      on("[data-rcat]", (el) => { const v = el.dataset.rcat; this._roomCat = v === "" || Number(v) === this._roomCat ? null : Number(v); this._render(); });
      on("[data-rlights]", (el) => {
        const r = this._rooms().find((x) => x.id === this._room), ls = (r?.ents || []).filter((i) => dom(i) === "light");
        if (ls.length) this._call("light", el.dataset.rlights === "on" ? "turn_on" : "turn_off", { entity_id: ls });
      });
      on("[data-cam]", (el) => { if (!this._edit) this._openCam(el.dataset.cam); });
      on("[data-open]", (el, ev) => { if (!ev.target.closest("[data-vq],[data-lk]")) this._openModal(el.dataset.open); });
      // wide vacuum card: quick commands without opening the card
      on("[data-vq]", (el, ev) => {
        ev.stopPropagation();
        const v = el.dataset.vid, act = el.dataset.vq;
        if (act === "room") return this._call("vacuum", "send_command", { entity_id: v, command: "app_segment_clean", params: [{ segments: [Number(el.dataset.seg)] }] });
        if (act === "fan") {
          const s = this._s(v), fl = (f) => { try { const x = this._hass.formatEntityAttributeValue?.(s, "fan_speed", f); return x && x !== f ? x : this._st(f); } catch { return this._st(f); } };
          return this._popMenu(el, t("Potência"), (s.attributes.fan_speed_list || []).map((f) => ["mdi:fan", fl(f), () => this._call("vacuum", "set_fan_speed", { entity_id: v, fan_speed: f }), "", f === s.attributes.fan_speed ? "var(--amber)" : ""]));
        }
        this._call("vacuum", act, { entity_id: v });
      });
      // climate cards: power and ± target, without opening the card
      on("[data-cpw]", (el, ev) => {
        ev.stopPropagation();
        const id = el.dataset.cpw;
        this._call("climate", this._isOn(id) ? "turn_off" : "turn_on", { entity_id: id });
      });
      on("[data-chvac]", (el, ev) => {
        ev.stopPropagation();
        const id = el.dataset.chvac, m = el.dataset.m;
        if (this._s(id)?.state !== m) this._call("climate", "set_hvac_mode", { entity_id: id, hvac_mode: m });
      });
      on("[data-ctemp]", (el, ev) => {
        ev.stopPropagation();
        const id = el.dataset.ctemp, a = this._s(id).attributes;
        const step = a.target_temp_step || (this._hass.config?.unit_system?.temperature === "°F" ? 1 : 0.5);
        const v = Math.min(a.max_temp ?? 35, Math.max(a.min_temp ?? 7, Math.round(((this._climateTarget(id) ?? 20) + step * Number(el.dataset.d)) * 10) / 10));
        (this._ctWant ||= {})[id] = { v, t: Date.now() };
        this._call("climate", "set_temperature", { entity_id: id, temperature: v });
        this._render();
      });
      on("[data-media]", (el, ev) => { ev.stopPropagation(); this._call("media_player", el.dataset.media, { entity_id: el.dataset.entId }); });
      on("[data-scene]", (el) => this._sceneTap(el.dataset.scene));
      // tiles: tap = action, long-press / right-click = details
      root.querySelectorAll("[data-ent]").forEach((el) => {
        const id = el.dataset.ent;
        let timer, long = false;
        // a robot vacuum opens its card on a plain tap: no long press, so the end of a press never lands on Start
        const start = () => { long = false; if (dom(id) === "vacuum") return; timer = setTimeout(() => { long = true; if (DETAIL.has(dom(id)) || this._meta?.[id]) this._openModal(id); }, 450); };
        const cancel = () => clearTimeout(timer);
        el.addEventListener("pointerdown", start);
        el.addEventListener("pointerup", cancel);
        el.addEventListener("pointerleave", cancel);
        el.addEventListener("contextmenu", (ev) => { ev.preventDefault(); cancel(); this._openModal(id); });
        el.addEventListener("click", (ev) => {
          if (long) return;
          // a lock only (un)locks from its icon; the rest of the tile opens its card
          if (dom(id) === "lock") return ev.target.closest("[data-lk]") ? this._tap(id) : this._openModal(id);
          if (el.querySelector("[data-pw]") && DETAIL.has(dom(id))) this._openModal(id);
          else this._tap(id);
        });
      });
      if (this._view === "house") bindHouse(this, root);
    }

    // modal
    const ov = root.querySelector(".ov");
    if (!ov) return;
    ov.addEventListener("click", (ev) => { if (ev.target === ov && !this._ghost(ov)) { this._modal = null; this._render(); } });
    on("[data-closex]", () => { this._modal = null; this._render(); });
    const id = this._modal;
    on("[data-toggle]", () => (dom(id) === "media_player" ? this._call("media_player", this._isOn(id) ? "turn_off" : "turn_on", { entity_id: id }) : this._tap(id)));
    on("[data-src]", (el) => this._call("media_player", "select_source", { entity_id: id, source: el.dataset.src }));
    on("[data-mute]", () => this._call("media_player", "volume_mute", { entity_id: id, is_volume_muted: !this._s(id).attributes.is_volume_muted }));
    on("[data-more]", () => { this._modal = null; this._render(); this._moreInfo(id); });
    on("[data-svc]", (el) => { const [d, s] = el.dataset.svc.split("."); this._call(d, s, { entity_id: id }); });
    on("[data-vsec]", (el) => { const k = el.dataset.vsec; this._vacOpen[k] = !this._vacOpen[k]; this._render(); });
    on("[data-vroutine]", (el) => {
      const e = el.dataset.vroutine;
      this._call("button", "press", { entity_id: e });
      this._fired = e; this._render();
      setTimeout(() => { this._fired = null; this._render(); }, 1200);
    });
    on("[data-vfan]", (el) => this._call("vacuum", "set_fan_speed", { entity_id: id, fan_speed: el.dataset.vfan }));
    on("[data-vroom]", (el) => {
      const sid = Number(el.dataset.vroom), pick = (this._vacPick[id] ||= []);
      this._vacPick[id] = pick.includes(sid) ? pick.filter((x) => x !== sid) : [...pick, sid];
      this._render();
    });
    on("[data-vclean]", () => {
      const segs = this._vacPick?.[id] || [];
      if (!segs.length) return;
      // what the Roborock integration itself sends to clean rooms (segments of the current map)
      this._call("vacuum", "send_command", { entity_id: id, command: "app_segment_clean", params: [{ segments: segs }] });
      this._vacPick[id] = [];
      this._render();
    });
    on("[data-color]", (el) => { const h = el.dataset.color; this._call("light", "turn_on", { entity_id: id, rgb_color: [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16)) }); });
    on("[data-preset]", (el) => this._call(dom(id), "set_preset_mode", { entity_id: id, preset_mode: el.dataset.preset }));
    on("[data-dvtg]", (el) => this._call("homeassistant", "toggle", { entity_id: el.dataset.dvtg }));
    on("[data-dvopensel]", (el) => { this._selOpen = this._selOpen === el.dataset.dvopensel ? null : el.dataset.dvopensel; this._render(); });
    on("[data-dvsel]", (el) => {
      const e = el.dataset.dvsel;
      (this._selWant ||= {})[e] = { o: el.dataset.opt, t: Date.now() };
      this._selOpen = null;
      this._call(dom(e) === "input_select" ? "input_select" : "select", "select_option", { entity_id: e, option: el.dataset.opt });
      this._render();
    });
    on("[data-dvbtn]", (el) => this._call("button", "press", { entity_id: el.dataset.dvbtn }));
    on("[data-dvopen]", (el) => this._openModal(el.dataset.dvopen));
    on("[data-dvnum]", (el) => {
      const e = el.dataset.dvnum, a = this._s(e).attributes, step = Number(a.step) || 1;
      const v = Math.min(a.max ?? Infinity, Math.max(a.min ?? -Infinity, Number(this._s(e).state) + step * Number(el.dataset.step)));
      this._call("number", "set_value", { entity_id: e, value: Math.round(v * 1000) / 1000 });
    });
    on("[data-hvac]", (el) => this._call("climate", "set_hvac_mode", { entity_id: id, hvac_mode: el.dataset.hvac }));
    on("[data-cmode]", (el) => {
      const attr = el.dataset.cmode;
      this._call("climate", `set_${attr}`, { entity_id: id, [attr]: el.dataset.v });
    });
    on("[data-temp]", (el) => {
      const a = this._s(id).attributes;
      const clamp = (v) => Math.min(a.max_temp ?? 35, Math.max(a.min_temp ?? 7, Math.round(v * 10) / 10));
      const dt = Number(el.dataset.temp), end = el.dataset.end;
      if (!end) return this._call("climate", "set_temperature", { entity_id: id, temperature: clamp((a.temperature ?? 20) + dt) });
      // range: move one end, never past the other
      let lo = a.target_temp_low, hi = a.target_temp_high;
      if (end === "low") lo = Math.min(clamp(lo + dt), hi); else hi = Math.max(clamp(hi + dt), lo);
      this._call("climate", "set_temperature", { entity_id: id, target_temp_low: lo, target_temp_high: hi });
    });
    root.querySelectorAll("[data-slider]").forEach((el) => this._bindSlider(el, id));
  }

  _bindSlider(el, id) {
    const fill = el.querySelector(".fill"), lbl = el.querySelector(".lbl");
    const kind = el.dataset.slider;
    let val = Number(el.dataset.val);
    const horiz = el.classList.contains("hslider");
    const at = (ev) => {
      const r = el.getBoundingClientRect();
      const f = horiz ? (ev.clientX - r.left) / r.width : (r.bottom - ev.clientY) / r.height;
      return Math.round(Math.min(100, Math.max(0, f * 100)));
    };
    const show = (v) => { fill.style[horiz ? "width" : "height"] = v + "%"; lbl.textContent = v + "%"; };
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
