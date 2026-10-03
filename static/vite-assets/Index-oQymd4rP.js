import{c as ce,r as a,j as e,u as ue,a as fe,b as de,d as pe,e as me,T as he,L as xe,f as D}from"./index-BsbITMST.js";import{u as X,m as $,a as G,b as K,c as J,d as Q}from"./chunk-TW2E3XVA-DXRlVWiH.js";import{R as ve,M as Z,r as ee,a as ne}from"./index-Cy7sNxe0.js";import"./vs2015-D9705uCu.js";import{C as F,v as ge}from"./versionCompare-B2m1vOrK.js";import{V as L,S as ye,O as we,P as be,a as te,b as je,M as Me,W as Ne,c as Ce,C as Re}from"./three.module-B_9urBBX.js";import"./index-BYLKxJUo.js";const Ee=[["path",{d:"M11 6a13 13 0 0 0 8.4-2.8A1 1 0 0 1 21 4v12a1 1 0 0 1-1.6.8A13 13 0 0 0 11 14H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",key:"q8bfy3"}],["path",{d:"M6 14a12 12 0 0 0 2.4 7.2 2 2 0 0 0 3.2-2.4A8 8 0 0 1 10 14",key:"1853fq"}],["path",{d:"M8 6v8",key:"15ugcq"}]],Se=ce("megaphone",Ee);const z=8,Ie=`
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
`;function Te({className:_,style:r,rotation:b=90,speed:f=.2,colors:c=[],transparent:x=!0,autoRotate:v=0,scale:C=1,frequency:j=1,warpStrength:R=1,mouseInfluence:E=1,parallax:d=.5,noise:S=.15,iterations:g=1,intensity:I=1.5,bandWidth:k=6}){const T=a.useRef(null),q=a.useRef(null),M=a.useRef(null),O=a.useRef(null),A=a.useRef(null),U=a.useRef(b),m=a.useRef(v),n=a.useRef(new L(0,0)),o=a.useRef(new L(0,0)),oe=a.useRef(8);return a.useEffect(()=>{const t=T.current,p=new ye,N=new we(-1,1,1,-1,0,1),h=new be(2,2),i=Array.from({length:z},()=>new te(0,0,0)),s=new je({vertexShader:ke,fragmentShader:Ie,uniforms:{uCanvas:{value:new L(1,1)},uTime:{value:0},uSpeed:{value:f},uRot:{value:new L(1,0)},uColorCount:{value:0},uColors:{value:i},uTransparent:{value:x?1:0},uScale:{value:C},uFrequency:{value:j},uWarpStrength:{value:R},uPointer:{value:new L(0,0)},uMouseInfluence:{value:E},uParallax:{value:d},uNoise:{value:S},uIterations:{value:g},uIntensity:{value:I},uBandWidth:{value:k}},premultipliedAlpha:!0,transparent:!0});O.current=s;const y=new Me(h,s);p.add(y);const l=new Ne({antialias:!1,powerPreference:"high-performance",alpha:!0});q.current=l,l.outputColorSpace=Ce,l.setPixelRatio(Math.min(window.devicePixelRatio||1,2)),l.setClearColor(0,x?0:1),l.domElement.style.width="100%",l.domElement.style.height="100%",l.domElement.style.display="block",t.appendChild(l.domElement);const H=new Re,B=()=>{const w=t.clientWidth||1,P=t.clientHeight||1;l.setSize(w,P,!1),s.uniforms.uCanvas.value.set(w,P)};if(B(),"ResizeObserver"in window){const w=new ResizeObserver(B);w.observe(t),A.current=w}else window.addEventListener("resize",B);const W=()=>{const w=H.getDelta(),P=H.elapsedTime;s.uniforms.uTime.value=P;const V=(U.current%360+m.current*P)*Math.PI/180,ae=Math.cos(V),re=Math.sin(V);s.uniforms.uRot.value.set(ae,re);const Y=o.current,le=n.current,ie=Math.min(1,w*oe.current);Y.lerp(le,ie),s.uniforms.uPointer.value.copy(Y),l.render(p,N),M.current=requestAnimationFrame(W)};return M.current=requestAnimationFrame(W),()=>{M.current!==null&&cancelAnimationFrame(M.current),A.current?A.current.disconnect():window.removeEventListener("resize",B),h.dispose(),s.dispose(),l.dispose(),l.forceContextLoss(),l.domElement&&l.domElement.parentElement===t&&t.removeChild(l.domElement)}},[]),a.useEffect(()=>{const t=O.current,p=q.current;if(!t)return;U.current=b,m.current=v,t.uniforms.uSpeed.value=f,t.uniforms.uScale.value=C,t.uniforms.uFrequency.value=j,t.uniforms.uWarpStrength.value=R,t.uniforms.uMouseInfluence.value=E,t.uniforms.uParallax.value=d,t.uniforms.uNoise.value=S,t.uniforms.uIterations.value=g,t.uniforms.uIntensity.value=I,t.uniforms.uBandWidth.value=k;const N=i=>{const s=i.replace("#","").trim(),y=s.length===3?[parseInt(s[0]+s[0],16),parseInt(s[1]+s[1],16),parseInt(s[2]+s[2],16)]:[parseInt(s.slice(0,2),16),parseInt(s.slice(2,4),16),parseInt(s.slice(4,6),16)];return new te(y[0]/255,y[1]/255,y[2]/255)},h=(c||[]).filter(Boolean).slice(0,z).map(N);for(let i=0;i<z;i++){const s=t.uniforms.uColors.value[i];i<h.length?s.copy(h[i]):s.set(0,0,0)}t.uniforms.uColorCount.value=h.length,t.uniforms.uTransparent.value=x?1:0,p&&p.setClearColor(0,x?0:1)},[b,v,f,C,j,R,E,d,S,g,I,k,c,x]),a.useEffect(()=>{const t=O.current,p=T.current;if(!t||!p)return;const N=h=>{const i=p.getBoundingClientRect(),s=(h.clientX-i.left)/(i.width||1)*2-1,y=-((h.clientY-i.top)/(i.height||1)*2-1);n.current.set(s,y)};return p.addEventListener("pointermove",N),()=>{p.removeEventListener("pointermove",N)}},[]),e.jsx("div",{ref:T,className:`w-full h-full relative overflow-hidden ${_}`,style:r})}const u={MINUTE:60*1e3,HOUR:3600*1e3,DAY:1440*60*1e3,WEEK:10080*60*1e3,MONTH:720*60*60*1e3,YEAR:365*24*60*60*1e3};function se(_){const r=Date.now()-_;return r<u.MINUTE?"刚刚":r<u.HOUR?`${Math.floor(r/u.MINUTE)} 分钟前`:r<u.DAY?`${Math.floor(r/u.HOUR)} 小时前`:r<u.WEEK?`${Math.floor(r/u.DAY)} 天前`:r<u.MONTH?`${Math.floor(r/u.WEEK)} 星期前`:r<u.YEAR?`${Math.floor(r/u.MONTH)} 个月前`:`${Math.floor(r/u.YEAR)} 年前`}function ze(){const{isDesktop:b}=ue(),{openExternalUrl:f}=fe(),{t:c}=de(),[x]=pe(),v=x.has("showAnnouncement"),C=x.has("showUpdate"),[j,R]=a.useState(!1),E=me(),[d,S]=a.useState({current:F,isLatest:!0,latestTimestamp:Date.now()}),[g,I]=a.useState({title:"",timestamp:Date.now(),content:""}),{isOpen:k,onOpen:T,onOpenChange:q}=X(),{isOpen:M,onOpen:O,onOpenChange:A}=X();a.useEffect(()=>{if(!v)return;(async()=>{try{let n;{const o=await fetch("/api/announcement");if(!o.ok)throw new Error("公告信息获取失败");n=await o.json()}I({title:n.title,timestamp:n.timestamp,content:n.content,expireDays:n.expireDays}),O()}catch(n){console.error("获取公告信息出错:",n)}})()},[v]),a.useEffect(()=>{if(v&&!j)return;(async()=>{try{let n;{const o=await fetch("/api/version");if(!o.ok)throw new Error("版本信息获取失败");n=await o.json()}if(n.latestVersion){const o=ge(n.latestVersion,F)<=0;S({current:F,latest:n.latestVersion,updateLog:n.updateLog,isLatest:o,latestTimestamp:n.timestamp}),!o&&C&&T()}}catch(n){console.error("获取版本信息出错:",n)}})()},[v,j]);const U=()=>{document.body.classList.add("fade-out"),setTimeout(()=>{E("/settings/general"),setTimeout(()=>{document.body.classList.contains("fade-out")&&window.location.reload()},300)},500)};return e.jsxs(e.Fragment,{children:[b&&e.jsx(he,{autoHide:!1}),e.jsxs("div",{children:[e.jsx("style",{children:`
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
        `}),e.jsx("div",{"data-overlay-container":"true",children:e.jsxs("main",{children:[e.jsx("div",{children:e.jsx("div",{className:"absolute bottom-0 top-0 flex h-screen w-full flex-col",children:e.jsx("div",{className:"relative flex flex-col gap-20 text-white md:gap-10",children:e.jsxs("div",{className:"flex h-screen w-full items-center justify-center relative overflow-hidden",children:[e.jsxs("div",{className:"flex flex-col items-center gap-10",id:"main-content",children:[e.jsx("div",{className:"absolute top-0 w-full h-full z-0 bg-[#060010]",id:"bg-container",children:e.jsx(Te,{colors:["#a855f7"],rotation:90,speed:.2,scale:1,frequency:1,warpStrength:1,mouseInfluence:1,noise:.15,parallax:.5,iterations:1,intensity:1.5,bandWidth:6,transparent:!0,autoRotate:0})}),e.jsx("div",{className:"absolute top-4 right-4 z-50",children:e.jsx(xe,{size:"sm",variant:"flat"})}),e.jsx("div",{className:"flex items-center justify-between",children:e.jsxs("h2",{className:"inline-block font-sourcehan text-center text-3xl lg:text-4xl md:text-3xl",id:"title",children:[c("home.welcome"),e.jsx("span",{className:"px-2 font-dela",id:"now-playing-text",children:c("home.nowPlaying")})]})}),e.jsxs("button",{className:"animated-button",onClick:U,children:[e.jsx("svg",{className:"arr-2",viewBox:"0 0 24 24",xmlns:"http://www.w3.org/2000/svg",children:e.jsx("path",{d:"M16.1716 10.9999L10.8076 5.63589L12.2218 4.22168L20 11.9999L12.2218 19.778L10.8076 18.3638L16.1716 12.9999H4V10.9999H16.1716Z"})}),e.jsx("span",{className:"button-text",children:c("home.goToSettings")}),e.jsx("span",{className:"button-circle"}),e.jsx("svg",{className:"arr-1",viewBox:"0 0 24 24",xmlns:"http://www.w3.org/2000/svg",children:e.jsx("path",{d:"M16.1716 10.9999L10.8076 5.63589L12.2218 4.22168L20 11.9999L12.2218 19.778L10.8076 18.3638L16.1716 12.9999H4V10.9999H16.1716Z"})})]})]}),e.jsxs("div",{style:{position:"fixed",bottom:"2.0rem",width:"100%",textAlign:"center"},children:[e.jsxs("div",{className:"font-poppins",id:"current-version-div",style:{marginBottom:"0.2rem"},children:[c("home.currentVersion"),d.current]}),e.jsx("div",{className:"font-poppins",id:"update-text",children:d.isLatest?c("home.isLatest"):d.latest?e.jsxs("a",{className:"cursor-pointer",onClick:()=>{f("https://gitee.com/widdit/now-playing/releases")},children:[c("home.hasUpdate"),d.latest]}):null})]})]})})})}),e.jsx($,{size:"xl",isDismissable:!1,scrollBehavior:"inside",hideCloseButton:!0,isOpen:k,onOpenChange:q,className:"px-3 py-2",children:e.jsx(G,{className:"font-poppins",children:m=>e.jsxs(e.Fragment,{children:[e.jsxs(K,{className:"flex justify-between items-center",children:[e.jsxs("div",{className:"flex items-center gap-2",children:[e.jsx("div",{className:"breathing-bg flex h-9 w-9 items-center justify-center rounded-full bg-[#15283c]",children:e.jsx(ve,{size:20,strokeWidth:2,color:"#0485f7"})}),d.latest," 新版本可用"]}),e.jsx("div",{className:"font-normal text-sm text-default-500",children:se(d.latestTimestamp)})]}),e.jsx(J,{children:e.jsx("div",{className:"markdown-body",children:e.jsx(Z,{rehypePlugins:[ee,ne],components:{img:({node:n,...o})=>e.jsx("img",{...o,referrerPolicy:"no-referrer",className:"max-w-full h-auto rounded-lg my-2"}),a:({node:n,...o})=>e.jsx("a",{...o,className:"text-primary hover:underline",target:"_blank",rel:"noopener noreferrer"})},children:d.updateLog})})}),e.jsxs(Q,{children:[e.jsx(D,{color:"default",variant:"flat",onPress:m,children:c("common.cancel")}),e.jsx(D,{color:"primary",onPress:()=>{m(),f("https://gitee.com/widdit/now-playing/releases")},children:c("common.confirm")})]})]})})}),e.jsx($,{size:"xl",isDismissable:!1,isKeyboardDismissDisabled:!0,scrollBehavior:"inside",hideCloseButton:!0,isOpen:M,onOpenChange:A,className:"px-3 py-2",children:e.jsx(G,{className:"font-poppins",children:m=>e.jsxs(e.Fragment,{children:[e.jsxs(K,{className:"flex justify-between items-center",children:[e.jsxs("div",{className:"flex items-center gap-2",children:[e.jsx("div",{className:"breathing-bg flex h-9 w-9 items-center justify-center rounded-full bg-[#15283c]",children:e.jsx(Se,{size:20,color:"#0485f7"})}),g.title]}),e.jsx("div",{className:"font-normal text-sm text-default-500",children:se(g.timestamp)})]}),e.jsx(J,{children:e.jsx("div",{className:"markdown-body",children:e.jsx(Z,{rehypePlugins:[ee,ne],components:{img:({node:n,...o})=>e.jsx("img",{...o,referrerPolicy:"no-referrer",className:"max-w-full h-auto rounded-lg my-2"}),a:({node:n,...o})=>e.jsx("a",{...o,className:"text-primary hover:underline",target:"_blank",rel:"noopener noreferrer"})},children:g.content})})}),e.jsx(Q,{children:e.jsx(D,{color:"primary",onPress:()=>{m(),R(!0)},children:c("common.confirm")})})]})})})]})})]})]})}export{ze as default};
