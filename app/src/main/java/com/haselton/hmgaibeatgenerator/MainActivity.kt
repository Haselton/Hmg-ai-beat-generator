package com.haselton.hmgaibeatgenerator

import android.content.ContentValues
import android.media.*
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream
import kotlin.concurrent.thread
import kotlin.math.*
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private var track: AudioTrack? = null
    private var pcm: ShortArray? = null
    private var lastPrompt = "beat"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(42,55,42,30);setBackgroundColor(0xff090b10.toInt())}
        fun text(s:String,z:Float)=TextView(this).apply{text=s;textSize=z;setTextColor(0xfff2f4f8.toInt());setPadding(0,9,0,9)}
        root.addView(text("HMG // AI BEAT GENERATOR",25f)); root.addView(text("Describe the instrumental you want",14f))
        val prompt=EditText(this).apply{hint="Example: dark Detroit horrorcore, slow heavy drums, ominous bells, aggressive 808, sparse melody";setTextColor(0xffffffff.toInt());setHintTextColor(0xff737985.toInt());minLines=4};root.addView(prompt)
        val bpmLabel=text("BPM 88",16f);root.addView(bpmLabel)
        val bpm=SeekBar(this).apply{max=100;progress=28};root.addView(bpm)
        bpm.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){bpmLabel.text="BPM ${60+p}"};override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}})
        val status=text("Ready • v0.2",14f);root.addView(status)
        val generate=Button(this).apply{text="GENERATE FROM PROMPT"};root.addView(generate)
        val play=Button(this).apply{text="PLAY / STOP";isEnabled=false};root.addView(play)
        val download=Button(this).apply{text="DOWNLOAD WAV";isEnabled=false};root.addView(download)
        root.addView(text("Prompt controls style, density, swing, scale, bass, melody and arrangement.",12f));setContentView(root)
        generate.setOnClickListener{ val p=prompt.text.toString().ifBlank{"grimy hip hop beat"};lastPrompt=p;status.text="Interpreting prompt & composing...";generate.isEnabled=false;thread{val r=BeatEngine.render(60+bpm.progress,p);runOnUiThread{pcm=r;status.text="Generated • ${r.size/88200}s • ${BeatEngine.describe(p)}";play.isEnabled=true;download.isEnabled=true;generate.isEnabled=true}}}
        play.setOnClickListener{if(track?.playState==AudioTrack.PLAYSTATE_PLAYING){track?.stop();track?.release();track=null}else pcm?.let{play(it)}}
        download.setOnClickListener{pcm?.let{data->try{val name="HMG_${lastPrompt.replace(Regex("[^A-Za-z0-9]+"),"_").take(24)}_${System.currentTimeMillis()}.wav";saveWav(name,data);status.text="Saved to Downloads/Music • $name"}catch(e:Exception){status.text="Save failed: ${e.message}"}}}
    }
    private fun play(data:ShortArray){track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()).setBufferSizeInBytes(data.size*2).setTransferMode(AudioTrack.MODE_STATIC).build();track!!.write(data,0,data.size);track!!.play()}
    private fun saveWav(name:String,data:ShortArray){val v=ContentValues().apply{put(MediaStore.Audio.Media.DISPLAY_NAME,name);put(MediaStore.Audio.Media.MIME_TYPE,"audio/wav");put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/HMG Beat Generator")};val uri=contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,v)?:error("Cannot create file");contentResolver.openOutputStream(uri)!!.use{Wav.write(it,data)}}
}

data class Style(val trap:Boolean,val boomBap:Boolean,val west:Boolean,val dark:Boolean,val aggressive:Boolean,val sparse:Boolean,val busy:Boolean,val swing:Double,val minor:Boolean,val melody:String,val bass:String)

object BeatEngine{
    const val SR=44100
    fun style(p:String):Style{val s=p.lowercase();fun has(vararg x:String)=x.any{s.contains(it)};return Style(has("trap","drill"),has("boom bap","boombap","90s","grimy"),has("west coast","g-funk","funk"),has("dark","horror","ominous","sinister","creepy"),has("hard","aggressive","heavy","hardcore"),has("sparse","minimal","simple"),has("busy","complex","fast hats"),if(has("swing","boom bap","grimy"))0.13 else 0.0,!has("happy","bright","major"),when{has("bell","chime")->"bell";has("organ")->"organ";has("string","orchestral")->"strings";has("piano","keys")->"keys";has("synth","g-funk")->"synth";else->if(has("dark","horror"))"bell" else "keys"},if(has("808","trap","drill"))"808" else if(has("funk","west coast"))"funk" else "sub")}
    fun describe(p:String):String{val s=style(p);return listOf(if(s.trap)"trap" else if(s.boomBap)"boom-bap" else if(s.west)"west-coast" else "hip-hop",if(s.minor)"minor" else "major",s.melody,s.bass).joinToString(" / ")}
    fun render(bpm:Int,prompt:String):ShortArray{val st=style(prompt);val bars=24;val beat=60.0/bpm;val frames=(bars*4*beat*SR).toInt();val l=DoubleArray(frames);val r=DoubleArray(frames);val rnd=Random(prompt.hashCode()+System.nanoTime().toInt());val step=beat/4
        for(bar in 0 until bars){val section=bar/4;val energy=when{bar<2->.45;section%3==2->1.0;else->.78};for(pos in 0..15){var t=bar*4*beat+pos*step;if(pos%2==1)t+=step*st.swing
            val kick=when{st.trap->pos in intArrayOf(0,3,7,10,14);st.boomBap->pos in intArrayOf(0,6,9,14);st.west->pos in intArrayOf(0,7,10);else->pos in intArrayOf(0,8,11)}
            if(kick && (!st.sparse||rnd.nextDouble()>.25))kick(l,r,t,(if(st.aggressive)1.05 else .82)*energy)
            val sn=if(st.trap)pos==8 else pos==4||pos==12;if(sn)snare(l,r,t,.52*energy)
            val hatStep=if(st.busy||st.trap)1 else 2;if(pos%hatStep==0 && !(st.sparse&&pos%4!=0))hat(l,r,t,.12*energy,rnd.nextDouble(-.35,.35));if(st.trap&&pos==15&&bar%4==3){hat(l,r,t-step*.66,.13,0.2);hat(l,r,t-step*.33,.15,-0.2)}}}
        val scale=if(st.minor)intArrayOf(0,3,5,7,10,12) else intArrayOf(0,4,5,7,9,12);val root=if(st.dark)46.25 else if(st.west)55.0 else 49.0
        for(bar in 0 until bars){val hook=(bar/4)%3==2;for(b in 0..3){if(!st.sparse||b==0||hook){val degree=scale[(bar+b)%min(4,scale.size)];bass(l,r,(bar*4+b)*beat,root*2.0.pow(degree/12.0),if(st.bass=="808")beat*.9 else beat*.55,if(st.aggressive).48 else .34)}}
            if(bar>=2){val notes=if(st.sparse)2 else if(hook)6 else 4;for(n in 0 until notes){val pos=(n*4+(bar+n)%3)%16;val degree=scale[(n+bar)%scale.size];val f=root*4*2.0.pow(degree/12.0);tone(l,r,bar*4*beat+pos*step,f,step*(if(st.melody=="strings")5 else 2),.10+(if(hook).05 else 0.0),st.melody,(n%3-1)*.38)}}}
        }
        val peak=max(l.maxOf{abs(it)},r.maxOf{abs(it)}).coerceAtLeast(.1);val out=ShortArray(frames*2);for(i in 0 until frames){out[i*2]=(tanh(l[i]/peak*2.1)*30000).toInt().toShort();out[i*2+1]=(tanh(r[i]/peak*2.1)*30000).toInt().toShort()};return out}
    private fun add(l:DoubleArray,r:DoubleArray,i:Int,v:Double,pan:Double){if(i !in l.indices)return;l[i]+=v*(1-pan)*.5;r[i]+=v*(1+pan)*.5}
    private fun kick(l:DoubleArray,r:DoubleArray,t:Double,g:Double){val st=(t*SR).toInt();for(i in 0 until (.38*SR).toInt()){val x=i.toDouble()/SR;add(l,r,st+i,sin(2*PI*(150*exp(-x*20)+42)*x)*exp(-x*13)*g,0.0)}}
    private fun snare(l:DoubleArray,r:DoubleArray,t:Double,g:Double){val st=(t*SR).toInt();val q=Random(st);for(i in 0 until (.18*SR).toInt()){val x=i.toDouble()/SR;add(l,r,st+i,(q.nextDouble()*2-1)*exp(-x*22)*g,0.08)}}
    private fun hat(l:DoubleArray,r:DoubleArray,t:Double,g:Double,p:Double){val st=(t*SR).toInt();val q=Random(st+3);for(i in 0 until (.055*SR).toInt()){val x=i.toDouble()/SR;add(l,r,st+i,(q.nextDouble()*2-1)*exp(-x*85)*g,p)}}
    private fun bass(l:DoubleArray,r:DoubleArray,t:Double,f:Double,d:Double,g:Double){val st=(t*SR).toInt();for(i in 0 until (d*SR).toInt()){val x=i.toDouble()/SR;add(l,r,st+i,(sin(2*PI*f*x)+.2*sin(4*PI*f*x))*exp(-x*2.6)*g,0.0)}}
    private fun tone(l:DoubleArray,r:DoubleArray,t:Double,f:Double,d:Double,g:Double,type:String,p:Double){val st=(t*SR).toInt();for(i in 0 until (d*SR).toInt()){val x=i.toDouble()/SR;val env=(1-exp(-x*35))*exp(-x*(if(type=="strings")1.3 else 4.0));val v=when(type){"bell"->sin(2*PI*f*x)+.42*sin(2*PI*f*2.01*x)+.2*sin(2*PI*f*3.9*x);"organ"->sin(2*PI*f*x)+.35*sin(4*PI*f*x);"synth"->sin(2*PI*f*x)+.25*sin(6*PI*f*x);"strings"->sin(2*PI*f*x)+.3*sin(2*PI*f*1.005*x);else->sin(2*PI*f*x)+.18*sin(4*PI*f*x)};add(l,r,st+i,v*env*g,p)}}
}

object Wav{fun write(o:OutputStream,d:ShortArray){val bytes=d.size*2;fun le(v:Int,n:Int){repeat(n){o.write(v shr (8*it) and 255)}};o.write("RIFF".toByteArray());le(36+bytes,4);o.write("WAVEfmt ".toByteArray());le(16,4);le(1,2);le(2,2);le(44100,4);le(44100*4,4);le(4,2);le(16,2);o.write("data".toByteArray());le(bytes,4);d.forEach{val v=it.toInt();o.write(v and 255);o.write(v shr 8 and 255)}}}
