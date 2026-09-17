package com.haselton.hmgaibeatgenerator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread
import kotlin.math.*
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private var track: AudioTrack? = null
    private var pcm: ShortArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(42,55,42,30); setBackgroundColor(0xff090b10.toInt()) }
        fun text(s:String, size:Float)=TextView(this).apply { text=s; textSize=size; setTextColor(0xfff2f4f8.toInt()); setPadding(0,10,0,10) }
        root.addView(text("HMG // BEAT GENERATOR",26f))
        root.addView(text("Prompt-driven instrumental prototype",14f))
        val prompt=EditText(this).apply { hint="Dark Detroit horrorcore, grimy drums, ominous mood..."; setTextColor(0xffffffff.toInt()); setHintTextColor(0xff737985.toInt()); minLines=3 }
        root.addView(prompt)
        val bpmLabel=text("BPM 88",16f); root.addView(bpmLabel)
        val bpm=SeekBar(this).apply { max=80; progress=28 }; root.addView(bpm)
        bpm.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){bpmLabel.text="BPM ${60+p}"}; override fun onStartTrackingTouch(s:SeekBar?){}; override fun onStopTrackingTouch(s:SeekBar?){} })
        val status=text("Ready",14f); root.addView(status)
        val generate=Button(this).apply{text="GENERATE BEAT"}; root.addView(generate)
        val play=Button(this).apply{text="PLAY / STOP"; isEnabled=false}; root.addView(play)
        root.addView(text("Prototype 0.1 • Instrumental only • Local generation",12f))
        setContentView(root)
        generate.setOnClickListener {
            status.text="Composing..."; generate.isEnabled=false
            thread { val result=BeatEngine.render(60+bpm.progress,prompt.text.toString()); runOnUiThread { pcm=result; status.text="Beat generated • ${(result.size/44100)} sec"; play.isEnabled=true; generate.isEnabled=true } }
        }
        play.setOnClickListener { if(track?.playState==AudioTrack.PLAYSTATE_PLAYING){track?.stop();track?.release();track=null}else pcm?.let{ play(it) } }
    }
    private fun play(data:ShortArray){
        track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()).setBufferSizeInBytes(data.size*2).setTransferMode(AudioTrack.MODE_STATIC).build()
        track!!.write(data,0,data.size); track!!.play()
    }
}

object BeatEngine {
    private const val SR=44100
    fun render(bpm:Int,prompt:String):ShortArray {
        val bars=16; val beats=bars*4; val seconds=beats*60.0/bpm; val frames=(seconds*SR).toInt(); val out=DoubleArray(frames)
        val beat=60.0/bpm; val step=beat/4.0; val dark=prompt.lowercase().let{it.contains("dark")||it.contains("horror")||it.contains("ominous")}
        val rnd=Random(prompt.hashCode()+bpm)
        for(s in 0 until bars*16){ val t=s*step; val pos=s%16
            if(pos==0||pos==8||(pos==11&&rnd.nextDouble()>.45)) kick(out,t,0.9)
            if(pos==4||pos==12) snare(out,t,0.62)
            if(pos%2==0) hat(out,t,0.17+rnd.nextDouble()*.08)
            if(pos==15&&s/16%4==3) { hat(out,t-step*.5,.2); hat(out,t,.25) }
        }
        val root=if(dark) 55.0 else 65.41
        for(b in 0 until beats){ if(b%4==0||rnd.nextDouble()>.62) bass(out,b*beat,root*if((b/4)%4==3)1.122 else 1.0,.42) }
        val peak=out.maxOf{abs(it)}.coerceAtLeast(1.0)
        val pcm=ShortArray(frames*2); for(i in 0 until frames){ val v=(tanh(out[i]/peak*1.8)*28500).toInt().toShort(); pcm[i*2]=v; pcm[i*2+1]=v }; return pcm
    }
    private fun kick(o:DoubleArray,t:Double,g:Double){ val st=(t*SR).toInt(); val n=(.42*SR).toInt(); for(i in 0 until n){if(st+i>=o.size)break; val x=i.toDouble()/SR; val f=145*exp(-x*18)+43; o[st+i]+=sin(2*PI*f*x)*exp(-x*12)*g}}
    private fun snare(o:DoubleArray,t:Double,g:Double){val st=(t*SR).toInt();val n=(.2*SR).toInt();val r=Random(st);for(i in 0 until n){if(st+i>=o.size)break;val x=i.toDouble()/SR;o[st+i]+=(r.nextDouble()*2-1)*exp(-x*24)*g}}
    private fun hat(o:DoubleArray,t:Double,g:Double){val st=(t*SR).toInt();val n=(.07*SR).toInt();val r=Random(st+7);for(i in 0 until n){if(st+i>=o.size)break;val x=i.toDouble()/SR;o[st+i]+=(r.nextDouble()*2-1)*exp(-x*70)*g}}
    private fun bass(o:DoubleArray,t:Double,f:Double,g:Double){val st=(t*SR).toInt();val n=(.55*SR).toInt();for(i in 0 until n){if(st+i>=o.size)break;val x=i.toDouble()/SR;o[st+i]+=sin(2*PI*f*x)*exp(-x*3.2)*g}}
}
