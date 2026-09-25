
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

public class MechanicalKeyboardNatural implements NativeKeyListener {
    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_FRAMES = 256;
    private static final int MAX_VOICES = 24;
    private static final float MASTER = 0.78f;
    private static final float VOLUME_VARIATION = 0.035f; // very subtle
    private final Set<Integer> held = new HashSet<>();
    private final Random random = new Random();
    private MixerEngine mixer;

    public static void main(String[] args) throws Exception {
        MechanicalKeyboardNatural app = new MechanicalKeyboardNatural();
        app.start();
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
        System.out.println("Mechanical Keyboard — NATURAL");
        System.out.println("One real Cherry MX Black recording • no pitch effects • no artificial stereo");
        System.out.println("Type anywhere on your Mac. Press Ctrl+C to stop.");
        while (true) Thread.sleep(1000);
    }

    void start() throws Exception {
        float[][] sample = loadStereoWav(Paths.get("sounds/key.wav"));
        mixer = new MixerEngine(sample[0], sample[1]);
        mixer.start();
        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeKeyListener(this);
    }

    float[][] loadStereoWav(Path path) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    SAMPLE_RATE, 16, 2, 4, SAMPLE_RATE, false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, in)) {
                byte[] data = pcm.readAllBytes();
                int frames = data.length / 4;
                float[] l = new float[frames], r = new float[frames];
                ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                for (int i=0;i<frames;i++) {
                    l[i] = b.getShort()/32768f;
                    r[i] = b.getShort()/32768f;
                }
                return new float[][]{l,r};
            }
        }
    }

    public void nativeKeyPressed(NativeKeyEvent e) {
        synchronized (held) {
            if (!held.add(e.getKeyCode())) return; // suppress key repeat
        }
        float gain = 1f + (random.nextFloat()*2f-1f)*VOLUME_VARIATION;
        mixer.trigger(gain);
    }

    public void nativeKeyReleased(NativeKeyEvent e) {
        synchronized (held) { held.remove(e.getKeyCode()); }
    }
    public void nativeKeyTyped(NativeKeyEvent e) {}

    void stop() {
        try { GlobalScreen.removeNativeKeyListener(this); GlobalScreen.unregisterNativeHook(); }
        catch (Exception ignored) {}
        if (mixer != null) mixer.close();
    }

    static class Voice {
        int pos=0; float gain;
        Voice(float gain){this.gain=gain;}
    }

    static class MixerEngine implements AutoCloseable {
        final float[] left,right;
        final List<Voice> voices=new ArrayList<>();
        final Object lock=new Object();
        volatile boolean running;
        SourceDataLine line;
        Thread thread;

        MixerEngine(float[] left,float[] right){this.left=left;this.right=right;}

        void start() throws Exception {
            AudioFormat f=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,SAMPLE_RATE,16,2,4,SAMPLE_RATE,false);
            line=AudioSystem.getSourceDataLine(f);
            line.open(f,BUFFER_FRAMES*4*8);
            line.start();
            running=true;
            thread=new Thread(this::loop,"keyboard-audio");
            thread.setDaemon(true);
            thread.start();
        }

        void trigger(float gain){
            synchronized(lock){
                if(voices.size()>=MAX_VOICES) voices.remove(0);
                voices.add(new Voice(gain));
            }
        }

        void loop(){
            byte[] out=new byte[BUFFER_FRAMES*4];
            float[] l=new float[BUFFER_FRAMES], r=new float[BUFFER_FRAMES];
            while(running){
                Arrays.fill(l,0); Arrays.fill(r,0);
                synchronized(lock){
                    Iterator<Voice> it=voices.iterator();
                    while(it.hasNext()){
                        Voice v=it.next();
                        for(int i=0;i<BUFFER_FRAMES;i++){
                            if(v.pos>=left.length) break;
                            l[i]+=left[v.pos]*v.gain;
                            r[i]+=right[v.pos]*v.gain;
                            v.pos++;
                        }
                        if(v.pos>=left.length) it.remove();
                    }
                }
                int p=0;
                for(int i=0;i<BUFFER_FRAMES;i++){
                    // Only gentle protection against summed-overlap clipping.
                    float sl=Math.max(-1f,Math.min(1f,l[i]*MASTER));
                    float sr=Math.max(-1f,Math.min(1f,r[i]*MASTER));
                    short a=(short)(sl*32767), b=(short)(sr*32767);
                    out[p++]=(byte)a; out[p++]=(byte)(a>>8);
                    out[p++]=(byte)b; out[p++]=(byte)(b>>8);
                }
                line.write(out,0,out.length);
            }
        }

        public void close(){
            running=false;
            if(thread!=null) try{thread.join(500);}catch(InterruptedException ignored){}
            if(line!=null){line.stop();line.close();}
        }
    }
}
