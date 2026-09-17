package com.haselton.hmgaibeatgenerator

import android.content.ContentValues
import android.media.*
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.*
import kotlin.random.Random

class MainActivity:AppCompatActivity(){
 private var track:AudioTrack?=null; private var pcm:ShortArray?=null; private var lastPrompt="beat"; private var bcSnare:ShortArray?=null
 override fun onCreate(b:Bundle?){super.onCreate(b);bcSnare=loadRaw(R.raw.battlecat_snare_tight)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(42,55,42,30);setBackgroundColor(0xff090b10.toInt())}
  fun txt(s:String,z:Float)=TextView(this).apply{text=s;textSize=z;setTextColor(0xfff2f4f8.toInt());setPadding(0,9,0,9)}
  root.addView(txt("HMG // AI BEAT GENERATOR",25f));root.addView(txt("Prompt-driven instrumentals • BattleCat kit",14f))
  val prompt=EditText(this).apply{hint="dark Detroit horrorcore, slow heavy drums, ominous bells, aggressive 808, sparse melody";setTextColor(0xffffffff.toInt());setHintTextColor(0xff737985.toInt());minLines=4};root.addView(prompt)
  val bpmLabel=txt("BPM 88",16f);root.addView(bpmLabel);val bpm=SeekBar(this).apply{max=100;progress=28};root.addView(bpm)
  bpm.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){bpmLabel.text="BPM ${60+p}"};override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}})
  val status=txt("Ready • v0.3",14f);root.addView(status);val gen=Button(this).apply{text="GENERATE FROM PROMPT"};root.addView(gen);val play=Button(this).apply{text="PLAY / STOP";isEnabled=false};root.addView(play);val save=Button(this).apply{text="DOWNLOAD WAV";isEnabled=false};root.addView(save);setContentView(root)
  gen.setOnClickListener{val p=prompt.text.toString().ifBlank{"grimy boom bap"};lastPrompt=p;status.text="Composing...";gen.isEnabled=false;thread{val a=BeatEngine.render(60+bpm.progress,p,bcSnare);runOnUiThread{pcm=a;status.text="Generated • ${a.size/88200}s • ${BeatEngine.describe(p)}";play.isEnabled=true;save.isEnabled=true;gen.isEnabled=true}}}
  play.setOnClickListener{if(track?.playState==AudioTrack.PLAYSTATE_PLAYING){track?.stop();track?.release();track=null}else pcm?.let{playPcm(it)}}
  save.setOnClickListener{pcm?.let{val n="HMG_${lastPrompt.replace(Regex("[^A-Za-z0-9]+"),"_").take(22)}_${System.currentTimeMillis()}.wav";try{saveWav(n,it);status.text="Saved • Music/HMG Beat Generator/$n"}catch(e:Exception){status.text="Save failed: ${e.message}"}}}
 }
 private fun loadRaw(id:Int):ShortArray?=try{val bytes=resources.openRawResource(id).readBytes();val bb=ByteBuffer.wrap(bytes,44,bytes.size-44).order(ByteOrder.LITTLE_ENDIAN);ShortArray((bytes.size-44)/2){bb.short}}catch(e:Exception){null}
 private fun playPcm(d:ShortArray){track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()).setBufferSizeInBytes(d.size*2).setTransferMode(AudioTrack.MODE_STATIC).build();track!!.write(d,0,d.size);track!!.play()}
 private fun saveWav(n:String,d:ShortArray){val v=ContentValues().apply{put(MediaStore.Audio.Media.DISPLAY_NAME,n);put(MediaStore.Audio.Media.MIME_TYPE,"audio/wav");put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/HMG Beat Generator")};val u=contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,v)?:error("create failed");contentResolver.openOutputStream(u)!!.use{Wav.write(it,d)}}
}

data class Style(val trap:Boolean,val boom:Boolean,val west:Boolean,val dark:Boolean,val hard:Boolean,val sparse:Boolean,val busy:Boolean,val swing:Double,val minor:Boolean,val voice:String,val bass:String)
object BeatEngine{
 const val SR=44100
 fun style(p:String):Style{val s=p.lowercase();fun h(vararg a:String)=a.any{s.contains(it)};return Style(h("trap","drill"),h("boom bap","boombap","90s","grimy"),h("west coast","g-funk","funk"),h("dark","horror","ominous","sinister","creepy"),h("hard","aggressive","heavy","hardcore"),h("sparse","minimal","simple"),h("busy","complex","fast hats"),if(h("swing","boom bap","grimy")) .13 else 0.0,!h("happy","bright","major"),when{h("bell","chime")->"bell";h("organ")->"organ";h("string","orchestral")->"strings";h("synth","g-funk")->"synth";else->if(h("dark","horror"))"bell" else "keys"},if(h("808","trap","drill"))"808" else if(h("funk","west coast"))"funk" else "sub")}
 fun describe(p:String):String{val s=style(p);return listOf(if(s.trap)"trap" else if(s.boom)"boom-bap" else if(s.west)"west-coast" else "hip-hop",if(s.minor)"minor" else "major",s.voice,s.bass,"BattleCat snare").joinToString(" / ")}
 fun render(bpm:Int,prompt:String,snareSample:ShortArray?):ShortArray{
  val st=style(prompt);val bars=20;val beat=60.0/bpm;val step=beat/4;val frames=(bars*4*beat*SR).toInt();val l=DoubleArray(frames);val r=DoubleArray(frames);val rnd=Random(prompt.hashCode()+System.nanoTime().toInt())
  for(bar in 0 until bars){val energy=if(bar<2).45 else if((bar/4)%3==2)1.0 else .78
   for(pos in 0..15){var t=bar*4*beat+pos*step;if(pos%2==1)t+=step*st.swing
    val kp=when{st.trap->pos in intArrayOf(0,3,7,10,14);st.boom->pos in intArrayOf(0,6,9,14);st.west->pos in intArrayOf(0,7,10);else->pos in intArrayOf(0,8,11)}
    if(kp&&(!st.sparse||rnd.nextDouble()>.25))kick(l,r,t,(if(st.hard)1.05 else .82)*energy)
    val sp=if(st.trap)pos==8 else pos==4||pos==12;if(sp){if(snareSample!=null)sample(l,r,t,snareSample,.68*energy)else snare(l,r,t,.52*energy)}
    val hs=if(st.busy||st.trap)1 else 2;if(pos%hs==0&&!(st.sparse&&pos%4!=0))hat(l,r,t,.12*energy,rnd.nextDouble(-.35,.35))
   }
  }
  val scale=if(st.minor)intArrayOf(0,3,5,7,10,12) else intArrayOf(0,4,5,7,9,12);val root=if(st.dark)46.25 else if(st.west)55.0 else 49.0
  for(bar in 0 until bars){val hook=(bar/4)%3==2
   for(b in 0..3){if(!st.sparse||b==0||hook){val deg=scale[(bar+b)%4];bass(l,r,(bar*4+b)*beat,root*2.0.pow(deg/12.0),if(st.bass=="808")beat*.9 else beat*.55,if(st.hard).48 else .34)}}
   if(bar>=2){val count=if(st.sparse)2 else if(hook)6 else 4;for(n in 0 until count){val pos=(n*4+(bar+n)%3)%16;val deg=scale[(n+bar)%scale.size];tone(l,r,bar*4*beat+pos*step,root*4*2.0.pow(deg/12.0),step*(if(st.voice=="strings")5 else 2),if(hook).15 else .10,st.voice,(n%3-1)*.38)}}
  }
  val peak=max(l.maxOf{abs(it)},r.maxOf{abs(it)}).coerceAtLeast(.1);val out=ShortArray(frames*2);for(i in 0 until frames){out[i*2]=(tanh(l[i]/peak*2.0)*29500).toInt().toShort();out[i*2+1]=(tanh(r[i]/peak*2.0)*29500).toInt().toShort()};return out
 }
 private fun add(l:DoubleArray,r:DoubleArray,i:Int,v:Double,p:Double){if(i !in l.indices)return;l[i]+=v*(1-p)*.5;r[i]+=v*(1+p)*.5}
 private fun sample(l:DoubleArray,r:DoubleArray,t:Double,s:ShortArray,g:Double){val start=(t*SR).toInt();var j=0;var i=start;while(j+1<s.size&&i<l.size){l[i]+=s[j]/32768.0*g;r[i]+=s[j+1]/32768.0*g;j+=2;i++}}
 private fun kick(l:DoubleArray,r:DoubleArray,t:Double,g:Double){val st=(t*SR).toInt();for(i in 0 until (.38*SR).toInt()){val x=i.toDouble()/SR;add(l,r,st+i,sin(2*PI*(150*exp(-x*20)+42)*x)*exp(-x*13)*g,0.0)}}
 private fun snare(l:DoubleArray,r:DoubleArray,t:Double,g:Double){val st=(t*SR).toInt();val q=Random(st);for(i in 0 until (.18*SR).toInt()){val x=i.toDouble()/SR;add(l,r,st+i,(q.nextDouble()*2-1)*exp(-x*22)*g,0.0)}}
 private fun hat(l:DoubleArray,r:DoubleArray,t:Double,g:Double,p:Double){val st=(t*SR).toInt();val q=Random(st+3);for(i in 0 until (.055*SR).toInt()){val x=i.toDouble()/SR;add(l,r,st+i,(q.nextDouble()*2-1)*exp(-x*85)*g,p)}}
 private fun bass(l:DoubleArray,r:DoubleArray,t:Double,f:Double,d:Double,g:Double){val st=(t*SR).toInt();for(i in 0 until (d*SR).toInt()){val x=i.toDouble()/SR;add(l,r,st+i,(sin(2*PI*f*x)+.2*sin(4*PI*f*x))*exp(-x*2.6)*g,0.0)}}
 private fun tone(l:DoubleArray,r:DoubleArray,t:Double,f:Double,d:Double,g:Double,type:String,p:Double){val st=(t*SR).toInt();for(i in 0 until (d*SR).toInt()){val x=i.toDouble()/SR;val env=(1-exp(-x*35))*exp(-x*(if(type=="strings")1.3 else 4.0));val v=when(type){"bell"->sin(2*PI*f*x)+.42*sin(2*PI*f*2.01*x)+.2*sin(2*PI*f*3.9*x);"organ"->sin(2*PI*f*x)+.35*sin(4*PI*f*x);"synth"->sin(2*PI*f*x)+.25*sin(6*PI*f*x);"strings"->sin(2*PI*f*x)+.3*sin(2*PI*f*1.005*x);else->sin(2*PI*f*x)+.18*sin(4*PI*f*x)};add(l,r,st+i,v*env*g,p)}}
}
object Wav{fun write(o:OutputStream,d:ShortArray){val bytes=d.size*2;fun le(v:Int,n:Int){repeat(n){o.write(v shr (8*it) and 255)}};o.write("RIFF".toByteArray());le(36+bytes,4);o.write("WAVEfmt ".toByteArray());le(16,4);le(1,2);le(2,2);le(44100,4);le(176400,4);le(4,2);le(16,2);o.write("data".toByteArray());le(bytes,4);for(x in d){val v=x.toInt();o.write(v and 255);o.write(v shr 8 and 255)}}}
