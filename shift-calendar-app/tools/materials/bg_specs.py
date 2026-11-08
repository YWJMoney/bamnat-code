"""밤낮 배경 100장의 설정(계열·이름·스타일·씨앗). make.py가 읽어요."""
S=[]
def add(id,name,fam,style,seed,**args): S.append(dict(id=id,name=name,family=fam,spec=dict(style=style,seed=seed,args=args)))
F='그라데이션'
G=[("복숭아 안개",["#FFB4C8","#FFD6A5","#FDE2FF","#FFC6D9"],"#FFF1F4"),("라벤더 꿈",["#B9A6FF","#8FD3FF","#FFC8E8","#D7C9FF"],"#F4F0FF"),("민트 아침",["#9FF0D4","#B5E8FF","#E8FFC9","#7FD8C9"],"#EEFBF6"),
   ("레몬 소다",["#FFF1A6","#B8F5E0","#A9D8FF","#FFE08A"],"#FFFBEA"),("구름 솜사탕",["#FFD1E8","#C8E7FF","#FFF3C4","#E2D4FF"],"#FBF8FF"),
   ("한밤 보라",["#3A1C71","#D76D77","#1F1B4D","#5B2A86"],"#120E2A"),("깊은 숲",["#0F3D3E","#1F7A6A","#E8C27A","#0B2A3A"],"#0B1F2A"),("심해",["#0A2342","#126872","#2CA58D","#0B1D3A"],"#06121F"),
   ("와인 노을",["#4A0E2E","#C0392B","#F39C6B","#2B0A1F"],"#1A0712"),("먹구름 금빛",["#1C1C24","#3B3B50","#C9A86A","#2A2A38"],"#121218")]
for i,(n,c,b) in enumerate(G): add(f"g{i+1:02}",n,F,"grad",100+i,cols=c,bg=b)
F='밤하늘·오로라'
for i,(n,sky,cols) in enumerate([("초록 오로라",["#01040C","#06142A","#0C2A44"],["#39F0B0","#6C7BFF","#C86BFF"]),("분홍 오로라",["#05030F","#1A0B2E","#2D1450"],["#FF7EC7","#9B6BFF","#5CE1E6"]),
    ("새벽 오로라",["#020A12","#0B2233","#16384F"],["#7CFFB2","#3CC6FF"]),("보라 오로라",["#07021A","#160A38","#241452"],["#B07CFF","#FF8AD8","#6E8BFF"])]):
    add(f"n{i+1:02}",n,F,"aurora",200+i,sky=sky,cols=cols)
for i,(n,sky,glow,moon) in enumerate([("초승달 밤",["#030612","#0A1030","#1B1A48"],"#3B2A6E",[760,420,58]),("은하수",["#02030A","#0A0E24","#171A3A"],"#2A2F6E",None),
    ("보름달 바다빛",["#020814","#082038","#123A5C"],"#1B5A7A",[300,520,70]),("별 쏟아지는 밤",["#000004","#06061A","#120F33"],"#2D1E5A",None),
    ("새벽 별",["#0A0F2E","#2B2E6B","#8A6FA8"],"#E59FB0",None),("한겨울 밤",["#04070F","#0E1A33","#22355C"],"#4A6FA5",[820,300,46])]):
    add(f"n{i+5:02}",n,F,"nightsky",210+i,sky=sky,glow=glow,moon=moon)
