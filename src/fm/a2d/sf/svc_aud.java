// Audio Sub-service
package fm.a2d.sf;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.*;
import fm.a2d.sf.helper.AudioHelper;
import fm.a2d.sf.helper.L;

import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.util.Timer;


public class svc_aud implements AudioManager.OnAudioFocusChangeListener {

  private AudioManager m_AM;
  private RadioRecorder mRecorder = null;
  private Context m_context;
  private ServiceAudioCallback m_svc_acb;
  private com_api m_com_api;

  private int m_hw_size = 4096;
  private int at_min_size = 32768;//5120 * 16;//65536;
  private int chan_out = AudioFormat.CHANNEL_OUT_STEREO;

  private boolean pcm_write_thread_active = false;
  private boolean pcm_read_thread_active = false;

  private AudioTrack m_audiotrack = null;
  private Thread pcm_write_thread = null;
  private Thread pcm_read_thread = null;

  private boolean mHeadsetPlugged = false;
  private BroadcastReceiver mHeadsetListener = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      com_uti.logd("onReceive headset action: " + action);

      if (action != null && action.equals(Intent.ACTION_HEADSET_PLUG)) {
        headsetPluggedHandler(intent);
      }
    }
  };;


  // Up to 32 buffers:
  private static final int aud_buf_num = 32;   // 4, 8 skips too often

  private byte[] aud_buf_001, aud_buf_002, aud_buf_003, aud_buf_004, aud_buf_005, aud_buf_006, aud_buf_007, aud_buf_008, aud_buf_009, aud_buf_010, aud_buf_011, aud_buf_012, aud_buf_013, aud_buf_014, aud_buf_015, aud_buf_016, aud_buf_017, aud_buf_018, aud_buf_019, aud_buf_020, aud_buf_021, aud_buf_022, aud_buf_023, aud_buf_024, aud_buf_025, aud_buf_026, aud_buf_027, aud_buf_028, aud_buf_029, aud_buf_030, aud_buf_031, aud_buf_032;

  private byte[][] aud_buf_data = new byte[][]{ aud_buf_001, aud_buf_002, aud_buf_003, aud_buf_004, aud_buf_005, aud_buf_006, aud_buf_007, aud_buf_008, aud_buf_009, aud_buf_010, aud_buf_011, aud_buf_012, aud_buf_013, aud_buf_014, aud_buf_015, aud_buf_016, aud_buf_017, aud_buf_018, aud_buf_019, aud_buf_020, aud_buf_021, aud_buf_022, aud_buf_023, aud_buf_024, aud_buf_025, aud_buf_026, aud_buf_027, aud_buf_028, aud_buf_029, aud_buf_030, aud_buf_031, aud_buf_032 };

  private int[] aud_buf_len = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

  private int aud_buf_tail = 0;    // Tail is next index for pcm recorder to write to.   If head = tail, there is no info.   Increment on pcm read  (write to  buffer).
  private int aud_buf_head = 0;    // Head is next index for pcm player   to read  from.                                     Increment on pcm write (read from buffer).

//bufs  0   1   2   3
//tail  0   1   2   3
//head  0   0   0   0
//tail  1   2   3   0
//head  1   1   1   1
//tail  2   3   0   1
//head  2   2   2   2
//tail  3   0   1   2
//head  3   3   3   3


  private int writes_processed = 0;
  private int min_pcm_write_buffers = 1;//2;                                // Runs if at least 2 buffers are ready...

  private int m_channels = 2;
  private int m_samplerate = 44100;                               // Default = 8000 (Max w/ AMR)

  private boolean need_record_finish = false;
  private int write_stats_seconds = 60;//10; // Over 11184 will overflow int in calcs of "stats_frames = (2 * write_stats_seconds * m_samplerate * m_channels) / len;"
  private int read_stats_seconds = 60;//00;//10; // Over 11184 will overflow int in calcs of "stats_frames = (2 *  read_stats_seconds * m_samplerate * m_channels) / len;"
  private RandomAccessFile rand_acc_file = null;

  private boolean pcm_write_thread_waiting = false;

  private int pcm_priority = -19;//-20;//-19;                                 // For both read and write
  private int write_ctr = -1;   // -1 so first ++ increments to 0

  private int pcm_size_max = 65536;//4096;//8192;//Not tunable yet !!!     65536; // 64Kbytes = 16K stereo samples    // @ 44.1K = 0.37 seconds each buffer = ~ 12 second delay (& mem alloc errors) for 32
  private int pcm_size_min = 320;

  private int buf_errs = 0;
  private int max_bufs = 0;
  private int read_ctr = 0;

  // used for "new AudioTrack"        in audio_start()
  // used for requestAudioFocus       in requestForFocus()  (via audio_start()/audio_stop()
  // used for getStream(Max)Volume    in MainService:radio_status_send()  for unused volume reporting
  // used for setVolumeControlStream  in MainActivity:onCreate()
  private int audio_stream = AudioManager.STREAM_MUSIC;

  private int in_spkr_call_vol_poll_tmr_hndlr = 0;
  private int spkr_call_vol_poll_tmr_hndlr_re_enters = 0;
  private Timer spkr_call_vol_poll_tmr = null;
  private int spkr_call_vol_poll_tmr_hndlr_times = 0;

  private int max_call_vol = 35;
  private int cur_call_vol = 25;

  private int max_musi_vol = 65;
  private int cur_musi_vol = 55;

  private int[] cur_vols = new int[8];// {-1, -1, -1, -1, -1, -1, -1, -1};

  private AudioRecord m_audiorecorder = null;
  private boolean m_audiorecord_reading = false;

  private int mRecorderSource = 0;

  private AudioHelper mAudioHelper;

  private L ml;

  // Code:

  public svc_aud(Context c, ServiceAudioCallback cb_aud, com_api svc_com_api) { // Constructor
    ml = L.getInstance();

    ml.write("svc_aud::constructor");
    m_svc_acb = cb_aud;
    m_context = c;
    m_com_api = svc_com_api;
    com_uti.logd("");

    mAudioHelper = new AudioHelper(c);

    m_AM = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);


