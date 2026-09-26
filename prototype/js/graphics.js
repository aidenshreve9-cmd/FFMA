"use strict";

/* ================= Canvas helpers ================= */
function rng(seed) { return () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let t = Math.imul(seed ^ seed >>> 15, 1 | seed); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }
const rgbCache = {};
const rgb = hex => rgbCache[hex] || (rgbCache[hex] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16)).join(","));
function glow(c, x, y, r, hex, a) {
  if (r <= 0 || a <= 0) return;
  const g = c.createRadialGradient(x, y, 0, x, y, r), k = rgb(hex);
  g.addColorStop(0, `rgba(${k},${a})`); g.addColorStop(.45, `rgba(${k},${a * .45})`); g.addColorStop(1, `rgba(${k},0)`);
  c.fillStyle = g; c.fillRect(x - r, y - r, r * 2, r * 2);
}
function streak(c, x, y, r, hex, a, rot, sx, sy) { c.save(); c.translate(x, y); c.rotate(rot); c.scale(sx, sy); glow(c, 0, 0, r, hex, a); c.restore(); }
function vgrad(c, w, h, stops) { const g = c.createLinearGradient(0, 0, 0, h); stops.forEach((col, i) => g.addColorStop(i / (stops.length - 1), col)); c.fillStyle = g; c.fillRect(0, 0, w, h); }
// Only reallocates the backing store when the element's size actually changes.
function fit(cv, scale) {
  // clientWidth ignores transition transforms, so the backing store isn't resized every frame of a screen animation
  const dpr = Math.min(2, window.devicePixelRatio || 1) * scale, cw = cv.clientWidth, ch = cv.clientHeight;
  const W = Math.max(1, Math.round(cw * dpr)), H = Math.max(1, Math.round(ch * dpr));
  if (cv.width !== W || cv.height !== H) { cv.width = W; cv.height = H; }
  const c = cv.getContext("2d"); c.setTransform(dpr, 0, 0, dpr, 0, 0);
  return { c, w: cw, h: ch };
}
const RS = rng(77);
const STARS = Array.from({ length: 260 }, () => ({ x: RS(), y: RS(), s: .5 + RS() * 1.3, b: .4 + RS() * .6, ph: RS() * 6.28, f: .3 + RS() * 1.1 }));
function starfield(c, w, h, t, n, alpha) {
  for (let i = 0; i < n; i++) {
    const s = STARS[i], a = alpha * s.b * (.45 + .55 * (.5 + .5 * Math.sin(t * s.f + s.ph)));
    c.fillStyle = `rgba(236,228,255,${a.toFixed(3)})`; c.fillRect(s.x * w, s.y * h, s.s, s.s);
  }
}

/* ---------- Nebula void background ---------- */
const R1 = rng(42);
const FILAMENTS = [
  { x: .22, y: .30, r: .75, col: "#6A0DAD", a: .42, rot: -.5, st: 2.2 },
  { x: .78, y: .58, r: .70, col: "#8A2BE2", a: .30, rot: .6,  st: 2.6 },
  { x: .55, y: .44, r: .55, col: "#D900FF", a: .16, rot: -.9, st: 3.0 },
  { x: .30, y: .72, r: .60, col: "#F72585", a: .14, rot: .3,  st: 2.4 },
  { x: .82, y: .22, r: .45, col: "#FF5400", a: .12, rot: -.2, st: 2.8 },
  { x: .15, y: .55, r: .40, col: "#E65100", a: .10, rot: .9,  st: 2.0 },
  { x: .62, y: .86, r: .55, col: "#6A0DAD", a: .30, rot: -.3, st: 2.5 },
].map(f => ({ ...f, p: R1() * 6.28, sx: .012 + R1() * .018, sy: .010 + R1() * .016 }));
const ORBITS = [
  { rx: .62, k: .34, tilt: -.32, a: .13, w: .050, ph: .4, col: "#FF5400", pr: 3.2 },
  { rx: .86, k: .30, tilt: -.32, a: .09, w: -.032, ph: 2.1, col: "#E0C3FC", pr: 2.2 },
  { rx: 1.12, k: .28, tilt: -.32, a: .07, w: .021, ph: 4.0, col: "#F72585", pr: 2.6 },
];
const MOTES = Array.from({ length: 90 }, () => ({ x: R1(), y: R1(), z: R1(), vx: (R1() - .5) * .004, vy: -.002 - R1() * .004, ph: R1() * 6.28,
  col: R1() < .18 ? "#FF5400" : R1() < .3 ? "#F72585" : "#E0C3FC" }));
