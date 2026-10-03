import{c as ue,r,j as e,i as j,u as fe,a as de,b as pe,d as me,e as he,T as xe,L as ve,f as D}from"./index-Dp7vl3jE.js";import{u as $,m as G,a as K,b as J,c as Q,d as Z}from"./chunk-TW2E3XVA-puybaMog.js";import{R as ge,M as ee,r as ne,a as te}from"./index-DO3fWBX2.js";import"./vs2015-D9705uCu.js";import{C as F,v as ye}from"./versionCompare-B2m1vOrK.js";import{V as _,S as we,O as be,P as je,a as oe,b as Ne,M as Me,W as Ce,c as Re,C as Ee}from"./three.module-B_9urBBX.js";import"./index-BsIVLvgp.js";const Se=[["path",{d:"M11 6a13 13 0 0 0 8.4-2.8A1 1 0 0 1 21 4v12a1 1 0 0 1-1.6.8A13 13 0 0 0 11 14H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",key:"q8bfy3"}],["path",{d:"M6 14a12 12 0 0 0 2.4 7.2 2 2 0 0 0 3.2-2.4A8 8 0 0 1 10 14",key:"1853fq"}],["path",{d:"M8 6v8",key:"15ugcq"}]],Ae=ue("megaphone",Se);const z=8,Ie=`
#define MAX_COLORS ${z}
uniform vec2 uCanvas;
uniform float uTime;
uniform float uSpeed;
uniform vec2 uRot;
uniform int uColorCount;
uniform vec3 uColors[MAX_COLORS];
uniform int uTransparent;
uniform float uScale;
uniform float uFrequency;
uniform float uWarpStrength;
uniform vec2 uPointer; // in NDC [-1,1]
uniform float uMouseInfluence;
uniform float uParallax;
uniform float uNoise;
uniform int uIterations;
uniform float uIntensity;
uniform float uBandWidth;
varying vec2 vUv;

void main() {
  float t = uTime * uSpeed;
  vec2 p = vUv * 2.0 - 1.0;
  p += uPointer * uParallax * 0.1;
  vec2 rp = vec2(p.x * uRot.x - p.y * uRot.y, p.x * uRot.y + p.y * uRot.x);
  vec2 q = vec2(rp.x * (uCanvas.x / uCanvas.y), rp.y);
  q /= max(uScale, 0.0001);
  q /= 0.5 + 0.2 * dot(q, q);
  q += 0.2 * cos(t) - 7.56;
  vec2 toward = (uPointer - rp);
  q += toward * uMouseInfluence * 0.2;

    for (int j = 0; j < 5; j++) {
      if (j >= uIterations - 1) break;
      vec2 rr = sin(1.5 * (q.yx * uFrequency) + 2.0 * cos(q * uFrequency));
      q += (rr - q) * 0.15;
    }

    vec3 col = vec3(0.0);
    float a = 1.0;

    if (uColorCount > 0) {
      vec2 s = q;
      vec3 sumCol = vec3(0.0);
      float cover = 0.0;
      for (int i = 0; i < MAX_COLORS; ++i) {
            if (i >= uColorCount) break;
            s -= 0.01;
            vec2 r = sin(1.5 * (s.yx * uFrequency) + 2.0 * cos(s * uFrequency));
            float m0 = length(r + sin(5.0 * r.y * uFrequency - 3.0 * t + float(i)) / 4.0);
            float kBelow = clamp(uWarpStrength, 0.0, 1.0);
            float kMix = pow(kBelow, 0.3); // strong response across 0..1
            float gain = 1.0 + max(uWarpStrength - 1.0, 0.0); // allow >1 to amplify displacement
            vec2 disp = (r - s) * kBelow;
            vec2 warped = s + disp * gain;
            float m1 = length(warped + sin(5.0 * warped.y * uFrequency - 3.0 * t + float(i)) / 4.0);
            float m = mix(m0, m1, kMix);
            float w = 1.0 - exp(-uBandWidth / exp(uBandWidth * m));
            sumCol += uColors[i] * w;
            cover = max(cover, w);
      }
      col = clamp(sumCol, 0.0, 1.0);
      a = uTransparent > 0 ? cover : 1.0;
    } else {
        vec2 s = q;
        for (int k = 0; k < 3; ++k) {
            s -= 0.01;
            vec2 r = sin(1.5 * (s.yx * uFrequency) + 2.0 * cos(s * uFrequency));
            float m0 = length(r + sin(5.0 * r.y * uFrequency - 3.0 * t + float(k)) / 4.0);
            float kBelow = clamp(uWarpStrength, 0.0, 1.0);
            float kMix = pow(kBelow, 0.3);
            float gain = 1.0 + max(uWarpStrength - 1.0, 0.0);
            vec2 disp = (r - s) * kBelow;
            vec2 warped = s + disp * gain;
            float m1 = length(warped + sin(5.0 * warped.y * uFrequency - 3.0 * t + float(k)) / 4.0);
            float m = mix(m0, m1, kMix);
            col[k] = 1.0 - exp(-uBandWidth / exp(uBandWidth * m));
        }
        a = uTransparent > 0 ? max(max(col.r, col.g), col.b) : 1.0;
    }

    col *= uIntensity;

    if (uNoise > 0.0001) {
      float n = fract(sin(dot(gl_FragCoord.xy + vec2(uTime), vec2(12.9898, 78.233))) * 43758.5453123);
      col += (n - 0.5) * uNoise;
      col = clamp(col, 0.0, 1.0);
    }

    vec3 rgb = (uTransparent > 0) ? col * a : col;
    gl_FragColor = vec4(rgb, a);
}
`,ke=`
varying vec2 vUv;
void main() {
  vUv = uv;
  gl_Position = vec4(position, 1.0);
}
`;function Te({className:q,style:l,rotation:x=90,speed:s=.2,colors:u=[],transparent:v=!0,autoRotate:g=0,scale:R=1,frequency:N=1,warpStrength:E=1,mouseInfluence:S=1,parallax:d=.5,noise:A=.15,iterations:y=1,intensity:I=1.5,bandWidth:k=6}){const T=r.useRef(null),U=r.useRef(null),M=r.useRef(null),O=r.useRef(null),P=r.useRef(null),B=r.useRef(x),m=r.useRef(g),n=r.useRef(new _(0,0)),a=r.useRef(new _(0,0)),ae=r.useRef(8);return r.useEffect(()=>{const t=T.current,p=new we,C=new be(-1,1,1,-1,0,1),h=new je(2,2),c=Array.from({length:z},()=>new oe(0,0,0)),o=new Ne({vertexShader:ke,fragmentShader:Ie,uniforms:{uCanvas:{value:new _(1,1)},uTime:{value:0},uSpeed:{value:s},uRot:{value:new _(1,0)},uColorCount:{value:0},uColors:{value:c},uTransparent:{value:v?1:0},uScale:{value:R},uFrequency:{value:N},uWarpStrength:{value:E},uPointer:{value:new _(0,0)},uMouseInfluence:{value:S},uParallax:{value:d},uNoise:{value:A},uIterations:{value:y},uIntensity:{value:I},uBandWidth:{value:k}},premultipliedAlpha:!0,transparent:!0});O.current=o;const w=new Me(h,o);p.add(w);const i=new Ce({antialias:!1,powerPreference:"high-performance",alpha:!0});U.current=i,i.outputColorSpace=Re,i.setPixelRatio(Math.min(window.devicePixelRatio||1,2)),i.setClearColor(0,v?0:1),i.domElement.style.width="100%",i.domElement.style.height="100%",i.domElement.style.display="block",t.appendChild(i.domElement);const H=new Ee,V=()=>{const b=t.clientWidth||1,L=t.clientHeight||1;i.setSize(b,L,!1),o.uniforms.uCanvas.value.set(b,L)};if(V(),"ResizeObserver"in window){const b=new ResizeObserver(V);b.observe(t),P.current=b}else window.addEventListener("resize",V);const W=()=>{const b=H.getDelta(),L=H.elapsedTime;o.uniforms.uTime.value=L;const Y=(B.current%360+m.current*L)*Math.PI/180,re=Math.cos(Y),le=Math.sin(Y);o.uniforms.uRot.value.set(re,le);const X=a.current,ie=n.current,ce=Math.min(1,b*ae.current);X.lerp(ie,ce),o.uniforms.uPointer.value.copy(X),i.render(p,C),M.current=requestAnimationFrame(W)};return M.current=requestAnimationFrame(W),()=>{M.current!==null&&cancelAnimationFrame(M.current),P.current?P.current.disconnect():window.removeEventListener("resize",V),h.dispose(),o.dispose(),i.dispose(),i.forceContextLoss(),i.domElement&&i.domElement.parentElement===t&&t.removeChild(i.domElement)}},[]),r.useEffect(()=>{const t=O.current,p=U.current;if(!t)return;B.current=x,m.current=g,t.uniforms.uSpeed.value=s,t.uniforms.uScale.value=R,t.uniforms.uFrequency.value=N,t.uniforms.uWarpStrength.value=E,t.uniforms.uMouseInfluence.value=S,t.uniforms.uParallax.value=d,t.uniforms.uNoise.value=A,t.uniforms.uIterations.value=y,t.uniforms.uIntensity.value=I,t.uniforms.uBandWidth.value=k;const C=c=>{const o=c.replace("#","").trim(),w=o.length===3?[parseInt(o[0]+o[0],16),parseInt(o[1]+o[1],16),parseInt(o[2]+o[2],16)]:[parseInt(o.slice(0,2),16),parseInt(o.slice(2,4),16),parseInt(o.slice(4,6),16)];return new oe(w[0]/255,w[1]/255,w[2]/255)},h=(u||[]).filter(Boolean).slice(0,z).map(C);for(let c=0;c<z;c++){const o=t.uniforms.uColors.value[c];c<h.length?o.copy(h[c]):o.set(0,0,0)}t.uniforms.uColorCount.value=h.length,t.uniforms.uTransparent.value=v?1:0,p&&p.setClearColor(0,v?0:1)},[x,g,s,R,N,E,S,d,A,y,I,k,u,v]),r.useEffect(()=>{const t=O.current,p=T.current;if(!t||!p)return;const C=h=>{const c=p.getBoundingClientRect(),o=(h.clientX-c.left)/(c.width||1)*2-1,w=-((h.clientY-c.top)/(c.height||1)*2-1);n.current.set(o,w)};return p.addEventListener("pointermove",C),()=>{p.removeEventListener("pointermove",C)}},[]),e.jsx("div",{ref:T,className:`w-full h-full relative overflow-hidden ${q}`,style:l})}const f={MINUTE:60*1e3,HOUR:3600*1e3,DAY:1440*60*1e3,WEEK:10080*60*1e3,MONTH:720*60*60*1e3,YEAR:365*24*60*60*1e3};function se(q){const l=Date.now()-q;if(l<f.MINUTE)return j.t("time.justNow","刚刚");if(l<f.HOUR){const s=Math.floor(l/f.MINUTE);return j.t("time.minutesAgo",{count:s,defaultValue:`${s} 分钟前`})}if(l<f.DAY){const s=Math.floor(l/f.HOUR);return j.t("time.hoursAgo",{count:s,defaultValue:`${s} 小时前`})}if(l<f.WEEK){const s=Math.floor(l/f.DAY);return j.t("time.daysAgo",{count:s,defaultValue:`${s} 天前`})}if(l<f.MONTH){const s=Math.floor(l/f.WEEK);return j.t("time.weeksAgo",{count:s,defaultValue:`${s} 星期前`})}if(l<f.YEAR){const s=Math.floor(l/f.MONTH);return j.t("time.monthsAgo",{count:s,defaultValue:`${s} 个月前`})}const x=Math.floor(l/f.YEAR);return j.t("time.yearsAgo",{count:x,defaultValue:`${x} 年前`})}function ze(){const{isDesktop:x}=fe(),{openExternalUrl:s}=de(),{t:u}=pe(),[v]=me(),g=v.has("showAnnouncement"),R=v.has("showUpdate"),[N,E]=r.useState(!1),S=he(),[d,A]=r.useState({current:F,isLatest:!0,latestTimestamp:Date.now()}),[y,I]=r.useState({title:"",timestamp:Date.now(),content:""}),{isOpen:k,onOpen:T,onOpenChange:U}=$(),{isOpen:M,onOpen:O,onOpenChange:P}=$();r.useEffect(()=>{if(!g)return;(async()=>{try{let n;{const a=await fetch("/api/announcement");if(!a.ok)throw new Error("公告信息获取失败");n=await a.json()}I({title:n.title,timestamp:n.timestamp,content:n.content,expireDays:n.expireDays}),O()}catch(n){console.error("获取公告信息出错:",n)}})()},[g]),r.useEffect(()=>{if(g&&!N)return;(async()=>{try{let n;{const a=await fetch("/api/version");if(!a.ok)throw new Error("版本信息获取失败");n=await a.json()}if(n.latestVersion){const a=ye(n.latestVersion,F)<=0;A({current:F,latest:n.latestVersion,updateLog:n.updateLog,isLatest:a,latestTimestamp:n.timestamp}),!a&&R&&T()}}catch(n){console.error("获取版本信息出错:",n)}})()},[g,N]);const B=()=>{document.body.classList.add("fade-out"),setTimeout(()=>{S("/settings/general"),setTimeout(()=>{document.body.classList.contains("fade-out")&&window.location.reload()},300)},500)};return e.jsxs(e.Fragment,{children:[x&&e.jsx(xe,{autoHide:!1}),e.jsxs("div",{children:[e.jsx("style",{children:`
          :root {
            --button-color: #de40ff;
          }

          body {
            background: #0f0f0f;
          }

          #title {
            font-size: 60px;
            color: white;
            user-select: none;
            line-height: 3rem;
            z-index: 5;
            transform: translateY(-10px);
          }

          #now-playing-text {
            color: #ffffff;
            font-weight: normal;
            margin: 0 0.75rem;
            user-select: none;
          }

          body.fade-out {
            animation: dissolve 0.5s forwards;
          }

          @keyframes dissolve {
            0% {
              filter: blur(0) brightness(1) hue-rotate(0deg) saturate(100%) contrast(100%) drop-shadow(0 0 0 rgba(255, 255, 255, 0));
            }

            25% {
              filter: blur(2px) brightness(1.8) hue-rotate(30deg) saturate(125%) contrast(125%) drop-shadow(0 0 6px #ff00ff);
            }

            50% {
              filter: blur(8px) brightness(2.2) hue-rotate(0deg) saturate(150%) contrast(150%) drop-shadow(0 0 12px #00ffff);
            }

            75% {
              filter: blur(12px) brightness(1.2) hue-rotate(-30deg) saturate(100%) contrast(100%) drop-shadow(0 0 16px #ffff00);
            }

            100% {
              filter: blur(20px) brightness(0) hue-rotate(0deg) saturate(0%) contrast(100%) drop-shadow(0 0 0 rgba(255, 255, 255, 0));
            }
          }

          #current-version-div, #update-text {
            user-select: none;
          }

          /* 动画按钮样式 */
          .animated-button {
            position: relative;
            display: flex;
            align-items: center;
            gap: 4px;
            padding: 16px 36px;
            border: 4px solid;
            border-color: transparent;
            font-size: 16px;
            background-color: inherit;
            border-radius: 100px;
            font-weight: 600;
            color: var(--button-color);
            box-shadow: 0 0 0 2px var(--button-color);
            cursor: pointer;
            overflow: hidden;
            transition: all 0.6s cubic-bezier(0.23, 1, 0.32, 1);
            mix-blend-mode: plus-lighter;
            filter: brightness(2.0) saturate(1.2);
            transform: translateY(-10px);
          }
          .animated-button svg {
            position: absolute;
            width: 24px;
            fill: var(--button-color);
            z-index: 9;
            transition: all 0.8s cubic-bezier(0.23, 1, 0.32, 1);
          }
          .animated-button .arr-1 {
            right: 16px;
          }
          .animated-button .arr-2 {
            left: -25%;
          }
          .animated-button .button-circle {
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            width: 20px;
            height: 20px;
            background-color: var(--button-color);
            border-radius: 50%;
            opacity: 0;
            transition: all 0.8s cubic-bezier(0.23, 1, 0.32, 1);
          }
          .animated-button .button-text {
            position: relative;
            z-index: 1;
            transform: translateX(-12px);
            transition: all 0.8s cubic-bezier(0.23, 1, 0.32, 1);
          }
          .animated-button:hover {
            box-shadow: 0 0 0 12px transparent;
            color: #0f0f0f;
            border-radius: 100px;
          }
          .animated-button:hover .arr-1 {
            right: -25%;
          }
          .animated-button:hover .arr-2 {
            left: 16px;
          }
          .animated-button:hover .button-text {
            transform: translateX(12px);
          }
          .animated-button:hover svg {
            fill: #0f0f0f;
          }
          .animated-button:active {
            scale: 0.95;
            box-shadow: 0 0 0 4px var(--button-color);
          }
          .animated-button:hover .button-circle {
            width: 220px;
            height: 220px;
            opacity: 1;
          }
        `}),e.jsx("div",{"data-overlay-container":"true",children:e.jsxs("main",{children:[e.jsx("div",{children:e.jsx("div",{className:"absolute bottom-0 top-0 flex h-screen w-full flex-col",children:e.jsx("div",{className:"relative flex flex-col gap-20 text-white md:gap-10",children:e.jsxs("div",{className:"flex h-screen w-full items-center justify-center relative overflow-hidden",children:[e.jsxs("div",{className:"flex flex-col items-center gap-10",id:"main-content",children:[e.jsx("div",{className:"absolute top-0 w-full h-full z-0 bg-[#060010]",id:"bg-container",children:e.jsx(Te,{colors:["#a855f7"],rotation:90,speed:.2,scale:1,frequency:1,warpStrength:1,mouseInfluence:1,noise:.15,parallax:.5,iterations:1,intensity:1.5,bandWidth:6,transparent:!0,autoRotate:0})}),e.jsx("div",{className:"absolute top-4 right-4 z-50",children:e.jsx(ve,{size:"sm",variant:"flat"})}),e.jsx("div",{className:"flex items-center justify-between",children:e.jsxs("h2",{className:"inline-block font-sourcehan text-center text-3xl lg:text-4xl md:text-3xl",id:"title",children:[u("home.welcome"),e.jsx("span",{className:"px-2 font-dela",id:"now-playing-text",children:u("home.nowPlaying")})]})}),e.jsxs("button",{className:"animated-button",onClick:B,children:[e.jsx("svg",{className:"arr-2",viewBox:"0 0 24 24",xmlns:"http://www.w3.org/2000/svg",children:e.jsx("path",{d:"M16.1716 10.9999L10.8076 5.63589L12.2218 4.22168L20 11.9999L12.2218 19.778L10.8076 18.3638L16.1716 12.9999H4V10.9999H16.1716Z"})}),e.jsx("span",{className:"button-text",children:u("home.goToSettings")}),e.jsx("span",{className:"button-circle"}),e.jsx("svg",{className:"arr-1",viewBox:"0 0 24 24",xmlns:"http://www.w3.org/2000/svg",children:e.jsx("path",{d:"M16.1716 10.9999L10.8076 5.63589L12.2218 4.22168L20 11.9999L12.2218 19.778L10.8076 18.3638L16.1716 12.9999H4V10.9999H16.1716Z"})})]})]}),e.jsxs("div",{style:{position:"fixed",bottom:"2.0rem",width:"100%",textAlign:"center"},children:[e.jsxs("div",{className:"font-poppins",id:"current-version-div",style:{marginBottom:"0.2rem"},children:[u("home.currentVersion"),d.current]}),e.jsx("div",{className:"font-poppins",id:"update-text",children:d.isLatest?u("home.isLatest"):d.latest?e.jsxs("a",{className:"cursor-pointer",onClick:()=>{s("https://gitee.com/widdit/now-playing/releases")},children:[u("home.hasUpdate"),d.latest]}):null})]})]})})})}),e.jsx(G,{size:"xl",isDismissable:!1,scrollBehavior:"inside",hideCloseButton:!0,isOpen:k,onOpenChange:U,className:"px-3 py-2",children:e.jsx(K,{className:"font-poppins",children:m=>e.jsxs(e.Fragment,{children:[e.jsxs(J,{className:"flex justify-between items-center",children:[e.jsxs("div",{className:"flex items-center gap-2",children:[e.jsx("div",{className:"breathing-bg flex h-9 w-9 items-center justify-center rounded-full bg-[#15283c]",children:e.jsx(ge,{size:20,strokeWidth:2,color:"#0485f7"})}),d.latest," - ",u("home.newVersionAvailable","新版本可用")]}),e.jsx("div",{className:"font-normal text-sm text-default-500",children:se(d.latestTimestamp)})]}),e.jsx(Q,{children:e.jsx("div",{className:"markdown-body",children:e.jsx(ee,{rehypePlugins:[ne,te],components:{img:({node:n,...a})=>e.jsx("img",{...a,referrerPolicy:"no-referrer",className:"max-w-full h-auto rounded-lg my-2"}),a:({node:n,...a})=>e.jsx("a",{...a,className:"text-primary hover:underline",target:"_blank",rel:"noopener noreferrer"})},children:d.updateLog})})}),e.jsxs(Z,{children:[e.jsx(D,{color:"default",variant:"flat",onPress:m,children:u("common.cancel")}),e.jsx(D,{color:"primary",onPress:()=>{m(),s("https://gitee.com/widdit/now-playing/releases")},children:u("common.confirm")})]})]})})}),e.jsx(G,{size:"xl",isDismissable:!1,isKeyboardDismissDisabled:!0,scrollBehavior:"inside",hideCloseButton:!0,isOpen:M,onOpenChange:P,className:"px-3 py-2",children:e.jsx(K,{className:"font-poppins",children:m=>e.jsxs(e.Fragment,{children:[e.jsxs(J,{className:"flex justify-between items-center",children:[e.jsxs("div",{className:"flex items-center gap-2",children:[e.jsx("div",{className:"breathing-bg flex h-9 w-9 items-center justify-center rounded-full bg-[#15283c]",children:e.jsx(Ae,{size:20,color:"#0485f7"})}),y.title]}),e.jsx("div",{className:"font-normal text-sm text-default-500",children:se(y.timestamp)})]}),e.jsx(Q,{children:e.jsx("div",{className:"markdown-body",children:e.jsx(ee,{rehypePlugins:[ne,te],components:{img:({node:n,...a})=>e.jsx("img",{...a,referrerPolicy:"no-referrer",className:"max-w-full h-auto rounded-lg my-2"}),a:({node:n,...a})=>e.jsx("a",{...a,className:"text-primary hover:underline",target:"_blank",rel:"noopener noreferrer"})},children:y.content})})}),e.jsx(Z,{children:e.jsx(D,{color:"primary",onPress:()=>{m(),E(!0)},children:u("common.confirm")})})]})})})]})})]})]})}export{ze as default};