/* Make buffer size a multiple of AudioManager.getProperty (PROPERTY_OUTPUT_FRAMES_PER_BUFFER). Otherwise your callback will occasionally get two calls per timeslice rather than one.
    Unless your CPU usage is really light, this will probably end up glitching.

Use the sample rate provided by AudioManager.getProperty (PROPERTY_OUTPUT_SAMPLE_RATE). Otherwise your buffers take a detour through the system resampler.

API level 17 / 4.2+
 */

    String hw_size_str = "";
    try {

      hw_size_str = m_AM.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
    } catch (Throwable e) {
      com_uti.loge("AudioManager.getProperty Throwable e: " + e);
      e.printStackTrace();
    }

    m_samplerate = 44100;   // !! Fixed now !!

    ml.write("svc_aud: sample rate = " + m_samplerate + " Hz");

    m_hw_size = 3840;//320;//3840;//4096;        If wrong, 3840 works better on Qualcomm at least
    try {
      m_hw_size = 2 * m_channels * Integer.parseInt(hw_size_str);
    } catch (Throwable e) {
      com_uti.loge("Rate Throwable e: " + e);
      e.printStackTrace();
    }
    com_uti.logd("m_hw_size 1: " + m_hw_size);
    if (m_hw_size >= 64 && m_hw_size <= pcm_size_max)
      m_hw_size = m_hw_size;
    else
      m_hw_size = 3840;//320;//3840;//4096;
    com_uti.logd("m_hw_size 2: " + m_hw_size);

    if (com_uti.device == com_uti.DEV_GEN)
      m_hw_size = 16384;
    else if (com_uti.device == com_uti.DEV_SDR)
      m_hw_size = 65536;//16384;//4096;//65536;//5120;//16384;
    else if (com_uti.device == com_uti.DEV_QCV && com_uti.m_manufacturer.equals("SONY"))
      m_hw_size = 5120;//320;
    else if (com_uti.device == com_uti.DEV_QCV)
      m_hw_size = 5120;//3840;//320;//3840;                                    // ?? Actual buffersize = 960 bytes ?
    else if (com_uti.device == com_uti.DEV_ONE)         // ?? HTC J One Try 8160 ?? (25.5 * 320)
      m_hw_size = 320;//3840 / 1;//1920;//960;//2304;//3072;
    else if (com_uti.device == com_uti.DEV_XZ2)
      m_hw_size = 5120;//320
    else if (com_uti.device == com_uti.DEV_LG2) {
      if (com_uti.lg2_stock_get())
        m_hw_size = 16384;//32768;//4096;//1024;
      else
        m_hw_size = 3840;//320;//3840;
    } else if (com_uti.device == com_uti.DEV_GS1)
      m_hw_size = 3520;
    else if (com_uti.device == com_uti.DEV_GS2)
      m_hw_size = 4096;
    else if (com_uti.device == com_uti.DEV_GS3)
      m_hw_size = 4224;
