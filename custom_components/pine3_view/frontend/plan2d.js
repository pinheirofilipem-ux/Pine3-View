// Pine3 View - 2D floor-plan editor (top view). Rooms are rectangles on a grid, one floor at a time.
// The saved layout ({room: {f, x, y, w, d}} in grid units) drives the 3D Home.

import { esc } from "./const.js";
import { t } from "./i18n.js";
import { pack, autoSize } from "./house3d.js";

export const COLS = 24;
export const ROWS = 16;
export const plan = { floor: null, armed: null, selected: null, extraFloors: [] };

const roomLevel = (r, layout) => layout[r.id]?.f ?? r.floor_level ?? 0;

export function floorLevels(rooms, layout) {
  return [...new Set([...rooms.map((r) => roomLevel(r, layout)), ...plan.extraFloors])].sort((a, b) => a - b);
}

export function floorName(rooms, lv) {
  return rooms.find((r) => (r.floor_level ?? 0) === lv && r.floor)?.floor || t("Piso {n}", { n: lv });
}

const overlaps = (a, b) => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.d && b.y < a.y + a.d;

/** Auto-generate a layout for every room from the automatic packing used by the 3D view. */
export function autoLayout(rooms, sizes) {
  const out = {};
  const levels = [...new Set(rooms.map((r) => r.floor_level ?? 0))];
  for (const lv of levels) {
    const { items } = pack(rooms.filter((r) => (r.floor_level ?? 0) === lv), sizes);
    for (const b of items) out[b.room.id] = { f: lv, x: Math.min(b.x, COLS - 1), y: Math.min(b.y, ROWS - 1), w: Math.min(b.w, COLS), d: Math.min(b.d, ROWS) };
  }
  return out;
}

export function planView(p, d, rooms) {
  const layout = d.house_layout || {};
  const levels = floorLevels(rooms, layout);
  if (plan.floor == null || !levels.includes(plan.floor)) plan.floor = levels[0] ?? 0;
  const lv = plan.floor;
  const here = rooms.filter((r) => layout[r.id] && layout[r.id].f === lv);
  const unplaced = rooms.filter((r) => !layout[r.id]);
  const sel = plan.selected && layout[plan.selected] ? rooms.find((r) => r.id === plan.selected) : null;

  const rects = here.map((r) => {
    const L = layout[r.id];
    const bad = here.some((o) => o.id !== r.id && overlaps(L, layout[o.id]));
    return `<div class="pr ${plan.selected === r.id ? "sel" : ""} ${bad ? "bad" : ""}" data-pr="${esc(r.id)}"
        style="left:${(L.x / COLS) * 100}%;top:${(L.y / ROWS) * 100}%;width:${(L.w / COLS) * 100}%;height:${(L.d / ROWS) * 100}%">
      <span class="pn"><ha-icon icon="${r.icon || "mdi:home-outline"}"></ha-icon>${esc(p._roomName(r))}</span>
      <small class="pd">${L.w}×${L.d}</small>
      <i class="rh" data-rh="${esc(r.id)}"></i></div>`;
  }).join("");

  return `<div class="panel glass">
      <h3 style="margin-top:0">${t("Planta da casa")}</h3>
      <p>${t("Desenha cada piso visto de cima. A Casa 3D usa esta planta: as divisões que não colocares são arrumadas automaticamente.")}</p>
      <div class="seg">${levels.map((l) => `<button class="pill ${l === lv ? "solid" : ""}" data-pfloor="${l}">${esc(floorName(rooms, l))}</button>`).join("")}
        <button class="pill" data-pfloor-add><ha-icon icon="mdi:plus"></ha-icon>${t("Piso")}</button></div>
      <div class="pgrid" data-pgrid style="--cols:${COLS};--rows:${ROWS}">${rects}<div class="pdraw" hidden></div></div>
      <div class="phint">${plan.armed
        ? t("Desenha um retângulo na grelha para colocar «{r}».", { r: esc(p._roomName(rooms.find((r) => r.id === plan.armed) || { name: "" })) })
        : t("Arrasta uma divisão para a mover e o canto para a redimensionar. Para colocar uma divisão nova, escolhe-a em baixo e desenha-a na grelha.")}</div>
      ${sel ? `<div class="psel">
          <b><ha-icon icon="${sel.icon || "mdi:home-outline"}"></ha-icon>${esc(p._roomName(sel))}</b>
          <span class="seg sm">${levels.map((l) => `<button class="pill ${layout[sel.id].f === l ? "solid" : ""}" data-pmove="${l}">${esc(floorName(rooms, l))}</button>`).join("")}</span>
          <button class="pill" data-premove><ha-icon icon="mdi:close"></ha-icon>${t("Tirar da planta")}</button>
        </div>` : ""}
      ${unplaced.length ? `<div class="sub">${t("Por colocar")}</div><div class="seg">${unplaced.map((r) =>
        `<button class="pill ${plan.armed === r.id ? "solid" : ""}" data-parm="${esc(r.id)}"><ha-icon icon="${r.icon || "mdi:home-outline"}"></ha-icon>${esc(p._roomName(r))}</button>`).join("")}</div>` : ""}
      <div class="row" style="justify-content:flex-start;margin-top:20px">
        <button class="pill" data-pauto><ha-icon icon="mdi:auto-fix"></ha-icon>${t("Gerar planta automática")}</button>
        <button class="pill" data-pclear><ha-icon icon="mdi:eraser"></ha-icon>${t("Limpar este piso")}</button>
      </div>
    </div>`;
}

