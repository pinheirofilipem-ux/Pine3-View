// Pine3 View - 3D view of the home: one block per room, stacked by floor, lit rooms glow.
// Pure CSS 3D (no WebGL). Rooms are packed automatically; their size can be set in Settings.

import { esc, dom } from "./const.js";
import { t, tp } from "./i18n.js";

const U = 56; // px per layout unit
const WALL = 30; // wall height in px
const FLOOR_GAP = 150; // vertical distance between floors when all are shown
const SIZES = { 1: [2, 2], 2: [3, 2], 3: [4, 3] };

export const view3d = { rx: 58, rz: -38, floor: "all" };

export function autoSize(r) {
  const n = r.count || 0;
  return n <= 3 ? 1 : n <= 7 ? 2 : 3;
}

/** Shelf-pack rooms into rows; returns {items:[{room,x,y,w,d}], W, D}. */
export function pack(rooms, sizes) {
  const boxes = rooms.map((r) => {
    const [w, d] = SIZES[sizes[r.id] || autoSize(r)];
    return { room: r, w, d };
  });
  const area = boxes.reduce((a, b) => a + b.w * b.d, 0);
  const rowW = Math.max(6, Math.ceil(Math.sqrt(area) * 1.35), ...boxes.map((b) => b.w));
  let x = 0, y = 0, rowD = 0, W = 0;
  for (const b of boxes.sort((a, c) => c.d - a.d || c.w - a.w)) {
    if (x + b.w > rowW) { y += rowD; x = 0; rowD = 0; }
    b.x = x; b.y = y;
    x += b.w; rowD = Math.max(rowD, b.d); W = Math.max(W, x);
  }
  return { items: boxes, W, D: y + rowD };
}

