package com.forsetigames.pulse100;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import java.util.Locale;
import java.util.Random;

final class InfiniteGameView extends View implements Choreographer.FrameCallback {
    private static final float W=720f,H=1280f,TOP=190f,BOTTOM=1110f;
    private static final int HOME=0,PLAY=1,PAUSE=2,OVER=3;
    private static final int NORMAL=0,PRISM=1,CHARGED=2,DARK=3;
    private static final int WHITE=-1;
    private static final int BG=Color.rgb(4,7,21),TEXT=Color.rgb(247,249,255),MUTED=Color.rgb(146,157,190);
    private static final int CYAN=Color.rgb(54,224,255),PINK=Color.rgb(255,74,206),GOLD=Color.rgb(255,210,70),VIOLET=Color.rgb(150,90,255),GREEN=Color.rgb(78,240,156);
    private static final int[] COLORS={CYAN,PINK,GOLD,VIOLET};

    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG),t=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng=new Random();
    private final ArrayList<Orb> orbs=new ArrayList<>();
    private final ArrayList<Wave> waves=new ArrayList<>();
    private final ArrayList<Wave> queued=new ArrayList<>();
    private final ArrayList<Spark> sparks=new ArrayList<>();
    private final ArrayList<Star> stars=new ArrayList<>();
    private final SharedPreferences prefs;
    private final Vibrator vibrator;
    private ToneGenerator tone;

    private int screen=HOME,sector=1,era=0,score=0,best=0,combo=0,bestCombo=0,pulseId=1,charges=1,lives=3;
    private int sectorGoal=22,sectorHits=0,colossusHp=0,colossusMax=0;
    private float energy=0f,sectorTime=0f,transition=0f,comboHold=0f,shake=0f,flash=0f,slow=0f,spawnClock=0f;
    private boolean running=false,eventActive=false,colossus=false;
    private long lastFrame=0L,seed=0L;
    private float scale=1f,ox=0f,oy=0f;
    private String banner="",subBanner="";
    private float bannerTime=0f;

    private final RectF playButton=new RectF(105,620,615,730),pauseButton=new RectF(632,42,690,100),resumeButton=new RectF(115,875,605,970),homeButton=new RectF(115,995,605,1080);

    InfiniteGameView(Context c){
        super(c); setFocusable(true); setKeepScreenOn(true);
        t.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL));
        prefs=c.getSharedPreferences("pulse100_v2",Context.MODE_PRIVATE); best=prefs.getInt("best_sector",0);
        vibrator=(Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE);
        try{tone=new ToneGenerator(AudioManager.STREAM_MUSIC,42);}catch(RuntimeException e){tone=null;}
        rng.setSeed(731337L); for(int i=0;i<90;i++){Star s=new Star();s.x=r(0,W);s.y=r(0,H);s.z=r(.4f,1.8f);s.a=r(.1f,.65f);stars.add(s);}    }

    void resumeLoop(){if(running)return;running=true;lastFrame=0;Choreographer.getInstance().postFrameCallback(this);}    
    void pauseLoop(){running=false;Choreographer.getInstance().removeFrameCallback(this);if(screen==PLAY)screen=PAUSE;}
    boolean handleBack(){if(screen==HOME)return false;screen=screen==PLAY?PAUSE:HOME;invalidate();return true;}

    @Override protected void onDetachedFromWindow(){pauseLoop();if(tone!=null){tone.release();tone=null;}super.onDetachedFromWindow();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){scale=Math.min(w/W,h/H);ox=(w-W*scale)/2f;oy=(h-H*scale)/2f;}
    @Override public void doFrame(long n){if(!running)return;float dt=lastFrame==0?1/60f:Math.min(.05f,(n-lastFrame)/1_000_000_000f);lastFrame=n;update(dt);invalidate();Choreographer.getInstance().postFrameCallback(this);}

    private void update(float dt){
        float timeScale=slow>0?.42f:1f;if(slow>0)slow-=dt;if(flash>0)flash-=dt;if(shake>0)shake-=dt;if(bannerTime>0)bannerTime-=dt;
        for(Star s:stars){s.y+=dt*(5f+era*2f)*s.z;if(s.y>H){s.y=0;s.x=r(0,W);}}
        if(screen!=PLAY){updateSparks(dt);return;}
        dt*=timeScale; sectorTime+=dt; spawnClock-=dt;
        if(transition>0){transition-=dt;if(transition<=0)beginSector();updateSparks(dt);return;}
        if(spawnClock<=0){spawnClock=Math.max(.18f,.72f-sector*.015f);if(orbs.size()<maxOrbs())spawnOrb(false);}
        moveOrbs(dt); updateWaves(dt); updateSparks(dt);
        if(comboHold>0){comboHold-=dt;if(comboHold<=0)combo=0;}
        if(colossus&&colossusHp<=0){finishSector(true);return;}
        if(sectorHits>=sectorGoal&&!colossus){finishSector(true);return;}
        if(sectorTime>sectorLimit()){lives--;if(lives<=0)gameOver();else{show("RESSONÂNCIA ROMPIDA","-1 núcleo");finishSector(false);}}
    }

    @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()!=MotionEvent.ACTION_DOWN)return true;float x=(e.getX()-ox)/scale,y=(e.getY()-oy)/scale;tap(x,y);return true;}
    private void tap(float x,float y){
        if(screen==HOME){if(playButton.contains(x,y))startJourney();}
        else if(screen==PLAY){if(pauseButton.contains(x,y))screen=PAUSE;else if(y>=TOP&&y<=BOTTOM&&charges>0){charges--;fire(x,y,charges==0?WHITE:dominantColor(),charges==0?138f:112f);}}
        else if(screen==PAUSE){if(resumeButton.contains(x,y))screen=PLAY;else if(homeButton.contains(x,y))screen=HOME;}
        else if(screen==OVER){if(playButton.contains(x,y))startJourney();else if(homeButton.contains(x,y))screen=HOME;}
        invalidate();
    }

    private void startJourney(){screen=PLAY;sector=1;era=0;score=0;combo=0;bestCombo=0;energy=0;charges=1;lives=3;seed=System.currentTimeMillis();rng.setSeed(seed);beginSector();show("RESSONÂNCIA INFINITA","Mantenha o universo vivo");}
    private void beginSector(){
        transition=0;sectorTime=0;sectorHits=0;waves.clear();queued.clear();sparks.clear();eventActive=sector%3==0;colossus=sector%5==0;era=Math.min(5,(sector-1)/4);
        sectorGoal=18+sector*4;colossusMax=colossus?20+sector*3:0;colossusHp=colossusMax;
        int target=Math.min(maxOrbs(),30+sector*4);while(orbs.size()<target)spawnOrb(true);while(orbs.size()>target)orbs.remove(orbs.size()-1);
        for(Orb o:orbs){o.alive=true;o.hit=0;o.last=0;}
        show(colossus?"ENTIDADE COLOSSAL":"SETOR "+sector,eraName());tone(ToneGenerator.TONE_PROP_ACK,100);
    }
    private void finishSector(boolean won){
        if(won){score+=sector*250+lives*100;sector++;energy=Math.min(100,energy+18);if(energy>=100){charges=Math.min(3,charges+1);energy-=100;}show("ASCENSÃO","Setor "+sector+" alcançado");}
        transition=1.35f;slow=.28f;shake=.22f;flash=.20f;vibrate(55);tone(ToneGenerator.TONE_PROP_ACK,150);
    }
    private void gameOver(){screen=OVER;best=Math.max(best,sector);prefs.edit().putInt("best_sector",best).apply();tone(ToneGenerator.TONE_PROP_NACK,220);vibrate(90);}

    private void spawnOrb(boolean anywhere){
        Orb o=new Orb();o.type=NORMAL;float roll=rng.nextFloat();if(sector>=3&&roll<.08f)o.type=PRISM;else if(sector>=6&&roll<.15f)o.type=CHARGED;else if(sector>=9&&roll<.20f)o.type=DARK;
        o.radius=o.type==CHARGED?19:o.type==PRISM?18:r(10,16);o.x=r(30,W-30);o.y=anywhere?r(TOP+30,BOTTOM-30):TOP+10;o.color=rng.nextInt(Math.min(4,1+sector/3));
        float a=r(0,(float)Math.PI*2),speed=r(50+sector*2,95+sector*4);o.vx=(float)Math.cos(a)*speed;o.vy=(float)Math.sin(a)*speed;o.phase=r(0,6.28f);o.alive=true;orbs.add(o);
    }
    private void moveOrbs(float dt){
        float cx=W/2f,cy=(TOP+BOTTOM)/2f;for(Orb o:orbs){if(!o.alive)continue;o.phase+=dt*(1.2f+era*.25f);
            if(era>=1){float dx=cx-o.x,dy=cy-o.y,inv=1f/(float)Math.max(80,Math.sqrt(dx*dx+dy*dy));float pull=(era>=4?44:18)*dt;o.vx+=-dy*inv*pull;o.vy+=dx*inv*pull;}
            if(eventActive){o.vx+=(float)Math.sin(sectorTime*1.7+o.phase)*12*dt;o.vy+=(float)Math.cos(sectorTime*1.4+o.phase)*12*dt;}
            o.x+=o.vx*dt;o.y+=o.vy*dt;if(o.x<o.radius){o.x=o.radius;o.vx=Math.abs(o.vx);}else if(o.x>W-o.radius){o.x=W-o.radius;o.vx=-Math.abs(o.vx);}if(o.y<TOP+o.radius){o.y=TOP+o.radius;o.vy=Math.abs(o.vy);}else if(o.y>BOTTOM-o.radius){o.y=BOTTOM-o.radius;o.vy=-Math.abs(o.vy);}
        }
    }
    private void fire(float x,float y,int color,float radius){Wave w=new Wave();w.id=pulseId++;w.x=x;w.y=y;w.color=color;w.max=radius;w.speed=300;w.a=1;waves.add(w);burst(x,y,TEXT,22);vibrate(18);tone(ToneGenerator.TONE_PROP_BEEP,65);}
    private void updateWaves(float dt){queued.clear();Iterator<Wave> it=waves.iterator();while(it.hasNext()){Wave w=it.next();if(!w.close){w.r+=w.speed*dt;if(w.r>=w.max)w.close=true;}else{w.r-=w.speed*.46f*dt;w.a-=dt*1.6f;}if(w.r<=0||w.a<=0){it.remove();continue;}hit(w);}waves.addAll(queued);}
    private void hit(Wave w){for(Orb o:orbs){if(!o.alive||o.last==w.id)continue;float dx=o.x-w.x,dy=o.y-w.y,d=(float)Math.sqrt(dx*dx+dy*dy);if(Math.abs(d-w.r)>o.radius+10)continue;if(w.color!=WHITE&&o.type!=PRISM&&o.color!=w.color)continue;o.last=w.id;if(o.type==CHARGED&&o.hit++==0){burst(o.x,o.y,COLORS[o.color],7);continue;}trigger(o,w);}}
    private void trigger(Orb o,Wave src){o.alive=false;sectorHits++;score+=10+combo*2;combo++;bestCombo=Math.max(bestCombo,combo);comboHold=2.2f;energy=Math.min(100,energy+2.8f+(src.gen*.15f));
        if(energy>=100){energy-=100;charges=Math.min(3,charges+1);show("PULSO RECUPERADO","Toque novamente");tone(ToneGenerator.TONE_PROP_ACK,70);}
        int c=o.type==PRISM?WHITE:o.color;Wave q=new Wave();q.id=pulseId++;q.x=o.x;q.y=o.y;q.color=c;q.gen=src.gen+1;q.max=86+Math.min(50,q.gen*3)+(o.type==CHARGED?25:0);q.speed=280;q.a=1;queued.add(q);burst(o.x,o.y,c==WHITE?TEXT:COLORS[c],combo>=60?24:14);
        if(colossus&&distance(o.x,o.y,W/2f,650)<210)colossusHp-=o.type==PRISM?3:1;
        if(combo==25||combo==60||combo==100){show(combo==100?"TRANSCENDÊNCIA":combo==60?"SOBRECARGA":"RESSONÂNCIA",combo+"x");slow=combo==100?.42f:.16f;shake=.22f;flash=.15f;vibrate(combo==100?80:35);}
    }

    private void burst(float x,float y,int color,int n){for(int i=0;i<n;i++){Spark s=new Spark();float a=r(0,6.28f),v=r(50,220);s.x=x;s.y=y;s.vx=(float)Math.cos(a)*v;s.vy=(float)Math.sin(a)*v;s.life=s.max=r(.25f,.85f);s.size=r(1.5f,5);s.color=color;sparks.add(s);}}
    private void updateSparks(float dt){Iterator<Spark> it=sparks.iterator();while(it.hasNext()){Spark s=it.next();s.x+=s.vx*dt;s.y+=s.vy*dt;s.vx*=.965f;s.vy*=.965f;s.life-=dt;if(s.life<=0)it.remove();}}

    @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(BG);c.save();c.translate(ox+(shake>0?r(-7,7):0),oy+(shake>0?r(-7,7):0));c.scale(scale,scale);background(c);if(screen==HOME)home(c);else if(screen==PLAY)play(c);else if(screen==PAUSE){play(c);overlay(c,"PAUSADO");button(c,resumeButton,"CONTINUAR",VIOLET);button(c,homeButton,"INÍCIO",Color.rgb(20,27,58));}else over(c);if(flash>0){p.setColor(alpha(TEXT,Math.min(.45f,flash*2)));c.drawRect(0,0,W,H,p);}c.restore();}
    private void background(Canvas c){int tint=era==0?CYAN:era==1?VIOLET:era==2?PINK:era==3?GOLD:era==4?GREEN:TEXT;for(Star s:stars){p.setColor(alpha(tint,s.a));c.drawCircle(s.x,s.y,s.z,p);}p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(alpha(tint,.08f));for(int y=TOP;y<BOTTOM;y+=72)c.drawLine(0,y,W,y,p);p.setStyle(Paint.Style.FILL);}
    private void home(Canvas c){center(c,"PULSE",185,82,CYAN,true);center(c,"100",275,96,PINK,true);center(c,"RESSONÂNCIA INFINITA",350,24,MUTED,false);for(int i=0;i<5;i++){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(COLORS[i%4]);c.drawCircle(W/2f,475,30+i*24,p);}p.setStyle(Paint.Style.FILL);button(c,playButton,"INICIAR JORNADA",VIOLET);center(c,"MELHOR SETOR "+best,850,28,GOLD,true);center(c,"Sem fim. Sem cortes. Cada reação transforma o universo.",940,18,MUTED,false);}
    private void play(Canvas c){
        text(c,"SETOR "+sector,28,58,26,VIOLET,true);center(c,eraName(),62,18,MUTED,false);text(c,"♥ "+lives,28,108,24,PINK,true);center(c,String.format(Locale.US,"%d",score),126,50,TEXT,true);center(c,"COMBO "+combo+"x",166,22,combo>=60?GOLD:PINK,true);button(c,pauseButton,"Ⅱ",Color.rgb(18,25,55));
        p.setColor(alpha(Color.rgb(5,9,28),.55f));c.drawRect(0,TOP,W,BOTTOM,p);
        if(colossus)drawColossus(c);for(Wave w:waves)drawWave(c,w);for(Orb o:orbs)if(o.alive)drawOrb(c,o);for(Spark s:sparks){p.setColor(alpha(s.color,Math.max(0,s.life/s.max)));c.drawCircle(s.x,s.y,s.size,p);}
        p.setColor(Color.rgb(25,34,68));c.drawRoundRect(new RectF(45,1135,675,1164),15,15,p);p.setColor(GREEN);c.drawRoundRect(new RectF(45,1135,45+630*(energy/100f),1164),15,15,p);center(c,"RESSONÂNCIA "+(int)energy+"%",1195,18,TEXT,true);
        for(int i=0;i<3;i++){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(i<charges?CYAN:Color.rgb(55,62,88));c.drawCircle(290+i*70,1230,22,p);}p.setStyle(Paint.Style.FILL);
        center(c,colossus?"NÚCLEO "+Math.max(0,colossusHp)+" / "+colossusMax:"PROGRESSO "+sectorHits+" / "+sectorGoal,1085,19,colossus?GOLD:CYAN,true);
        if(bannerTime>0){center(c,banner,560,42,TEXT,true);center(c,subBanner,605,20,MUTED,false);}
    }
    private void drawColossus(Canvas c){float pulse=8+(float)Math.sin(sectorTime*3)*6;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(8);p.setColor(alpha(GOLD,.65f));c.drawCircle(W/2f,650,145+pulse,p);p.setStrokeWidth(3);p.setColor(alpha(PINK,.65f));c.drawCircle(W/2f,650,105-pulse*.4f,p);p.setStyle(Paint.Style.FILL);p.setColor(alpha(VIOLET,.35f));c.drawCircle(W/2f,650,82,p);}
    private void drawWave(Canvas c,Wave w){int color=w.color==WHITE?TEXT:COLORS[w.color];p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(alpha(color,w.a));c.drawCircle(w.x,w.y,w.r,p);p.setStrokeWidth(18);p.setColor(alpha(color,w.a*.12f));c.drawCircle(w.x,w.y,w.r,p);p.setStyle(Paint.Style.FILL);}
    private void drawOrb(Canvas c,Orb o){int color=o.type==PRISM?TEXT:o.type==DARK?Color.rgb(68,72,96):COLORS[o.color];p.setColor(alpha(color,.18f));c.drawCircle(o.x,o.y,o.radius*1.8f,p);p.setColor(color);c.drawCircle(o.x,o.y,o.radius,p);if(o.type==CHARGED){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(TEXT);c.drawCircle(o.x,o.y,o.radius+6,p);p.setStyle(Paint.Style.FILL);}if(o.type==PRISM){p.setColor(PINK);c.drawCircle(o.x-5,o.y,4,p);p.setColor(CYAN);c.drawCircle(o.x+5,o.y,4,p);}}
    private void over(Canvas c){center(c,"JORNADA ENCERRADA",220,42,PINK,true);center(c,"SETOR "+sector,390,112,TEXT,true);center(c,"PONTOS "+score,500,30,GOLD,true);center(c,"MELHOR COMBO "+bestCombo+"x",555,24,MUTED,false);button(c,playButton,"NOVA JORNADA",VIOLET);button(c,homeButton,"INÍCIO",Color.rgb(20,27,58));}
    private void overlay(Canvas c,String s){p.setColor(alpha(Color.rgb(2,4,14),.87f));c.drawRect(0,0,W,H,p);center(c,s,370,52,TEXT,true);}
    private void button(Canvas c,RectF r,String s,int color){p.setColor(color);c.drawRoundRect(r,26,26,p);center(c,s,r.centerY()+10,24,TEXT,true);}
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){t.setColor(color);t.setTextSize(size);t.setTypeface(android.graphics.Typeface.create("sans",bold?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL));c.drawText(s,x,y,t);}    
    private void center(Canvas c,String s,float y,float size,int color,boolean bold){t.setTextAlign(Paint.Align.CENTER);text(c,s,W/2f,y,size,color,bold);t.setTextAlign(Paint.Align.LEFT);}    
    private void show(String a,String b){banner=a;subBanner=b;bannerTime=2.1f;}
    private void tone(int type,int ms){if(tone!=null)try{tone.startTone(type,ms);}catch(RuntimeException ignored){}}
    private void vibrate(long ms){if(vibrator==null||!vibrator.hasVibrator())return;if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE));else vibrator.vibrate(ms);}    
    private int dominantColor(){return combo==0?WHITE:(combo/10)%Math.min(4,1+sector/3);}    
    private int maxOrbs(){return Math.min(170,70+sector*5);}private float sectorLimit(){return 34+Math.min(20,sector*.8f);}private String eraName(){String[] n={"CÂMARA","ÓRBITA","CONSTELAÇÃO","NEBULOSA","SINGULARIDADE","HORIZONTE INFINITO"};return n[era];}
    private float distance(float x1,float y1,float x2,float y2){float dx=x1-x2,dy=y1-y2;return(float)Math.sqrt(dx*dx+dy*dy);}private float r(float a,float b){return a+rng.nextFloat()*(b-a);}private int alpha(int c,float a){return Color.argb((int)(Math.max(0,Math.min(1,a))*255),Color.red(c),Color.green(c),Color.blue(c));}

    private static final class Orb{float x,y,vx,vy,radius,phase;int color,type,hit,last;boolean alive;}
    private static final class Wave{float x,y,r,max,speed,a;int color,id,gen;boolean close;}
    private static final class Spark{float x,y,vx,vy,life,max,size;int color;}
    private static final class Star{float x,y,z,a;}
}
