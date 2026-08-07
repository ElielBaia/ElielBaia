package com.forsetigames.babel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Fixed-step oriented-rectangle rigid body solver optimized for a stacking game. */
public final class PhysicsWorld {
    public static final float G=980f;
    private final List<Block> blocks;
    private final Events events;
    private final Set<Integer> supported=new HashSet<>();

    public interface Events {
        void onImpact(Block b,float speed);
        void onSettled(Block b);
        void onLost(Block b);
    }

    public PhysicsWorld(List<Block> blocks,Events events){this.blocks=blocks;this.events=events;}

    public void step(float dt,float wind){
        supported.clear();
        for(Block b:blocks){
            if(b.state!=Block.ACTIVE) continue;
            b.age+=dt;
            float gust=wind*b.spec.windDrag*(1f+(float)Math.sqrt(Math.max(0,b.y)/1000f)*.45f);
            b.vx+=gust/Math.max(1f,b.mass())*dt*.8f;
            b.vy-=G*dt;
            b.vx*=.9992f;
            b.omega*=.9985f;
            b.x+=b.vx*dt;
            b.y+=b.vy*dt;
            b.angle+=b.omega*dt;
            if(b.omega>3.4f)b.omega=3.4f; else if(b.omega<-3.4f)b.omega=-3.4f;
        }

        for(int iteration=0;iteration<5;iteration++){
            for(int i=0;i<blocks.size();i++){
                Block a=blocks.get(i);
                if(a.state!=Block.ACTIVE)continue;
                resolveGround(a);
                for(int j=0;j<blocks.size();j++){
                    if(i==j)continue;
                    Block b=blocks.get(j);
                    if(b.state!=Block.ACTIVE && b.state!=Block.STABLE)continue;
                    if(b.state==Block.ACTIVE && b.id<a.id)continue;
                    if(Math.abs(a.x-b.x)>a.aabbHalfW()+b.aabbHalfW()+8f)continue;
                    if(Math.abs(a.y-b.y)>a.aabbHalfH()+b.aabbHalfH()+8f)continue;
                    Collision c=sat(a,b);
                    if(c!=null)resolve(a,b,c);
                }
            }
        }

        for(Block b:blocks){
            if(b.state!=Block.ACTIVE)continue;
            boolean hasSupport=supported.contains(b.id);
            float speed2=b.vx*b.vx+b.vy*b.vy;
            if(hasSupport && speed2<34f*34f && Math.abs(b.omega)<.20f){
                b.sleepTimer+=dt;
                b.vx*=.88f;b.vy*=.72f;b.omega*=.82f;
                if(b.sleepTimer>.48f){
                    b.state=Block.STABLE;
                    b.vx=b.vy=b.omega=0f;
                    b.sleepTimer=0f;
                    if(b.material==MaterialType.CLAY){
                        float squash=.90f-Math.min(.09f,b.lastImpact/1500f);
                        float oldArea=b.w*b.h;
                        b.h*=squash;
                        b.w=Math.min(b.w*1.16f,oldArea/Math.max(1f,b.h)*1.10f);
                    }
                    events.onSettled(b);
                }
            }else{
                b.sleepTimer=Math.max(0f,b.sleepTimer-dt*2f);
            }
            if(b.y<-500f || b.x<-650f || b.x>1730f){
                b.state=Block.LOST;
                events.onLost(b);
            }
        }
    }

    private void resolveGround(Block a){
        float minY=Float.POSITIVE_INFINITY;
        float[] cs=corners(a);
        for(int i=1;i<8;i+=2)minY=Math.min(minY,cs[i]);
        if(minY>=0f)return;
        float pen=-minY;
        a.y+=pen*.92f;
        if(a.vy<0f){
            float impact=-a.vy;
            a.vy=-a.vy*a.spec.restitution*.35f;
            a.vx*=Math.max(.55f,1f-a.spec.friction*.20f);
            a.omega*=.75f;
            a.lastImpact=Math.max(a.lastImpact,impact);
            if(impact>90f)events.onImpact(a,impact);
        }
        supported.add(a.id);
    }

