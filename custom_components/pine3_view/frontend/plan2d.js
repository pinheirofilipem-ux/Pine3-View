// Pine3 View - 2D floor-plan editor (top view). One floor at a time, on a grid.
// A room is either a rectangle {f, x, y, w, d} or a free shape made of grid cells
// {f, x, y, w, d, cells: [[x, y], ...]} (x/y/w/d is then the bounding box), which allows
// L, U, T and any other right-angled shape. The saved layout drives the 3D Home.

import { esc } from "./const.js";
import { t } from "./i18n.js";
import { pack, autoSize } from "./house3d.js";

export const COLS = 24;
export const ROWS = 16;
export const plan = { floor: null, armed: null, selected: null, extraFloors: [], tool: "move", brush: null };

const roomLevel = (r, layout) => layout[r.id]?.f ?? r.floor_level ?? 0;
const key = (x, y) => `${x},${y}`;

/** Cells covered by a layout entry (rectangles are expanded). */
export function cellsOf(L) {
  if (L.cells) return L.cells;
  const out = [];
  for (let y = L.y; y < L.y + L.d; y++) for (let x = L.x; x < L.x + L.w; x++) out.push([x, y]);
  return out;
}

/** Layout entry from a set of cells; a full rectangle is stored as a plain rectangle. */
export function fromCells(f, cells) {
  if (!cells.length) return null;
  const xs = cells.map((c) => c[0]), ys = cells.map((c) => c[1]);
  const x = Math.min(...xs), y = Math.min(...ys), w = Math.max(...xs) - x + 1, d = Math.max(...ys) - y + 1;
  const uniq = [...new Map(cells.map((c) => [key(c[0], c[1]), c])).values()].sort((a, b) => a[1] - b[1] || a[0] - b[0]);
  return uniq.length === w * d ? { f, x, y, w, d } : { f, x, y, w, d, cells: uniq };
}

export function floorLevels(rooms, layout) {
  return [...new Set([...rooms.map((r) => roomLevel(r, layout)), ...plan.extraFloors])].sort((a, b) => a - b);
}

export function floorName(rooms, lv) {
  return rooms.find((r) => (r.floor_level ?? 0) === lv && r.floor)?.floor || t("Piso {n}", { n: lv });
}

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

const pct = (v, n) => `${(v / n) * 100}%`;

function roomHtml(p, r, L, sel, clash) {
  const name = `<span class="pn"><ha-icon icon="${r.icon || "mdi:home-outline"}"></ha-icon>${esc(p._roomName(r))}</span>`;
  const box = `left:${pct(L.x, COLS)};top:${pct(L.y, ROWS)};width:${pct(L.w, COLS)};height:${pct(L.d, ROWS)}`;
  if (!L.cells) {
    return `<div class="pr ${sel ? "sel" : ""} ${clash ? "bad" : ""}" data-pr="${esc(r.id)}" style="${box}">
      ${name}<small class="pd">${L.w}×${L.d}</small><i class="rh" data-rh="${esc(r.id)}"></i></div>`;
  }
  // free shape: one tile per cell, borders only where the shape ends
  const set = new Set(L.cells.map((c) => key(c[0], c[1])));
  const tiles = L.cells.map(([x, y]) => {
    const b = [!set.has(key(x, y - 1)), !set.has(key(x + 1, y)), !set.has(key(x, y + 1)), !set.has(key(x - 1, y))];
    const rad = [b[0] && b[3], b[0] && b[1], b[2] && b[1], b[2] && b[3]].map((on) => (on ? "8px" : "0")).join(" ");
    return `<i class="pc" style="left:${pct(x - L.x, L.w)};top:${pct(y - L.y, L.d)};width:${pct(1, L.w)};height:${pct(1, L.d)};border-width:${b.map((on) => (on ? "2px" : "0")).join(" ")};border-radius:${rad}"></i>`;
  }).join("");
  const [lx, ly] = L.cells[0];
  return `<div class="pr shape ${sel ? "sel" : ""} ${clash ? "bad" : ""}" data-pr="${esc(r.id)}" style="${box}">
    ${tiles}<span class="pnw" style="left:${pct(lx - L.x, L.w)};top:${pct(ly - L.y, L.d)}">${name}</span></div>`;
}