F='산·노을'
L=[("분홍 노을 산",["#FFB08A","#FFD7B0","#FFF0DA"],["#D79BB0","#A97AA6","#7B5C93","#4F4276","#2E2A55"],[700,760,"#FFF6E2",80],False),
   ("푸른 새벽 산",["#9CB8E8","#C9D8F2","#F1E9F3"],["#9AAAD0","#7B8DBA","#5C6FA0","#3F5384","#283A66"],[300,700,"#FFFFFF",70],False),
   ("민트 아침 산",["#BDEBDD","#E2F6EE","#FFFBEA"],["#8CCFB8","#62B39C","#43957F","#2C7563","#1B5547"],[760,640,"#FFFCE8",76],False),
   ("주황 해넘이",["#2E1A47","#C2456A","#FF9A5A","#FFD18A"],["#8E3B5B","#6A2C4F","#4A2042","#2C1531"],[540,1000,"#FFE3A8",95],False),
   ("별 뜬 산",["#04071A","#121A3E","#2B2E62"],["#232A55","#1A2046","#131836","#0B0F24"],None,True),
   ("가을 능선",["#FFE0B5","#FFC88A","#F8A86A"],["#D9824A","#B8613A","#8E4430","#5E2D25"],[260,820,"#FFF4D8",70],False),
   ("안개 숲 산",["#DCE6E4","#EEF3F1","#F8FAF8"],["#AFC4BE","#8AA89F","#668A80","#466B62","#2C4D46"],None,False),
   ("사막 저녁",["#F7C59F","#F4A582","#E07A5F"],["#D98E66","#B96E52","#8F5040","#5E3330"],[780,880,"#FFF0D2",88],False),
   ("라벤더 언덕",["#E7DBFF","#F5EDFF","#FFF5F9"],["#C8B5EE","#A891D9","#8770BF","#644FA0","#45357C"],[320,600,"#FFFFFF",60],False),
   ("한밤 푸른 산",["#081025","#13244A","#26407A"],["#1D3466","#162A55","#0F1F42","#09152F"],[740,520,"#E9F0FF",54],True)]
for i,(n,sky,ly,sun,st) in enumerate(L): add(f"m{i+1:02}",n,'산·노을',"landscape",300+i,sky=sky,layers=ly,sun=sun,stars_on=st)
F='바다·물'
for i,(n,sky,cols) in enumerate([("맑은 바다 물결",["#DFF6FF","#BDE7F7"],["#8ED0EC","#5AB2DD","#3591C7","#1E6FA6","#14507F"]),("민트 물결",["#E9FFF8","#C8F3E6"],["#9BE3CF","#6FCDB6","#48B29C","#2E9181","#1D6E64"]),
    ("밤바다 물결",["#0B1630","#16284F"],["#1E3A6B","#18315C","#12284D","#0C1E3D","#07142B"]),("분홍 물결",["#FFF0F4","#FFDDE6"],["#FFC2D1","#FFA3BA","#F784A3","#E2668C","#C24B74"])]):
    add(f"w{i+1:02}",n,F,"waves",400+i,sky=sky,cols=cols)
for i,(n,sky,sea,sc) in enumerate([("보랏빛 해넘이 바다",["#2B2D6E","#B1517F","#FF9E6B"],["#5A3A6E","#2A2550","#121433"],"#FFB36B"),
    ("금빛 바다",["#3A2A5C","#E07B5A","#FFD08A"],["#7A4A4E","#3E2A3E","#1A1420"],"#FFD27A"),("새벽 바다",["#1B2A4E","#6E7FB5","#F2C6C2"],["#3E4E7A","#22305A","#111A38"],"#FFE6D8")]):
    add(f"w{i+5:02}",n,F,"sunset_sea",410+i,sky=sky,sea=sea,sun_col=sc)
for i,(n,t,b) in enumerate([("물속 햇살","#4FD1E6","#06345A"),("에메랄드 물속","#5CE6C2","#053A3A"),("깊은 푸른 물","#3B8DE6","#06173A")]):
    add(f"w{i+8:02}",n,F,"underwater",420+i,top=t,bottom=b)
F='빛망울'
B=[("보랏빛 불빛",["#0E0B1E","#2A1240"],["#FFB86B","#FF6FA8","#FFD86B","#9E7BFF"],False),("금빛 축제",["#120C04","#2E1D08"],["#FFD27A","#FFB347","#FFE9B0","#FF9A4D"],False),
   ("푸른 도시 불빛",["#020B1A","#0B2140"],["#5CC8FF","#7A9CFF","#9FF0FF","#FFFFFF"],False),("파스텔 빛망울",["#FFF4F8","#F2ECFF"],["#FFB3C9","#B9A6FF","#9EDCFF","#FFE29A"],False),
   ("성탄 불빛",["#06140C","#0E2A1A"],["#FF5C5C","#FFD27A","#7CFF9A","#FFFFFF"],False),("네온 핑크",["#10020E","#2A0630"],["#FF4FD8","#B54FFF","#4FE8FF"],False),
   ("육각 빛",["#0A0A14","#1E1A2E"],["#FFC47A","#FF8A8A","#A78BFF"],True),("초록 빛망울",["#04110E","#0B2A22"],["#7CFFB8","#3CE0C4","#E9FF8A"],False),
   ("새벽 빛망울",["#1B2140","#5A4A7A"],["#FFC6A8","#FF9EC0","#FFE3B3"],False),("하얀 겨울 빛",["#E9EEF6","#D6DEEB"],["#FFFFFF","#CFE3FF","#FFF1D6"],False)]