export function houseView(p, rooms) {
  const cfg = p._cfg();
  const layout = cfg.house_layout || {};
  const lvOf = (r) => layout[r.id]?.f ?? r.floor_level ?? 0;
  const levels = [...new Set(rooms.map(lvOf))].sort((a, b) => a - b);
  const floorName = (lv) => rooms.find((r) => (r.floor_level ?? 0) === lv && r.floor)?.floor || t("Piso {n}", { n: lv });
  if (view3d.floor !== "all" && !levels.includes(view3d.floor)) view3d.floor = "all";
  const shown = view3d.floor === "all" ? levels : [view3d.floor];

  // Rooms drawn in the floor-plan editor keep their place; the rest are packed beside them.
  const plans = shown.map((lv) => {
    const onFloor = rooms.filter((r) => lvOf(r) === lv);
    const placed = onFloor.filter((r) => layout[r.id]).map((r) => ({ room: r, ...layout[r.id] }));
    const auto = pack(onFloor.filter((r) => !layout[r.id]), cfg.house_size || {});
    const ox = placed.length ? Math.max(...placed.map((b) => b.x + b.w)) + 1 : 0;
    const items = [...placed, ...auto.items.map((b) => ({ ...b, x: b.x + ox }))];
    return { lv, items, W: Math.max(1, ...items.map((b) => b.x + b.w)), D: Math.max(1, ...items.map((b) => b.y + b.d)) };
  });
  const W = Math.max(...plans.map((pl) => pl.W), 1), D = Math.max(...plans.map((pl) => pl.D), 1);

  const floorsHtml = plans.map((pl, k) => {
    const z = k * FLOOR_GAP;
    // drawn plans share one coordinate system across floors; only automatic plans are centred
    const drawn = Object.keys(layout).length > 0;
    const ox = drawn ? 0 : ((W - pl.W) / 2) * U, oy = drawn ? 0 : ((D - pl.D) / 2) * U;
    const roomsHtml = pl.items.map((b) => {
      const r = b.room, sw = p._switchables(r), lit = sw.filter((i) => p._isOn(i));
      const lights = r.ents.filter((i) => dom(i) === "light").slice(0, 6);
      const c = p._roomClimate(r);
      const open = r.ents.some((i) => (dom(i) === "binary_sensor" && ["door", "window", "garage_door", "opening"].includes(p._s(i)?.attributes.device_class) && p._s(i).state === "on") || (dom(i) === "cover" && p._isOn(i)));
      // free shapes (from the floor-plan editor): one floor tile per cell, walls only on the outline
      const cells = b.cells ? b.cells.map(([x, y]) => [x - b.x, y - b.y]) : null;
      let shape = `<div class="fl"></div><div class="wl n"></div><div class="wl s"></div><div class="wl e"></div><div class="wl w"></div>`;
      let spots = null, lbl = "";
      if (cells) {
        const set = new Set(cells.map(([x, y]) => x + "," + y));
        const has = (x, y) => set.has(x + "," + y);
        shape = cells.map(([x, y]) => {
          const L = x * U, T = y * U;
          return `<div class="fl c" style="left:${L}px;top:${T}px;width:${U}px;height:${U}px"></div>`
            + (has(x, y - 1) ? "" : `<div class="wl ws n" style="left:${L}px;top:${T}px;width:${U}px;height:${WALL}px"></div>`)
            + (has(x, y + 1) ? "" : `<div class="wl ws s" style="left:${L}px;top:${T + U - WALL}px;width:${U}px;height:${WALL}px"></div>`)
            + (has(x - 1, y) ? "" : `<div class="wl ws w" style="left:${L}px;top:${T}px;width:${WALL}px;height:${U}px"></div>`)
            + (has(x + 1, y) ? "" : `<div class="wl ws e" style="left:${L + U - WALL}px;top:${T}px;width:${WALL}px;height:${U}px"></div>`);
        }).join("");
        spots = lights.map((_, n) => cells[Math.floor(((n + 0.5) * cells.length) / Math.max(1, lights.length))]).map(([x, y]) => [((x + 0.5) / b.w) * 100, ((y + 0.5) / b.d) * 100]);
        // label over the cell closest to the shape's centre of mass
        const cx = cells.reduce((a, c) => a + c[0], 0) / cells.length, cy = cells.reduce((a, c) => a + c[1], 0) / cells.length;
        const [mx, my] = cells.reduce((best, c) => (Math.hypot(c[0] - cx, c[1] - cy) < Math.hypot(best[0] - cx, best[1] - cy) ? c : best), cells[0]);
        lbl = `style="left:${(mx + 0.5) * U}px;top:${(my + 0.5) * U}px"`;
      }
      const cols = Math.min(3, Math.max(1, lights.length));
      const pins = lights.map((id, n) => {
        const [px, py] = spots ? spots[n] : [((n % cols) + 1) / (cols + 1) * 100, (Math.floor(n / cols) + 1) / (Math.ceil(lights.length / cols) + 1) * 100];
        return `<i class="pin ${p._isOn(id) ? "on" : ""}" data-pin="${esc(id)}" title="${esc(p._name(id))}" style="left:${px}%;top:${py}%"></i>`;
      }).join("");
      const meta = [c.temp && c.temp.replace(/,\d/, "").replace(".", "").replace("°C", "°"), lit.length ? tp(lit.length, "{n} ligado", "{n} ligados") : null].filter(Boolean).join(" · ");
      return `<div class="r3 ${lit.length ? "lit" : ""} ${open ? "open" : ""} ${cells ? "shape" : ""}" data-room="${esc(r.id)}" style="left:${ox + b.x * U}px;top:${oy + b.y * U}px;width:${b.w * U}px;height:${b.d * U}px">
        ${shape}
        ${pins}
        <div class="lbw" ${lbl}><div class="lb"><b>${esc(r.name)}</b>${meta ? `<small>${esc(meta)}</small>` : ""}</div></div>
      </div>`;
    }).join("");
    return `<div class="fl3" style="transform:translateZ(${z}px)">
      <div class="slab" style="width:${W * U + 36}px;height:${D * U + 36}px"></div>
      ${roomsHtml}
      ${shown.length > 1 ? `<div class="lbw flab" style="left:-18px;top:-18px"><div class="lb"><small>${esc(floorName(pl.lv))}</small></div></div>` : ""}
    </div>`;
  }).join("");

  const chips = levels.length > 1 ? `<div class="chips3">
      <button class="pill ${view3d.floor === "all" ? "solid" : ""}" data-floor="all">${t("Todos os pisos")}</button>
      ${levels.map((lv) => `<button class="pill ${view3d.floor === lv ? "solid" : ""}" data-floor="${lv}">${esc(floorName(lv))}</button>`).join("")}
    </div>` : "";

  const lit = rooms.filter((r) => p._switchables(r).some((i) => p._isOn(i))).length;
  return `<div class="hero"><div><div class="eyebrow">${tp(lit, "{n} divisão acesa", "{n} divisões acesas")}</div><div class="t" style="margin-top:8px">${t("Casa 3D")}</div></div>
      <div class="right"><button class="pill" data-editplan><ha-icon icon="mdi:floor-plan"></ha-icon>${t("Editar planta")}</button>${p._gear()}</div></div>
    ${chips}
    <div class="stage glass" data-stage>
      <div class="scene3d" style="--rx:${view3d.rx}deg;--rz:${view3d.rz}deg;--sc:1;width:${W * U}px;height:${D * U}px;--stack:${(shown.length - 1) * FLOOR_GAP}px">
        ${rooms.length ? floorsHtml : ""}
      </div>
      <div class="ctl3">
        <button class="mini" data-rot="-30" title="${t("Rodar")}"><ha-icon icon="mdi:rotate-left"></ha-icon></button>
        <button class="mini" data-rot="reset" title="${t("Repor vista")}"><ha-icon icon="mdi:crosshairs"></ha-icon></button>
        <button class="mini" data-rot="30" title="${t("Rodar")}"><ha-icon icon="mdi:rotate-right"></ha-icon></button>
      </div>
      <div class="hint3">${t("Arrasta para rodar · Toca numa divisão para a abrir · Toca numa luz para a ligar")}</div>
    </div>
    ${rooms.length ? "" : `<div class="empty">${t("Ainda não há divisões com dispositivos.")}</div>`}`;
}