// Sony C6603: 1024

    com_uti.logd("m_hw_size 3: " + m_hw_size);

    m_hw_size = AudioRecord.getMinBufferSize(m_samplerate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
    com_uti.logd("m_hw_size 4: " + m_hw_size);

    if (m_hw_size > pcm_size_max)
      m_hw_size = pcm_size_max;
    if (m_hw_size < pcm_size_min)
      m_hw_size = pcm_size_min;

    ml.write("svc_aud: hw_size = " + m_hw_size + "; samplerate = " + m_samplerate);

    //plg_api_start ();                                                   // Determine and set device specific audio plugin
  }

  // Command handlers:

  public String audio_sessid_get() {                                  // Handle audio session changes
    return m_com_api.audio_sessid;
  }


  // Player and overall audio state control: (public's now via m_com_api)

  public String audio_state_set(String desired_state) {                // Called only by MainService:audio_state_set() & MainService:audio_start()
    com_uti.logd("desired_state: " + desired_state + "  current audio_state: " + m_com_api.audio_state);

    ml.write("svc_aud::audio_state_set(" + desired_state + ")");
    if (desired_state.equals(C.AUDIO_STATE_TOGGLE)) {
      desired_state = m_com_api.audio_state.equals(C.AUDIO_STATE_START) ? C.AUDIO_STATE_PAUSE : C.AUDIO_STATE_START;
    }
    ml.write("svc_aud::audio_state_set(" + desired_state + ") <= after replace toggle");

    switch (desired_state) {
      case C.AUDIO_STATE_START:
        audio_start();
        break;

      case C.AUDIO_STATE_STOP:
        audio_stop();
        break;

      case C.AUDIO_STATE_PAUSE:
        audio_pause();
        break;
    }

    return m_com_api.audio_state;
  }

  private void at_min_size_set() {
    at_min_size = AudioTrack.getMinBufferSize(m_samplerate, chan_out, AudioFormat.ENCODING_PCM_16BIT);
    com_uti.logd("at_min_size 1: " + at_min_size);
    //QCV at 44.1: 22576
    //at_min_size = 32768;//5120 * 16;//65536;//                                        // 80 KBytes about 0.5 seconds of stereo audio data at 44K
    // QCV: 11288, 12288 (12 * 2^10) = 6144 samples = 128 milliseconds, 24576

    at_min_size = 30720;
    com_uti.logd("at_min_size 2: " + at_min_size);
    mRecorder = new RadioRecorder(m_context, m_samplerate, m_channels, m_com_api);
  }

  public RadioRecorder getRecorder() {
    return mRecorder;
  }

  private void audio_start() {
    com_uti.logd("audio_state: " + m_com_api.audio_state + "  m_audiotrack: " + m_audiotrack + "  device: " + com_uti.device);

    if (m_com_api.audio_state.equals(C.AUDIO_STATE_START)) { // If already playing...
      return;
    }

    startHeadsetPluggedListener(); // Register for headset plugged/unplugged events

    requestForFocus(true); // Get audio focus

    pcm_audio_start(); // Start input and output

    m_com_api.audio_state = C.AUDIO_STATE_START;
    if (m_svc_acb != null) {
      m_svc_acb.cb_audio_state(m_com_api.audio_state);
    }
    //audio_sessid_get ();                                                // Update audio_sessid
  }

  // Called externally for user requested pause
  // Called internally for audio_stop and transient focus loss
  private void audio_pause() {

    com_uti.logd("audio_state: " + m_com_api.audio_state);

    if (!m_com_api.audio_state.equals(C.AUDIO_STATE_START) && !m_com_api.audio_state.equalsIgnoreCase("pausing") && !m_com_api.audio_state.equals(C.AUDIO_STATE_STOPPING)) {
      return; // !! What about Starting ??
    }

    pcm_audio_pause(true);

    audio_output_off(); // AFTER

    m_com_api.audio_state = C.AUDIO_STATE_PAUSE;

    if (m_svc_acb != null) {
      m_svc_acb.cb_audio_state(m_com_api.audio_state); // CAN_DUCK
    }
    //audio_sessid_get ();                                                // Update audio_sessid
  }

  // Called externally for tuner stop and service onDestroy
  // Called internally for former GS1 audio_pause and for onError of MediaPlayer       May re-add for digital/analog toggle
  private void audio_stop() {                                           // Stop audio

    com_uti.logd("audio_state: " + m_com_api.audio_state);

    if (!m_com_api.audio_state.equals(C.AUDIO_STATE_START) && !m_com_api.audio_state.equals(C.AUDIO_STATE_PAUSE) && !m_com_api.audio_state.equals(C.AUDIO_STATE_STOPPING)) {
      return; // !! What about Starting ??
    }

    stopHeadsetPluggedListener(); // Unregister for headset plugged/unplugged events

    audio_pause();

    if (m_audiotrack != null) {
      m_audiotrack.release();
    }

    m_audiotrack = null;

    m_com_api.audio_state = C.AUDIO_STATE_STOP;
    if (m_svc_acb != null)
      m_svc_acb.cb_audio_state(m_com_api.audio_state);
    requestForFocus(false);

    if (mHeadsetPlugged) {
      mAudioHelper.makeWiredHeadPhones();
    } else {
      mAudioHelper.makeSpeaker();
    }

    //audio_sessid_get (); // Update audio_sessid
    //stopSelf ();         // service is no longer necessary. Will be started again if needed.
  }

  // Headset listener:

/*  Receiver Lifecycle

A BroadcastReceiver object is only valid for the duration of the call to onReceive(Context, Intent).
Once your code returns from this function, the system considers the object to be finished and no longer active.

This has important repercussions to what you can do in an onReceive(Context, Intent) implementation: anything that requires asynchronous operation is not available,
because you will need to return from the function to handle the asynchronous operation, but at that point the BroadcastReceiver is no longer active and thus
the system is free to kill its process before the asynchronous operation completes.

In particular, you may not show a dialog or bind to a service from within a BroadcastReceiver. For the former, you should instead use the NotificationManager API.



For the latter, you can use Context.startService() to send a command to the service.  */


  // Listen for ACTION_HEADSET_PLUG notifications. (Plugged in/out)

  private void stopHeadsetPluggedListener() {
    com_uti.logd("mHeadsetListener: " + mHeadsetListener);

    if (mHeadsetListener != null) {
      m_context.unregisterReceiver(mHeadsetListener);
      mHeadsetListener = null;
    }
  }

  private void startHeadsetPluggedListener() { // Headset plug events
    IntentFilter iFilter = new IntentFilter();
    iFilter.addAction(Intent.ACTION_HEADSET_PLUG);

    //iFilter.addAction (android.intent.action.MODE_CHANGED);
    //iFilter.addAction ("HDMI_CONNECTED");
    //ACTION_MEDIA_BUTTON
    //iFilter.addAction (android.bluetooth.intent.action.HEADSET_STATE_CHANGED);
    //iFilter.addAction (android.bluetooth.intent.HEADSET_STATE);
    //iFilter.addAction (android.bluetooth.intent.HEADSET_STATE_CHANGED);
    //iFilter.addAction (android.bluetooth.intent.action.MODE_CHANGED);
    //iFilter.addAction ("android.bluetooth.a2dp.action.SINK_STATE_CHANGED");

    iFilter.addCategory(Intent.CATEGORY_DEFAULT);

    Intent intent = m_context.registerReceiver(mHeadsetListener, iFilter);

    com_uti.logd("Headset plugged listener started (onReceive): " + intent);
  }

