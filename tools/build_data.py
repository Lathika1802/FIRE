import json, math, heapq, re
old = json.load(open('../data/sudar-data.json'))
old['locations']=old['locations'][:12]; old['roads']=old['roads'][:14]; old['units']=old['units'][:6]; old['incidents']=old['incidents'][:4]

# Approximate real lat/lon for the 12 original places (names/ids/types unchanged)
orig_ll = {"ERD":(11.3410,77.7170,"Erode"),"PRD":(11.2600,77.6000,"Perundurai"),"BVN":(11.4450,77.6820,"Bhavani"),
"GOB":(11.4530,77.4390,"Gobichettipalayam"),"STM":(11.5050,77.2380,"Sathyamangalam"),"MDK":(11.2200,77.7250,"Modakkurichi"),
"KDM":(11.0770,77.8850,"Kodumudi"),"ANT":(11.5760,77.5900,"Anthiyur"),"NMB":(11.3600,77.3800,"Nambiyur"),
"BVS":(11.4700,77.1200,"Sathyamangalam"),"CHD":(11.4050,77.7050,"Erode"),"HPS":(11.3380,77.7230,"Erode")}

# name | type | lat | lon | taluk   (approximate coordinates -- verify before real use)
RAW = """
Erode Junction Railway Station|JUNCTION|11.3276|77.7289|Erode
Erode Old Bus Stand|JUNCTION|11.3412|77.7140|Erode
Veerappanchatram Textile Market|MARKET|11.3500|77.7120|Erode
Karungalpalayam|RESIDENTIAL|11.3380|77.7040|Erode
Surampatti|RESIDENTIAL|11.3200|77.7400|Erode
Periyar Nagar|RESIDENTIAL|11.3445|77.7230|Erode
Thindal|RESIDENTIAL|11.3100|77.6950|Erode
Vairapalayam|RESIDENTIAL|11.3600|77.7260|Erode
Nasiyanur|RESIDENTIAL|11.3800|77.6700|Erode
Lakkapuram|RESIDENTIAL|11.2900|77.7200|Erode
Moolapalayam|RESIDENTIAL|11.3250|77.7100|Erode
Kollampalayam|RESIDENTIAL|11.3480|77.7380|Erode
Erode Collectorate Complex|STATION|11.3510|77.7300|Erode
Erode Turmeric Regulated Market|MARKET|11.3440|77.7340|Erode
Sampath Nagar|RESIDENTIAL|11.3550|77.7370|Erode
Erode Cauvery Bridge|JUNCTION|11.3510|77.7490|Erode
Kasipalayam Powerloom Cluster|INDUSTRIAL|11.3900|77.7300|Erode
Kumalankuttai Powerloom Cluster|INDUSTRIAL|11.3700|77.7000|Erode
Erode Government Arts College|SCHOOL|11.3420|77.7000|Erode
Erode Private Hospital Zone|HOSPITAL|11.3330|77.7180|Erode
Erode South Fire & Rescue Station|STATION|11.3150|77.7250|Erode
Vendipalayam|RESIDENTIAL|11.3900|77.6900|Erode
Perundurai Town Market|MARKET|11.2760|77.5870|Perundurai
Perundurai Medical College Hospital|HOSPITAL|11.2650|77.5760|Perundurai
Perundurai Police & Fire Station|STATION|11.2780|77.5900|Perundurai
Kongu Engineering College|SCHOOL|11.2740|77.5790|Perundurai
Chennimalai Handloom Cluster|INDUSTRIAL|11.1700|77.6000|Perundurai
Chennimalai Town|MARKET|11.1750|77.6050|Perundurai
Vijayamangalam|JUNCTION|11.2430|77.5050|Perundurai
Kanjikoil|JUNCTION|11.3100|77.5700|Perundurai
Thuduppathi|RESIDENTIAL|11.2200|77.5400|Perundurai
Ingur|JUNCTION|11.2730|77.5430|Perundurai
Pasur|RESIDENTIAL|11.2000|77.6480|Perundurai
Thingalur|RESIDENTIAL|11.2900|77.6300|Perundurai
Vellode Bird Sanctuary Road|JUNCTION|11.2700|77.6750|Perundurai
Kavundachipalayam|RESIDENTIAL|11.2400|77.6200|Perundurai
Bhavani Town Market|MARKET|11.4460|77.6850|Bhavani
Bhavani Government Hospital|HOSPITAL|11.4440|77.6860|Bhavani
Bhavani Fire & Police Station|STATION|11.4430|77.6800|Bhavani
Bhavani Carpet Weaving Cluster|INDUSTRIAL|11.4500|77.6900|Bhavani
Kavindapadi|JUNCTION|11.4333|77.5833|Bhavani
Ammapettai|RESIDENTIAL|11.4300|77.6200|Bhavani
Jambai|RESIDENTIAL|11.4250|77.5900|Bhavani
Kalingarayan Anicut|JUNCTION|11.4170|77.6600|Bhavani
Singampettai|RESIDENTIAL|11.4600|77.6500|Bhavani
Sithar|RESIDENTIAL|11.4900|77.7100|Bhavani
Bhavani Govt Higher Secondary School|SCHOOL|11.4480|77.6780|Bhavani
Gobichettipalayam Government Hospital|HOSPITAL|11.4500|77.4400|Gobichettipalayam
Gobichettipalayam Bus Stand|JUNCTION|11.4540|77.4430|Gobichettipalayam
Gobichettipalayam Fire & Police Station|STATION|11.4510|77.4360|Gobichettipalayam
Gobichettipalayam Arts & Science College|SCHOOL|11.4560|77.4200|Gobichettipalayam
Kugalur|JUNCTION|11.4900|77.4700|Gobichettipalayam
Kolappalur|RESIDENTIAL|11.4000|77.4900|Gobichettipalayam
Kavilipalayam|RESIDENTIAL|11.4200|77.4200|Gobichettipalayam
Kodiveri Weir|JUNCTION|11.4000|77.3900|Gobichettipalayam
Pariyur Kondathu Kaliamman Temple|JUNCTION|11.4800|77.5400|Gobichettipalayam
Nallur|RESIDENTIAL|11.4300|77.4700|Gobichettipalayam
Dobbi Weekly Market|MARKET|11.4100|77.4500|Gobichettipalayam
Nambiyur Government Hospital|HOSPITAL|11.3650|77.3850|Nambiyur
Nambiyur Fire & Police Station|STATION|11.3620|77.3780|Nambiyur
Kosanam|RESIDENTIAL|11.3800|77.4500|Nambiyur
Elathur|RESIDENTIAL|11.3500|77.3500|Nambiyur
Kolathur|RESIDENTIAL|11.3300|77.4200|Nambiyur
Chikkarasampalayam|RESIDENTIAL|11.3900|77.3300|Nambiyur
Sathyamangalam Town Market|MARKET|11.5030|77.2420|Sathyamangalam
Sathyamangalam Government Hospital|HOSPITAL|11.5010|77.2400|Sathyamangalam
Sathyamangalam Fire & Police Station|STATION|11.5060|77.2360|Sathyamangalam
Bannari Amman Temple|JUNCTION|11.5400|77.1500|Sathyamangalam
Bannari Amman Institute of Technology|SCHOOL|11.5000|77.2700|Sathyamangalam
Dhimbam Ghat Road|JUNCTION|11.5900|77.1700|Sathyamangalam
Hasanur|JUNCTION|11.6300|77.0500|Sathyamangalam
Punjai Puliampatti Market|MARKET|11.2800|77.1300|Sathyamangalam
Kadambur|RESIDENTIAL|11.5700|77.0900|Sathyamangalam
Bhavanisagar Town|RESIDENTIAL|11.4650|77.1300|Sathyamangalam
Rangasamudram|RESIDENTIAL|11.4600|77.2000|Sathyamangalam
Thalavadi|JUNCTION|11.7600|77.0000|Thalavadi
Thalavadi Government Hospital|HOSPITAL|11.7580|77.0050|Thalavadi
Thalavadi Police Outpost|STATION|11.7620|76.9950|Thalavadi
Anthiyur Town Market|MARKET|11.5780|77.5920|Anthiyur
Anthiyur Government Hospital|HOSPITAL|11.5740|77.5880|Anthiyur
Anthiyur Fire & Police Station|STATION|11.5790|77.5870|Anthiyur
Vellithiruppur|RESIDENTIAL|11.5400|77.6800|Anthiyur
Burgur|JUNCTION|11.7200|77.5400|Anthiyur
Appakudal|RESIDENTIAL|11.4900|77.5000|Anthiyur
Thavittupalayam|RESIDENTIAL|11.5300|77.5600|Anthiyur
Kodumudi Magudeswarar Temple|JUNCTION|11.0790|77.8830|Kodumudi
Kodumudi Government Hospital|HOSPITAL|11.0800|77.8870|Kodumudi
Kodumudi Fire & Police Station|STATION|11.0760|77.8900|Kodumudi
Sivagiri|RESIDENTIAL|11.1700|77.7500|Kodumudi
Arachalur|MARKET|11.1500|77.6900|Kodumudi
Avalpoondurai|RESIDENTIAL|11.1300|77.7400|Kodumudi
Kodumudi Cauvery Ghat|JUNCTION|11.0850|77.8800|Kodumudi
Modakkurichi Government Hospital|HOSPITAL|11.2250|77.7200|Modakkurichi
Modakkurichi Fire & Police Station|STATION|11.2180|77.7280|Modakkurichi
Elumathur|RESIDENTIAL|11.2000|77.8000|Modakkurichi
Kollankoil|RESIDENTIAL|11.2300|77.6700|Modakkurichi
Kodumudi Road Junction|JUNCTION|11.1800|77.8000|Modakkurichi
Vellode Market|MARKET|11.3000|77.6700|Modakkurichi
Kangeyam Road Junction|JUNCTION|11.2700|77.7500|Modakkurichi
Modakkurichi Rice Mill Cluster|INDUSTRIAL|11.2400|77.7000|Modakkurichi
Ariyappampalayam|RESIDENTIAL|11.3100|77.6300|Modakkurichi
Erode District Collectorate Annex|STATION|11.3470|77.7210|Erode
""".strip().split("\n")

