"use strict";

/* ================= Welcome: wormhole of glowing rings ================= */
// A funnel of dotted rings narrows into a neck of bright rings and flares out again beyond it.
// Rings stream through it continuously while the colour drifts mint → pink → violet → blue → cyan.
// Each dot is drawn as a small glowing point sprite, so the cost follows the dots, not the screen.
const WORM_RINGS = 62, WORM_DOTS = 170;
const WORM_COMMON = `
const float TAU=6.2831853;
const vec2 THROAT=vec2(0.02,0.52);
vec3 pal(float h){
  h=fract(h)*5.0;
  float f=smoothstep(0.0,1.0,fract(h));
  vec3 mint=vec3(0.45,1.0,0.62), pink=vec3(1.0,0.45,0.86), violet=vec3(0.66,0.42,1.0), blue=vec3(0.36,0.58,1.0), cyan=vec3(0.36,0.96,0.9);
  if(h<1.0) return mix(mint,pink,f);
  if(h<2.0) return mix(pink,violet,f);
  if(h<3.0) return mix(violet,blue,f);
  if(h<4.0) return mix(blue,cyan,f);
  return mix(cyan,mint,f);
}`;
const WORM_VIGNETTE = `
float vignette(vec2 res){
  vec2 p=(gl_FragCoord.xy-0.5*res)/min(res.x,res.y);
  return 1.0-0.45*smoothstep(0.5,1.3,length(p*vec2(1.0,0.72)));
}`;
const WORM_BG_FRAG = `
precision mediump float;
uniform vec2 uRes;
uniform float uTime;
${WORM_COMMON}
${WORM_VIGNETTE}
void main(){
  vec2 p=(gl_FragCoord.xy-0.5*uRes)/min(uRes.x,uRes.y);
  float t=uTime;
  vec3 col=mix(vec3(0.004,0.03,0.035),vec3(0.02,0.17,0.17),exp(-length((p-THROAT-vec2(0.12,0.2))*vec2(0.8,0.6))*1.6));
  col+=pal(t/30.0)*0.05*exp(-length(p-THROAT)*2.5);
  vec2 hp=p-(THROAT+vec2(-0.12,-0.2)+0.1*vec2(sin(t*0.19),cos(t*0.15)));
  float haze=exp(-dot(hp*vec2(1.0,0.7),hp*vec2(1.0,0.7))/0.07)*(0.5+0.5*sin(t*0.23));
  vec3 rb=0.5+0.5*cos(TAU*(dot(p,vec2(1.2,0.7))*1.4-t*0.04+vec3(0.0,0.33,0.67)));
  col+=rb*haze*0.08;
  gl_FragColor=vec4(col*vignette(uRes),1.0);
}`;
const WORM_DOT_VERT = `
attribute vec2 a;
uniform vec2 uRes;
uniform float uTime;
varying vec3 vCol;
varying float vSize,vCore,vHalo,vCoreW,vHaloW;
${WORM_COMMON}
const float S=26.0, TN=10.0, N=${WORM_RINGS}.0, M=${WORM_DOTS}.0, NECK=0.12, HALF=0.30, RMAX=1.9;
void main(){
  float t=uTime, m=min(uRes.x,uRes.y);
  float u=a.x+fract(t*0.16);
  float z,r,w;
  if(u<S){ w=(S-u)/S; r=NECK+RMAX*pow(w,1.7); z=-HALF-1.2*sqrt(NECK*(r-NECK)); }
  else if(u<S+TN){ w=0.0; float s=(u-S)/TN; z=-HALF+2.0*HALF*s; r=NECK*(1.0+0.08*sin(s*3.1416)); }
  else { w=(u-S-TN)/S; r=NECK+RMAX*pow(w,1.7); z=HALF+1.2*sqrt(NECK*(r-NECK)); }
  vec3 A=normalize(vec3(0.42+0.04*sin(t*0.11),0.80,-0.46));
  vec2 E1=normalize(vec2(A.y,-A.x)), E2=normalize(A.xy);
  float k=abs(A.z);
  float g=1.6/(1.8-A.z*z);
  vec2 C=THROAT+A.xy*z*g;
  float R=r*g;
  float ang=a.y/M*TAU+t*0.05;
  vec2 P=C+E1*R*cos(ang)+E2*R*k*sin(ang);
  gl_Position=vec4(P*2.0*m/uRes,0.0,1.0);

  float neck=1.0-smoothstep(0.0,0.12,w);
  float fade=smoothstep(0.0,2.0,u)*smoothstep(N,N-3.0,u);
  float b=fade*mix(1.0,0.3,pow(w,0.6))*(z>0.0?mix(1.0,0.65,w):1.0)*(0.6+0.4*cos(ang-2.2));
  vCol=pal(t/30.0+u*0.012)*b;
  // where dots crowd together (the neck), scale them down so they merge into an even glowing line
  float dr=0.0032*g*m, halo=mix(3.0*dr,0.011*m,neck);
  float sp=TAU*R*sqrt(0.5+0.5*k*k)*m/M;
  vCore=dr; vHalo=halo;
  vCoreW=min(1.0,sp/(2.0*dr))*mix(1.0,1.1,neck);
  vHaloW=0.35*min(1.0,sp/(1.77*halo));
  vSize=2.0*max(dr+1.5,2.0*halo);
  gl_PointSize=vSize;
}`;
const WORM_DOT_FRAG = `
precision mediump float;
uniform highp vec2 uRes;
varying vec3 vCol;
varying float vSize,vCore,vHalo,vCoreW,vHaloW;
${WORM_VIGNETTE}
void main(){
  float d=length(gl_PointCoord-0.5)*vSize;
  float core=smoothstep(vCore+1.0,vCore-0.5,d);
  float halo=exp(-d*d/(vHalo*vHalo));
  gl_FragColor=vec4(vCol*(core*vCoreW+halo*vHaloW)*vignette(uRes),1.0);
}`;
function createTunnel(canvas) {
  let gl = null;
  try { gl = canvas.getContext("webgl", { alpha: false, antialias: false }); } catch (e) {}
  const none = { draw() {}, release() {} };
  if (!gl) return none; // the canvas's CSS gradient stands in
  const sh = (type, src) => { const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s); return gl.getShaderParameter(s, gl.COMPILE_STATUS) ? s : null; };
  const link = (vsrc, fsrc) => {
    const vs = sh(gl.VERTEX_SHADER, vsrc), fs = sh(gl.FRAGMENT_SHADER, fsrc);
    if (!vs || !fs) return null;
    const pr = gl.createProgram(); gl.attachShader(pr, vs); gl.attachShader(pr, fs); gl.linkProgram(pr);
    return gl.getProgramParameter(pr, gl.LINK_STATUS) ? pr : null;
  };
  const bg = link(ORB_VERT, WORM_BG_FRAG), dots = link(WORM_DOT_VERT, WORM_DOT_FRAG);
  if (!bg || !dots) return none;
  const buffer = data => { const b = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, b); gl.bufferData(gl.ARRAY_BUFFER, data, gl.STATIC_DRAW); return b; };
  const quad = buffer(new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]));
  const ids = new Float32Array(WORM_RINGS * WORM_DOTS * 2);
  for (let i = 0, n = 0; i < WORM_RINGS; i++) for (let j = 0; j < WORM_DOTS; j++) { ids[n++] = i; ids[n++] = j; }
  const dotBuf = buffer(ids);
  const bgP = gl.getAttribLocation(bg, "p"), dotA = gl.getAttribLocation(dots, "a");
  const bgRes = gl.getUniformLocation(bg, "uRes"), bgTime = gl.getUniformLocation(bg, "uTime");
  const dotRes = gl.getUniformLocation(dots, "uRes"), dotTime = gl.getUniformLocation(dots, "uTime");
  gl.blendFunc(gl.ONE, gl.ONE);
  let live = true;
  return {
    draw(t) {
      if (!live) return;
      const dpr = Math.min(orbQuality, window.devicePixelRatio || 1);
      const W = Math.max(2, Math.round(canvas.clientWidth * dpr)), H = Math.max(2, Math.round(canvas.clientHeight * dpr));
      if (canvas.width !== W || canvas.height !== H) { canvas.width = W; canvas.height = H; }
      gl.viewport(0, 0, W, H);
      gl.disable(gl.BLEND);
      gl.useProgram(bg);
      gl.bindBuffer(gl.ARRAY_BUFFER, quad); gl.enableVertexAttribArray(bgP); gl.vertexAttribPointer(bgP, 2, gl.FLOAT, false, 0, 0);
      gl.uniform2f(bgRes, W, H); gl.uniform1f(bgTime, t);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      gl.enable(gl.BLEND);
      gl.useProgram(dots);
      gl.bindBuffer(gl.ARRAY_BUFFER, dotBuf); gl.enableVertexAttribArray(dotA); gl.vertexAttribPointer(dotA, 2, gl.FLOAT, false, 0, 0);
      gl.uniform2f(dotRes, W, H); gl.uniform1f(dotTime, t);
      gl.drawArrays(gl.POINTS, 0, WORM_RINGS * WORM_DOTS);
    },
    release() {
      if (!live) return;
      live = false;
      const ext = gl.getExtension("WEBGL_lose_context"); if (ext) ext.loseContext();
    },
  };
}
const tunnel = createTunnel($("tunnel"));
