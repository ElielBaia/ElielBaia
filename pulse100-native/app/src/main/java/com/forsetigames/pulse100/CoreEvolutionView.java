package com.forsetigames.pulse100;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

final class CoreEvolutionView extends View implements Choreographer.FrameCallback {
    static final float W=720f,H=1280f,TOP=150f,BOTTOM=1160f,CX=360f,CY=670f;
    static final int BG=Color.rgb(3,6,18),TEXT=Color.rgb(246,248,255),MUTED=Color.rgb(142,151,183);
    static final int CYAN=Color.rgb(55,224,255),PINK=Color.rgb(255,72,199),GOLD=Color.rgb(255,211,73),VIOLET=Color.rgb(150,90,255),GREEN=Color.rgb(85,239,155),RED=Color.rgb(255,72,92);
    static final int[] COLORS={CYAN,PINK,GOLD,VIOLET};
    static final int HOME=0,PLAY=1,PAUSE=2,RESULT=3,UPGRADE=4;
    static final int NORMAL=0,ARMORED=1,GENERATOR=2,PRISM=3,HEAL=4;
    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG),t=new Paint(Paint.ANTI_ALIAS_FLAG);
    final Random rng=new Random();
    final ArrayList<Enemy> enemies=new ArrayList<>();
    final ArrayList<Pulse> pulses=new ArrayList<>();
    final ArrayList<Spark> sparks=new ArrayList<>();
    final ArrayList<Satellite> sats=new ArrayList<>();
    final ArrayList<Star> stars=new ArrayList<>();
    final SharedPreferences prefs; final Vibrator vib; ToneGenerator tone;
    int screen=HOME,life=5,maxLife=5,charges=3,maxCharges=3,combo=0,bestCombo=0,score=0,bestScore=0,form=1,sector=1,bossOrgans=0;
    float resonance=0,evolution=0,spawnTimer=0,session=0,comboTimer=0,damageFlash=0,shake=0,slow=0,chargeHold=0;
    boolean holding=false,boss=false,runWon=false,shield=false,regen=false,doublePulse=false,widePulse=false,gravity=false,autoShot=false;
    long last=0; boolean running=false; float scale=1,ox=0,oy=0;
    final RectF playBtn=new RectF(110,720,610,820),pauseBtn=new RectF(630,35,690,95),againBtn=new RectF(110,930,610,1025),homeBtn=new RectF(110,1050,610,1135);
    final RectF[] cards={new RectF(55,420,665,565),new RectF(55,590,665,735),new RectF(55,760,665,905)};
    String[] upNames=new String[3],upDesc=new String[3]; int[] upIds=new int[3];

    CoreEvolutionView(Context c){super(c);setFocusable(true);setKeepScreenOn(true);prefs=c.getSharedPreferences("pulse100v3",Context.MODE_PRIVATE);bestScore=prefs.getInt("best",0);vib=(Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE);try{tone=new ToneGenerator(AudioManager.STREAM_MUSIC,45);}catch(Exception e){}createStars();}
    void resumeLoop(){if(running)return;running=true;last=0;Choreographer.getInstance().postFrameCallback(this);} void pauseLoop(){running=false;Choreographer.getInstance().removeFrameCallback(this);if(screen==PLAY)screen=PAUSE;}
    boolean handleBack(){if(screen==HOME)return false;screen=HOME;invalidate();return true;}
    protected void onSizeChanged(int w,int h,int ow,int oh){scale=Math.min(w/W,h/H);ox=(w-W*scale)/2;oy=(h-H*scale)/2;}
    public void doFrame(long n){if(!running)return;float dt=last==0?1/60f:Math.min(.05f,(n-last)/1e9f);last=n;updateStars(dt);if(screen==PLAY)update(dt);else updateFx(dt);invalidate();Choreographer.getInstance().postFrameCallback(this);}
    public boolean onTouchEvent(MotionEvent e){float x=(e.getX()-ox)/scale,y=(e.getY()-oy)/scale;if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(screen==HOME&&playBtn.contains(x,y))start();else if(screen==PLAY){if(pauseBtn.contains(x,y))screen=PAUSE;else if(charges>0&&y>TOP&&y<BOTTOM){holding=true;chargeHold=0;}}else if(screen==PAUSE){if(againBtn.contains(x,y))screen=PLAY;else if(homeBtn.contains(x,y))screen=HOME;}else if(screen==RESULT){if(againBtn.contains(x,y))start();else if(homeBtn.contains(x,y))screen=HOME;}else if(screen==UPGRADE){for(int i=0;i<3;i++)if(cards[i].contains(x,y)){applyUpgrade(upIds[i]);screen=PLAY;break;}}}else if(e.getActionMasked()==MotionEvent.ACTION_UP&&screen==PLAY&&holding){holding=false;fire(x,y,Math.min(1.2f,chargeHold));}return true;}
    void start(){screen=PLAY;life=maxLife=5;charges=maxCharges=3;combo=bestCombo=score=0;form=sector=1;resonance=evolution=session=spawnTimer=comboTimer=0;boss=false;bossOrgans=0;runWon=false;shield=regen=doublePulse=widePulse=gravity=autoShot=false;enemies.clear();pulses.clear();sparks.clear();sats.clear();for(int i=0;i<12;i++)spawnBasic();sound(ToneGenerator.TONE_PROP_ACK,100);}
    void update(float dt){float d=slow>0?dt*.42f:dt;session+=dt;slow=Math.max(0,slow-dt);damageFlash=Math.max(0,damageFlash-dt);shake*=.9f;comboTimer-=dt;if(comboTimer<=0)combo=0;if(holding)chargeHold+=dt;spawnTimer-=dt;if(spawnTimer<=0){spawnTimer=Math.max(.22f,1.05f-sector*.055f);spawnWave();}
      if(session>240&&!boss){boss=true;bossOrgans=3;sector=5;spawnBossRing();}
      if(!boss&&session>sector*48){sector++;if(sector==2||sector==3||sector==4)chooseUpgrade();}
      moveEnemies(d);updatePulses(d);updateSats(d);updateFx(dt);if(regen&&life<maxLife&&((int)session)%18==0)life=Math.min(maxLife,life+1);if(life<=0)finish(false);if(boss&&bossOrgans<=0)finish(true);
    }
    void spawnWave(){int n=1+(sector>=3?rng.nextInt(2):0);for(int i=0;i<n;i++){float r=rng.nextFloat();if(boss&&r<.22f)spawnType(ARMORED);else if(sector>=3&&r<.12f)spawnType(GENERATOR);else if(r<.18f)spawnType(PRISM);else if(r<.205f)spawnType(HEAL);else if(sector>=2&&r<.34f)spawnType(ARMORED);else spawnBasic();}}
    void spawnBasic(){spawnType(NORMAL);} void spawnType(int type){Enemy e=new Enemy();e.type=type;e.color=rng.nextInt(Math.min(4,1+sector));float a=(float)(rng.nextDouble()*Math.PI*2),rad=470+rng.nextFloat()*80;e.x=CX+(float)Math.cos(a)*rad;e.y=CY+(float)Math.sin(a)*rad;e.r=type==GENERATOR?24:type==ARMORED?19:15;e.hp=type==ARMORED?2:1;e.speed=26+sector*5+rng.nextFloat()*18;e.phase=rng.nextFloat()*6.28f;enemies.add(e);}
    void spawnBossRing(){for(int i=0;i<28;i++){Enemy e=new Enemy();e.type=i%9==0?GENERATOR:ARMORED;e.color=i%4;float a=(float)(i*Math.PI*2/28);e.x=CX+(float)Math.cos(a)*410;e.y=CY+(float)Math.sin(a)*410;e.r=e.type==GENERATOR?24:18;e.hp=e.type==ARMORED?2:1;e.speed=34;e.bossMinion=true;enemies.add(e);}}
    void moveEnemies(float dt){for(Iterator<Enemy>it=enemies.iterator();it.hasNext();){Enemy e=it.next();float dx=CX-e.x,dy=CY-e.y,dist=(float)Math.sqrt(dx*dx+dy*dy);if(gravity){float g=Math.max(0,220-dist)/220;e.x+=dx/dist*g*16*dt;e.y+=dy/dist*g*16*dt;}float wob=(float)Math.sin(e.phase+session*1.8f)*.22f;e.x+=(dx/dist*e.speed-dy/dist*e.speed*wob)*dt;e.y+=(dy/dist*e.speed+dx/dist*e.speed*wob)*dt;if(e.type==GENERATOR){e.cool-=dt;if(e.cool<=0){e.cool=2.8f;spawnBasic();}}
      if(dist<52){if(e.type==HEAL){life=Math.min(maxLife,life+1);burst(e.x,e.y,GREEN,20);}else hitCore();it.remove();}}
    }
    void hitCore(){if(shield){shield=false;burst(CX,CY,CYAN,30);sound(ToneGenerator.TONE_PROP_ACK,60);return;}life--;combo=0;damageFlash=.45f;shake=18;vibrate(55);sound(ToneGenerator.TONE_PROP_NACK,130);}
    void fire(float x,float y,float hold){if(charges<=0)return;charges--;float max=widePulse?170:135;max+=hold*95;pulses.add(new Pulse(x,y,-1,max,0));if(hold>.65f){slow=.35f;for(Enemy e:enemies){float dx=x-e.x,dy=y-e.y,d=(float)Math.sqrt(dx*dx+dy*dy);if(d<230){e.x+=dx*.08f;e.y+=dy*.08f;}}}burst(x,y,TEXT,22);sound(ToneGenerator.TONE_PROP_BEEP,70);vibrate(20);}
    void updatePulses(float dt){ArrayList<Pulse> add=new ArrayList<>();for(Iterator<Pulse>it=pulses.iterator();it.hasNext();){Pulse u=it.next();u.r+=u.closing?-u.speed*.58f*dt:u.speed*dt;if(!u.closing&&u.r>=u.max)u.closing=true;if(u.r<=0){it.remove();continue;}for(Iterator<Enemy>ei=enemies.iterator();ei.hasNext();){Enemy e=ei.next();if(e.hitId==u.id)continue;float dx=e.x-u.x,dy=e.y-u.y,d=(float)Math.sqrt(dx*dx+dy*dy);if(Math.abs(d-u.r)>e.r+9)continue;if(u.color>=0&&e.type!=PRISM&&u.color!=e.color)continue;e.hitId=u.id;e.hp--;if(e.hp>0){burst(e.x,e.y,COLORS[e.color],6);continue;}ei.remove();convert(e,u,add);}}
      pulses.addAll(add);
    }
    void convert(Enemy e,Pulse src,ArrayList<Pulse> add){combo++;bestCombo=Math.max(bestCombo,combo);comboTimer=2.2f;score+=10+combo;resonance+=6+(combo>=10?2:0);evolution+=2.5f+(combo>=25?1.5f:0);int c=e.type==PRISM?-1:e.color;float rad=(widePulse?116:94)+(doublePulse?10:0);add.add(new Pulse(e.x,e.y,c,rad,src.gen+1));if(doublePulse)add.add(new Pulse(e.x,e.y,c,rad*.66f,src.gen+2,.22f));if(e.type==HEAL)life=Math.min(maxLife,life+1);if(e.type==GENERATOR&&boss)bossOrgans=Math.max(0,bossOrgans-1);if(combo==25||combo==50||combo==75){charges=Math.min(maxCharges,charges+1);sound(ToneGenerator.TONE_PROP_ACK,80);}if(combo==100){slow=1.2f;charges=maxCharges;shield=true;burst(CX,CY,TEXT,80);sound(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,250);}while(resonance>=100){resonance-=100;charges=Math.min(maxCharges,charges+1);}if(evolution>=100&&form<3){evolution-=100;form++;maxCharges=Math.min(5,maxCharges+1);charges=maxCharges;shield=true;sats.add(new Satellite(sats.size()));chooseUpgrade();burst(CX,CY,form==2?CYAN:GOLD,70);slow=.75f;}burst(e.x,e.y,c<0?TEXT:COLORS[c],12);}
    void updateSats(float dt){for(Satellite s:sats){s.a+=dt*(.8f+s.index*.08f);s.x=CX+(float)Math.cos(s.a)*(105+s.index*20);s.y=CY+(float)Math.sin(s.a)*(105+s.index*20);s.cool-=dt;if(autoShot&&s.cool<=0){s.cool=1.1f;Enemy nearest=null;float best=9999;for(Enemy e:enemies){float dx=e.x-s.x,dy=e.y-s.y,d=dx*dx+dy*dy;if(d<best){best=d;nearest=e;}}if(nearest!=null)pulses.add(new Pulse(nearest.x,nearest.y,-1,58,0));}}}
    void chooseUpgrade(){if(screen!=PLAY)return;String[] names={"PULSO EXPANSIVO","REAÇÃO DUPLA","CAMPO GRAVITACIONAL","ESCUDO VIVO","SATÉLITE SENTINELA","NÚCLEO REFORÇADO"};String[] desc={"Ondas maiores e mais fáceis de conectar","Cada conversão cria uma segunda onda","A Ruína é puxada para o centro","Bloqueia o próximo impacto recebido","Satélites disparam pulsos automáticos","Aumenta a integridade máxima em 1"};int start=rng.nextInt(names.length);for(int i=0;i<3;i++){upIds[i]=(start+i*2)%names.length;upNames[i]=names[upIds[i]];upDesc[i]=desc[upIds[i]];}screen=UPGRADE;}
    void applyUpgrade(int id){if(id==0)widePulse=true;else if(id==1)doublePulse=true;else if(id==2)gravity=true;else if(id==3)shield=true;else if(id==4){autoShot=true;if(sats.isEmpty())sats.add(new Satellite(0));}else if(id==5){maxLife++;life++;}sound(ToneGenerator.TONE_PROP_ACK,130);}
    void finish(boolean won){runWon=won;screen=RESULT;bestScore=Math.max(bestScore,score);prefs.edit().putInt("best",bestScore).apply();sound(won?ToneGenerator.TONE_PROP_ACK:ToneGenerator.TONE_PROP_NACK,220);vibrate(won?90:45);}
    void createStars(){rng.setSeed(77);for(int i=0;i<95;i++){Star s=new Star();s.x=rng.nextFloat()*W;s.y=rng.nextFloat()*H;s.z=.5f+rng.nextFloat()*2;s.a=.12f+rng.nextFloat()*.45f;stars.add(s);}}
    void updateStars(float dt){for(Star s:stars){s.y+=s.z*(2+form*2)*dt;if(s.y>H)s.y=0;}}
    void updateFx(float dt){for(Iterator<Spark>it=sparks.iterator();it.hasNext();){Spark s=it.next();s.x+=s.vx*dt;s.y+=s.vy*dt;s.vx*=.96f;s.vy*=.96f;s.life-=dt;if(s.life<=0)it.remove();}}
    void burst(float x,float y,int c,int n){for(int i=0;i<n;i++){float a=rng.nextFloat()*6.28f,sp=35+rng.nextFloat()*170;Spark s=new Spark();s.x=x;s.y=y;s.vx=(float)Math.cos(a)*sp;s.vy=(float)Math.sin(a)*sp;s.life=.25f+rng.nextFloat()*.6f;s.max=s.life;s.c=c;s.z=1+rng.nextFloat()*4;sparks.add(s);}}
    protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(BG);c.save();c.translate(ox+(rng.nextFloat()-.5f)*shake,oy+(rng.nextFloat()-.5f)*shake);c.scale(scale,scale);background(c);if(screen==HOME)home(c);else if(screen==PLAY)play(c);else if(screen==PAUSE){play(c);overlay(c,"PAUSADO");button(c,againBtn,"CONTINUAR",VIOLET);button(c,homeBtn,"INÍCIO",Color.rgb(18,26,58));}else if(screen==UPGRADE){play(c);upgrade(c);}else result(c);c.restore();}
    void background(Canvas c){int tint=form==1?CYAN:form==2?VIOLET:GOLD;for(Star s:stars){p.setStyle(Paint.Style.FILL);p.setColor(alpha(tint,s.a));c.drawCircle(s.x,s.y,s.z,p);}p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(alpha(tint,.09f));for(float y=TOP;y<BOTTOM;y+=72)c.drawLine(0,y,W,y,p);p.setStyle(Paint.Style.FILL);}
    void home(Canvas c){center(c,"PULSE 100",180,72,CYAN,true);center(c,"CORE EVOLUTION",250,32,PINK,true);center(c,"PROTEJA • CONVERTA • EVOLUA",315,20,MUTED,false);drawCore(c,CX,500,1,0);button(c,playBtn,"DESPERTAR O NÚCLEO",VIOLET);center(c,"RECORDE  "+bestScore,915,24,GOLD,true);center(c,"COMECE COMO UMA CENTELHA. TERMINE COMO UM UNIVERSO.",1180,15,MUTED,false);}
    void play(Canvas c){text(c,"SETOR "+sector,28,48,22,VIOLET,true);center(c,"FORMA "+form,55,18,MUTED,false);text(c,"VIDA "+life+"/"+maxLife,28,96,20,life<=2?RED:GREEN,true);text(c,"PONTOS "+score,470,55,20,TEXT,true);text(c,"COMBO "+combo,470,98,22,combo>=100?GOLD:PINK,true);button(c,pauseBtn,"Ⅱ",Color.rgb(18,26,58));for(Pulse u:pulses)drawPulse(c,u);for(Enemy e:enemies)drawEnemy(c,e);for(Satellite s:sats){p.setColor(CYAN);c.drawCircle(s.x,s.y,8,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(alpha(CYAN,.45f));c.drawCircle(s.x,s.y,14,p);p.setStyle(Paint.Style.FILL);}drawCore(c,CX,CY,form,session);for(Spark s:sparks){p.setColor(alpha(s.c,Math.max(0,s.life/s.max)));c.drawCircle(s.x,s.y,s.z,p);}bar(c,95,1110,530,18,resonance/100f,CYAN,"RESSONÂNCIA");for(int i=0;i<maxCharges;i++){p.setColor(i<charges?TEXT:Color.rgb(38,46,75));c.drawCircle(130+i*38,1165,10,p);}if(holding){float r=35+Math.min(1.2f,chargeHold)*78;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(TEXT);c.drawCircle(CX,CY,r,p);p.setStyle(Paint.Style.FILL);}if(boss){center(c,"A COLMEIA  •  ÓRGÃOS "+bossOrgans,135,20,RED,true);}}
    void drawCore(Canvas c,float x,float y,int f,float tm){float pulse=1+(float)Math.sin(tm*3)*.06f;p.setStyle(Paint.Style.STROKE);for(int i=0;i<f+1;i++){p.setStrokeWidth(3+i);p.setColor(alpha(i%2==0?CYAN:GOLD,.55f));c.drawCircle(x,y,(44+i*19)*pulse,p);}p.setStyle(Paint.Style.FILL);p.setColor(f==1?CYAN:f==2?VIOLET:GOLD);c.drawCircle(x,y,(28+f*7)*pulse,p);p.setColor(TEXT);c.drawCircle(x,y,8,p);if(shield){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(alpha(GREEN,.8f));c.drawCircle(x,y,88,p);p.setStyle(Paint.Style.FILL);}}
    void drawEnemy(Canvas c,Enemy e){int col=e.type==HEAL?GREEN:(e.type==PRISM?TEXT:COLORS[e.color]);p.setColor(Color.rgb(18,6,18));c.drawCircle(e.x,e.y,e.r+5,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(e.type==ARMORED?5:3);p.setColor(e.type==GENERATOR?RED:col);c.drawCircle(e.x,e.y,e.r,p);if(e.type==GENERATOR){Path path=new Path();for(int i=0;i<6;i++){float a=(float)(i*Math.PI/3);float xx=e.x+(float)Math.cos(a)*e.r,yy=e.y+(float)Math.sin(a)*e.r;if(i==0)path.moveTo(xx,yy);else path.lineTo(xx,yy);}path.close();c.drawPath(path,p);}p.setStyle(Paint.Style.FILL);if(e.type==PRISM){p.setColor(TEXT);c.drawCircle(e.x,e.y,5,p);}if(e.type==ARMORED&&e.hp>1){p.setColor(RED);c.drawCircle(e.x,e.y,4,p);}}
    void drawPulse(Canvas c,Pulse u){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7);p.setColor(alpha(u.color<0?TEXT:COLORS[u.color],.72f));c.drawCircle(u.x,u.y,u.r,p);p.setStrokeWidth(2);p.setColor(alpha(u.color<0?CYAN:COLORS[u.color],.35f));c.drawCircle(u.x,u.y,u.r+12,p);p.setStyle(Paint.Style.FILL);}
    void upgrade(Canvas c){p.setColor(alpha(Color.rgb(2,4,14),.9f));c.drawRect(0,0,W,H,p);center(c,"EVOLUÇÃO",260,48,GOLD,true);center(c,"ESCOLHA UMA MUTAÇÃO",315,20,MUTED,false);for(int i=0;i<3;i++){p.setColor(Color.rgb(18,25,58));c.drawRoundRect(cards[i],24,24,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(i==0?CYAN:i==1?PINK:GOLD);c.drawRoundRect(cards[i],24,24,p);p.setStyle(Paint.Style.FILL);text(c,upNames[i],85,cards[i].top+50,23,TEXT,true);text(c,upDesc[i],85,cards[i].top+95,17,MUTED,false);}}
    void result(Canvas c){center(c,runWon?"NÚCLEO ASCENDENTE":"A LUZ SE APAGOU",220,42,runWon?GOLD:RED,true);center(c,runWon?"A COLMEIA FOI CONVERTIDA":"A ENERGIA PERMANECE",275,20,MUTED,false);drawCore(c,CX,470,Math.max(1,form),session);center(c,"PONTOS  "+score,650,36,TEXT,true);center(c,"MAIOR COMBO  "+bestCombo,705,23,PINK,true);center(c,"FORMA  "+form+"  •  SETOR "+sector,752,20,MUTED,false);button(c,againBtn,"NOVA EVOLUÇÃO",VIOLET);button(c,homeBtn,"INÍCIO",Color.rgb(18,26,58));}
    void overlay(Canvas c,String s){p.setColor(alpha(Color.rgb(2,4,14),.84f));c.drawRect(0,0,W,H,p);center(c,s,380,48,TEXT,true);} void button(Canvas c,RectF r,String s,int col){p.setColor(col);c.drawRoundRect(r,24,24,p);center(c,s,r.centerY()+9,22,TEXT,true);}
    void bar(Canvas c,float x,float y,float w,float h,float v,int col,String label){p.setColor(Color.rgb(24,31,63));c.drawRoundRect(new RectF(x,y,x+w,y+h),9,9,p);p.setColor(col);c.drawRoundRect(new RectF(x,y,x+w*Math.max(0,Math.min(1,v)),y+h),9,9,p);text(c,label,x,y-12,14,MUTED,true);}
    void center(Canvas c,String s,float y,float size,int col,boolean bold){t.setTextAlign(Paint.Align.CENTER);t.setTextSize(size);t.setColor(col);t.setTypeface(android.graphics.Typeface.create("sans",bold?1:0));c.drawText(s,W/2,y,t);} void text(Canvas c,String s,float x,float y,float size,int col,boolean bold){t.setTextAlign(Paint.Align.LEFT);t.setTextSize(size);t.setColor(col);t.setTypeface(android.graphics.Typeface.create("sans",bold?1:0));c.drawText(s,x,y,t);}
    int alpha(int c,float a){return Color.argb((int)(255*Math.max(0,Math.min(1,a))),Color.red(c),Color.green(c),Color.blue(c));} void vibrate(int ms){try{if(vib==null)return;if(Build.VERSION.SDK_INT>=26)vib.vibrate(VibrationEffect.createOneShot(ms,90));else vib.vibrate(ms);}catch(Exception ignored){}} void sound(int kind,int ms){try{if(tone!=null)tone.startTone(kind,ms);}catch(Exception ignored){}}
    static int nextId=1;
    static final class Enemy{float x,y,r,speed,phase,cool=2;int type,color,hp,hitId;boolean bossMinion;}
    static final class Pulse{int id=nextId++,color,gen;float x,y,r=2,max,speed=255,delay;boolean closing;Pulse(float x,float y,int c,float m,int g){this(x,y,c,m,g,0);}Pulse(float x,float y,int c,float m,int g,float d){this.x=x;this.y=y;color=c;max=m;gen=g;delay=d;}}
    static final class Spark{float x,y,vx,vy,life,max,z;int c;} static final class Satellite{float x,y,a,cool;int index;Satellite(int i){index=i;a=i*2.1f;}} static final class Star{float x,y,z,a;}
}