for i,(n,sky,c,hx) in enumerate(B): add(f"b{i+1:02}",n,'빛망울',"bokeh",500+i,sky=sky,cols=c,hexagon=hx)
F='종이·천'
P=[("하얀 줄 노트",dict(base="#FBF7EE",kind="lined")),("노란 줄 노트",dict(base="#FFF6D6",kind="lined",ink="#C9B27A",margin="#E58F7A")),("모눈 공책",dict(base="#FAFAF7",kind="grid",ink="#A8C3E0")),
   ("점 노트",dict(base="#F7F4EE",kind="dots",ink="#B8AE9C")),("크래프트지",dict(base="#C9A57A",kind="kraft",fiber=0.05)),("리넨 천",dict(base="#E9E2D6",kind="linen")),
   ("한지",dict(base="#F3EEE2",kind="kraft",fiber=0.03)),("칠판 모눈",dict(base="#24352F",kind="grid",ink="#DDE7E0",fiber=0.03)),("밤 줄 노트",dict(base="#1E2230",kind="lined",ink="#4C5878",margin="#A0607A")),
   ("오래된 종이",dict(base="#EADBC0",kind="plain",fiber=0.05,warm=0.25))]
for i,(n,a) in enumerate(P): add(f"p{i+1:02}",n,F,"paper",600+i,**a)
F='유리·물방울'
for i,(n,sky,c) in enumerate([("분홍 유리",["#E9E4FF","#FDE7F1","#E2F3FF"],["#B7A4FF","#FF9EC4","#8FD0FF"]),("민트 유리",["#E4FFF6","#E8F7FF","#F6FFE8"],["#7FE0C2","#9FD8FF","#D7F59A"]),
    ("밤 유리",["#0E1026","#1E1A40","#2C2050"],["#6E5BFF","#FF5EA8","#33C2FF"]),("햇살 유리",["#FFF6E0","#FFE9D6","#FFF0F0"],["#FFC56B","#FF9A8A","#FFD8A8"]),("푸른 유리",["#DDEBFF","#E9F3FF","#F4F8FF"],["#7FA8FF","#A5C8FF","#C2B5FF"])]):
    add(f"l{i+1:02}",n,F,"glass",700+i,sky=sky,cols=c)
for i,(n,sky) in enumerate([("비 온 창",["#1C3B57","#3E6E8E","#9CC3D8"]),("노을 창 물방울",["#3A2A5C","#B5607A","#F2A97A"]),("초록 창 물방울",["#0E2A22","#2F6B52","#9BD3A8"]),
    ("분홍 물방울",["#F9D7E3","#F3C2D6","#E8D8F5"]),("밤 창 물방울",["#05070F","#121A33","#2B3560"])]):
    add(f"l{i+6:02}",n,F,"droplets",710+i,sky=sky)
