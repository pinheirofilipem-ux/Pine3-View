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
  ameixa: "radial-gradient(90% 70% at 55% 35%, #7b4467 0%, transparent 60%), radial-gradient(70% 60% at 0% 0%, #4a2440 0%, transparent 60%), radial-gradient(80% 70% at 100% 100%, #2c1630 0%, transparent 60%), linear-gradient(160deg, #3a1d36, #1d0f1f)",
  pinheiro: "radial-gradient(120% 90% at 10% 0%, #2f6b4a 0%, transparent 55%), radial-gradient(90% 80% at 100% 100%, #1d3f5e 0%, transparent 60%), linear-gradient(160deg, #11261b, #0b1520)",
  noite: "radial-gradient(100% 80% at 80% 0%, #3a2d6b 0%, transparent 55%), radial-gradient(90% 70% at 0% 100%, #1a3a5c 0%, transparent 60%), linear-gradient(170deg, #121026, #070912)",
  aurora: "radial-gradient(90% 70% at 0% 0%, #1f8a7a 0%, transparent 55%), radial-gradient(80% 70% at 100% 30%, #6a3fa0 0%, transparent 60%), linear-gradient(180deg, #0e1e2a, #120c22)",
  areia: "radial-gradient(100% 80% at 20% 0%, #a0724a 0%, transparent 55%), radial-gradient(80% 70% at 100% 100%, #5a4a7a 0%, transparent 60%), linear-gradient(160deg, #3a2a20, #1a1420)",
  oceano: "radial-gradient(100% 80% at 90% 0%, #1e6fa8 0%, transparent 55%), radial-gradient(80% 70% at 0% 100%, #0f8a8a 0%, transparent 60%), linear-gradient(170deg, #0a1f33, #06121c)",
};
const THEME_NAMES = { ameixa: "Ameixa", pinheiro: "Pinheiro", noite: "Noite", aurora: "Aurora", areia: "Areia", oceano: "Oceano" };

// Fonts: Outfit (display) + Figtree (text), both SIL OFL 1.1, shipped in ./fonts.
(() => {
  if (document.getElementById("pine3-fonts")) return;
  const base = new URL("./fonts/", import.meta.url).href;
  const LATIN = "U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD";
  const EXT = "U+0100-02BA,U+02BD-02C5,U+02C7-02CC,U+02CE-02D7,U+02DD-02FF,U+0304,U+0308,U+0329,U+1D00-1DBF,U+1E00-1E9F,U+1EF2-1EFF,U+2020,U+20A0-20AB,U+20AD-20C0,U+2113,U+2C60-2C7F,U+A720-A7FF";
  const face = (fam, file, range) => `@font-face{font-family:"${fam}";font-style:normal;font-display:swap;font-weight:100 900;src:url(${base}${file}) format("woff2-variations"),url(${base}${file}) format("woff2");unicode-range:${range}}`;
  const st = document.createElement("style");
  st.id = "pine3-fonts";
  st.textContent = [
    face("Pine3 Outfit", "outfit-latin-wght-normal.woff2", LATIN), face("Pine3 Outfit", "outfit-latin-ext-wght-normal.woff2", EXT),
    face("Pine3 Figtree", "figtree-latin-wght-normal.woff2", LATIN), face("Pine3 Figtree", "figtree-latin-ext-wght-normal.woff2", EXT),
  ].join("");
  document.head.appendChild(st);
})();