export function bindHouse(p, root) {
  const stage = root.querySelector("[data-stage]");
  if (!stage) return;
  const scene = stage.querySelector(".scene3d");
  const apply = () => {
    scene.style.setProperty("--rx", view3d.rx + "deg");
    scene.style.setProperty("--rz", view3d.rz + "deg");
  };
  const fit = () => {
    const w = scene.offsetWidth, d = scene.offsetHeight;
    const diag = Math.hypot(w, d) + 80;
    const sc = Math.min(1.25, (stage.clientWidth - 24) / diag, (stage.clientHeight - 60) / (diag * 0.62 + parseFloat(scene.style.getPropertyValue("--stack") || 0)));
    scene.style.setProperty("--sc", Math.max(0.35, sc).toFixed(3));
  };
  fit();
  let start = null, moved = false;
  stage.addEventListener("pointerdown", (ev) => {
    if (ev.target.closest("button")) return;
    start = { x: ev.clientX, y: ev.clientY, rx: view3d.rx, rz: view3d.rz };
    moved = false;
  });
  stage.addEventListener("pointermove", (ev) => {
    if (!start) return;
    const dx = ev.clientX - start.x, dy = ev.clientY - start.y;
    if (!moved && Math.hypot(dx, dy) > 6) { moved = true; stage.setPointerCapture(ev.pointerId); p._drag = true; stage.classList.add("dragging"); }
    if (!moved) return;
    view3d.rz = start.rz + dx * 0.35;
    view3d.rx = Math.min(78, Math.max(18, start.rx - dy * 0.25));
    apply();
  });
  const end = () => { start = null; p._drag = null; stage.classList.remove("dragging"); setTimeout(() => { moved = false; }, 0); };
  stage.addEventListener("pointerup", end);
  stage.addEventListener("pointercancel", end);
  // clicks: pins toggle a light, rooms open, unless the pointer was dragging
  stage.addEventListener("click", (ev) => {
    if (moved) { ev.stopPropagation(); return; }
    const pin = ev.target.closest("[data-pin]");
    if (pin) { ev.stopPropagation(); p._call("light", "toggle", { entity_id: pin.dataset.pin }); return; }
    const room = ev.target.closest("[data-room]");
    if (room) { ev.stopPropagation(); p._go("room", room.dataset.room); }
  }, true);
  root.querySelectorAll("[data-rot]").forEach((b) => b.addEventListener("click", () => {
    if (b.dataset.rot === "reset") { view3d.rx = 58; view3d.rz = -38; } else view3d.rz += Number(b.dataset.rot);
    apply();
  }));
  root.querySelector("[data-editplan]")?.addEventListener("click", () => p._openSettings("plan"));
  root.querySelectorAll("[data-floor]").forEach((b) => b.addEventListener("click", () => {
    view3d.floor = b.dataset.floor === "all" ? "all" : Number(b.dataset.floor);
    p._render();
  }));
  p._fit3d = fit;
}