    private void resolve(Block a,Block b,Collision c){
        float invA=a.state==Block.ACTIVE?1f/a.mass():0f;
        float invB=b.state==Block.ACTIVE?1f/b.mass():0f;
        if(invA+invB<=0f)return;

        float correction=Math.max(0f,c.penetration-.35f)*.76f/(invA+invB);
        a.x+=c.nx*correction*invA;a.y+=c.ny*correction*invA;
        b.x-=c.nx*correction*invB;b.y-=c.ny*correction*invB;

        float rax=c.cx-a.x, ray=c.cy-a.y;
        float rbx=c.cx-b.x, rby=c.cy-b.y;
        float vax=a.vx-a.omega*ray, vay=a.vy+a.omega*rax;
        float vbx=b.vx-b.omega*rby, vby=b.vy+b.omega*rbx;
        float rvx=vax-vbx,rvy=vay-vby;
        float velN=rvx*c.nx+rvy*c.ny;
        if(velN>0f)return;

        float invIA=a.state==Block.ACTIVE?1f/Math.max(1f,a.inertia()):0f;
        float invIB=b.state==Block.ACTIVE?1f/Math.max(1f,b.inertia()):0f;
        float raCross=rax*c.ny-ray*c.nx;
        float rbCross=rbx*c.ny-rby*c.nx;
        float denom=invA+invB+raCross*raCross*invIA+rbCross*rbCross*invIB;
        if(denom<.00001f)return;
        float e=Math.min(a.spec.restitution,b.spec.restitution);
        float j=-(1f+e)*velN/denom;
        float ix=c.nx*j,iy=c.ny*j;
        applyImpulse(a, ix,iy,rax,ray,invA,invIA);
        applyImpulse(b,-ix,-iy,rbx,rby,invB,invIB);

        vax=a.vx-a.omega*ray;vay=a.vy+a.omega*rax;
        vbx=b.vx-b.omega*rby;vby=b.vy+b.omega*rbx;
        rvx=vax-vbx;rvy=vay-vby;
        float tangentX=rvx-c.nx*(rvx*c.nx+rvy*c.ny);
        float tangentY=rvy-c.ny*(rvx*c.nx+rvy*c.ny);
        float tl=(float)Math.sqrt(tangentX*tangentX+tangentY*tangentY);
        if(tl>.0001f){
            tangentX/=tl;tangentY/=tl;
            float raT=rax*tangentY-ray*tangentX;
            float rbT=rbx*tangentY-rby*tangentX;
            float dT=invA+invB+raT*raT*invIA+rbT*rbT*invIB;
            float jt=-(rvx*tangentX+rvy*tangentY)/Math.max(.0001f,dT);
            float mu=(float)Math.sqrt(a.spec.friction*b.spec.friction);
            float maxF=Math.abs(j)*mu;
            jt=Math.max(-maxF,Math.min(maxF,jt));
            float fx=tangentX*jt,fy=tangentY*jt;
            applyImpulse(a,fx,fy,rax,ray,invA,invIA);
            applyImpulse(b,-fx,-fy,rbx,rby,invB,invIB);
        }

        float relImpact=Math.max(0f,-velN);
        a.lastImpact=Math.max(a.lastImpact,relImpact);
        b.lastImpact=Math.max(b.lastImpact,relImpact);
        if(relImpact>105f)events.onImpact(a,relImpact);

        if(c.ny>.42f)supported.add(a.id);
        if(c.ny<-.42f && b.state==Block.ACTIVE)supported.add(b.id);
    }

    private static void applyImpulse(Block b,float ix,float iy,float rx,float ry,float invM,float invI){
        if(invM<=0f)return;
        b.vx+=ix*invM;b.vy+=iy*invM;
        b.omega+=(rx*iy-ry*ix)*invI;
    }

    private static final class Collision{
        float nx,ny,penetration,cx,cy;
    }

    private static Collision sat(Block a,Block b){
        float[] ac=corners(a),bc=corners(b);
        float[][] axes=new float[4][2];
        axisFromEdge(ac,0,axes[0]);axisFromEdge(ac,2,axes[1]);
        axisFromEdge(bc,0,axes[2]);axisFromEdge(bc,2,axes[3]);
        float minOverlap=Float.POSITIVE_INFINITY,bestX=0,bestY=1;
        for(float[] axis:axes){
            float minA=Float.POSITIVE_INFINITY,maxA=Float.NEGATIVE_INFINITY;
            float minB=Float.POSITIVE_INFINITY,maxB=Float.NEGATIVE_INFINITY;
            for(int i=0;i<8;i+=2){
                float p=ac[i]*axis[0]+ac[i+1]*axis[1];minA=Math.min(minA,p);maxA=Math.max(maxA,p);
                p=bc[i]*axis[0]+bc[i+1]*axis[1];minB=Math.min(minB,p);maxB=Math.max(maxB,p);
            }
            float overlap=Math.min(maxA,maxB)-Math.max(minA,minB);
            if(overlap<=0f)return null;
            if(overlap<minOverlap){minOverlap=overlap;bestX=axis[0];bestY=axis[1];}
        }
        float dx=a.x-b.x,dy=a.y-b.y;
        if(dx*bestX+dy*bestY<0f){bestX=-bestX;bestY=-bestY;}
        Collision c=new Collision();c.nx=bestX;c.ny=bestY;c.penetration=minOverlap;
        float[] pa=supportPoint(ac,-bestX,-bestY);
        float[] pb=supportPoint(bc,bestX,bestY);
        c.cx=(pa[0]+pb[0])*.5f;c.cy=(pa[1]+pb[1])*.5f;
        return c;
    }

    private static float[] corners(Block b){
        float hw=b.w*.5f,hh=b.h*.5f;
        float c=(float)Math.cos(b.angle),s=(float)Math.sin(b.angle);
        float[] local={-hw,-hh, hw,-hh, hw,hh, -hw,hh};
        float[] out=new float[8];
        for(int i=0;i<8;i+=2){
            float lx=local[i],ly=local[i+1];
            out[i]=b.x+lx*c-ly*s;out[i+1]=b.y+lx*s+ly*c;
        }
        return out;
    }
    private static void axisFromEdge(float[] c,int p,float[] out){
        int q=(p+2)%8;float ex=c[q]-c[p],ey=c[q+1]-c[p+1];
        float nx=-ey,ny=ex;float l=(float)Math.sqrt(nx*nx+ny*ny);if(l<.0001f){out[0]=1;out[1]=0;}else{out[0]=nx/l;out[1]=ny/l;}
    }
    private static float[] supportPoint(float[] cs,float dx,float dy){
        float best=Float.NEGATIVE_INFINITY,bx=0,by=0;int count=0;
        for(int i=0;i<8;i+=2){float p=cs[i]*dx+cs[i+1]*dy;if(p>best+.4f){best=p;bx=cs[i];by=cs[i+1];count=1;}else if(Math.abs(p-best)<.4f){bx+=cs[i];by+=cs[i+1];count++;}}
        return new float[]{bx/Math.max(1,count),by/Math.max(1,count)};
    }
}