locs = []
for o in old["locations"]:
    la, lo, tk = orig_ll[o["id"]]
    locs.append({"id":o["id"],"name":o["name"],"type":o["type"],"lat":la,"lon":lo,"taluk":tk})
used = {l["id"] for l in locs}
for line in RAW:
    n,t,la,lo,tk = line.split("|")
    base = re.sub(r'[^A-Z]','',n.upper())[:3]; c = base; i = 2
    while c in used: c = base + str(i); i += 1
    used.add(c)
    locs.append({"id":c,"name":n,"type":t,"lat":float(la),"lon":float(lo),"taluk":tk})

S = 8  # px per km
for l in locs:
    l["x"] = round((l["lon"]-77.0)*109.0*S); l["y"] = round((11.85-l["lat"])*111.3*S)

def hav(a,b):
    p=math.pi/180; dla=(b["lat"]-a["lat"])*p; dlo=(b["lon"]-a["lon"])*p
    h=math.sin(dla/2)**2+math.cos(a["lat"]*p)*math.cos(b["lat"]*p)*math.sin(dlo/2)**2
    return 6371*2*math.asin(math.sqrt(h))
byid = {l["id"]:l for l in locs}

# Roads: keep the 14 original roads exactly, then add MST + 2 nearest neighbours for connectivity
roads = [dict(r) for r in old["roads"]]
have = {tuple(sorted((r["from"],r["to"]))) for r in roads}
def traffic(a,b):
    cityish = {"Erode":1.4,"Perundurai":1.2,"Bhavani":1.25,"Gobichettipalayam":1.2}
    base = (cityish.get(a["taluk"],1.0)+cityish.get(b["taluk"],1.0))/2
    if "MARKET" in (a["type"],b["type"]): base += 0.15
    if "INDUSTRIAL" in (a["type"],b["type"]): base += 0.1
    return round(min(max(base,1.0),1.8),1)
