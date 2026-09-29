package com.micromax.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun HotspotVisualEditorPage(api: Api, router: JSONObject?) {
    var brand by remember { mutableStateOf("MICRO-MAX") }
    var title by remember { mutableStateOf("بوابة Wi‑Fi") }
    var subtitle by remember { mutableStateOf("اتصال سريع وآمن") }
    var primary by remember { mutableStateOf("#22D3EE") }
    var background by remember { mutableStateOf("#07111F") }
    var button by remember { mutableStateOf("دخول إلى الإنترنت") }
    var saving by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val login = generateLoginHtml(brand,title,subtitle,primary,background,button)
    val status = generateStatusHtml(brand,primary,background)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("HotSpot Visual Studio",fontSize=28.sp,fontWeight=FontWeight.Black);Text("تحرير بصري • معاينة • login.html + status.html • نشر آمن",fontSize=12.sp,color=TextMuted)};Icon(Icons.Default.AutoAwesome,null,tint=Accent,modifier=Modifier.size(32.dp))}
        if(router==null){GlassCard{Icon(Icons.Default.Router,null,tint=Accent);Text("اختر راوتر MikroTik أولاً",fontWeight=FontWeight.Bold);Text("بعد اختيار الراوتر يمكنك حفظ القالب ونشره مع Backup تلقائي.",color=TextMuted)}} else {
            Surface(color=Accent.copy(alpha=.08f),shape=RoundedCornerShape(14.dp)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Router,null,tint=Accent);Spacer(Modifier.width(8.dp));Text(router.optString("name","Router"),fontWeight=FontWeight.Bold);Spacer(Modifier.width(8.dp));Text(router.optString("host",""),fontSize=11.sp,color=TextMuted)}}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(tab==0,{tab=0},{Text("login.html")},leadingIcon={Icon(Icons.Default.Login,null)});FilterChip(tab==1,{tab=1},{Text("status.html")},leadingIcon={Icon(Icons.Default.Speed,null)})}
            GlassCard { Text("المعاينة المباشرة",fontWeight=FontWeight.Black,fontSize=20.sp); if(tab==0) LoginPreview(brand,title,subtitle,primary,background,button) else StatusPreview(brand,primary,background) }
            GlassCard { Text("خصائص التصميم",fontWeight=FontWeight.Black,fontSize=19.sp); AppField(brand,{brand=it},"اسم العلامة",Icons.Default.Business); AppField(title,{title=it},"العنوان",Icons.Default.Title); AppField(subtitle,{subtitle=it},"الوصف",Icons.Default.Description); AppField(button,{button=it},"نص الزر",Icons.Default.TouchApp); AppField(primary,{primary=it},"اللون الرئيسي HEX",Icons.Default.Palette); AppField(background,{background=it},"الخلفية HEX",Icons.Default.FormatColorFill) }
            GlassCard { Text("النشر",fontWeight=FontWeight.Black,fontSize=19.sp); Text("سيتم إنشاء نسخة احتياطية تلقائيًا قبل استبدال ملفات MikroTik.",fontSize=11.sp,color=TextMuted); msg?.let{Text(it,fontSize=11.sp,color=if(it.startsWith("تم"))Green else Red)}; Button(enabled=!saving,onClick={scope.launch{saving=true;msg=null;try{val body=JSONObject().put("loginHtml",login).put("statusHtml",status);api.post("/api/routers/${router.optString("id")}/hotspot-design/publish",body.toString());msg="تم نشر login.html و status.html مع Backup تلقائي"}catch(e:Exception){msg=e.message?:"فشل النشر"}finally{saving=false}}},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.CloudUpload,null);Spacer(Modifier.width(7.dp));Text(if(saving)"جاري النشر…" else "نشر التصميم للراوتر")}}
        }
    }
}

private fun hexColor(v:String, fallback:Color):Color = try { Color(android.graphics.Color.parseColor(v)) } catch(_:Exception){fallback}

@Composable private fun LoginPreview(brand:String,title:String,subtitle:String,primary:String,bg:String,button:String){Box(Modifier.fillMaxWidth().height(340.dp).background(hexColor(bg,Color(0xFF07111F)),RoundedCornerShape(24.dp)).padding(18.dp),contentAlignment=Alignment.Center){Column(Modifier.widthIn(max=390.dp).fillMaxWidth().background(Color.White.copy(alpha=.08f),RoundedCornerShape(24.dp)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(brand,fontSize=26.sp,fontWeight=FontWeight.Black);Text(title,fontSize=20.sp,fontWeight=FontWeight.Bold);Text(subtitle,fontSize=12.sp,color=Color.White.copy(alpha=.65f));Box(Modifier.fillMaxWidth().height(48.dp).background(Color.Black.copy(alpha=.25f),RoundedCornerShape(13.dp)));Box(Modifier.fillMaxWidth().height(48.dp).background(Color.Black.copy(alpha=.25f),RoundedCornerShape(13.dp)));Button(onClick={},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=hexColor(primary,Accent))){Text(button,color=Color.Black,fontWeight=FontWeight.Black)}}}}

