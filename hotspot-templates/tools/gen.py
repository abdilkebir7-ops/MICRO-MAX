import json,re,html,sys,pathlib
HERE=pathlib.Path(__file__).resolve().parent
ROOT=HERE.parent
BASE=(ROOT.parent/'app/src/main/assets/login_base.html').read_text(encoding='utf-8')
P=lambda **k:k
PRESETS=[
 P(id="cyan-blue",name="Cyan Blue",brandA="MICROMAX",brandB="MAX",tagline="PREMIUM WI-FI",logo="📶",bg="#040b1c",bg2="#0a1a3d",tx="#eaf2ff",p1="#1d9bff",p2="#2563eb",p3="#4f46e5",gold="#60a5fa",dark=True),
 P(id="aurora-light",name="Aurora Light",brandA="MICROMAX",brandB="MAX",tagline="PREMIUM WI-FI",logo="🚀",bg="#eaf6ff",bg2="#efeaff",tx="#0b1530",p1="#0891b2",p2="#6366f1",p3="#a855f7",gold="#b7860b",dark=False),
 P(id="emerald",name="Emerald",brandA="MICROMAX",brandB="MAX",tagline="PREMIUM WI-FI",logo="🌿",bg="#ecfdf5",bg2="#d1fae5",tx="#052e2b",p1="#0d9488",p2="#059669",p3="#16a34a",gold="#d97706",dark=False),
 P(id="sunset",name="Sunset",brandA="MICROMAX",brandB="MAX",tagline="PREMIUM WI-FI",logo="🌅",bg="#fff7ed",bg2="#ffe4e6",tx="#3b1d12",p1="#f97316",p2="#e11d48",p3="#db2777",gold="#b45309",dark=False),
 P(id="midnight-glass",name="Midnight Glass",brandA="MICROMAX",brandB="MAX",tagline="PREMIUM WI-FI",logo="🚀",bg="#0b0720",bg2="#1a1040",tx="#f1ecff",p1="#22d3ee",p2="#8b5cf6",p3="#d946ef",gold="#fbbf24",dark=True),
 P(id="minimal",name="Minimal",brandA="MICROMAX",brandB="MAX",tagline="WI-FI",logo="📡",bg="#f8fafc",bg2="#eef2f7",tx="#0f172a",p1="#111827",p2="#334155",p3="#475569",gold="#64748b",dark=False),
]
DEF=dict(member=True,voucher=True,qr=True,trial=True,defTab="member",vpass="same",
 subAr="اختر طريقة الدخول المناسبة لك",subFr="Choisissez votre méthode d’accès",goAr="دخول إلى الإنترنت",goFr="ACCÉDER À INTERNET")
def rgb(h):
    h=h.lstrip('#');return tuple(int(h[i:i+2],16) for i in (0,2,4))
def rgbs(h):return ",".join(map(str,rgb(h)))
def mix(h,w):
    r=rgb(h);return "#%02x%02x%02x"%tuple(round(c+(255-c)*w) for c in r)
def esc(x):return html.escape(str(x),quote=True).replace("%%","%")
def js(x):return str(x).replace("\\","\\\\").replace('"','\\"').replace("<","\\u003c")
def render(t,preview=False):
    t={**DEF,**t};d=t["dark"]
    v=dict(BG=t["bg"],BG2=t["bg2"],TX=t["tx"],P1=t["p1"],P2=t["p2"],P3=t["p3"],GOLD=t["gold"],GOLD2=mix(t["gold"],.35),
      P1RGB=rgbs(t["p1"]),P2RGB=rgbs(t["p2"]),GOLDRGB=rgbs(t["gold"]),
      CARD="rgba(255,255,255,.06)" if d else "rgba(255,255,255,.65)",
      CS="rgba(%s,.94)"%rgbs(t["bg2"]) if d else "rgba(255,255,255,.94)",
      MUT="#9db0d0" if d else "#5b6b88",LINE="rgba(150,180,255,.22)" if d else "rgba(30,60,120,.14)",
      INP="rgba(0,0,0,.28)" if d else "#ffffff",
      SHADOW="0 25px 60px rgba(0,0,0,.45)" if d else "0 22px 50px rgba(60,70,160,.18)",
      BRANDA=esc(t["brandA"]),BRANDB=esc(t["brandB"]),TAGLINE=esc(t["tagline"]),
      SUBAR=js(t["subAr"]),SUBFR=js(t["subFr"]),GOAR=js(t["goAr"]),GOFR=js(t["goFr"]),VPASS="empty" if t["vpass"]=="empty" else "same")
    lg=str(t["logo"]).strip()
    v["LOGO"]='<img src="%s" alt="">'%esc(lg) if lg.startswith(("http://","https://","/")) else '<span class="rk">%s</span>'%esc(lg)
    tabs=[k for k in("member","voucher","qr") if t[k]] or ["member"]
    dt=t["defTab"] if t["defTab"] in tabs else tabs[0]
    v["DEFTAB"]=dt
    css=""
    for k in("member","voucher","qr"):
        if k not in tabs: css+=".seg button[data-m=%s]{display:none}"%k
    if len(tabs)==1: css+=".seg{display:none}"
    if not t["trial"]: css+=".trial{display:none}"
    v["EXTRACSS"]=css
    out=BASE
    for k,x in v.items(): out=out.replace("%%"+k+"%%",x)
    if preview:
        out=re.sub(r'<!--\$\(if error\)-->.*?<!--\$\(endif\)-->','',out,flags=re.S)
        out=re.sub(r'<!--\$\(if chap-id\)-->.*?<!--\$\(endif\)-->','',out,flags=re.S)
        out=out.replace("<!--$(if trial == 'yes')-->","").replace("<!--$(endif)-->","")
        for a,b in(("$(link-login-only)","#"),("$(link-orig-esc)",""),("$(link-orig)",""),("$(username)",""),("$(mac-esc)","aabbcc")):out=out.replace(a,b)
    return out
if __name__=="__main__":
    check="--check" in sys.argv
    bad=0
    for t in PRESETS:
        out=render(t); p=ROOT/"templates"/("login_%s.html"%t["id"])
        if re.search(r'%%[A-Z0-9]+%%',out): print("leftover token in",t["id"]); bad=1
        if check:
            if not p.exists() or p.read_text(encoding="utf-8")!=out: print("out of date:",p.name); bad=1
        else: p.write_text(out,encoding="utf-8")
    if not check: (HERE/"presets.json").write_text(json.dumps(PRESETS,ensure_ascii=False,indent=1),encoding="utf-8")
    sys.exit(bad)
