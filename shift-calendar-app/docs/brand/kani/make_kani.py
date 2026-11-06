import json,sys
INK="#15192B"; CREAM="#F7F1E6"; CHEEK="#FF9EA0"
SHIFT={"day":"#E9B95A","night":"#34418F","off":"#43B58A"}
def band_icon(k):
    if k=="day": return '<circle cx="88" cy="37" r="5.5" fill="#FFF3C4"/>'
    if k=="night": return '<path d="M90 31a7 7 0 1 0 0 12a5.5 5.5 0 1 1 0-12z" fill="#FFE9A8"/>'
    return '<path d="M84 41c0-6 5-10 10-10c0 6-4 10-10 10z" fill="#E6FFF2"/><path d="M84 41l6-6" stroke="#43B58A" stroke-width="1.6" stroke-linecap="round"/>'
def svg(shift,face,arms="down",extra="",title="",OUT=INK,sfx=""):
    a={"down":'<path d="M20 80q-9 6-7 15" /><path d="M110 80q9 6 7 15" />',
       "up":'<path d="M20 70q-12-10-9-24" /><path d="M110 70q12-10 9-24" />',
       "one":'<path d="M20 80q-9 6-7 15" /><path d="M110 70q12-10 9-24" />',
       "point":'<path d="M20 80q-9 6-7 15" /><path d="M110 82q10 4 14 14" />'}[arms]
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 130 130" role="img" aria-label="{title}"><title>{title}</title>
<defs><clipPath id="b{shift}{len(face)}{sfx}"><rect x="21" y="24" width="88" height="88" rx="24"/></clipPath></defs>
<g fill="none" stroke="{OUT}" stroke-width="4" stroke-linecap="round">{a}</g>
<ellipse cx="49" cy="116" rx="11" ry="5" fill="{OUT}"/><ellipse cx="81" cy="116" rx="11" ry="5" fill="{OUT}"/>
<rect x="21" y="24" width="88" height="88" rx="24" fill="{CREAM}"/>
<g clip-path="url(#b{shift}{len(face)}{sfx})"><rect x="21" y="24" width="88" height="27" fill="{SHIFT[shift]}"/></g>
{band_icon(shift)}
<rect x="21" y="24" width="88" height="88" rx="24" fill="none" stroke="{OUT}" stroke-width="3.5"/>
<rect x="44" y="14" width="9" height="20" rx="4.5" fill="{OUT}"/><rect x="77" y="14" width="9" height="20" rx="4.5" fill="{OUT}"/>
<ellipse cx="38" cy="88" rx="6.5" ry="3.8" fill="{CHEEK}" opacity=".7"/><ellipse cx="92" cy="88" rx="6.5" ry="3.8" fill="{CHEEK}" opacity=".7"/>
<g fill="{INK}" stroke="{INK}" stroke-linecap="round" stroke-linejoin="round">{face}</g>
{extra}</svg>'''
S='stroke-width="3.5" fill="none"'
E={
"basic":dict(shift="day",title="칸이 기본",face=f'<circle cx="51" cy="76" r="5" stroke="none"/><circle cx="79" cy="76" r="5" stroke="none"/><path d="M57 86q8 7 16 0" {S}/>'),
"sleepy":dict(shift="night",title="칸이 졸림",face=f'<path d="M45 77q6 5 12 0" {S}/><path d="M73 77q6 5 12 0" {S}/><ellipse cx="65" cy="90" rx="3.5" ry="4" stroke="none"/>',
  extra='<g fill="#7C8BD6" font-family="sans-serif" font-weight="700"><text x="104" y="22" font-size="16">z</text><text x="114" y="12" font-size="11">z</text></g>'),
"stretch":dict(shift="day",arms="up",title="칸이 기지개",face=f'<path d="M45 78q6-6 12 0" {S}/><path d="M73 78q6-6 12 0" {S}/><path d="M57 85q8 10 16 0z" stroke-width="2"/>',
  extra='<g stroke="#E9B95A" stroke-width="3" stroke-linecap="round"><path d="M8 30l-6-4"/><path d="M6 42h-6"/><path d="M122 30l6-4"/><path d="M124 42h6"/></g>'),
"done":dict(shift="day",arms="one",title="칸이 해냈어요",face=f'<circle cx="51" cy="76" r="5" stroke="none"/><path d="M73 77q6-5 12 0" {S}/><path d="M56 85q9 9 18 0" {S}/>',
  extra='<g fill="#E9B95A"><path d="M12 22l2.5 6l6 2.5l-6 2.5l-2.5 6l-2.5-6l-6-2.5l6-2.5z"/><path d="M112 8l1.8 4.2l4.2 1.8l-4.2 1.8l-1.8 4.2l-1.8-4.2l-4.2-1.8l4.2-1.8z"/></g>'),
"worry":dict(shift="day",title="칸이 걱정",face=f'<circle cx="51" cy="78" r="4.5" stroke="none"/><circle cx="79" cy="78" r="4.5" stroke="none"/><path d="M44 70l12-4" stroke-width="3" fill="none"/><path d="M86 70l-12-4" stroke-width="3" fill="none"/><path d="M57 90q4-4 8 0t8 0" {S}/>',
  extra='<path d="M100 56q4 7 0 10q-4-3 0-10z" fill="#8EC5FF" stroke="#2A2F45" stroke-width="1.5"/>'),
"empty":dict(shift="day",arms="point",title="칸이 안내",face=f'<circle cx="54" cy="77" r="5" stroke="none"/><circle cx="82" cy="77" r="5" stroke="none"/><path d="M60 87q7 5 14 0" {S}/>'),
"off":dict(shift="off",title="칸이 쉬는 날",face=f'<path d="M45 76h12" stroke-width="3.5"/><path d="M73 76h12" stroke-width="3.5"/><path d="M57 86q8 7 16 0" {S}/>'),
}
out={}
for k,v in E.items():
    out[k]=svg(v["shift"],v["face"],v.get("arms","down"),v.get("extra",""),v["title"])
    open(f"kani-{k}.svg","w").write(out[k])
    out[k+"-dark"]=svg(v["shift"],v["face"],v.get("arms","down"),v.get("extra",""),v["title"],OUT="#C9D1F5",sfx="d")
    open(f"kani-{k}-dark.svg","w").write(out[k+"-dark"])
json.dump(out,open("all.json","w"),ensure_ascii=False)