export const css3d = `
.chips3{display:flex;gap:10px;flex-wrap:wrap;margin-top:18px}
.stage{position:relative;margin-top:20px;border-radius:32px;height:min(68vh,640px);min-height:380px;overflow:hidden;perspective:1600px;perspective-origin:50% 35%;cursor:grab;touch-action:none;user-select:none;-webkit-user-select:none}
.stage.dragging{cursor:grabbing}
.scene3d{position:absolute;left:50%;top:52%;transform-style:preserve-3d;transform:translate(-50%,-50%) scale(var(--sc)) rotateX(var(--rx)) rotateZ(var(--rz)) translateZ(calc(var(--stack) / -2));transition:transform .08s linear}
.fl3{position:absolute;inset:0;transform-style:preserve-3d;pointer-events:none}
.fl3 .r3{pointer-events:auto}
.fl3 .r3.shape{pointer-events:none}
.slab{position:absolute;left:-18px;top:-18px;pointer-events:none;border-radius:18px;background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.12);box-shadow:0 30px 60px rgba(0,0,0,.35);transform:translateZ(-2px)}
.r3{position:absolute;transform-style:preserve-3d;cursor:pointer;padding:3px}
.r3 .fl{position:absolute;inset:3px;border-radius:10px;background:rgba(255,255,255,.10);border:1px solid rgba(255,255,255,.16);transition:background .3s,box-shadow .3s}
.r3:hover .fl{background:rgba(255,255,255,.16)}
.r3.lit .fl{background:radial-gradient(circle at 50% 50%,color-mix(in srgb,var(--amber) 85%,#fff) 0%,color-mix(in srgb,var(--amber) 55%,transparent) 55%,color-mix(in srgb,var(--amber) 25%,transparent) 100%);box-shadow:0 0 40px color-mix(in srgb,var(--amber) 55%,transparent);border-color:color-mix(in srgb,var(--amber) 70%,#fff)}
.r3 .wl{position:absolute;background:rgba(255,255,255,.10);border:1px solid rgba(255,255,255,.20);backface-visibility:visible}
.r3.lit .wl{background:color-mix(in srgb,var(--amber) 22%,rgba(255,255,255,.12));border-color:color-mix(in srgb,var(--amber) 45%,rgba(255,255,255,.3))}
.r3.open .wl.s,.r3.open .wl.e{border-style:dashed}
.r3 .wl.n{left:3px;right:3px;top:3px;height:${WALL}px;transform-origin:top;transform:rotateX(90deg)}
.r3 .wl.s{left:3px;right:3px;bottom:3px;height:${WALL}px;transform-origin:bottom;transform:rotateX(-90deg)}
.r3 .wl.w{top:3px;bottom:3px;left:3px;width:${WALL}px;transform-origin:left;transform:rotateY(-90deg)}
.r3 .wl.e{top:3px;bottom:3px;right:3px;width:${WALL}px;transform-origin:right;transform:rotateY(90deg)}
.r3.shape{padding:0;pointer-events:none}
.r3.shape .fl.c{inset:auto;border-radius:0;border:0;pointer-events:auto}
.r3.shape.lit .fl.c{background:color-mix(in srgb,var(--amber) 62%,rgba(255,255,255,.25));box-shadow:none}
.r3.shape .pin{pointer-events:auto}
.r3 .wl.ws{right:auto;bottom:auto}
.r3 .wl.ws.n{transform-origin:top;transform:rotateX(90deg)}
.r3 .wl.ws.s{transform-origin:bottom;transform:rotateX(-90deg)}
.r3 .wl.ws.w{transform-origin:left;transform:rotateY(-90deg)}
.r3 .wl.ws.e{transform-origin:right;transform:rotateY(90deg)}
.pin{position:absolute;width:16px;height:16px;margin:-8px 0 0 -8px;border-radius:50%;background:rgba(255,255,255,.35);border:2px solid rgba(255,255,255,.6);transform:translateZ(3px);cursor:pointer}
.pin.on{background:#fff;border-color:var(--amber);box-shadow:0 0 14px 4px var(--amber)}
.lbw{position:absolute;left:50%;top:50%;transform-style:preserve-3d;transform:translateZ(${WALL + 26}px) rotateZ(calc(var(--rz) * -1)) rotateX(calc(var(--rx) * -1));pointer-events:none}
.lb{transform:translate(-50%,-100%);white-space:nowrap;text-align:center;padding:6px 12px;border-radius:14px;background:rgba(20,10,22,.55);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px);border:1px solid rgba(255,255,255,.14);line-height:1.2}
.lb b{display:block;font-family:var(--display);font-weight:500;font-size:15px}
.lb small{display:block;font-size:12px;color:var(--muted)}
.r3.lit .lb{background:color-mix(in srgb,var(--cream) 92%,transparent);color:var(--ink)}
.r3.lit .lb small{color:var(--ink-soft)}
.flab{transform:translateZ(4px) rotateZ(calc(var(--rz) * -1)) rotateX(calc(var(--rx) * -1))}
.flab .lb{background:rgba(20,10,22,.45);border:0;transform:translate(-50%,-50%)}
.flab .lb small{font-size:13px;letter-spacing:.18em;text-transform:uppercase}
.ctl3{position:absolute;right:16px;top:16px;display:flex;gap:8px}
.hint3{position:absolute;left:0;right:0;bottom:14px;text-align:center;font-size:13px;color:var(--muted);pointer-events:none;padding:0 16px}
@media (max-width:720px){.flab{display:none}}
`;