F='계절'
add("s01","벚꽃 바람",F,"petals",800,sky=["#FFE9F0","#FFF6F8","#EAF4FF"],petal_cols=["#FFB7CC","#FFC9D8","#FF9DB9","#FFE1EA"],scale=1.8)
add("s02","밤 벚꽃",F,"petals",801,sky=["#140A24","#2A1640","#4A2A5E"],petal_cols=["#FFB7CC","#FFD6E2","#FF9DB9"],scale=1.8,count=120)
add("s03","하늘 벚꽃",F,"petals",802,sky=["#A8D8FF","#D6ECFF","#FFF2F6"],petal_cols=["#FFFFFF","#FFD3E0","#FFB7CC"],scale=1.8)
add("s04","단풍 바람",F,"petals",803,sky=["#FFF3E2","#FBE3C6"],petal_cols=["#E8742C","#C9481E","#F2A33A","#B5651D"],kind="leaf",count=110)
add("s05","초록 잎",F,"petals",804,sky=["#F2FBEA","#DFF3D2"],petal_cols=["#7CC46A","#4FA65A","#A9DB7A","#3B8A4E"],kind="leaf",count=100)
add("s06","눈 오는 밤",F,"petals",805,sky=["#1E2A4A","#3A4E78","#8EA6C8"],petal_cols=["#FFFFFF","#E6F0FF"],kind="snow",count=520,center_calm=0.3)
add("s07","하얀 눈",F,"petals",806,sky=["#DCE7F5","#EEF3FA","#FFFFFF"],petal_cols=["#FFFFFF","#C9D9EE"],kind="snow",count=420,center_calm=0.3)
add("s08","장맛비",F,"petals",807,sky=["#4A5A6E","#6E7F94","#A7B4C2"],petal_cols=["#E6EEF7","#FFFFFF"],kind="rain",count=500,center_calm=0.2)
add("s09","여름 뭉게구름",F,"clouds",808,sky=["#5AA9F0","#9DD0FA","#E3F3FF"],sun=[820,300])
add("s10","노을 구름",F,"clouds",809,sky=["#5B4B9A","#E57B8E","#FFC38A"],cloud="#FFD9C9",cover=0.45)
F='도시 밤'
C=[("보랏빛 도시",["#0B0A24","#2A1B4F","#6B2E68"],"#FF7AB0"),("푸른 시간 도시",["#0A1838","#1F3A78","#5577B8"],"#7FB2FF"),("네온 도시",["#05010F","#1A0630","#3D0A52"],"#FF3FD0"),
   ("노을 도시",["#2A1640","#B24A5E","#FF9A5A"],"#FFB36B"),("한밤 도시",["#010208","#070B1C","#111A33"],"#3A5A9A"),("비 오는 도시",["#06161C","#0E2E38","#1E4E5A"],"#4FD1C5"),
   ("새벽 도시",["#1A1F45","#5A5A8E","#D9A2B5"],"#FFC8B0"),("금빛 도시",["#120C04","#3A230A","#7A4A14"],"#FFB84D"),("겨울 도시",["#0C1424","#24365A","#5C7AA8"],"#BFD8FF"),
   ("자정 보라 도시",["#0A0418","#21103E","#3B1B66"],"#9F7BFF")]
for i,(n,sky,gl) in enumerate(C): add(f"c{i+1:02}",n,F,"city",900+i,sky=sky,glow=gl,moon=(i%3!=1))
F='기하 무늬'
for i,(n,bg,c,k,calm) in enumerate([("멤피스",["#FFF8EC"][0],["#FF8A7A","#5CC8C2","#FFC857","#6C63FF","#1F1F3A"],"memphis",0.5),
   ("테라조",["#F3EEE8"][0],["#E7A08B","#9CB8A6","#3B4A5A","#E9C77B","#C9C3BC"],"terrazzo",0.3),
   ("레트로 무지개",["#F6EFE6"][0],["#E07A5F","#F2CC8F","#81B29A","#3D405B"],"arcs",0.0),
   ("물결 선",["#F5F1FF"][0],["#C9B8FF","#FFC2D6","#B5E3FF"],"lines",0.35),
   ("아르데코 부채",["#14202E"][0],["#C9A86A","#1E3045","#E8D3A2"],"deco",0.0),
   ("말랑 체크",["#FFF4EC"][0],["#FFD3C2","#FFE3A8","#D7EFD0"],"checker",0.2),
   ("하프톤",["#FFF7F0"][0],["#FF8FA3","#FFC38A"],"halftone",0.0),
   ("밤 멤피스",["#16142A"][0],["#FF7AA8","#5CE1D6","#FFD166","#8C7BFF"],"memphis",0.5),
   ("초록 테라조",["#E9F1EC"][0],["#4E8B6E","#A9CBB7","#F2C894","#2F4F46"],"terrazzo",0.3),
   ("파스텔 물결 선",["#FFF8F2"][0],["#FFD6A5","#CDE7B0","#A3D2E0","#E5C3F0"],"lines",0.35)]):
    add(f"x{i+1:02}",n,F,"geo",1000+i,bg=bg,cols=c,kind=k,calm=calm)
assert len(S)==100, len(S)
assert len({s['id'] for s in S})==100
BACKGROUNDS = S