const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const dom = (id) => id.split(".")[0];
const store = {
  get(k, d) { try { const v = localStorage.getItem("pine3." + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem("pine3." + k, JSON.stringify(v)); } catch { /* ignore */ } },
};

const css = `
*,*::before,*::after{box-sizing:border-box}
:host{display:block;position:relative;min-height:100vh;color:#fff;font-family:"Pine3 Figtree",-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;-webkit-font-smoothing:antialiased;
  --display:"Pine3 Outfit","Pine3 Figtree",sans-serif;
  --glass:rgba(255,255,255,.09);--glass-hi:rgba(255,255,255,.14);--glass-strong:rgba(38,26,40,.82);--line:rgba(255,255,255,.12);--muted:rgba(255,255,255,.66);
  --cream:#f5efe1;--ink:#2a221f;--ink-soft:rgba(42,34,31,.6);--amber:#f6b83f;--r:28px}
.bg{position:fixed;inset:0;z-index:0;transition:background .6s}
.bg::after{content:"";position:absolute;inset:0;background:radial-gradient(transparent 45%,rgba(0,0,0,.28))}
.wrap{position:relative;z-index:1;max-width:1500px;margin:0 auto;padding:0 40px 130px}
.glass{background:var(--glass);backdrop-filter:blur(30px) saturate(150%);-webkit-backdrop-filter:blur(30px) saturate(150%);border:1px solid var(--line)}
.hero .t,h2,.rc .nm,.clock,.status,.tile .nm,.tile.sensor .val,.ring .tgt,.sheet .hd .nm,.wpill b,.big-num,.media .ttl{font-family:var(--display)}
header{display:flex;align-items:center;gap:10px;padding:22px 0 0}
header .sp{flex:1}
.iconbtn{width:52px;height:52px;border-radius:50%;display:grid;place-items:center;color:#fff;cursor:pointer;border:1px solid var(--line);background:var(--glass);backdrop-filter:blur(20px);-webkit-backdrop-filter:blur(20px);flex:none}
.iconbtn ha-icon{--mdc-icon-size:22px}
.eyebrow{font-size:13px;font-weight:600;letter-spacing:.2em;text-transform:uppercase;color:var(--muted);display:flex;align-items:center;gap:10px}
.badge{display:inline-grid;place-items:center;min-width:22px;height:22px;padding:0 6px;border-radius:999px;background:var(--glass-hi);font-size:11px;letter-spacing:0;color:#fff}
.top{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;padding-top:36px;flex-wrap:wrap}
.clock{font-variant-numeric:tabular-nums;font-weight:300;font-size:clamp(52px,7vw,76px);letter-spacing:-.02em;line-height:1;margin-top:8px}
.right{display:flex;align-items:center;gap:14px;flex-wrap:wrap}
.wpill{display:flex;align-items:center;gap:12px;height:56px;padding:0 22px 0 18px;border-radius:999px}
.wpill ha-icon{--mdc-icon-size:26px}
.wpill b{font-size:26px;font-weight:400}
.wpill span{color:var(--muted);font-size:16px}
.people{display:flex}
.av{width:52px;height:52px;border-radius:50%;display:grid;place-items:center;font-family:var(--display);font-weight:600;font-size:20px;color:var(--ink);border:2px solid rgba(255,255,255,.35);position:relative;margin-left:-12px;background-size:cover;background-position:center}
.av:first-child{margin-left:0}
.av i{position:absolute;right:-1px;bottom:-1px;width:14px;height:14px;border-radius:50%;border:2px solid #3a2238;background:#6b6b6b}
.av.home i{background:#34c759}
.statusrow{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;margin-top:36px;flex-wrap:wrap}
.status{font-size:clamp(30px,4vw,40px);font-weight:300;letter-spacing:-.01em;margin-top:8px}
.actions{display:flex;gap:12px;flex-wrap:wrap}
.pill{all:unset;cursor:pointer;height:46px;padding:0 20px;border-radius:999px;background:var(--glass-hi);font-weight:600;font-size:16px;display:inline-flex;align-items:center;gap:8px;border:1px solid var(--line);transition:transform .12s}
.pill:active{transform:scale(.96)}
.pill ha-icon{--mdc-icon-size:20px}
.pill.act,.pill.solid{background:var(--cream);color:var(--ink);border-color:transparent}
.sect{margin-top:30px}
.scenes{display:flex;gap:12px;overflow-x:auto;padding:14px 0 4px;scrollbar-width:none}
.scenes::-webkit-scrollbar{display:none}
.scene{flex:none;display:flex;align-items:center;gap:10px;height:52px;padding:0 24px 0 20px;border-radius:999px;cursor:pointer;font-weight:600;font-size:17px;transition:transform .12s,background .2s}
.scene ha-icon{--mdc-icon-size:20px}
.scene:active{transform:scale(.96)}
.scene.fired{background:var(--cream);color:var(--ink)}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:20px;margin-top:22px}
.rc{position:relative;min-width:0;border-radius:var(--r);padding:22px;min-height:250px;cursor:pointer;display:flex;flex-direction:column;transition:transform .15s,background .25s,color .25s}
.rc:active{transform:scale(.98)}
.rc.on{background:var(--cream);color:var(--ink);border-color:transparent;box-shadow:0 18px 40px -18px rgba(0,0,0,.45)}
.rc .row1{display:flex;justify-content:space-between;align-items:flex-start}
.ri{width:52px;height:52px;border-radius:50%;display:grid;place-items:center;background:var(--glass-hi)}
.ri ha-icon{--mdc-icon-size:26px}
.rc.on .ri{background:var(--amber);color:var(--ink)}
.pw{width:64px;height:64px;border-radius:50%;display:grid;place-items:center;background:var(--glass-hi);cursor:pointer;flex:none;transition:transform .12s}
.pw:active{transform:scale(.92)}
.pw ha-icon{--mdc-icon-size:28px}
.rc.on .pw,.tile.on .pw{background:rgba(42,34,31,.08);color:var(--ink)}
.rc .foot{margin-top:auto}
.rc .nm{font-size:22px;font-weight:500;letter-spacing:-.01em;line-height:1.15}
.rc .meta{font-size:16px;color:var(--muted);margin-top:4px}
.rc.on .meta{color:var(--ink-soft)}
.dots{display:flex;align-items:center;gap:6px;margin-top:12px;min-height:22px}
.dots i{width:10px;height:10px;border-radius:50%;background:rgba(255,255,255,.28)}
.rc.on .dots i{background:rgba(42,34,31,.16)}
.dots i.on,.rc.on .dots i.on{background:var(--amber)}
.dots .tmp{margin-left:auto;font-size:16px;font-variant-numeric:tabular-nums}
.rc .eyebrow{color:inherit;opacity:.7}
.rc.on .eyebrow{color:var(--ink)}
.big-num{font-size:46px;font-weight:300;letter-spacing:-.02em;line-height:1}
.big-txt{font-family:var(--display);font-size:30px;font-weight:300;line-height:1.15}
.media .ttl{font-size:22px;font-weight:500}
.media .art{color:var(--muted);font-size:15px;margin-top:2px}
.media .bar{height:6px;border-radius:3px;background:rgba(255,255,255,.2);margin-top:14px;overflow:hidden}
.media .bar i{display:block;height:100%;background:#fff;border-radius:3px}
.media .ctl{display:flex;justify-content:center;align-items:center;gap:22px;margin-top:12px}
.media .ctl span{cursor:pointer;display:grid;place-items:center}
.media .ctl .pp{width:52px;height:52px;border-radius:50%;background:var(--cream);color:var(--ink)}
.hero{padding:30px 2px 4px;display:flex;align-items:flex-end;justify-content:space-between;gap:16px;flex-wrap:wrap}
.hero .t{font-size:clamp(36px,5vw,52px);font-weight:400;letter-spacing:-.02em;line-height:1.05}
.hero .s{color:var(--muted);font-size:17px;margin-top:8px}
h2{font-size:13px;font-weight:600;letter-spacing:.2em;text-transform:uppercase;color:var(--muted);margin:32px 2px 0;font-family:inherit}
.tiles{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:18px;margin-top:16px}
.tile{position:relative;border-radius:26px;padding:18px;cursor:pointer;min-height:170px;display:flex;flex-direction:column;justify-content:space-between;transition:background .25s,color .25s,transform .12s;user-select:none;-webkit-user-select:none;touch-action:manipulation}
.tile:active{transform:scale(.97)}
.tile.big{grid-column:span 2}
.tile .row1{display:flex;justify-content:space-between;align-items:flex-start}
.tile .ic{width:48px;height:48px;border-radius:50%;display:grid;place-items:center;background:var(--glass-hi);transition:background .2s}
.tile .ic ha-icon{--mdc-icon-size:24px}
.tile .pw{width:54px;height:54px}
.tile .pw ha-icon{--mdc-icon-size:24px}
.tile .nm{font-weight:500;font-size:19px;line-height:1.2;margin-top:14px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.tile .st{font-size:15px;color:var(--muted);margin-top:3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.tile.on{background:var(--cream);color:var(--ink);border-color:transparent;box-shadow:0 18px 40px -20px rgba(0,0,0,.45)}
.tile.on .st,.tile.on .ex{color:var(--ink-soft)}
.tile.on .ic{background:var(--acc);color:var(--ink)}
.tile.sensor{cursor:default}
.tile.sensor .val{font-size:34px;font-weight:300;letter-spacing:-.02em;margin-top:6px}
.tile.na{opacity:.45}
.tile .ex{font-size:13px;color:var(--muted);margin-top:4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.dock{position:fixed;left:50%;bottom:22px;transform:translateX(-50%);z-index:5;display:flex;gap:6px;padding:7px;border-radius:999px;background:rgba(60,40,62,.55);backdrop-filter:blur(30px) saturate(150%);-webkit-backdrop-filter:blur(30px) saturate(150%);border:1px solid var(--line)}
.dock button{all:unset;cursor:pointer;display:flex;align-items:center;gap:10px;height:50px;padding:0 24px;border-radius:999px;font-size:18px;font-weight:500;color:var(--muted);transition:background .2s,color .2s}
.dock button.act{background:var(--cream);color:var(--ink)}
.dock ha-icon{--mdc-icon-size:22px}
.empty{color:var(--muted);padding:40px 4px}
.ov{position:fixed;inset:0;z-index:10;background:rgba(10,4,12,.45);backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);display:grid;place-items:center;animation:fade .18s}
@keyframes fade{from{opacity:0}}
@keyframes pop{from{transform:scale(.94);opacity:0}}
.sheet{width:min(400px,calc(100vw - 32px));max-height:calc(100vh - 32px);overflow:auto;border-radius:34px;padding:24px;background:var(--glass-strong);border:1px solid var(--line);animation:pop .22s cubic-bezier(.2,.9,.3,1.2)}
.sheet .hd{display:flex;align-items:center;gap:12px}
.sheet .hd .ic{width:48px;height:48px;border-radius:50%;display:grid;place-items:center;background:var(--acc,rgba(255,255,255,.15));color:var(--ink)}
.sheet .hd .nm{font-size:20px;font-weight:500;flex:1}
.sheet .hd .st{font-size:14px;color:var(--muted)}
.vslider{position:relative;width:124px;height:300px;margin:26px auto 10px;border-radius:38px;background:rgba(255,255,255,.14);overflow:hidden;cursor:ns-resize;touch-action:none}
.vslider .fill{position:absolute;left:0;right:0;bottom:0;background:var(--cream)}
.vslider .lbl{position:absolute;left:0;right:0;bottom:16px;text-align:center;font-weight:600;font-family:var(--display);font-size:18px;color:var(--ink)}
.row{display:flex;gap:10px;justify-content:center;margin-top:16px;flex-wrap:wrap}
.swatches{display:flex;gap:10px;justify-content:center;margin-top:14px}
.sw{width:32px;height:32px;border-radius:50%;cursor:pointer;border:2px solid rgba(255,255,255,.4)}
.ring{width:230px;height:230px;margin:22px auto 6px;border-radius:50%;display:grid;place-items:center;position:relative}
.ring .tgt{font-size:56px;font-weight:300;letter-spacing:-.03em}
.ring .cur{font-size:14px;color:var(--muted);text-align:center}
.round{all:unset;cursor:pointer;width:58px;height:58px;border-radius:50%;display:grid;place-items:center;background:rgba(255,255,255,.14);font-size:26px}
.attrs{margin-top:14px;font-size:13px;color:var(--muted);display:grid;grid-template-columns:auto 1fr;gap:6px 14px}
.attrs b{color:#fff;font-weight:500;text-align:right;overflow:hidden;text-overflow:ellipsis}
.more{all:unset;cursor:pointer;display:block;width:100%;text-align:center;margin-top:18px;color:var(--muted);font-size:14px}
.themes{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-top:12px}
.themes .th{height:64px;border-radius:18px;cursor:pointer;display:flex;align-items:flex-end;padding:8px 10px;font-size:12px;font-weight:600;border:2px solid transparent}
.themes .th.act{border-color:#fff}
.list{margin-top:12px;display:flex;flex-direction:column;gap:6px;max-height:36vh;overflow:auto}
.list .it{display:flex;align-items:center;gap:10px;padding:8px 8px 8px 12px;border-radius:16px;background:rgba(255,255,255,.06);font-size:15px}
.list .it span{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.list .it small{color:var(--muted)}
.list .pill{height:36px;font-size:14px;padding:0 14px}
.sub{font-size:12px;font-weight:600;color:var(--muted);margin:22px 2px 0;text-transform:uppercase;letter-spacing:.18em}
@media (max-width:720px){
  .wrap{padding:0 16px 116px}.top{padding-top:20px}.statusrow{margin-top:24px}
  .grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.rc{min-height:190px;padding:16px;border-radius:24px}
  .rc .nm{font-size:18px}.rc .meta{font-size:14px}.ri{width:44px;height:44px}.pw{width:50px;height:50px}
  .tiles{grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.tile{min-height:150px;padding:14px}.tile .nm{font-size:16px}.tile .pw{width:44px;height:44px}
  .wpill{height:48px;padding:0 16px}.wpill b{font-size:22px}.wpill span{display:none}.av,.iconbtn{width:46px;height:46px}
  .dock button{padding:0 16px;height:46px}.dock button span{display:none}.big-num{font-size:38px}.big-txt{font-size:24px}
}
`;

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
  }

  connectedCallback() {
    this._tick = setInterval(() => this._updateClock(), 15000);
    if (this._hass) this._connect();
  }
  disconnectedCallback() {
    clearInterval(this._tick);
    this._unsub?.then?.((u) => u()).catch(() => {});
    this._unsub = null;
    this._connecting = false;
  }
  set narrow(n) { this._narrow = n; }
  set hass(h) {
    this._hass = h;
    if (!this._connecting) this._connect();
    if (this._drag) return; // don't redraw under a finger
    this._render();
  }

  // ---------- data ----------
  // The home model is built server-side (discovery.py) and pushed on registry changes.
  async _connect() {
    this._connecting = true;
    try {
      this._model = await this._hass.callWS({ type: "pine3_view/home" });
      if (this._model.settings?.theme) this._theme = this._model.settings.theme;
      this._unsub = this._hass.connection.subscribeMessage((m) => { this._model = m; this._render(); }, { type: "pine3_view/subscribe" });
    } catch (err) {
      console.warn("Pine3 View: API indisponível, a usar descoberta local", err);
      this._model = null;
    }
    this._render();
  }

  _saveSettings(patch) {
    if (!this._model) return;
    Object.assign(this._model.settings, patch);
    this._hass.callWS({ type: "pine3_view/settings", ...patch }).catch((e) => console.error("Pine3 View", e));
  }

  _rooms() {
    if (this._model) {
      this._meta = {};
      const out = [];
      for (const r of this._model.rooms) {
        for (const d of r.devices) this._meta[d.id] = d;
        if (r.hidden) continue;
        const ents = r.devices.filter((d) => !d.hidden && this._s(d.id)).map((d) => d.id);
        const scenes = r.scenes.filter((i) => this._s(i));
        if (!ents.length && !scenes.length) continue;
        out.push({ ...r, ents: [...ents, ...scenes], count: ents.length });
      }
      return out;
    }
    return this._localRooms();
  }

  _localRooms() {
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
    for (const r of list) r.count = r.ents.filter((i) => dom(i) !== "scene").length;
    return list;
  }

  _s(id) { return this._hass.states[id]; }
  _name(id) { return this._meta?.[id]?.name || this._s(id)?.attributes.friendly_name || id; }
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
    const by = (dc) => [...(r[dc] || []), ...r.ents].find((i) => dom(i) === "sensor" && this._s(i)?.attributes.device_class === dc && Number.isFinite(Number(this._s(i).state)));
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
    else if (this._view === "rooms") body = this._roomsView(rooms);
    else if (this._view === "security") body = this._securityView(rooms);
    else body = this._homeView(rooms);

    const header = this._narrow || this._view === "room" ? `<header>
      ${this._narrow ? `<div class="iconbtn" data-act="menu"><ha-icon icon="mdi:menu"></ha-icon></div>` : ""}
      ${this._view === "room" ? `<div class="iconbtn" data-act="back"><ha-icon icon="mdi:chevron-left"></ha-icon></div>` : ""}
      <div class="sp"></div>
    </header>` : "";
    const nav = (v, icon, label) => `<button class="${this._view === v || (v === "rooms" && this._view === "room") ? "act" : ""}" data-nav="${v}"><ha-icon icon="${icon}"></ha-icon><span>${label}</span></button>`;
    const dock = `<nav class="dock">${nav("home", "mdi:home-outline", "Casa")}${nav("rooms", "mdi:view-grid-outline", "Divisões")}${nav("scenes", "mdi:creation-outline", "Cenas")}</nav>`;

    this.shadowRoot.innerHTML = `<style>${css}</style>
      <div class="bg" style="background:${THEMES[this._theme] || THEMES.ameixa}"></div>
      <div class="wrap">${header}${body}</div>${dock}${this._modalView()}`;
    this._bind(rooms);
  }

  _gear() { return `<div class="iconbtn" data-act="themes" title="Definições"><ha-icon icon="mdi:cog-outline"></ha-icon></div>`; }
  _plural(n, one, many) { return `${n} ${n === 1 ? one : many}`; }

  _topBar() {
    const h = this._hass, now = new Date();
    const date = now.toLocaleDateString("pt-PT", { weekday: "long", day: "numeric", month: "long" });
    const weather = this._model?.weather || Object.keys(h.states).find((i) => dom(i) === "weather");
    const w = weather && this._s(weather);
    const persons = (this._model?.persons || Object.keys(h.states).filter((i) => dom(i) === "person")).map((i) => this._s(i)).filter(Boolean).slice(0, 4);
    const tint = ["#f3a96b", "#f6cf6a", "#c9d3dc", "#b9e0c3"];
    const people = persons.length ? `<div class="people">${persons.map((p, k) => {
      const pic = p.attributes.entity_picture;
      const ini = (p.attributes.friendly_name || "?").trim()[0].toUpperCase();
      return `<div class="av ${p.state === "home" ? "home" : ""}" title="${esc(p.attributes.friendly_name)}" style="background-color:${tint[k % 4]};${pic ? `background-image:url('${esc(pic)}')` : ""}">${pic ? "" : esc(ini)}<i></i></div>`;
    }).join("")}</div>` : "";
    return `<div class="top">
      <div><div class="eyebrow">${esc(date)}</div><div class="clock">${now.toLocaleTimeString("pt-PT", { hour: "2-digit", minute: "2-digit" })}</div></div>
      <div class="right">
        ${w ? `<div class="wpill glass"><ha-icon icon="${WEATHER_ICON[w.state] || "mdi:weather-partly-cloudy"}"></ha-icon><b>${num(Math.round(w.attributes.temperature ?? 0))}°</b><span>${esc(WEATHER_PT[w.state] || w.state)}</span></div>` : ""}
        ${people}${this._gear()}
      </div></div>`;
  }

  _switchables(r) { return r.ents.filter((i) => ["light", "switch", "fan", "input_boolean"].includes(dom(i))); }
  _roomLights(r) { return this._switchables(r); }

  _roomCard(r) {
    const sw = this._switchables(r), on = sw.filter((i) => this._isOn(i));
    const c = this._roomClimate(r);
    const active = on.length > 0;
    const meta = sw.length ? (active ? `${on.length} de ${sw.length} ${sw.length === 1 ? "ligado" : "ligados"}` : "Tudo desligado") : this._plural(r.count, "dispositivo", "dispositivos");
    return `<div class="rc ${active ? "on" : "glass"}" data-room="${esc(r.id)}">
      <div class="row1"><div class="ri"><ha-icon icon="${r.icon || "mdi:home-outline"}"></ha-icon></div>
        ${sw.length ? `<div class="pw" data-roomlights="${esc(r.id)}" title="${active ? "Desligar tudo" : "Ligar luzes"}"><ha-icon icon="mdi:power"></ha-icon></div>` : ""}</div>
      <div class="foot"><div class="nm">${esc(r.name)}</div><div class="meta">${meta}</div>
        <div class="dots">${sw.slice(0, 8).map((i) => `<i class="${this._isOn(i) ? "on" : ""}"></i>`).join("")}${c.temp ? `<span class="tmp">${c.temp.replace(/,\d/, "").replace("°C", "°")}</span>` : ""}</div></div>
    </div>`;
  }

  _homeView(rooms) {
    const h = this._hass;
    const all = rooms.flatMap((r) => r.ents);
    const lights = all.filter((i) => dom(i) === "light");
    const lightsOn = lights.filter((i) => this._isOn(i));
    const status = lightsOn.length ? `${this._plural(lightsOn.length, "luz acesa", "luzes acesas")} em casa` : lights.length ? "Todas as luzes apagadas" : "Tudo tranquilo em casa";

    const scenes = (this._model ? this._model.scenes.filter((i) => this._s(i)) : Object.keys(h.states).filter((i) => dom(i) === "scene" && !h.entities?.[i]?.hidden)).slice(0, 12);
    const scenesHtml = scenes.length ? `<div class="sect"><div class="eyebrow">Cenas <span class="badge">${scenes.length}</span></div>
      <div class="scenes">${scenes.map((i) => `<div class="scene glass ${this._fired === i ? "fired" : ""}" data-scene="${i}"><ha-icon icon="${this._s(i).attributes.icon || "mdi:creation-outline"}"></ha-icon>${esc(this._name(i))}</div>`).join("")}</div></div>` : "";

    // Summary cards
    const cards = [];
    const temps = all.filter((i) => dom(i) === "sensor" && this._s(i).attributes.device_class === "temperature").map((i) => Number(this._s(i).state)).filter(Number.isFinite);
    const hums = rooms.flatMap((r) => r.humidity || []).map((i) => Number(this._s(i)?.state)).filter(Number.isFinite);
    const climate = all.find((i) => dom(i) === "climate");
    if (temps.length || climate) {
      const t = temps.length ? temps.reduce((a, b) => a + b, 0) / temps.length : this._s(climate).attributes.current_temperature;
      const heating = climate && this._isOn(climate);
      cards.push(`<div class="rc ${heating ? "on" : "glass"}" ${climate ? `data-open="${climate}"` : ""}>
        <div class="row1"><div class="ri" style="background:#34c759;color:#0d2a14"><ha-icon icon="mdi:thermometer"></ha-icon></div><div class="eyebrow" style="margin:14px 0 0 0;flex:1;padding-left:14px">Clima</div></div>
        <div class="foot"><div class="big-num">${t != null ? Math.round(t) : "–"}°</div><div class="meta">${hums.length ? `Humidade ${Math.round(hums.reduce((a, b) => a + b, 0) / hums.length)}%` : climate ? esc(this._stateText(climate)) : "Interior"}</div></div></div>`);
    }
    const secIds = all.filter((i) => dom(i) === "lock" || (dom(i) === "binary_sensor" && ["door", "window", "garage_door", "opening"].includes(this._s(i).attributes.device_class)));
    if (secIds.length) {
      const bad = secIds.filter((i) => (dom(i) === "lock" ? this._s(i).state === "unlocked" : this._s(i).state === "on"));
      cards.push(`<div class="rc glass" data-nav="security">
        <div class="row1"><div class="ri" style="${bad.length ? "background:#ff9f0a;color:#2a1500" : ""}"><ha-icon icon="mdi:shield-${bad.length ? "alert-outline" : "check-outline"}"></ha-icon></div><div class="eyebrow" style="margin:14px 0 0 0;flex:1;padding-left:14px">Segurança</div></div>
        <div class="foot"><div class="big-txt">${bad.length ? `${this._plural(bad.length, "item aberto", "itens abertos")}` : "Tudo seguro"}</div><div class="meta">${this._plural(secIds.length, "item monitorizado", "itens monitorizados")}</div></div></div>`);
    }
    const media = all.find((i) => dom(i) === "media_player" && this._s(i).state === "playing") || all.find((i) => dom(i) === "media_player" && this._s(i).state === "paused");
    if (media) {
      const a = this._s(media).attributes, roomName = rooms.find((r) => r.ents.includes(media))?.name;
      const pct = a.media_duration ? Math.min(100, ((a.media_position || 0) / a.media_duration) * 100) : 0;
      cards.push(`<div class="rc glass media" data-open="${media}" style="${a.entity_picture ? `background-image:linear-gradient(rgba(30,16,32,.55),rgba(30,16,32,.85)),url('${esc(a.entity_picture)}');background-size:cover;background-position:center` : ""}">
        <div class="eyebrow" style="color:#5ee08a;letter-spacing:.14em"><ha-icon icon="mdi:music-note" style="--mdc-icon-size:16px"></ha-icon>${esc(roomName ? `A tocar · ${roomName}` : "A tocar")}</div>
        <div class="foot"><div class="ttl">${esc(a.media_title || this._name(media))}</div><div class="art">${esc(a.media_artist || "")}</div>
          ${pct ? `<div class="bar"><i style="width:${pct}%"></i></div>` : ""}
          <div class="ctl"><span data-media="media_previous_track" data-ent-id="${media}"><ha-icon icon="mdi:skip-previous"></ha-icon></span><span class="pp" data-media="media_play_pause" data-ent-id="${media}"><ha-icon icon="mdi:${this._s(media).state === "playing" ? "pause" : "play"}"></ha-icon></span><span data-media="media_next_track" data-ent-id="${media}"><ha-icon icon="mdi:skip-next"></ha-icon></span></div></div></div>`);
    }

    return `${this._topBar()}
      <div class="statusrow"><div><div class="eyebrow">Estado da casa</div><div class="status">${status}</div></div>
        ${lights.length ? `<div class="actions"><button class="pill" data-all="on"><ha-icon icon="mdi:lightbulb-outline"></ha-icon>Acender tudo</button><button class="pill solid" data-all="off"><ha-icon icon="mdi:power"></ha-icon>Apagar tudo</button></div>` : ""}</div>
      ${scenesHtml}
      ${rooms.length ? `<div class="grid">${rooms.map((r) => this._roomCard(r)).join("")}${cards.join("")}</div>` : `<div class="empty">Ainda não há divisões com dispositivos. Atribui áreas aos teus dispositivos no Home Assistant.</div>`}`;
  }

  _roomsView(rooms) {
    return `<div class="hero"><div><div class="eyebrow">${this._plural(rooms.length, "divisão", "divisões")}</div><div class="t" style="margin-top:8px">Divisões</div></div>${this._gear()}</div>
      <div class="grid">${rooms.map((r) => this._roomCard(r)).join("")}</div>`;
  }

  _securityView(rooms) {
    const all = rooms.flatMap((r) => r.ents);
    const ids = all.filter((i) => dom(i) === "lock" || dom(i) === "camera" || (dom(i) === "binary_sensor" && ["door", "window", "garage_door", "opening", "motion", "smoke", "moisture", "gas"].includes(this._s(i).attributes.device_class)));
    return `<div class="hero"><div><div class="eyebrow">Segurança</div><div class="t" style="margin-top:8px">Portas, janelas e fechaduras</div></div>${this._gear()}</div>
      ${ids.length ? `<div class="tiles">${ids.map((i) => this._tile(i)).join("")}</div>` : `<div class="empty">Nada para monitorizar.</div>`}`;
  }

  _tile(id) {
    const s = this._s(id), d = dom(id), on = this._isOn(id);
    const sensor = d === "sensor";
    const big = d === "media_player" && s.state === "playing" && this._narrow === false;
    const power = ["light", "switch", "fan", "input_boolean"].includes(d);
    return `<div class="tile ${on ? "on" : "glass"} ${sensor ? "sensor" : ""} ${big ? "big" : ""} ${s.state === "unavailable" ? "na" : ""}" data-ent="${esc(id)}" style="--acc:${this._accent(id)}">
      <div class="row1"><div class="ic"><ha-icon icon="${this._icon(id)}"></ha-icon></div>${power ? `<div class="pw" data-pw="${esc(id)}"><ha-icon icon="mdi:power"></ha-icon></div>` : ""}</div>
      <div><div class="nm">${esc(this._name(id))}</div>${sensor ? `<div class="val">${esc(this._sensorVal(id))}</div>` : `<div class="st">${esc(this._stateText(id))}</div>`}${this._extras(id)}</div>
    </div>`;
  }

  _extras(id) {
    const ex = (this._meta?.[id]?.extras || []).filter((e) => this._s(e) && this._s(e).state !== "unavailable");
    if (!ex.length) return "";
    const txt = ex.map((e) => {
      const dc = this._s(e).attributes.device_class;
      if (dom(e) === "binary_sensor") return dc === "battery" ? (this._s(e).state === "on" ? "Bateria fraca" : "") : this._stateText(e);
      return this._sensorVal(e) + (dc === "battery" ? " 🔋" : "");
    }).filter(Boolean);
    return txt.length ? `<div class="ex">${esc(txt.join(" · "))}</div>` : "";
  }

  _roomView(room) {
    const c = this._roomClimate(room);
    const sub = [c.temp, c.hum].filter(Boolean).join(" · ");
    const sections = SECTIONS.map(([title, domains]) => {
      const ents = room.ents.filter((i) => domains.includes(dom(i)));
      return ents.length ? `<h2>${title}</h2><div class="tiles">${ents.map((i) => this._tile(i)).join("")}</div>` : "";
    }).join("");
    const scenes = room.ents.filter((i) => dom(i) === "scene");
    const sc = scenes.length ? `<h2>Cenas</h2><div class="scenes">${scenes.map((i) => `<div class="scene glass ${this._fired === i ? "fired" : ""}" data-scene="${i}"><ha-icon icon="${this._s(i).attributes.icon || "mdi:creation-outline"}"></ha-icon>${esc(this._name(i))}</div>`).join("")}</div>` : "";
    return `<div class="hero"><div><div class="eyebrow">${this._plural(room.count, "dispositivo", "dispositivos")}</div><div class="t" style="margin-top:8px">${esc(room.name)}</div>${sub ? `<div class="s">${sub}</div>` : ""}</div>${this._gear()}</div>${sc}${sections}`;
  }

  _scenesView() {
    const h = this._hass;
    const list = (d) => this._model ? (d === "scene" ? this._model.scenes : this._model.scripts).filter((i) => this._s(i)) : Object.keys(h.states).filter((i) => dom(i) === d && !h.entities?.[i]?.hidden);
    const sc = list("scene"), sp = list("script");
    const grid = (ids) => `<div class="tiles">${ids.map((i) => this._tile(i)).join("")}</div>`;
    return `<div class="hero"><div><div class="eyebrow">Toca para executar</div><div class="t" style="margin-top:8px">Cenas e scripts</div></div>${this._gear()}</div>
      ${sc.length ? `<h2>Cenas</h2>${grid(sc)}` : ""}${sp.length ? `<h2>Scripts</h2>${grid(sp)}` : ""}
      ${!sc.length && !sp.length ? `<div class="empty">Não há cenas nem scripts.</div>` : ""}`;
  }

  _modalView() {
    if (this._modal === "_themes") {
      let extra = "";
      if (this._model) {
        const rooms = this._model.rooms;
        const hiddenDev = rooms.flatMap((r) => r.devices.filter((d) => d.hidden).map((d) => ({ ...d, room: r.name })));
        extra = `<div class="sub">Divisões</div><div class="list">${rooms.map((r) => `<div class="it"><ha-icon icon="${r.icon}"></ha-icon><span>${esc(r.name)} <small>${r.devices.length}</small></span>
            <button class="pill ${r.hidden ? "" : "act"}" data-roomvis="${esc(r.id)}">${r.hidden ? "Escondida" : "Visível"}</button></div>`).join("")}</div>
          ${hiddenDev.length ? `<div class="sub">Dispositivos escondidos</div><div class="list">${hiddenDev.map((d) => `<div class="it"><span>${esc(d.name)} <small>${esc(d.room)}</small></span><button class="pill" data-unhide="${esc(d.id)}">Mostrar</button></div>`).join("")}</div>` : ""}`;
      }
      return `<div class="ov" data-close><div class="sheet"><div class="hd"><div class="nm">Definições</div></div>
        <div class="sub">Fundo</div>
        <div class="themes">${Object.keys(THEMES).map((k) => `<div class="th ${k === this._theme ? "act" : ""}" data-theme="${k}" style="background:${THEMES[k]}">${THEME_NAMES[k]}</div>`).join("")}</div>${extra}</div></div>`;
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
      ${content}<button class="more" data-more>Mais definições</button>${this._meta?.[id] ? `<button class="more" data-hide style="margin-top:6px">Esconder do Pine3 View</button>` : ""}</div></div>`;
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
      const sw = this._switchables(r), onNow = sw.filter((i) => this._isOn(i));
      if (onNow.length) this._call("homeassistant", "turn_off", { entity_id: onNow });
      else this._call("homeassistant", "turn_on", { entity_id: sw.filter((i) => dom(i) === "light").length ? sw.filter((i) => dom(i) === "light") : sw });
    });
    on("[data-all]", (el) => {
      const lights = this._rooms().flatMap((r) => r.ents).filter((i) => dom(i) === "light");
      if (lights.length) this._call("light", el.dataset.all === "on" ? "turn_on" : "turn_off", { entity_id: lights });
    });
    on("[data-pw]", (el, ev) => { ev.stopPropagation(); this._call("homeassistant", "toggle", { entity_id: el.dataset.pw }); });
    on("[data-open]", (el) => this._openModal(el.dataset.open));
    on("[data-media]", (el, ev) => { ev.stopPropagation(); this._call("media_player", el.dataset.media, { entity_id: el.dataset.entId }); });
    on("[data-scene]", (el) => {
      this._call("scene", "turn_on", { entity_id: el.dataset.scene });
      this._fired = el.dataset.scene; this._render();
      setTimeout(() => { this._fired = null; this._render(); }, 1200);
    });
    on("[data-theme]", (el) => { this._theme = el.dataset.theme; store.set("theme", this._theme); this._saveSettings({ theme: this._theme }); this._render(); });
    on("[data-roomvis]", (el) => {
      const set = new Set(this._model.settings.hidden_rooms || []), id = el.dataset.roomvis;
      set.has(id) ? set.delete(id) : set.add(id);
      this._model.rooms.forEach((r) => { if (r.id === id) r.hidden = set.has(id); });
      this._saveSettings({ hidden_rooms: [...set] }); this._render();
    });
    on("[data-unhide]", (el) => {
      const id = el.dataset.unhide;
      this._model.rooms.forEach((r) => r.devices.forEach((d) => { if (d.id === id) d.hidden = false; }));
      this._saveSettings({ hidden: (this._model.settings.hidden || []).filter((x) => x !== id) }); this._render();
    });

    // tiles: tap = action, long-press / right-click = details
    root.querySelectorAll("[data-ent]").forEach((el) => {
      const id = el.dataset.ent;
      let timer, long = false;
      const start = () => { long = false; timer = setTimeout(() => { long = true; if (DETAIL.has(dom(id)) || this._meta?.[id]) this._openModal(id); }, 450); };
      const cancel = () => clearTimeout(timer);
      el.addEventListener("pointerdown", start);
      el.addEventListener("pointerup", cancel);
      el.addEventListener("pointerleave", cancel);
      el.addEventListener("contextmenu", (ev) => { ev.preventDefault(); cancel(); this._openModal(id); });
      el.addEventListener("click", () => {
        if (long) return;
        if (el.querySelector("[data-pw]") && DETAIL.has(dom(id))) this._openModal(id);
        else this._tap(id);
      });
    });

    // modal
    const ov = root.querySelector(".ov");
    if (!ov) return;
    ov.addEventListener("click", (ev) => { if (ev.target === ov) { this._modal = null; this._render(); } });
    const id = this._modal;
    on("[data-toggle]", () => this._tap(id));
    on("[data-more]", () => { this._modal = null; this._render(); this._moreInfo(id); });
    on("[data-hide]", () => {
      this._model.rooms.forEach((r) => r.devices.forEach((d) => { if (d.id === id) d.hidden = true; }));
      this._saveSettings({ hidden: [...new Set([...(this._model.settings.hidden || []), id])] });
      this._modal = null; this._render();
    });
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