@Composable private fun StatusPreview(brand:String,primary:String,bg:String){Box(Modifier.fillMaxWidth().height(340.dp).background(hexColor(bg,Color(0xFF07111F)),RoundedCornerShape(24.dp)).padding(18.dp),contentAlignment=Alignment.Center){Column(Modifier.widthIn(max=420.dp).fillMaxWidth().background(Color.White.copy(alpha=.08f),RoundedCornerShape(24.dp)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(brand,fontSize=26.sp,fontWeight=FontWeight.Black);Text("حالة الاتصال",color=Color.White.copy(alpha=.65f));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){repeat(2){Box(Modifier.weight(1f).height(70.dp).background(Color.Black.copy(alpha=.25f),RoundedCornerShape(14.dp)))}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){repeat(2){Box(Modifier.weight(1f).height(70.dp).background(Color.Black.copy(alpha=.25f),RoundedCornerShape(14.dp)))}};Text("تسجيل الخروج",color=hexColor(primary,Accent),fontWeight=FontWeight.Bold)}}}

private fun esc(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
private fun generateLoginHtml(brand:String,title:String,subtitle:String,primary:String,bg:String,button:String):String="""<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(title)}</title><style>:root{--p:${esc(primary)};--bg:${esc(bg)}}*{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;font-family:Arial,sans-serif;background:linear-gradient(145deg,var(--bg),#0f172a);color:#f8fafc;padding:20px}.card{width:min(430px,100%);padding:28px;border:1px solid #ffffff22;border-radius:28px;background:#ffffff0d;backdrop-filter:blur(20px)}input,button{width:100%;padding:15px;margin-top:10px;border-radius:14px}input{border:1px solid #ffffff22;background:#0004;color:white}button{border:0;background:var(--p);font-weight:900}</style></head><body><main class="card"><h1>${esc(brand)}</h1><p>${esc(title)}</p><small>${esc(subtitle)}</small><div style="color:#fb7185;margin-top:8px">$(error)</div><form action="$(link-login-only)" method="post"><input type="hidden" name="dst" value="$(link-orig)"><input name="username" placeholder="اسم المستخدم" required><input type="password" name="password" placeholder="كلمة المرور" required><button type="submit">${esc(button)}</button></form></main><script>(function(){try{var q=new URLSearchParams(location.search),u=q.get('username'),p=q.get('password'),f=document.forms[0];if(!u||!p||!f)return;f.username.value=u;f.password.value=p;if(q.get('auto')==='1'&&!sessionStorage.getItem('mmauto')){sessionStorage.setItem('mmauto','1');f.submit();}}catch(e){}})();</script></body></html>"""
private fun generateStatusHtml(brand:String,primary:String,bg:String):String="""<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(brand)} Status</title><style>:root{--p:${esc(primary)};--bg:${esc(bg)}}body{margin:0;min-height:100vh;display:grid;place-items:center;font-family:Arial,sans-serif;background:linear-gradient(145deg,var(--bg),#0f172a);color:#f8fafc;padding:20px}.card{width:min(500px,100%);padding:28px;border:1px solid #ffffff22;border-radius:28px;background:#ffffff0d;backdrop-filter:blur(20px)}.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.item{padding:15px;border-radius:15px;background:#0004}.k{color:#94a3b8;font-size:12px}.v{font-weight:800;margin-top:5px}a{color:var(--p)}</style></head><body><main class="card"><h1>${esc(brand)}</h1><p>حالة الاتصال</p><div class="grid"><div class="item"><div class="k">المستخدم</div><div class="v">$(username)</div></div><div class="item"><div class="k">IP</div><div class="v">$(ip)</div></div><div class="item"><div class="k">التحميل</div><div class="v">$(bytes-out-nice)</div></div><div class="item"><div class="k">الرفع</div><div class="v">$(bytes-in-nice)</div></div></div><p>مدة الجلسة: $(uptime)</p><p>المتبقي: $(session-time-left)</p><a href="$(link-logout)">تسجيل الخروج</a></main></body></html>"""