export function planView(p, d, rooms) {
  const layout = d.house_layout || {};
  const levels = floorLevels(rooms, layout);
  if (plan.floor == null || !levels.includes(plan.floor)) plan.floor = levels[0] ?? 0;
  const lv = plan.floor;
  const here = rooms.filter((r) => layout[r.id] && layout[r.id].f === lv);
  const unplaced = rooms.filter((r) => !layout[r.id]);
  const sel = plan.selected && layout[plan.selected] ? rooms.find((r) => r.id === plan.selected) : null;
  if (plan.brush && !rooms.some((r) => r.id === plan.brush)) plan.brush = null;

  // cells claimed by more than one room are flagged
  const owner = new Map(), clash = new Set();
  for (const r of here) for (const [x, y] of cellsOf(layout[r.id])) {
    const k = key(x, y);
    if (owner.has(k)) { clash.add(r.id); clash.add(owner.get(k)); } else owner.set(k, r.id);
  }
  const rects = here.map((r) => roomHtml(p, r, layout[r.id], plan.selected === r.id, clash.has(r.id))).join("");

  const tools = [["move", "mdi:cursor-move", t("Mover e redimensionar")], ["paint", "mdi:brush", t("Pintar quadrículas")], ["erase", "mdi:eraser", t("Apagar quadrículas")]];
  const brushRooms = [...here, ...unplaced];
  const brushName = plan.brush ? p._roomName(rooms.find((r) => r.id === plan.brush)) : "";
  const hint = plan.tool === "paint"
    ? (plan.brush ? t("Toca ou arrasta nas quadrículas para as juntar a «{r}». Pintar por cima de outra divisão passa essas quadrículas para esta.", { r: esc(brushName) })
      : t("Escolhe em baixo a divisão a pintar."))
    : plan.tool === "erase" ? t("Toca ou arrasta nas quadrículas para as tirar da divisão.")
      : plan.armed ? t("Desenha um retângulo na grelha para colocar «{r}».", { r: esc(p._roomName(rooms.find((r) => r.id === plan.armed) || { name: "" })) })
        : t("Arrasta uma divisão para a mover e o canto para a redimensionar. Para colocar uma divisão nova, escolhe-a em baixo e desenha-a na grelha.");

  return `<div class="panel glass">
      <h3 style="margin-top:0">${t("Planta da casa")}</h3>
      <p>${t("Desenha cada piso visto de cima. A Casa 3D usa esta planta: as divisões que não colocares são arrumadas automaticamente.")}</p>
      <div class="seg">${levels.map((l) => `<button class="pill ${l === lv ? "solid" : ""}" data-pfloor="${l}">${esc(floorName(rooms, l))}</button>`).join("")}
        <button class="pill" data-pfloor-add><ha-icon icon="mdi:plus"></ha-icon>${t("Piso")}</button></div>
      <div class="seg ptools">${tools.map(([k, icon, label]) => `<button class="pill ${plan.tool === k ? "solid" : ""}" data-ptool="${k}"><ha-icon icon="${icon}"></ha-icon>${label}</button>`).join("")}</div>
      ${plan.tool === "paint" ? `<div class="seg">${brushRooms.map((r) => `<button class="pill ${plan.brush === r.id ? "solid" : ""}" data-pbrush="${esc(r.id)}"><ha-icon icon="${r.icon || "mdi:home-outline"}"></ha-icon>${esc(p._roomName(r))}</button>`).join("")}</div>` : ""}
      <div class="pgrid tool-${plan.tool}" data-pgrid style="--cols:${COLS};--rows:${ROWS}">${rects}<div class="pdraw" hidden></div></div>
      <div class="phint">${hint}</div>
      ${sel && plan.tool === "move" ? `<div class="psel">
          <b><ha-icon icon="${sel.icon || "mdi:home-outline"}"></ha-icon>${esc(p._roomName(sel))}</b>
          <span class="seg sm">${levels.map((l) => `<button class="pill ${layout[sel.id].f === l ? "solid" : ""}" data-pmove="${l}">${esc(floorName(rooms, l))}</button>`).join("")}</span>
          <button class="pill" data-pshape><ha-icon icon="mdi:brush"></ha-icon>${t("Editar forma")}</button>
          <button class="pill" data-premove><ha-icon icon="mdi:close"></ha-icon>${t("Tirar da planta")}</button>
        </div>` : ""}
      ${unplaced.length && plan.tool === "move" ? `<div class="sub">${t("Por colocar")}</div><div class="seg">${unplaced.map((r) =>
        `<button class="pill ${plan.armed === r.id ? "solid" : ""}" data-parm="${esc(r.id)}"><ha-icon icon="${r.icon || "mdi:home-outline"}"></ha-icon>${esc(p._roomName(r))}</button>`).join("")}</div>` : ""}
      <div class="row" style="justify-content:flex-start;margin-top:20px">
        <button class="pill" data-pauto><ha-icon icon="mdi:auto-fix"></ha-icon>${t("Gerar planta automática")}</button>
        <button class="pill" data-pclear><ha-icon icon="mdi:eraser-variant"></ha-icon>${t("Limpar este piso")}</button>
      </div>
    </div>`;
}