export function bindPlan(p, root, d, rooms) {
  const grid = root.querySelector("[data-pgrid]");
  if (!grid) return;
  const layout = () => (d.house_layout = { ...(d.house_layout || {}) });
  const upd = () => p._render();
  const unit = (ev) => {
    const r = grid.getBoundingClientRect();
    return { x: ((ev.clientX - r.left) / r.width) * COLS, y: ((ev.clientY - r.top) / r.height) * ROWS };
  };
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  const on = (sel, fn) => root.querySelectorAll(sel).forEach((el) => el.addEventListener("click", (ev) => fn(el, ev)));

  on("[data-pfloor]", (el) => { plan.floor = Number(el.dataset.pfloor); plan.selected = null; upd(); });
  on("[data-pfloor-add]", () => {
    const lv = Math.max(...floorLevels(rooms, d.house_layout || {}), -1) + 1;
    plan.extraFloors = [...new Set([...plan.extraFloors, lv])];
    plan.floor = lv; plan.selected = null; upd();
  });
  on("[data-parm]", (el) => { plan.armed = plan.armed === el.dataset.parm ? null : el.dataset.parm; plan.selected = null; upd(); });
  on("[data-pmove]", (el) => { const L = layout(); L[plan.selected] = { ...L[plan.selected], f: Number(el.dataset.pmove) }; plan.floor = Number(el.dataset.pmove); upd(); });
  on("[data-premove]", () => { const L = layout(); delete L[plan.selected]; plan.selected = null; upd(); });
  on("[data-pauto]", () => { d.house_layout = autoLayout(rooms, d.house_size || {}); plan.selected = null; plan.armed = null; upd(); });
  on("[data-pclear]", () => {
    const L = layout();
    for (const id of Object.keys(L)) if (L[id].f === plan.floor) delete L[id];
    plan.selected = null; upd();
  });

  grid.addEventListener("pointerdown", (ev) => {
    ev.preventDefault();
    const start = unit(ev);
    const rh = ev.target.closest("[data-rh]"), pr = ev.target.closest("[data-pr]");
    const L = d.house_layout || {};
    let mode, id, orig, box, el;
    if (rh) { mode = "resize"; id = rh.dataset.rh; }
    else if (pr) { mode = "move"; id = pr.dataset.pr; }
    else if (plan.armed) { mode = "draw"; id = plan.armed; }
    else { plan.selected = null; upd(); return; }
    if (mode !== "draw") { orig = { ...L[id] }; el = grid.querySelector(`[data-pr="${CSS.escape(id)}"]`); plan.selected = id; el.classList.add("sel", "drag"); }
    else { el = grid.querySelector(".pdraw"); el.hidden = false; }
    p._drag = true;
    const place = (b) => {
      box = b;
      el.style.left = (b.x / COLS) * 100 + "%"; el.style.top = (b.y / ROWS) * 100 + "%";
      el.style.width = (b.w / COLS) * 100 + "%"; el.style.height = (b.d / ROWS) * 100 + "%";
      const dim = el.querySelector(".pd"); if (dim) dim.textContent = `${b.w}×${b.d}`;
    };
    const move = (mv) => {
      const u = unit(mv);
      if (mode === "move") {
        place({ ...orig, x: clamp(Math.round(orig.x + u.x - start.x), 0, COLS - orig.w), y: clamp(Math.round(orig.y + u.y - start.y), 0, ROWS - orig.d) });
      } else if (mode === "resize") {
        place({ ...orig, w: clamp(Math.round(orig.w + u.x - start.x), 1, COLS - orig.x), d: clamp(Math.round(orig.d + u.y - start.y), 1, ROWS - orig.y) });
      } else {
        const x0 = clamp(Math.floor(Math.min(start.x, u.x)), 0, COLS - 1), y0 = clamp(Math.floor(Math.min(start.y, u.y)), 0, ROWS - 1);
        const x1 = clamp(Math.ceil(Math.max(start.x, u.x)), x0 + 1, COLS), y1 = clamp(Math.ceil(Math.max(start.y, u.y)), y0 + 1, ROWS);
        place({ f: plan.floor, x: x0, y: y0, w: x1 - x0, d: y1 - y0 });
      }
    };
    const up = () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
      window.removeEventListener("pointercancel", up);
      p._drag = null;
      if (box) {
        const Lw = layout();
        if (mode === "draw") {
          // a plain tap places a default-size room at the tapped cell
          if (box.w * box.d < 2) box = { ...box, ...{ w: Math.min(4, COLS - box.x), d: Math.min(3, ROWS - box.y) } };
          Lw[id] = box; plan.armed = null; plan.selected = id;
        } else Lw[id] = box;
      } else if (mode === "draw") {
        const x = clamp(Math.floor(start.x), 0, COLS - 4), y = clamp(Math.floor(start.y), 0, ROWS - 3);
        const sz = { 1: [3, 3], 2: [4, 3], 3: [5, 4] }[d.house_size?.[id] || autoSize(rooms.find((r) => r.id === id) || {})];
        layout()[id] = { f: plan.floor, x, y, w: sz[0], d: sz[1] };
        plan.armed = null; plan.selected = id;
      }
      upd();
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
    window.addEventListener("pointercancel", up);
  });
}

