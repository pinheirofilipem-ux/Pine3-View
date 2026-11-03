// Pine3 View - panel styles.
export const css = `
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
.set{padding:40px 0 60px}
.set-hd{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;flex-wrap:wrap}
.set-hd .t{font-family:var(--display);font-size:clamp(38px,5vw,54px);font-weight:400;letter-spacing:-.02em;margin-top:10px;line-height:1}
.set-hd .s{color:var(--muted);font-size:17px;margin-top:10px}
.set-hd .actions{align-items:center}
.pill.dim{opacity:.55;cursor:default}
.set-body{display:grid;grid-template-columns:270px minmax(0,1fr);gap:40px;margin-top:40px;align-items:start}
.set-nav{display:flex;flex-direction:column;gap:6px;position:sticky;top:20px}
.set-nav button{all:unset;cursor:pointer;display:flex;align-items:center;gap:14px;height:58px;padding:0 14px;border-radius:18px;font-size:18px;font-weight:500;color:rgba(255,255,255,.85);transition:background .2s}
.set-nav button:hover{background:rgba(255,255,255,.06)}
.set-nav button.act{background:rgba(255,255,255,.9);color:var(--ink)}
.set-nav .ni{width:38px;height:38px;border-radius:50%;display:grid;place-items:center;background:var(--glass-hi)}
.set-nav button.act .ni{background:rgba(42,34,31,.08)}
.set-nav ha-icon{--mdc-icon-size:20px}
.set-main{display:flex;flex-direction:column;gap:22px;min-width:0}
.panel{border-radius:32px;padding:28px}
.panel h3{font-family:var(--display);font-size:23px;font-weight:500;margin:26px 0 4px}
.panel p{color:var(--muted);font-size:16px;margin:0 0 16px}
.preview{display:flex;justify-content:center;gap:22px;padding:10px 0 8px;flex-wrap:wrap}
.pv{width:210px;min-height:230px;flex:none;cursor:default}
.pv .ri{background:var(--amber)!important;color:var(--ink)}
.pv.off .ri{background:var(--glass-hi)!important;color:#fff}
.pv-badge{position:absolute;top:-12px;right:-12px;width:34px;height:34px;border-radius:50%;display:grid;place-items:center;background:var(--amber);color:var(--ink);font-weight:700;font-size:15px;border:3px solid rgba(255,255,255,.25)}
.pv-bar{height:34px;border-radius:17px;background:rgba(42,34,31,.08);margin-top:14px;overflow:hidden}
.pv-bar i{display:block;width:75%;height:100%;border-radius:17px;background:var(--amber)}
.swr{display:flex;flex-wrap:wrap;gap:14px}
.swc{width:56px;height:56px;border-radius:50%;cursor:pointer;border:3px solid rgba(255,255,255,.18);position:relative;transition:transform .12s}
.swc:hover{transform:scale(1.06)}
.swc.act{border-color:#fff}
.swc.act::after{content:"";position:absolute;left:50%;top:50%;width:14px;height:8px;border-left:2.5px solid #2a221f;border-bottom:2.5px solid #2a221f;transform:translate(-50%,-65%) rotate(-45deg)}
.bgs{display:grid;grid-template-columns:repeat(auto-fill,minmax(190px,1fr));gap:16px}
.bgc{cursor:pointer;border-radius:24px;padding:10px;border:3px solid rgba(255,255,255,.12);background:rgba(255,255,255,.05)}
.bgc div{height:110px;border-radius:16px}
.bgc span{display:block;font-weight:600;margin:10px 4px 2px}
.bgc.act{border-color:#fff;background:rgba(255,255,255,.14)}
.opts{display:flex;flex-direction:column;gap:4px}
.opt{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px 4px;border-bottom:1px solid var(--line)}
.opt:last-child{border-bottom:0}
.opt b{display:block;font-size:17px;font-weight:600}
.opt small{display:block;color:var(--muted);font-size:14px;margin-top:2px}
.tg{width:58px;height:34px;border-radius:17px;background:rgba(255,255,255,.18);position:relative;cursor:pointer;flex:none;transition:background .2s}
.tg i{position:absolute;top:3px;left:3px;width:28px;height:28px;border-radius:50%;background:#fff;transition:transform .2s;box-shadow:0 2px 6px rgba(0,0,0,.25)}
.tg.on{background:var(--amber)}
.tg.on i{transform:translateX(24px)}
.list.big{max-height:none}
.list.big .it{padding:10px 12px;gap:12px;font-size:16px}
.ri.sm{width:40px;height:40px}.ri.sm ha-icon{--mdc-icon-size:20px}
.mini{all:unset;cursor:pointer;width:38px;height:38px;border-radius:50%;display:grid;place-items:center;background:var(--glass-hi)}
.mini[disabled]{opacity:.3;cursor:default}
.facts{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:12px;margin-top:14px}
.facts div{padding:16px;border-radius:20px;background:rgba(255,255,255,.06)}
.facts small{display:block;color:var(--muted);font-size:13px}
.facts b{display:block;font-family:var(--display);font-size:24px;font-weight:500;margin-top:4px}
a.pill{color:inherit;text-decoration:none}
.toast{position:fixed;left:50%;bottom:30px;transform:translateX(-50%);z-index:20;background:var(--cream);color:var(--ink);font-weight:600;padding:14px 22px;border-radius:999px;box-shadow:0 10px 30px rgba(0,0,0,.35);animation:pop .2s}
.wpill{cursor:pointer}
.hero .right{display:flex;gap:12px;align-items:center}
.sheet.wide{width:min(560px,calc(100vw - 32px))}
.fhs{display:flex;gap:10px;overflow-x:auto;padding:10px 2px 4px;scrollbar-width:none}
.fhs::-webkit-scrollbar{display:none}
.fh{flex:none;width:68px;display:flex;flex-direction:column;align-items:center;gap:6px;padding:12px 4px;border-radius:18px;background:rgba(255,255,255,.06)}
.fh small{font-size:12px;color:var(--muted)}
.fh b{font-family:var(--display);font-weight:500;font-size:18px}
.pp{color:#7cc4ff!important}
.fds{display:flex;flex-direction:column;margin-top:8px}
.fd{display:grid;grid-template-columns:1fr 28px 44px 44px 40px;align-items:center;gap:8px;padding:10px 4px;border-bottom:1px solid var(--line);font-size:15px}
.fd:last-child{border-bottom:0}

.fd b{font-family:var(--display);font-weight:500;text-align:right}
.fd small{color:var(--muted);text-align:right}
.opt.col{flex-direction:column;align-items:stretch}
.seg{display:flex;flex-wrap:wrap;gap:8px;margin-top:10px}
.seg .pill{height:40px;font-size:15px;padding:0 16px}
.seg.sm{margin:0;gap:4px;flex-wrap:nowrap}
.mini.on{background:var(--cream);color:var(--ink);font-weight:700}
.mini{font-size:13px;font-weight:600}
/* edit mode */
.edhint{color:var(--muted);font-size:15px;margin-top:10px}
.editing .rc[data-key],.editing .tile[data-key]{animation:wob .35s ease-in-out infinite alternate;cursor:grab;touch-action:none}
.editing .rc[data-key]:nth-child(2n),.editing .tile[data-key]:nth-child(2n){animation-delay:-.17s}
@keyframes wob{from{transform:rotate(-.6deg)}to{transform:rotate(.6deg)}}
.dragging{animation:none!important;transform:scale(1.04)!important;z-index:3;box-shadow:0 24px 50px rgba(0,0,0,.45)!important;cursor:grabbing!important}
.ed-x{all:unset;position:absolute;left:-10px;top:-10px;z-index:2;width:32px;height:32px;border-radius:50%;background:#ff453a;color:#fff;display:grid;place-items:center;font-size:22px;font-weight:700;line-height:1;cursor:pointer;box-shadow:0 4px 12px rgba(0,0,0,.35)}
.ed-big{all:unset;position:absolute;right:-10px;bottom:-10px;z-index:2;width:34px;height:34px;border-radius:50%;background:#fff;color:var(--ink);display:grid;place-items:center;cursor:pointer;box-shadow:0 4px 12px rgba(0,0,0,.35)}
.ed-big ha-icon{--mdc-icon-size:18px}
.edbar{position:fixed;right:22px;bottom:22px;z-index:6;display:flex;gap:10px}
.edbar .pill{height:52px;padding:0 24px;font-size:17px;backdrop-filter:blur(20px);-webkit-backdrop-filter:blur(20px)}
.rc.add,.tile.add{border:2px dashed rgba(255,255,255,.3);background:transparent;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;color:var(--muted);cursor:pointer;font-weight:600}
.rc.add ha-icon,.tile.add ha-icon{--mdc-icon-size:30px;width:56px;height:56px;border-radius:50%;display:grid;place-items:center;background:var(--glass-hi);color:#fff}
.rc.big{grid-column:span 2}
.quick{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px}
.qk{all:unset;cursor:pointer;display:flex;align-items:center;gap:6px;height:36px;padding:0 12px;border-radius:999px;background:rgba(42,34,31,.07);font-size:14px;font-weight:600;max-width:100%}
.rc.glass .qk{background:var(--glass-hi)}
.qk span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:140px}
.qk ha-icon{--mdc-icon-size:18px}
.qk.on ha-icon{color:var(--amber)}
.rc.on .qk.on{background:var(--amber);color:var(--ink)}
.rc.on .qk.on ha-icon{color:var(--ink)}
/* list layout */
.as-list .grid{grid-template-columns:1fr;gap:10px}
.as-list .rc{min-height:0;flex-direction:row;align-items:center;gap:16px;padding:14px 18px;border-radius:22px}
.as-list .rc .row1{display:contents}
.as-list .rc .ri{order:0}
.as-list .rc .foot{order:1;margin:0;flex:1;display:flex;align-items:center;gap:18px;min-width:0}
.as-list .rc .foot .nm{font-size:19px;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.as-list .rc .foot .meta{margin:0;flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.as-list .rc .dots{margin:0}
.as-list .rc .pw{order:2;width:52px;height:52px}
.as-list .rc .quick{order:3;margin:0}
.as-list .rc.big{grid-column:auto}
.as-list .rc .eyebrow{margin:0!important;padding:0!important;flex:none!important}
.as-list .rc .big-num,.as-list .rc .big-txt{font-size:24px}
.as-list .rc.media{flex-wrap:wrap}
.as-list .tiles{grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:10px}
.as-list .tile{min-height:0;flex-direction:row;align-items:center;gap:14px;padding:12px 16px;border-radius:20px}
.as-list .tile .row1{display:contents}
.as-list .tile .ic{order:0;flex:none}
.as-list .tile .tx{order:1;flex:1;min-width:0}
.as-list .tile .nm{margin-top:0;-webkit-line-clamp:1}
.as-list .tile .pw{order:2;width:46px;height:46px}
.as-list .tile.big{grid-column:auto}
.as-list .tile.sensor .val{font-size:22px;margin-top:0}
/* screensaver */
.saver{position:fixed;inset:0;z-index:30;background:radial-gradient(80% 70% at 50% 40%,#22121f,#050305);display:grid;place-items:center;cursor:pointer;animation:fade .6s}
.sv-in{text-align:center;animation:drift 60s ease-in-out infinite alternate}
@keyframes drift{0%{transform:translate(-4vw,-3vh)}50%{transform:translate(3vw,2vh)}100%{transform:translate(-2vw,4vh)}}
.sv-clock{font-size:clamp(90px,18vw,220px);font-weight:200;margin:0;color:rgba(255,255,255,.92)}
.sv-date{font-size:clamp(18px,2.4vw,28px);color:rgba(255,255,255,.6);margin-top:6px}
.sv-meta{display:flex;gap:26px;justify-content:center;margin-top:26px;color:rgba(255,255,255,.55);font-size:18px;flex-wrap:wrap}
.sv-meta span{display:flex;align-items:center;gap:8px}
.search{all:unset;box-sizing:border-box;width:100%;height:48px;padding:0 18px;border-radius:999px;background:rgba(255,255,255,.08);border:1px solid var(--line);font-size:16px;color:#fff}
.search::placeholder{color:var(--muted)}
.it.off{opacity:.55}
.it ha-icon{--mdc-icon-size:20px;flex:none}
.tag{font-style:normal;font-size:12px;font-weight:600;padding:4px 10px;border-radius:999px;background:rgba(255,255,255,.08);color:var(--muted);white-space:nowrap}
.tag.pri{background:color-mix(in srgb,var(--amber) 30%,transparent);color:#fff}
@media (max-width:720px){
  .tag{display:none}

  .rc.big{grid-column:span 2}.edbar{right:12px;bottom:14px}.edbar .pill{height:46px;padding:0 18px}
  .as-list .rc .foot{flex-direction:column;align-items:flex-start;gap:2px}.as-list .rc .dots{display:none}.as-list .tiles{grid-template-columns:1fr}
  .fd{grid-template-columns:1fr 26px 40px 40px 36px;font-size:14px}.stage{height:58vh;min-height:340px;border-radius:24px;margin-bottom:20px}.hint3{display:none}

  .set{padding-top:20px}.set-body{grid-template-columns:1fr;gap:18px;margin-top:22px}
  .set-nav{flex-direction:row;overflow-x:auto;position:static;scrollbar-width:none}.set-nav::-webkit-scrollbar{display:none}
  .set-nav button{height:46px;font-size:15px;flex:none;padding:0 14px 0 8px}.set-nav .ni{width:30px;height:30px}
  .panel{padding:20px;border-radius:26px}.swc{width:44px;height:44px}.pv{width:160px;min-height:190px}.bgs{grid-template-columns:1fr 1fr}

  .wrap{padding:0 16px 116px}.top{padding-top:20px}.statusrow{margin-top:24px}
  .grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.rc{min-height:190px;padding:16px;border-radius:24px}
  .rc .nm{font-size:18px}.rc .meta{font-size:14px}.ri{width:44px;height:44px}.pw{width:50px;height:50px}
  .tiles{grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.tile{min-height:150px;padding:14px}.tile .nm{font-size:16px}.tile .pw{width:44px;height:44px}
  .wpill{height:48px;padding:0 16px}.wpill b{font-size:22px}.wpill span{display:none}.av,.iconbtn{width:46px;height:46px}
  .dock button{padding:0 16px;height:46px}.dock button span{display:none}.big-num{font-size:38px}.big-txt{font-size:24px}
}
`;
