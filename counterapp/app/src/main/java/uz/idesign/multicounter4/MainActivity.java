package uz.idesign.multicounter4;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    static final long DAILY=-100L;
    final String[] ids={"SB","SVLV","LQ","A","LHQLB","IX","SAL"};
    final String[] labels={"1. SB","2. SVLV","3. LQ","4. A","5. LHQLB","6. IX","7. SAL"};
    final int[] accents={
        Color.rgb(33,150,243),Color.rgb(53,199,89),Color.rgb(255,138,0),
        Color.rgb(156,39,244),Color.rgb(0,188,212),Color.rgb(255,193,7),Color.rgb(255,82,82)
    };
    final long[] seeds={-1033700L,-1033700L,-1033700L,-1033216L,-1033700L,-2073300L,0L};
    android.content.SharedPreferences p;
    int selected=0;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        p=getSharedPreferences("multi_counter_4_store",MODE_PRIVATE);
        seedIfNeeded();
        processAllDays();
        showHome();
    }

    void seedIfNeeded(){
        boolean existing=false;
        for(int i=0;i<6;i++) if(p.contains("total_"+ids[i])) existing=true;
        if(existing) return;
        android.content.SharedPreferences.Editor e=p.edit();
        for(int i=0;i<ids.length;i++) e.putLong("total_"+ids[i],seeds[i]);
        e.apply();
    }

    int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    TextView tv(String s,float sp,int color,boolean bold){
        TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if(bold) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    GradientDrawable bg(int fill,int stroke,float radius){
        GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius));
        if(stroke!=Color.TRANSPARENT) g.setStroke(dp(2),stroke); return g;
    }
    LinearLayout column(){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setBackgroundColor(Color.BLACK); return l;
    }
    Space space(int h){ Space s=new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h))); return s; }

    long total(String id){ return p.getLong("total_"+id,0L); }
    long clicksToday(String id){
        String d=LocalDate.now().toString();
        return d.equals(p.getString("today_"+id,""))?p.getLong("clicks_"+id,0L):0L;
    }
    String lastKey(String id){ return "last_"+id; }

    void processAllDays(){
        LocalDate today=LocalDate.now();
        for(String id:ids){
            String raw=p.getString(lastKey(id),null);
            if(raw==null||raw.length()==0){
                p.edit().putString(lastKey(id),today.toString()).putString("today_"+id,today.toString()).putLong("clicks_"+id,0L).apply();
                continue;
            }
            LocalDate last;
            try{ last=LocalDate.parse(raw); }catch(Exception e){ last=today; }
            long val=total(id);
            LocalDate d=last.plusDays(1);
            while(!d.isAfter(today)){
                val+=DAILY;
                appendAuto(id,d,val);
                last=d; d=d.plusDays(1);
            }
            android.content.SharedPreferences.Editor ed=p.edit().putLong("total_"+id,val).putString(lastKey(id),last.toString());
            if(!today.toString().equals(p.getString("today_"+id,""))){
                ed.putString("today_"+id,today.toString()).putLong("clicks_"+id,0L);
            }
            ed.apply();
        }
    }

    JSONArray history(){
        try{return new JSONArray(p.getString("history_json","[]"));}catch(Exception e){return new JSONArray();}
    }
    void saveHistory(JSONArray a){ p.edit().putString("history_json",a.toString()).apply(); }
    JSONObject rec(String date,String id,String type,long delta,long after,String time){
        JSONObject o=new JSONObject();
        try{o.put("date",date);o.put("counterId",id);o.put("type",type);o.put("delta",delta);o.put("totalAfter",after);o.put("time",time);}catch(Exception ignored){}
        return o;
    }
    void appendAuto(String id,LocalDate d,long after){
        JSONArray a=history();
        a.put(rec(d.toString(),id,"AUTO",DAILY,after,d.atStartOfDay().toString()));
        trimAndSave(a);
    }
    void mergeClick(String id,long amount,long after){
        JSONArray a=history(); String date=LocalDate.now().toString(); long sum=amount;
        JSONArray out=new JSONArray();
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i); if(o==null) continue;
            if(date.equals(o.optString("date"))&&id.equals(o.optString("counterId"))&&o.optString("type").contains("CLICK")){
                sum+=o.optLong("delta"); continue;
            }
            out.put(o);
        }
        out.put(rec(date,id,"CLICK (KUNLIK)",sum,after,LocalDateTime.now().toString()));
        trimAndSave(out);
    }
    void appendSet(String id,long delta,long after){
        JSONArray a=history();
        a.put(rec(LocalDate.now().toString(),id,"O'RNATISH",delta,after,LocalDateTime.now().toString()));
        trimAndSave(a);
    }
    void trimAndSave(JSONArray a){
        if(a.length()<=20000){saveHistory(a);return;}
        JSONArray b=new JSONArray(); for(int i=a.length()-20000;i<a.length();i++) b.put(a.opt(i)); saveHistory(b);
    }
    void addClick(String id,long amt){
        processAllDays(); long n=total(id)+amt; String today=LocalDate.now().toString();
        long old=today.equals(p.getString("today_"+id,""))?p.getLong("clicks_"+id,0L):0L;
        p.edit().putLong("total_"+id,n).putString("today_"+id,today).putLong("clicks_"+id,old+amt).apply();
        mergeClick(id,amt,n);
    }

    void setContent(View v){ setContentView(v); }

    TextView header(String text,float size){
        TextView h=tv(text,size,Color.WHITE,true); h.setGravity(Gravity.CENTER); return h;
    }

    void showHome(){
        processAllDays();
        LinearLayout root=column(); root.setPadding(dp(16),dp(18),dp(16),dp(18));
        ScrollView scroll=new ScrollView(this); LinearLayout body=column(); body.setPadding(0,0,0,dp(20));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon=tv("▥",28,Color.WHITE,true); TextView title=tv("KUNLIK HISOBLAGICH",27,Color.WHITE,true);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(58),1); title.setLayoutParams(tp);
        TextView gear=tv("⚙",35,Color.WHITE,false); gear.setGravity(Gravity.CENTER); gear.setPadding(dp(10),0,dp(4),0);
        gear.setOnClickListener(v->showSettings());
        top.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(58))); top.addView(title); top.addView(gear,new LinearLayout.LayoutParams(dp(55),dp(58)));
        body.addView(top); body.addView(space(22));

        for(int i=0;i<ids.length;i++){
            final int idx=i;
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(18),dp(10),dp(16),dp(10));
            int fill=darkTint(accents[i]); card.setBackground(bg(fill,accents[i],20));
            LinearLayout left=new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
            TextView name=tv(labels[i],34,accents[i],true); TextView sum=tv("JAMI: "+total(ids[i]),20,Color.WHITE,true);
            left.addView(name); left.addView(space(6)); left.addView(sum);
            LinearLayout right=new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL); right.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
            TextView small=tv("HAR YANGI KUN",15,Color.LTGRAY,false); small.setGravity(Gravity.RIGHT);
            TextView minus=tv("-100  ›",28,Color.WHITE,true); minus.setGravity(Gravity.RIGHT);
            right.addView(small); right.addView(minus);
            card.addView(left,new LinearLayout.LayoutParams(0,dp(150),1)); card.addView(right,new LinearLayout.LayoutParams(dp(170),dp(150)));
            card.setOnClickListener(v->{selected=idx;showCounter();});
            body.addView(card,new LinearLayout.LayoutParams(-1,dp(156))); body.addView(space(14));
        }
        TextView hist=tv("◷  UMUMIY TARIX                                      ›",22,Color.LTGRAY,true);
        hist.setPadding(dp(18),0,dp(10),0); hist.setBackground(bg(Color.rgb(20,20,20),Color.TRANSPARENT,18));
        hist.setOnClickListener(v->showHistory("ALL"));
        body.addView(hist,new LinearLayout.LayoutParams(-1,dp(82)));
        scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContent(root);
    }

    int darkTint(int c){
        int r=Color.red(c)/6,g=Color.green(c)/6,b=Color.blue(c)/6; return Color.rgb(r,g,b);
    }

    void showCounter(){
        processAllDays(); String id=ids[selected], label=labels[selected]; int ac=accents[selected];
        LinearLayout root=column(); root.setPadding(dp(18),dp(15),dp(18),dp(18));
        ScrollView sv=new ScrollView(this); LinearLayout body=column();

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=tv("‹",52,Color.WHITE,false); back.setGravity(Gravity.CENTER); back.setOnClickListener(v->showHome());
        TextView ttl=tv(label,34,ac,true); ttl.setGravity(Gravity.CENTER);
        top.addView(back,new LinearLayout.LayoutParams(dp(52),dp(68))); top.addView(ttl,new LinearLayout.LayoutParams(0,dp(68),1)); top.addView(new Space(this),new LinearLayout.LayoutParams(dp(52),1));
        body.addView(top);
        TextView dt=tv(LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),24,Color.GRAY,true);
        TextView clock=tv(java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),28,Color.WHITE,true); clock.setGravity(Gravity.RIGHT);
        LinearLayout daterow=new LinearLayout(this); daterow.addView(dt,new LinearLayout.LayoutParams(0,dp(54),1)); daterow.addView(clock,new LinearLayout.LayoutParams(0,dp(54),1)); body.addView(daterow);
        body.addView(space(12));
        TextView every=tv("HAR YANGI KUN = -100",24,Color.DKGRAY,true); every.setGravity(Gravity.CENTER); body.addView(every,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView jami=tv("JAMI",28,Color.DKGRAY,true); jami.setGravity(Gravity.CENTER); body.addView(jami,new LinearLayout.LayoutParams(-1,dp(54)));
        TextView big=tv(String.valueOf(total(id)),66,Color.WHITE,true); big.setGravity(Gravity.CENTER); body.addView(big,new LinearLayout.LayoutParams(-1,dp(105)));
        TextView clickT=tv("BUGUN CLICK:  "+clicksToday(id),27,Color.DKGRAY,true); clickT.setGravity(Gravity.CENTER); body.addView(clickT,new LinearLayout.LayoutParams(-1,dp(76)));

        Button plus=new Button(this); plus.setText("+ CLICK"); plus.setTextSize(30); plus.setTextColor(Color.BLACK); plus.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        plus.setBackground(bg(Color.WHITE,Color.TRANSPARENT,20)); plus.setOnClickListener(v->{addClick(id,1);showCounter();});
        body.addView(plus,new LinearLayout.LayoutParams(-1,dp(92))); body.addView(space(13));

        Button plus20=new Button(this); plus20.setText("+20 CLICK"); plus20.setTextSize(27); plus20.setTextColor(Color.WHITE); plus20.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        plus20.setBackground(bg(Color.BLACK,Color.GRAY,20)); plus20.setOnClickListener(v->{addClick(id,20);showCounter();});
        body.addView(plus20,new LinearLayout.LayoutParams(-1,dp(84))); body.addView(space(22));

        TextView hist=tv("TARIXNI KO'RISH                                      ▼",22,Color.WHITE,true);
        hist.setPadding(dp(18),0,dp(10),0); hist.setBackground(bg(Color.rgb(22,22,22),Color.TRANSPARENT,18)); hist.setOnClickListener(v->showHistory(id));
        body.addView(hist,new LinearLayout.LayoutParams(-1,dp(72))); body.addView(space(28));

        LinearLayout setbox=column(); setbox.setPadding(dp(18),dp(13),dp(18),dp(13)); setbox.setBackground(bg(Color.rgb(21,21,21),Color.TRANSPARENT,16));
        setbox.addView(tv("JAMI SONNI O'RNATISH",19,Color.LTGRAY,true),new LinearLayout.LayoutParams(-1,dp(42)));
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        EditText input=new EditText(this); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.DKGRAY); input.setHint(String.valueOf(total(id))); input.setTextSize(22); input.setSingleLine(true); input.setInputType(2|4096);
        input.setBackground(bg(Color.rgb(20,20,20),Color.GRAY,5)); input.setPadding(dp(14),0,dp(10),0);
        Button set=new Button(this); set.setText("O'RNATISH"); set.setTextSize(18); set.setTypeface(Typeface.DEFAULT,Typeface.BOLD); set.setTextColor(Color.BLACK); set.setBackground(bg(Color.WHITE,Color.TRANSPARENT,18));
        set.setOnClickListener(v->{try{long nv=Long.parseLong(input.getText().toString().trim());long old=total(id);p.edit().putLong("total_"+id,nv).apply();appendSet(id,nv-old,nv);showCounter();}catch(Exception e){Toast.makeText(this,"Son kiriting",Toast.LENGTH_SHORT).show();}});
        row.addView(input,new LinearLayout.LayoutParams(0,dp(70),1)); Space gap=new Space(this); row.addView(gap,new LinearLayout.LayoutParams(dp(12),1)); row.addView(set,new LinearLayout.LayoutParams(dp(170),dp(70)));
        setbox.addView(row); body.addView(setbox,new LinearLayout.LayoutParams(-1,dp(145)));
        sv.addView(body); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1)); setContent(root);
    }

    void showHistory(String initial){
        LinearLayout root=column(); root.setPadding(dp(14),dp(14),dp(14),dp(12));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=tv("‹",52,Color.WHITE,false); back.setGravity(Gravity.CENTER); back.setOnClickListener(v->showHome());
        TextView title=tv("UMUMIY TARIX",30,Color.WHITE,true); title.setGravity(Gravity.CENTER);
        TextView trash=tv("🗑",30,Color.LTGRAY,false); trash.setGravity(Gravity.CENTER); trash.setOnClickListener(v->confirmClearHistory());
        top.addView(back,new LinearLayout.LayoutParams(dp(55),dp(68))); top.addView(title,new LinearLayout.LayoutParams(0,dp(68),1)); top.addView(trash,new LinearLayout.LayoutParams(dp(55),dp(68))); root.addView(top);

        HorizontalScrollView hsv=new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs=new LinearLayout(this); tabs.setPadding(0,dp(8),0,dp(8));
        String[] f={"ALL","SB","SVLV","LQ","A","LHQLB","IX","SAL"};
        for(String x:f){
            TextView b=tv(x.equals("ALL")?"Barchasi":x,17,Color.WHITE,true); b.setGravity(Gravity.CENTER);
            b.setBackground(bg(x.equals(initial)?Color.rgb(42,153,235):Color.rgb(25,25,25),Color.TRANSPARENT,14));
            b.setOnClickListener(v->showHistory(x));
            tabs.addView(b,new LinearLayout.LayoutParams(dp(x.equals("ALL")?105:88),dp(58)));
            Space sp=new Space(this); tabs.addView(sp,new LinearLayout.LayoutParams(dp(8),1));
        }
        hsv.addView(tabs); root.addView(hsv,new LinearLayout.LayoutParams(-1,dp(76)));

        LinearLayout heads=new LinearLayout(this);
        String[] hh={"Sana","Bo'lim","Turi","±","Jami"}; int[] ww={120,90,135,80,145};
        for(int i=0;i<hh.length;i++){TextView h=tv(hh[i],15,Color.GRAY,false); if(i>2)h.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); heads.addView(h,new LinearLayout.LayoutParams(dp(ww[i]),dp(42)));}
        root.addView(heads);

        ScrollView sv=new ScrollView(this); LinearLayout list=column(); list.setPadding(0,dp(4),0,dp(20));
        ArrayList<JSONObject> records=normalizedHistory();
        for(JSONObject o:records){
            String cid=o.optString("counterId"); if(!initial.equals("ALL")&&!initial.equals(cid)) continue;
            int idx=indexOf(cid); int ac=idx>=0?accents[idx]:Color.WHITE;
            LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),0,dp(10),0); row.setBackground(bg(Color.rgb(22,22,22),Color.TRANSPARENT,13));
            String date=o.optString("date"); try{date=LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));}catch(Exception ignored){}
            TextView d=tv(date,14,Color.WHITE,false); TextView c=tv(cid,14,ac,true); TextView ty=tv(o.optString("type"),13,Color.LTGRAY,false);
            long delta=o.optLong("delta"); TextView de=tv((delta>0?"+":"")+delta,15,delta>=0?Color.rgb(50,205,110):Color.rgb(255,70,70),true); de.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            TextView af=tv(String.valueOf(o.optLong("totalAfter")),14,Color.WHITE,false); af.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            row.addView(d,new LinearLayout.LayoutParams(dp(120),dp(62)));row.addView(c,new LinearLayout.LayoutParams(dp(82),dp(62)));row.addView(ty,new LinearLayout.LayoutParams(dp(130),dp(62)));row.addView(de,new LinearLayout.LayoutParams(dp(72),dp(62)));row.addView(af,new LinearLayout.LayoutParams(0,dp(62),1));
            list.addView(row,new LinearLayout.LayoutParams(-1,dp(62))); list.addView(space(8));
        }
        sv.addView(list); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1)); setContent(root);
    }

    ArrayList<JSONObject> normalizedHistory(){
        JSONArray a=history(); LinkedHashMap<String,JSONObject> clicks=new LinkedHashMap<>(); ArrayList<JSONObject> out=new ArrayList<>();
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i); if(o==null)continue;
            String type=o.optString("type");
            if(type.contains("CLICK")){
                String key=o.optString("date")+"|"+o.optString("counterId");
                JSONObject old=clicks.get(key);
                if(old==null){try{old=new JSONObject(o.toString());}catch(Exception e){old=o;} clicks.put(key,old);}
                else try{old.put("delta",old.optLong("delta")+o.optLong("delta"));old.put("totalAfter",o.optLong("totalAfter"));old.put("time",o.optString("time"));old.put("type","CLICK (KUNLIK)");}catch(Exception ignored){}
            }else out.add(o);
        }
        out.addAll(clicks.values());
        Collections.sort(out,(a1,b1)->b1.optString("time").compareTo(a1.optString("time")));
        return out;
    }
    int indexOf(String id){for(int i=0;i<ids.length;i++)if(ids[i].equals(id))return i;return -1;}

    void confirmClearHistory(){
        new AlertDialog.Builder(this).setTitle("Tarixni tozalash").setMessage("Barcha tarix o'chirilsinmi?")
            .setNegativeButton("Yo'q",null).setPositiveButton("Ha",(d,w)->{p.edit().putString("history_json","[]").apply();showHistory("ALL");}).show();
    }

    void showSettings(){
        LinearLayout root=column(); root.setPadding(dp(20),dp(25),dp(20),dp(20));
        LinearLayout top=new LinearLayout(this); TextView back=tv("‹",52,Color.WHITE,false); back.setGravity(Gravity.CENTER); back.setOnClickListener(v->showHome());
        TextView title=header("SOZLAMALAR",30); top.addView(back,new LinearLayout.LayoutParams(dp(55),dp(68)));top.addView(title,new LinearLayout.LayoutParams(0,dp(68),1));top.addView(new Space(this),new LinearLayout.LayoutParams(dp(55),1));root.addView(top);
        root.addView(space(25));
        TextView info=tv("7 ta hisoblagich\nHar yangi kun: -100\nTarix: kunlik yig'indi\nVersiya: 12.0",20,Color.LTGRAY,false); info.setPadding(dp(20),dp(20),dp(20),dp(20));info.setBackground(bg(Color.rgb(22,22,22),Color.TRANSPARENT,18));root.addView(info,new LinearLayout.LayoutParams(-1,dp(170)));
        root.addView(space(20));
        Button clear=new Button(this);clear.setText("TARIXNI TOZALASH");clear.setTextSize(18);clear.setOnClickListener(v->confirmClearHistory());root.addView(clear,new LinearLayout.LayoutParams(-1,dp(65)));
        setContent(root);
    }

    @Override public void onBackPressed(){ showHome(); }
}