export const cssPlan = `
.pgrid{position:relative;margin-top:16px;width:100%;aspect-ratio:var(--cols)/var(--rows);border-radius:18px;touch-action:none;user-select:none;-webkit-user-select:none;cursor:crosshair;
  background-color:rgba(255,255,255,.04);border:1px solid var(--line);
  background-image:linear-gradient(rgba(255,255,255,.07) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.07) 1px,transparent 1px);
  background-size:calc(100% / var(--cols)) calc(100% / var(--rows))}
.pr{position:absolute;box-sizing:border-box;border-radius:10px;background:color-mix(in srgb,var(--cream) 88%,transparent);color:var(--ink);border:2px solid transparent;cursor:grab;overflow:hidden;padding:6px 8px;display:flex;flex-direction:column;justify-content:space-between}
.pr.sel{border-color:var(--amber);box-shadow:0 0 0 3px color-mix(in srgb,var(--amber) 35%,transparent)}
.pr.drag{cursor:grabbing;opacity:.9;z-index:2}
.pr.bad{border-color:#ff453a;border-style:dashed}
.pn{display:flex;align-items:center;gap:6px;font-weight:600;font-size:14px;line-height:1.1;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.pn ha-icon{--mdc-icon-size:16px;flex:none}
.pd{font-size:11px;color:var(--ink-soft)}
.rh{position:absolute;right:0;bottom:0;width:22px;height:22px;cursor:nwse-resize;background:linear-gradient(135deg,transparent 50%,var(--amber) 50%);border-radius:0 0 8px 0}
.pdraw{position:absolute;border:2px dashed var(--amber);border-radius:10px;background:color-mix(in srgb,var(--amber) 20%,transparent);pointer-events:none}
.phint{color:var(--muted);font-size:14px;margin-top:10px}
.psel{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-top:14px;padding:12px 14px;border-radius:18px;background:rgba(255,255,255,.06)}
.psel b{display:flex;align-items:center;gap:8px;font-size:16px}
.psel .seg.sm{flex-wrap:wrap}
.psel .pill{height:36px;font-size:14px;padding:0 14px}
@media (max-width:720px){.pn{font-size:11px}.pn ha-icon{display:none}.pd{display:none}.rh{width:16px;height:16px}}
`;