export function bindPlan(p, root, d, rooms) {
  if (!root.querySelector("[data-pgrid]")) return;
  const layout = () => (d.house_layout = { ...(d.house_layout || {}) });
  const upd = () => p._render();
  const gridEl = () => root.querySelector("[data-pgrid]");
  const unit = (ev) => {
    const r = gridEl().getBoundingClientRect();
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
  on("[data-ptool]", (el) => {
    plan.tool = el.dataset.ptool; plan.armed = null;
    if (plan.tool === "paint" && !plan.brush) plan.brush = plan.selected;
    upd();
  });
  on("[data-pbrush]", (el) => { plan.brush = el.dataset.pbrush; upd(); });
  on("[data-pshape]", () => { plan.tool = "paint"; plan.brush = plan.selected; upd(); });
  on("[data-parm]", (el) => { plan.armed = plan.armed === el.dataset.parm ? null : el.dataset.parm; plan.selected = null; upd(); });
  on("[data-pmove]", (el) => { const L = layout(); L[plan.selected] = { ...L[plan.selected], f: Number(el.dataset.pmove) }; plan.floor = Number(el.dataset.pmove); upd(); });
  on("[data-premove]", () => { const L = layout(); delete L[plan.selected]; plan.selected = null; upd(); });
  on("[data-pauto]", () => { d.house_layout = autoLayout(rooms, d.house_size || {}); plan.selected = null; plan.armed = null; upd(); });
  on("[data-pclear]", () => {
    const L = layout();
    for (const id of Object.keys(L)) if (L[id].f === plan.floor) delete L[id];
    plan.selected = null; upd();
  });

  gridEl().addEventListener("pointerdown", (ev) => {
    ev.preventDefault();
    if (plan.tool === "paint" || plan.tool === "erase") return paintStroke(ev);
    const start = unit(ev);
    const grid = gridEl();
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
      el.style.left = pct(b.x, COLS); el.style.top = pct(b.y, ROWS);
      el.style.width = pct(b.w, COLS); el.style.height = pct(b.d, ROWS);
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
          if (box.w * box.d < 2) box = { ...box, w: Math.min(4, COLS - box.x), d: Math.min(3, ROWS - box.y) };
          Lw[id] = box; plan.armed = null; plan.selected = id;
        } else if (orig.cells) {
          // free shapes move as a whole
          const dx = box.x - orig.x, dy = box.y - orig.y;
          Lw[id] = { ...box, cells: orig.cells.map(([x, y]) => [x + dx, y + dy]) };
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

  // Paint / erase: every cell the pointer crosses is added to the brush room (taken away from
  // whichever room had it) or removed from its room.
  function paintStroke(ev) {
    if (plan.tool === "paint" && !plan.brush) return;
    const done = new Set();
    p._drag = true;
    const apply = (e) => {
      const u = unit(e);
      const cx = Math.floor(u.x), cy = Math.floor(u.y);
      if (cx < 0 || cy < 0 || cx >= COLS || cy >= ROWS || done.has(key(cx, cy))) return;
      done.add(key(cx, cy));
      const L = layout(), lv = plan.floor;
      for (const id of Object.keys(L)) {
        if (L[id].f !== lv || (plan.tool === "paint" && id === plan.brush)) continue;
        const cells = cellsOf(L[id]);
        if (!cells.some(([x, y]) => x === cx && y === cy)) continue;
        const next = fromCells(lv, cells.filter(([x, y]) => x !== cx || y !== cy));
        if (next) L[id] = next; else delete L[id];
      }
      if (plan.tool === "paint") {
        const cur = L[plan.brush] && L[plan.brush].f === lv ? cellsOf(L[plan.brush]) : [];
        L[plan.brush] = fromCells(lv, [...cur, [cx, cy]]);
        plan.selected = plan.brush;
      }
      p._render();
    };
    apply(ev);
    const up = () => {
      window.removeEventListener("pointermove", apply);
      window.removeEventListener("pointerup", up);
      window.removeEventListener("pointercancel", up);
      p._drag = null;
      p._render();
    };
    window.addEventListener("pointermove", apply);
    window.addEventListener("pointerup", up);
    window.addEventListener("pointercancel", up);
  }
}

export const cssPlan = `
.pgrid{position:relative;margin-top:16px;width:100%;aspect-ratio:var(--cols)/var(--rows);border-radius:18px;touch-action:none;user-select:none;-webkit-user-select:none;cursor:crosshair;
  background-color:rgba(255,255,255,.04);border:1px solid var(--line);
  background-image:linear-gradient(rgba(255,255,255,.07) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.07) 1px,transparent 1px);
  background-size:calc(100% / var(--cols)) calc(100% / var(--rows))}
.pgrid.tool-paint{cursor:cell}
.pgrid.tool-erase{cursor:not-allowed}
.ptools{margin-top:12px}
.pr{position:absolute;box-sizing:border-box;border-radius:10px;background:color-mix(in srgb,var(--cream) 88%,transparent);color:var(--ink);border:2px solid transparent;cursor:grab;overflow:hidden;padding:6px 8px;display:flex;flex-direction:column;justify-content:space-between}
.pr.sel{border-color:var(--amber);box-shadow:0 0 0 3px color-mix(in srgb,var(--amber) 35%,transparent)}
.pr.drag{cursor:grabbing;opacity:.9;z-index:2}
.pr.bad{border-color:#ff453a;border-style:dashed}
.pr.shape{background:none;border:0;box-shadow:none;padding:0;overflow:visible;pointer-events:none}
.pc{position:absolute;box-sizing:border-box;background:color-mix(in srgb,var(--cream) 88%,transparent);border:0 solid transparent;pointer-events:auto;cursor:grab}
.pr.shape.sel .pc{border-color:var(--amber);background:color-mix(in srgb,var(--cream) 95%,transparent)}
.pr.shape.bad .pc{border-color:#ff453a;border-style:dashed}
.tool-paint .pr,.tool-paint .pc,.tool-erase .pr,.tool-erase .pc{cursor:inherit}
.pnw{position:absolute;padding:6px 8px;pointer-events:none;max-width:400%;z-index:1}
.pn{display:flex;align-items:center;gap:6px;font-weight:600;font-size:14px;line-height:1.1;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;color:var(--ink)}
.pn ha-icon{--mdc-icon-size:16px;flex:none}
.pd{font-size:11px;color:var(--ink-soft)}
.rh{position:absolute;right:0;bottom:0;width:22px;height:22px;cursor:nwse-resize;background:linear-gradient(135deg,transparent 50%,var(--amber) 50%);border-radius:0 0 8px 0}
.tool-paint .rh,.tool-erase .rh{display:none}
.pdraw{position:absolute;border:2px dashed var(--amber);border-radius:10px;background:color-mix(in srgb,var(--amber) 20%,transparent);pointer-events:none}
.phint{color:var(--muted);font-size:14px;margin-top:10px}
.psel{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-top:14px;padding:12px 14px;border-radius:18px;background:rgba(255,255,255,.06)}
.psel b{display:flex;align-items:center;gap:8px;font-size:16px}
.psel .seg.sm{flex-wrap:wrap}
.psel .pill{height:36px;font-size:14px;padding:0 14px}
@media (max-width:720px){.pn{font-size:11px}.pn ha-icon{display:none}.pd{display:none}.rh{width:16px;height:16px}.ptools .pill{font-size:13px;padding:0 12px}}
`;