// Home keeps the orb in an almost pitch-black void; other screens let the nebula come up.
const NEBULA_LEVEL = { start: .55, home: .18, settings: 1, done: .8, session: .8 };
let nebLevel = NEBULA_LEVEL.start;
function drawNebula(t) {
  const { c, w, h } = fit($("nebula"), .5), m = Math.max(w, h), L = nebLevel;
  c.globalCompositeOperation = "source-over";
  c.fillStyle = "#000"; c.fillRect(0, 0, w, h);
  glow(c, w * .5, h * .5, m * .85, "#1E005B", .85 * L);
  glow(c, w * (.35 + Math.sin(t * .02) * .06), h * .32, m * .6, "#2A085C", .7 * L);
  c.globalCompositeOperation = "lighter";
  FILAMENTS.forEach(f => {
    const x = w * (f.x + Math.sin(t * f.sx + f.p) * .12), y = h * (f.y + Math.cos(t * f.sy + f.p) * .08);
    const r = m * f.r * .5 * (1 + .1 * Math.sin(t * .03 + f.p));
    streak(c, x, y, r, f.col, f.a * L, f.rot + Math.sin(t * .01 + f.p) * .25, f.st, 1 / f.st * 1.4);
  });
  c.globalCompositeOperation = "source-over";
  const cx = w * .5, cy = h * .47;
  const voidAmt = Math.max(0, Math.min(1, (1 - L) / .6));
  if (voidAmt > 0) glow(c, cx, cy, Math.min(w, h) * .75, "#000000", .95 * voidAmt);
  ORBITS.forEach(o => {
    const rx = w * o.rx, ry = rx * o.k;
    c.save(); c.translate(cx, cy); c.rotate(o.tilt);
    c.strokeStyle = `rgba(224,195,252,${o.a * (.5 + .5 * L)})`; c.lineWidth = .6;
    c.beginPath(); c.ellipse(0, 0, rx, ry, 0, 0, Math.PI * 2); c.stroke();
    const ang = o.ph + t * o.w, px = Math.cos(ang) * rx, py = Math.sin(ang) * ry;
    glow(c, px, py, o.pr * 5, o.col, .5);
    c.fillStyle = "#fff"; c.beginPath(); c.arc(px, py, o.pr * .45, 0, 7); c.fill();
    c.restore();
  });
  MOTES.forEach(d => {
    const x = ((d.x + t * d.vx) % 1 + 1) % 1 * w, y = ((d.y + t * d.vy) % 1 + 1) % 1 * h;
    const tw = .55 + .45 * Math.sin(t * .6 + d.ph);
    if (d.z > .82) glow(c, x, y, 6 + d.z * 10, d.col, .10 * tw);
    else { c.fillStyle = `rgba(${rgb(d.col)},${((.25 + d.z * .6) * tw).toFixed(3)})`; c.fillRect(x, y, .6 + d.z * 1.2, .6 + d.z * 1.2); }
  });
}