def add(a,b):
    k = tuple(sorted((a,b)))
    if k in have: return
    have.add(k); A,B = byid[a],byid[b]
    roads.append({"from":a,"to":b,"km":round(hav(A,B)*1.25,1),"traffic":traffic(A,B),"blocked":False})
ids = [l["id"] for l in locs]
# MST (Prim)
inT = {ids[0]}; pq = []
for j in ids[1:]: heapq.heappush(pq,(hav(byid[ids[0]],byid[j]),ids[0],j))
while len(inT) < len(ids):
    d,a,b = heapq.heappop(pq)
    if b in inT: continue
    inT.add(b); add(a,b)
    for j in ids:
        if j not in inT: heapq.heappush(pq,(hav(byid[b],byid[j]),b,j))
for a in ids:
    near = sorted((hav(byid[a],byid[b]),b) for b in ids if b != a)[:2]
    for d,b in near:
        if d < 25: add(a,b)
# Original roads keep their original km only where plausible; otherwise use geometry
for r in roads[:14]:
    g = hav(byid[r["from"]],byid[r["to"]])*1.25
    if r["km"] < g*0.7 or r["km"] > g*2.2: r["km"] = round(g,1)

units = [dict(u) for u in old["units"]]
for l in locs:
    if l["id"] in {u["station"] for u in units}: continue
    if l["type"]=="STATION":
        units += [{"id":f'{l["id"]}-FIRE-1',"type":"FIRE","station":l["id"]},{"id":f'{l["id"]}-POL-1',"type":"POLICE","station":l["id"]}]
    elif l["type"]=="HOSPITAL":
        units += [{"id":f'{l["id"]}-AMB-1',"type":"AMBULANCE","station":l["id"]}]

out = {"town":old["town"],"locations":locs,"roads":roads,"units":units,
       "incidents":[{k:i[k] for k in ("id","type","location","severity","reportedAt","needs")} for i in old["incidents"]]}
json.dump(out,open('../data/sudar-data.json','w'),indent=1)
open('../web/js/data.js','w').write("const SUDAR_DATA = "+json.dumps(out,separators=(',',':'))+";\n")
print(len(locs),"places",len(roads),"roads",len(units),"units")