/* ACTION_HEADSET_PLUG is a sticky broadcast. Call:

    Intent last_broadcast_hdst_plug = registerReceiver (null, new IntentFilter (ACTION_HEADSET_PLUG), null, null);
    
To get the last-broadcast Intent for that action, which will tell you what the headset state is. 

My code now looks as follows:

Intent intent = registerReceiver (myHandler,new IntentFilter(Intent.ACTION_HEADSET_PLUG));
if (intent != null)
  myHandler.onReceive (this, intent);
*/

  private void headsetPluggedHandler(Intent intent) {
    com_uti.logd("headsetPluggedHandler Intent received: " + intent + "  state: " + intent.getIntExtra("state", -555) + "  name: " + intent.getStringExtra("name"));

    //startHeadsetPluggedListener onReceive ACTION_HEADSET_PLUG Intent received: Intent { act=android.intent.action.HEADSET_PLUG flg= 0x40000000 (has extras) }  state: 1  name: h2w
    //startHeadsetPluggedListener onReceive ACTION_HEADSET_PLUG Intent received: Intent { act=android.intent.action.HEADSET_PLUG flg= 0x40000000 (has extras) }  state: 0  name: h2w
    // ?? state: 8 means unplugged ?
    //headsetPluggedHandler Intent received: Intent { act=android.intent.action.HEADSET_PLUG flg=0x40000010 (has extras) }  state: 0  name: Headset

    int state = intent.getIntExtra("state", -555);

    if (state == -555) {
      com_uti.loge("headsetPluggedHandler no state");
    }

    if (state != 0) {
      mHeadsetPlugged = true;
      return;
    }

    mHeadsetPlugged = false;
    if (m_com_api.audio_state.equals(C.AUDIO_STATE_START) && mRecorderSource <= 1 /* !! 8*/ && !com_uti.file_get("/mnt/sdcard/sf/aud_mic"))
      dai_set(true);
  }

  private int pcm_write_start() {
    m_channels = 2;
    if (m_com_api.audio_stereo.equalsIgnoreCase("Mono")) {
      //chan_out = AudioFormat.CHANNEL_OUT_MONO;        // !!!!!!!! Why disabled !!!! ????
      //m_channels = 1;
    }
    at_min_size_set();
    com_uti.logd("samp_rate: " + m_samplerate + "  chan_out: " + chan_out + "  at_min_size: " + at_min_size + "  m_audiotrack: " + m_audiotrack);
    try {
      m_audiotrack = new AudioTrack(audio_stream, m_samplerate, chan_out, AudioFormat.ENCODING_PCM_16BIT, at_min_size, AudioTrack.MODE_STREAM);
      /*if (m_audiotrack == null) {
        return (-1);
      }*/
      m_audiotrack.play(); // java.lang.IllegalStateException: play() called on uninitialized AudioTrack.
    } catch (Throwable e) {
      com_uti.loge("Throwable: " + e);
      e.printStackTrace();
    }

    if (pcm_write_thread_active)
      return -1;

    pcm_write_thread = new Thread(pcm_write_runnable, "pcm_write");
    com_uti.logd("pcm_write_thread: " + pcm_write_thread);
    if (pcm_write_thread == null) {
      com_uti.loge("pcm_write_thread == null");
      return -1;
    }
    pcm_write_thread_active = true;

    try { // !!!! Thread may already be started
      Thread.State st = pcm_write_thread.getState();
      if (st == java.lang.Thread.State.NEW || st == java.lang.Thread.State.TERMINATED) {
        pcm_write_thread.start();
      }
    } catch (Throwable e) {
      com_uti.loge("Throwable: " + e);
      e.printStackTrace();
      return -1;
    }
    return 0;
  }

  private int pcm_write_stop() {
    pcm_write_thread_active = false;
    if (pcm_write_thread != null)
      pcm_write_thread.interrupt();
    return (0);
  }

  // Focus:
  private void requestForFocus(boolean needFocus) {
    com_uti.logd("focus_request: " + needFocus);

    if (needFocus) { // If focus desired...
      m_AM.requestAudioFocus(this, audio_stream, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK); //AudioManager.AUDIOFOCUS_GAIN);
    } else {// If focus return...
      m_AM.abandonAudioFocus(this);
    }
  }

  public void onAudioFocusChange(int focusChange) {
    com_uti.logd("focusChange: " + focusChange + "  audio_state: " + m_com_api.audio_state);
    switch (focusChange) {
      case AudioManager.AUDIOFOCUS_GAIN:                                // Gain
        com_uti.logd("focusChange: GAIN");
        audio_start();                                                 // Start/Restart audio
        break;
      case AudioManager.AUDIOFOCUS_LOSS:                                // Permanent loss
        //com_uti.logd ("focusChange: LOSS");
        audio_stop();                                                  // Stop audio       Loss of/stopping audio could/should stop tuner (& Tuner API ?)
        com_uti.logd("DONE focusChange: LOSS");
        break;
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:                      // Transient loss
        //com_uti.logd ("focusChange: ...LOSS_TRANSIENT");
        audio_pause();                                                 // Pause audio
        com_uti.logd("DONE focusChange: ...LOSS_TRANSIENT");
        break;
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: // Transient loss we can ignore if we want
        //com_uti.logd ("focusChange: LOSS_TRANSIENT_CAN_DUCK");
        audio_pause();                                                 // Pause audio
        com_uti.logd("DONE focusChange: LOSS_TRANSIENT_CAN_DUCK");
        break;
      default:
    }
  }

  // PCM:

  private void pcm_stat_log(String prefix, int increment, int offset, int len, byte[] buf) {
    int max = -32768, min = 32769, avg = 0, max_avg = 0;
    int i = 0;
    for (i = 0; i < len / 2; i += increment) {
      ///*signed*/ short short_sample = *((/*signed*/ short *) & buf [i + 0 + offset]);
      int sample = 0;
      int lo_byte = buf[i * 2 + offset * 2 + 0];
      int hi_byte = buf[i * 2 + offset * 2 + 1];
      sample = hi_byte * 256 + lo_byte;
      if (lo_byte < 0)
        sample += 256;

      if (sample > max)
        max = sample;
      if (sample < min)
        min = sample;
      avg += sample;
    }
    int samples = len / (2 * increment);
    //avg = (max + min) / 2;      // Doesn't work well due to spikes
    avg /= samples;             // This smooths average to about -700 on GS3.
    //if ( (max - avg) > max_avg)   // Avg means we don't have to test min
    max_avg = max - avg;
    com_uti.logd(prefix + "  offset: " + offset + " read_ctr: " + read_ctr + " write_ctr: " + write_ctr + "  len: " + len + "  samples: " + samples + "  max: " + max + "  min: " + min + "  avg: " + avg + "  max_avg: " + max_avg);
  }

  private void pcm_stat_logs(String prefix, int channels, int len, byte[] buf) {         // Length in bytes
    if (channels == 1) {
      pcm_stat_log(prefix, 1, 0, len, buf);                                // Treat as mono
    } else {
      pcm_stat_log(prefix, 2, 0, len, buf);                                // Left is first
      pcm_stat_log(prefix, 2, 1, len, buf);                                // Right is second
    }
  }


  // pcm_read -> pcm_write
  private final Runnable pcm_write_runnable = new Runnable() {
    public void run() {
      com_uti.logd("pcm_write_runnable run()");
      native_priority_set(pcm_priority);
      try {

        while (pcm_write_thread_active) { // While PCM Write Thread should be active...
          write_ctr++; // -1 so first ++ increments to 0 because easier to inc at start; bottom indeterminate with continue()

          int bufs = aud_buf_tail - aud_buf_head;

          if (bufs < 0) {
            bufs += aud_buf_num; // Fix underflow
          }

          if (bufs < min_pcm_write_buffers) { // If minimum number of buffers is not ready... (Currently at least 2)
            try {
              pcm_write_thread_waiting = true;
              Thread.sleep(3);  // Wait ms milliseconds    3 matches Spirit1
              pcm_write_thread_waiting = false;
            } catch (InterruptedException e) {
              pcm_write_thread_waiting = false;
            }

            write_ctr--;
            continue; // Restart loop
          }

          //com_uti.loge ("pcm_write_runnable run() ready to write bufs: " + bufs + "  tail: " + aud_buf_tail + "  head: " + aud_buf_head);

          int len = aud_buf_len[aud_buf_head]; // Length of head buffer in bytes
          byte[] aud_buf = aud_buf_data[aud_buf_head]; // Pointer to head buffer

          long curr_ms_start;
          long curr_ms_time;

          if (mRecorder != null) {
            curr_ms_start = com_uti.tmr_ms_get();
            mRecorder.write(aud_buf, len);
            curr_ms_time = com_uti.tmr_ms_get() - curr_ms_start;
            if (curr_ms_time >= 100) {
              com_uti.loge("run_pcm_write mRecorder.write too long curr_ms_time: " + curr_ms_time + "  len: " + len + "  len_written: unk"  + "  aud_buf: " + aud_buf);
            }
          }

          int len_written = 0;
          int new_len;

          long total_ms_start = com_uti.tmr_ms_get();
          curr_ms_time = -1;
          long total_ms_time = -1;
          len_written = 0;

          // Write head buffer to audiotrack  All parameters in bytes (but could be all in shorts)
          while (pcm_write_thread_active && len_written < len && total_ms_time < 3000) {

            if (total_ms_time >= 0) {
              com_uti.ms_sleep(30);
            }

            if (!pcm_write_thread_active) {
              break;
            }

            curr_ms_start = com_uti.tmr_ms_get();

            new_len = m_audiotrack.write(aud_buf, 0, len);
            if (new_len > 0) {
              len_written += new_len;
            }

            total_ms_time = com_uti.tmr_ms_get() - total_ms_start;
            curr_ms_time = com_uti.tmr_ms_get() - curr_ms_start;

            //com_uti.loge ("pcm_write_runnable run() len_written: " + len_written);

            // Largest value 0xFFFFFFFC = 4294967292 max total file size, so max data size = 4294967256 = 1073741814 samples (0x3FFFFFF6)
            //wav_write_bytes (wav_header, 0x04, 4, audiorecorder_data_size + 36);
            // Chunksize = total filesize - 8 = DataSize + 36

            int stats_frames = (2 * write_stats_seconds * m_samplerate * m_channels) / len;
            if (writes_processed % stats_frames == 0) { // Every stats_seconds
              pcm_stat_logs("Write", m_channels, len, aud_buf);
            }

            writes_processed++; // Update pointers etc
            aud_buf_head++;
            if (aud_buf_head < 0 || aud_buf_head > aud_buf_num - 1) {
              aud_buf_head &= aud_buf_num - 1;
            }
          }

          // Restart loop
        }

        // Here when thread is finished...
        com_uti.logd("pcm_write_runnable run() done writes_processed: " + writes_processed);
      } catch (Throwable e) {
        com_uti.loge("pcm_write_runnable run() throwable: " + e);
        e.printStackTrace();
      } // Fall through to terminate if exception

      if (mRecorder != null) {
        audio_record_state_set(C.RECORD_STATE_STOP);
      }
    }
  };


  // Native API:

  static {
    System.loadLibrary("jut");
  }

  // PCM other:
  private native int native_priority_set(int priority);

  private native int native_prop_get(int prop);


  private android.os.Handler dai_delay_handler = new android.os.Handler();    // Need at init else: java.lang.RuntimeException: Can't create handler inside thread that has not called Looper.prepare()
  private Runnable dai_delay_runnable = null;
  private int dai_delay = 0;

  private String dai_do(final boolean enable) {
    com_uti.logd("enable: " + enable);
    String ret = "";
    if (enable)
      ret = com_uti.s2d_set("radio_dai_state", "start");       // m_plg_api.digital_input_on ();
    else
      ret = com_uti.s2d_set("radio_dai_state", "stop");        // m_plg_api.digital_input_off ();
    return (ret);
  }

  private String dai_set(final boolean enable) {
    String ret = "";
    com_uti.logd("enable: " + enable);

    if (!enable)                                                       // If disable
      dai_delay = 0;                                                    // Do immediately
    else if (com_uti.device == com_uti.DEV_GS2)
      dai_delay = 500;//200;
    else if (com_uti.device == com_uti.DEV_GS3)
      dai_delay = 200;
    else
      dai_delay = 200;//0;//1000;

    dai_delay = 0;  // !! No delay ??

    if (dai_delay <= 0)
      dai_do(enable);
    else if (dai_delay_handler != null) {
      dai_delay_runnable = new Runnable() {
        public void run() {
          dai_do(enable);
        }
      };
      dai_delay_handler.postDelayed(dai_delay_runnable, dai_delay);
    } else {
      com_uti.loge("!!!!!!!!!!!!!!");
      dai_do(enable);
    }

    return (ret);
  }


  private int pcm_read_start() {
    com_uti.logd("pcm_read_start pcm_read_thread_active: " + pcm_read_thread_active);
    if (pcm_read_thread_active)
      return (-1);

    audiorecorder_read_start();

    pcm_read_thread_active = true;
    pcm_read_thread = new Thread(pcm_read_runnable, "pcm_read");
    pcm_read_thread.start();

    return (0);
  }

  private int pcm_read_stop() {
    com_uti.logd("pcm_read_stop pcm_read_thread_active: " + pcm_read_thread_active);
    if (!pcm_read_thread_active)
      return (-1);

    pcm_read_thread_active = false;
    pcm_read_thread.interrupt();

    audiorecorder_read_stop();

    if (mRecorderSource <= 8 && !com_uti.file_get("/mnt/sdcard/sf/aud_mic"))
      dai_set(false);

    return (0);
  }

  private final Runnable pcm_read_runnable = new Runnable() {          // Read/Input thread
    public void run() {
      com_uti.logd("pcm_read_runnable run()");

      native_priority_set(pcm_priority);

      try {
        buf_errs = 0; // Init stats, pointers, etc
        aud_buf_tail = aud_buf_head = 0; // Drop all buffers
        com_uti.logd("pcm_read_runnable run() m_samplerate: " + m_samplerate + "  m_channels: " + m_channels);

        while (pcm_read_thread_active) { // While PCM Read Thread should be active...
          if (aud_buf_read(m_samplerate, m_channels, m_hw_size)) { // Fill a PCM read buffer, If filled...
            int bufs = aud_buf_tail - aud_buf_head;
            //com_uti.loge ("pcm_read_runnable run() bufs: " + bufs + "  tail: " + aud_buf_tail + "  head: " + aud_buf_head);
            if (bufs < 0) {
              bufs += aud_buf_num; // Fix underflow
            }
            if (bufs >= min_pcm_write_buffers) { // If minimum number of buffers is ready... (Currently at least 2)
              if (pcm_write_thread != null) {
                if (pcm_write_thread_waiting) {
                  pcm_write_thread.interrupt(); // Wake up pcm_write_thread sooner than usual
                }
              }
            }
          } else { // Else, if no data could be retrieved...
            com_uti.ms_sleep(50); // Wait 50 milli-seconds for errors to clear
          }
        }

      } catch (Throwable e) {
        com_uti.loge("pcm_read_runnable run() throwable: " + e);
        e.printStackTrace();
      }                                                                 // Fall through to terminate if exception

      com_uti.logd("pcm_read_runnable run() read_ctr: " + read_ctr + "  write_ctr: " + write_ctr + "  buf_errs: " + buf_errs + "  max_bufs: " + max_bufs);
    }
  };


  private boolean audio_blank = false;

  public boolean audio_blank_get() {
    return (audio_blank);
  }

  public boolean audio_blank_set(boolean blank) {
    audio_blank = blank;
    return (audio_blank);
  }

  // All lengths in bytes; converting to shorts created problems.
  private boolean aud_buf_read(int samplerate, int channels, int len_max) { // Fill a PCM read buffer for PCM Read thread
    int bufs = aud_buf_tail - aud_buf_head;

    if (bufs < 0) { // If underflowed...
      bufs += aud_buf_num; // Wrap
    }

    //logd ("bufs: " + bufs + "  tail: " + aud_buf_tail + "  head: " + aud_buf_head);

    if (bufs > max_bufs) { // If new maximum buffers in progress...
      max_bufs = bufs; // Save new max
    }

    if (bufs >= (aud_buf_num * 3) / 4) {
      com_uti.ms_sleep(300);     // 0.1s = 20KBytes @ 48k stereo  (2.5 8k buffers)
    }

    if (bufs >= aud_buf_num - 1) { // If NOT 6 or less buffers in progress, IE if room to write another (max = 7)
      com_uti.loge("Out of aud_buf");
      buf_errs++;
      aud_buf_tail = aud_buf_head = 0; // Drop all buffers
    }

    //int buf_tail = aud_buf_tail;  !!
    int len = -555;

    try {
      if (aud_buf_data[aud_buf_tail] == null) {
        // Allocate memory to pcm_size_max. Could use len_max but that prevents live tuning unless re-allocate while running.
        aud_buf_data[aud_buf_tail] = new byte[pcm_size_max];
      }

      if (m_audiorecorder != null) {
        len = m_audiorecorder.read(aud_buf_data[aud_buf_tail], 0, len_max);
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }

    if (len <= 0) {
      com_uti.loge("get error: " + len + "  tail index: " + aud_buf_tail);
      com_uti.ms_sleep(1000);
      return false;
    }

    if (com_uti.device == com_uti.DEV_QCV) {
      int ctr;
      for (ctr = 0; ctr < len; ctr++) {
        if (aud_buf_data[aud_buf_tail][ctr] != 0) {
          break;
        }
      }
      audio_blank = ctr >= len;
    }

    int stats_frames = (2 * read_stats_seconds * samplerate * channels) / len;
    int reads_processed = read_ctr;
    if (reads_processed % stats_frames == 0) {    // Every stats_seconds
      pcm_stat_logs("Read ", channels, len, aud_buf_data[aud_buf_tail]);
    }

    // Protect from ArrayIndexOutOfBoundsException
    if (aud_buf_tail < 0 || aud_buf_tail > aud_buf_num - 1) {
      aud_buf_tail &= aud_buf_num - 1;
    }

    // On shutdown: java.lang.ArrayIndexOutOfBoundsException: length=32; index=32
    aud_buf_len[aud_buf_tail] = len;

    aud_buf_tail++;

    if (aud_buf_tail < 0 || aud_buf_tail > aud_buf_num - 1) {
      aud_buf_tail &= aud_buf_num - 1;
    }

    read_ctr++;

    //aud_buf_tail = buf_tail;

    return (true);
  }

  public String audio_stereo_set(String new_audio_stereo) {
    //if (m_com_api.audio_stereo.equalsIgnoreCase (new_audio_stereo))   // Done if no change
    //  return (-1);
    m_com_api.audio_stereo = new_audio_stereo;                          // Set new audio output
    com_uti.logd("Set new audio_stereo: " + m_com_api.audio_stereo);
    return (m_com_api.audio_stereo);
  }


  // ? Use stereo_set() for speaker mode ?

  //pcm_read_runnable
//pcm_write_runnable
//min_pcm
  private void pcm_audio_start() { // Start input and output   Called only by audio_output_set() (for restart) or audio_start ()
    pcm_write_start();
    pcm_read_start();
    if (mRecorderSource <= 8 && !com_uti.file_get("/mnt/sdcard/sf/aud_mic"))
      dai_set(true);
  }

  private void pcm_audio_pause(boolean include_read) {
    if (m_audiotrack != null)
      m_audiotrack.pause();
    if (include_read)
      pcm_read_stop();
    pcm_write_stop();
  }

  private void pcm_audio_stop(boolean include_read) {
    pcm_audio_pause(include_read);
    if (m_audiotrack != null)
      m_audiotrack.release();
    m_audiotrack = null;
  }

  // Called only from audio_pause, after pcm read and write stopped
  private void audio_output_off() {
    // If Speaker switch off and headset plugged in...
    if (!m_com_api.audio_output.equals(C.AUDIO_OUTPUT_SPEAKER) || !mHeadsetPlugged) {
      audio_routing_get();
    }
  }

  // CAN_DUCK
  // Called by MainService:onStartCommand() (change from UI/Widget) & MainService:audio_state_set() (at start from prefs)
  public String audio_output_set(String new_audio_output) {
    com_uti.logd("current api audio_state: " + m_com_api.audio_state + "  current api audio_output: " + m_com_api.audio_output + "  new_audio_output: " + new_audio_output);
    if (new_audio_output.equalsIgnoreCase(C.AUDIO_OUTPUT_TOGGLE)) {
      new_audio_output = m_com_api.audio_output.equals(C.AUDIO_OUTPUT_SPEAKER) ? C.AUDIO_OUTPUT_HEADSET : C.AUDIO_OUTPUT_SPEAKER;
    }

    boolean need_restart = false;

    switch (new_audio_output.toLowerCase()) {

      case C.AUDIO_OUTPUT_SPEAKER:
        /*if (need_restart) {
          pcm_audio_stop(true);
        }*/

        mAudioHelper.makeSpeaker();
        break;

      case C.AUDIO_OUTPUT_HEADSET:
        if (mHeadsetPlugged && m_com_api.audio_output.equalsIgnoreCase(C.AUDIO_OUTPUT_SPEAKER)) {
          /*if (need_restart) {
            pcm_audio_stop(true);
          }*/

          mAudioHelper.makeWiredHeadPhones();
        } else {
          need_restart = false;
        }
        break;
    }

    //audio_routing_get();

    com_uti.prefs_set(m_context, C.AUDIO_OUTPUT, new_audio_output);
    m_com_api.audio_output = new_audio_output; // Set new audio output

    if (need_restart) {
      pcm_audio_start(); // If audio started and device needs restart... (GS3 only needs for OmniROM, but make universal)
    } else if (m_com_api.audio_state.equalsIgnoreCase("start") && mRecorderSource <= 8 && !com_uti.file_get("/mnt/sdcard/sf/aud_mic")) {
      //dai_set(true);
    }

    com_uti.logd("Done new audio_output: " + m_com_api.audio_output);
    return m_com_api.audio_output;
  }

  // http://osxr.org/android/source/hardware/libhardware_legacy/include/hardware_legacy/AudioSystemLegacy.h
  //enum audio_devices { // output devices
  private static final int DEVICE_OUT_EARPIECE = 0x1;
  private static final int DEVICE_OUT_SPEAKER = 0x2;
  private static final int DEVICE_OUT_WIRED_HEADSET = 0x4;
  private static final int DEVICE_OUT_WIRED_HEADPHONE = 0x8;

  private static final int DEVICE_STATE_UNAVAILABLE = 0;
  private static final int DEVICE_STATE_AVAILABLE = 1;

  // Called by audio_output_off() & audio_output_set()
  @SuppressLint("PrivateApi")
  private int setDeviceConnectionState(final int device, final int state, final String address) {
    int ret = -3;
    com_uti.logd("device: " + device + "; state: " + state + "; address: '" + address + "'");
    audio_routing_get();
    try {
      Class<?> audioSystem = Class.forName("android.media.AudioSystem"); // Use reflection
      Method setDeviceConnectionState = audioSystem.getMethod("setDeviceConnectionState", int.class, int.class, String.class);
      ret = (Integer) setDeviceConnectionState.invoke(audioSystem, device, state, address);
      com_uti.logd("ret: " + ret);
    } catch (Exception e) {
      com_uti.loge("exception: " + e);
    }
    audio_routing_get();
    return (ret);
  }

  private int audio_routing_get() {
    int ctr = 0, bits = 0, ret = 0, ret_bits = 0;
    StringBuilder bin_out = new StringBuilder();
    for (ctr = 31; ctr >= 0; ctr--) {
      bits = 1 << ctr;
      ret = getDeviceConnectionState(bits, "");
      bin_out.append(ret);
      if (ctr % 4 == 0) { // Every 4 bits...
        bin_out.append(" "); // Add separator space
      }
      if (ret == 1) {
        ret_bits |= bits;
      }
    }
    com_uti.logd("getDeviceConnectionState: " + bin_out + "  ret_bits: " + ret_bits);
    return ret_bits;
  }

  @SuppressLint("PrivateApi")
  private int getDeviceConnectionState(final int device, final String address) {   // Reflector
    int ret = -5;
    try {
      Class<?> audioSystem = Class.forName("android.media.AudioSystem");
      Method getDeviceConnectionState = audioSystem.getMethod("getDeviceConnectionState", int.class, String.class);
      ret = (Integer) getDeviceConnectionState.invoke(audioSystem, device, address);
//      com_uti.logd ("getDeviceConnectionState ret: " + ret); 
    } catch (Throwable e) {
      com_uti.loge("throwable: " + e);
      //e.printStackTrace ();
    }
    return (ret);
  }


  // Start:

/* android.media.MediaRecorder.AudioSource :
                  Value   API Level
DEFAULT             0       1
MIC                 1       1
VOICE_UPLINK        2       4   (Tx)
VOICE_DOWNLINK      3       4   (Rx)
VOICE_CALL          4       4   (uplink + downlink (! if supported !))
CAMCORDER           5       7
VOICE_RECOGNITION   6       7   (Microphone audio source tuned for voice recognition if available, behaves like DEFAULT otherwise. )
VOICE_COMMUNICATION 7       11  (Microphone audio source tuned for voice communications such as VoIP. It will for instance take advantage of echo cancellation or automatic gain control if available. It otherwise behaves like DEFAULT if no voice processing is applied.)
*/


  private boolean audiorecorder_read_stop() {
    m_audiorecord_reading = false;
    if (m_audiorecorder == null)
      return (false);
    m_audiorecorder.stop();
    m_audiorecorder.release();
    m_audiorecorder = null;
    return (true);
  }

  private boolean audiorecorder_read_start() {
    try {
      m_audiorecorder = audio_record_get();

      if (m_audiorecorder != null) {
        //java.lang.IllegalStateException: startRecording() called on an uninitialized AudioRecord.
        m_audiorecorder.startRecording();
        com_uti.logd("getChannelConfiguration: " + m_audiorecorder.getChannelConfiguration() + "   getChannelCount: " + m_audiorecorder.getChannelCount());

        m_audiorecord_reading = true;
      } else {
        com_uti.loge("m_audiorecorder == null !!");
        m_audiorecord_reading = false;
      }
    } catch (Exception e) {//FileNotFoundException e) {
      e.printStackTrace();
      return (false);
    }

    return (true);
  }

  @SuppressWarnings("DanglingJavadoc")
  private AudioRecord audio_record_get() {
    /**
     * DEFAULT              пашет, качественный микрофон, тихий
     * MIC                  пашет, качественный микрофон
     * VOICE_UPLINK         не пашет
     * VOICE_DOWNLINK       не пашет
     * VOICE_CALL           не пашет
     * CAMCORDER            пашет, микрофон говно, но очень чувствительный
     * VOICE_RECOGNITION    пашет, микрофон говно, только левый канал
     * VOICE_COMMUNICATION  пашет, микрофон говно, но очень чувствительный
     * REMOTE_SUBMIX        не пашет
     */
    int src = MediaRecorder.AudioSource.DEFAULT;
    //for (int cnt_src : m_srcs) { // For all sources...
      // Тут был src==11, который проверял файл /mnt/sdcard/sf/rec_src

      short audioFormat = AudioFormat.ENCODING_PCM_16BIT;
      short channelConfig = AudioFormat.CHANNEL_IN_STEREO; // AUDIO_CHANNEL_IN_FRONT_BACK?
      int rate = 44100; // 44kHz

      try {
        int bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);
        com_uti.logd("src: " + src + " rate: " + rate + " audioFormat: " + audioFormat + " channelConfig: " + channelConfig + " bufferSize: " + bufferSize);
        AudioRecord recorder = new AudioRecord(src, rate, channelConfig, audioFormat, m_hw_size);
        if (recorder.getState() == AudioRecord.STATE_INITIALIZED) { // If works, then done
          mRecorderSource = src;
          return recorder;
        }
      } catch (Exception e) {
        com_uti.loge("Exception: " + e);
      }

    //}
    return null;
  }

  public String audio_record_state_set(String newState) {
    if (mRecorder != null) {
      mRecorder.setState(newState);
    }

    return m_com_api.audio_record_state;
  }

}