/* ---------- 3D quantum plasma orb (WebGL, with a 2D fallback) ---------- */
const ORB_VERT = "attribute vec2 p;void main(){gl_Position=vec4(p,0.0,1.0);}";
const ORB_FRAG = `
precision highp float;
uniform vec2 uRes; uniform float uTime; uniform float uAct;
float hash(vec3 p){ p=fract(p*0.3183099+0.1); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
float noise(vec3 x){ vec3 i=floor(x); vec3 f=fract(x); f=f*f*(3.0-2.0*f);
  return mix(mix(mix(hash(i),hash(i+vec3(1.,0.,0.)),f.x),mix(hash(i+vec3(0.,1.,0.)),hash(i+vec3(1.,1.,0.)),f.x),f.y),
             mix(mix(hash(i+vec3(0.,0.,1.)),hash(i+vec3(1.,0.,1.)),f.x),mix(hash(i+vec3(0.,1.,1.)),hash(i+vec3(1.,1.,1.)),f.x),f.y),f.z); }
float fbm(vec3 p){ float s=0.0; float a=0.5; for(int i=0;i<3;i++){ s+=a*noise(p); p=p*2.03+vec3(1.7,9.2,3.1); a*=0.5; } return s/0.875; }
mat3 rY(float a){ float c=cos(a); float s=sin(a); return mat3(c,0.,s, 0.,1.,0., -s,0.,c); }
mat3 rX(float a){ float c=cos(a); float s=sin(a); return mat3(1.,0.,0., 0.,c,-s, 0.,s,c); }
const vec3 DEEP=vec3(0.008,0.012,0.08);
const vec3 COBALT=vec3(0.035,0.07,0.34);
const vec3 VIOLET=vec3(0.54,0.17,0.89);
const vec3 MAGENTA=vec3(0.97,0.16,0.55);
const vec3 LAV=vec3(0.88,0.76,0.99);
const vec3 HOT=vec3(1.0,0.97,1.0);
void main(){
  vec2 uv=(gl_FragCoord.xy*2.0-uRes)/uRes.y;
  float t=uTime;
  float R=0.70*(1.0+0.022*sin(t*0.698))*(1.0+0.055*uAct);   // ~9 s breathing, expands on touch
  float r=length(uv);
  float d=max(r-R,0.0);
  vec3 haloC=mix(VIOLET,MAGENTA,clamp(0.3+0.25*sin(t*0.21)+0.35*uAct,0.0,1.0));
  float halo=(exp(-d*10.0)*0.55+exp(-d*4.0)*0.22)*(0.85+0.6*uAct)*smoothstep(1.0,0.72,r);
  vec3 col=haloC*halo; float alpha=halo;
  if(r<R+0.01){
    float rc=min(r,R-0.0001);
    float z=sqrt(R*R-rc*rc);
    vec3 n=normalize(vec3(uv,z));
    vec3 sp=n*2.6+vec3(0.0,0.0,t*0.06);
    vec3 np=normalize(n+0.16*vec3(fbm(sp)-0.5,fbm(sp+vec3(5.2,1.3,2.7))-0.5,0.0)); // undulating glass shell
    vec3 ro=vec3(uv,z)+(np-n)*0.22*R;                                                // refraction offset
    float dt=2.0*z/20.0;
    mat3 m1=rY(t*0.045)*rX(0.4+0.25*sin(t*0.027));
    mat3 m2=rY(-t*0.031+1.3)*rX(-0.3);
    vec3 acc=vec3(0.0); float T=1.0;
    for(int i=0;i<20;i++){
      vec3 q=(ro-vec3(0.0,0.0,(float(i)+0.5)*dt))/R;
      float rr=length(q);
      float n1=fbm(m1*q*2.0+vec3(0.0,t*0.02,0.0));
      float n2=noise(m2*q*3.4+vec3(t*0.025));
      float fil=(pow(1.0-abs(n1*2.0-1.0),6.0)+0.7*pow(1.0-abs(n2*2.0-1.0),9.0))*smoothstep(1.05,0.25,rr);
      float core=exp(-rr*rr*(10.0-3.5*uAct))*(1.0+0.9*uAct);
      vec3 c=mix(COBALT,VIOLET,smoothstep(0.05,0.45,fil));
      c=mix(c,MAGENTA,smoothstep(0.35,0.95,fil)*(0.55+0.45*sin(q.y*3.0+q.x*2.0+t*0.15)));
      c+=mix(LAV,HOT,smoothstep(0.3,1.1,core))*core*1.6;
      float a=clamp((fil*1.5+core*0.9)*dt*3.0,0.0,1.0);
      acc+=T*c*a*1.5;
      T*=1.0-a*0.6;
    }
    vec3 inside=acc+T*mix(DEEP,COBALT,0.35+0.3*n.y);
    float fres=pow(1.0-max(np.z,0.0),2.6);
    inside+=mix(VIOLET,MAGENTA,0.5+0.5*sin(atan(uv.y,uv.x)*2.0+t*0.12))*fres*0.85;
    vec3 L=normalize(vec3(-0.45,0.55,0.75)); vec3 H=normalize(L+vec3(0.0,0.0,1.0));
    float nh=max(dot(np,H),0.0);
    float spec=pow(nh,140.0)*1.3+pow(nh,20.0)*0.08;
    float glint=pow(fbm(np*9.0+vec3(t*0.08,0.0,-t*0.05)),11.0)*3.0*smoothstep(0.35,0.95,dot(np,L));
    inside+=HOT*(spec+glint);
    inside=1.0-exp(-inside*1.25);
    float e=smoothstep(R+0.004,R-0.006,r);
    col=mix(col,inside,e); alpha=mix(alpha,1.0,e);
  }
  gl_FragColor=vec4(col,alpha);
}`;
function createOrb(canvas) {
  let gl = null;
  try { gl = canvas.getContext("webgl", { premultipliedAlpha: true, alpha: true, antialias: false }); } catch (e) {}
  if (gl) {
    const sh = (type, src) => { const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s); return gl.getShaderParameter(s, gl.COMPILE_STATUS) ? s : null; };
    const vs = sh(gl.VERTEX_SHADER, ORB_VERT), fs = sh(gl.FRAGMENT_SHADER, ORB_FRAG);
    const prog = vs && fs && gl.createProgram();
    if (prog) {
      gl.attachShader(prog, vs); gl.attachShader(prog, fs); gl.linkProgram(prog);
      if (gl.getProgramParameter(prog, gl.LINK_STATUS)) {
        gl.useProgram(prog);
        const buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buf);
        gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
        const loc = gl.getAttribLocation(prog, "p"); gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
        const uRes = gl.getUniformLocation(prog, "uRes"), uTime = gl.getUniformLocation(prog, "uTime"), uAct = gl.getUniformLocation(prog, "uAct");
        return {
          draw(t, a) {
            const dpr = Math.min(orbQuality, window.devicePixelRatio || 1), W = Math.max(2, Math.round(canvas.clientWidth * dpr));
            if (canvas.width !== W) { canvas.width = W; canvas.height = W; }
            gl.viewport(0, 0, W, W);
            gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT);
            gl.uniform2f(uRes, W, W); gl.uniform1f(uTime, t); gl.uniform1f(uAct, a);
            gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
          },
        };
      }
    }
    gl = null;
  }
  // 2D fallback: layered plasma glows inside a glass circle.
  const PL = ["#8A2BE2", "#F72585", "#1E3AA8", "#D900FF", "#6A0DAD", "#F72585"].map((col, i) => ({ col, a: .05 + i * .013, b: .07 + i * .009, p: i * 1.3 }));
  return {
    draw(t, a) {
      const { c, w } = fit(canvas, 1), cx = w / 2, R = w * .35 * (1 + .022 * Math.sin(t * .698)) * (1 + .055 * a);
      c.clearRect(0, 0, w, w);
      c.globalCompositeOperation = "lighter"; glow(c, cx, cx, R * 1.45, "#8A2BE2", .35 + .2 * a); c.globalCompositeOperation = "source-over";
      c.save(); c.beginPath(); c.arc(cx, cx, R, 0, 7); c.clip();
      const base = c.createRadialGradient(cx, cx, 0, cx, cx, R); base.addColorStop(0, "#0b1450"); base.addColorStop(1, "#02030f");
      c.fillStyle = base; c.fillRect(0, 0, w, w);
      c.globalCompositeOperation = "lighter";
      PL.forEach(p => glow(c, cx + Math.sin(t * p.a + p.p) * R * .45, cx + Math.cos(t * p.b + p.p) * R * .45, R * .8, p.col, .5));
      glow(c, cx, cx, R * (.45 + .15 * a), "#FFFFFF", .55 + .3 * a);
      c.globalCompositeOperation = "source-over";
      const rim = c.createRadialGradient(cx, cx, R * .7, cx, cx, R); rim.addColorStop(0, "rgba(0,0,0,0)"); rim.addColorStop(1, "rgba(217,0,255,.35)");
      c.fillStyle = rim; c.fillRect(0, 0, w, w);
      c.restore();
    },
  };
}
const orbs = {};
const orb = key => orbs[key] || (orbs[key] = createOrb($(key + "Core")));
