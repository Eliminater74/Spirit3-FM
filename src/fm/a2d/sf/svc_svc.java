package fm.a2d.sf;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import java.util.Timer;
import java.util.TimerTask;

// Service class implements Tuner API callbacks & Service Audio API callback
public class svc_svc extends Service implements svc_tcb, svc_acb {

  // Also see AndroidManifest.xml.

  // Action: Commands sent to this service
  //"fm.a2d.sf.action.set"

  // Result: Broadcast multiple status/state data items to all registered listeners; apps, widgets, etc.
  //"fm.a2d.sf.result.get"

  // No constructor

  // Static data:
  private static int            stat_creates= 1;

  // Instance data:
  private Context               m_context   = this;
  private com_api               m_com_api   = null;
  private svc_tap               m_svc_tap   = null;
  private svc_aap               m_svc_aap   = null;

  private Notification          mynot       = null;                         // Create a new Notification object
  private static final int      S2_NOTIF_ID = 2112;                         // No relevance to S2_NOTIF_ID except to uniquely identify notification !! Spirit1 uses same !!
  private NotificationManager   m_NM        = null;
  private boolean               need_startfg= true;

  //private int                 start_type  = START_NOT_STICKY; // Don't restart if killed; not important enough to restart, which may crash again. ?? But still restarts ??
  private int                   start_type  = START_STICKY; // !!!! See if Sticky reduces audio dropouts

  private String []             plst_freq   = new String[com_api.PRESET_COUNT];
  private String []             plst_name   = new String[com_api.PRESET_COUNT];
  private int                   preset_curr = 0;
  private int                   preset_num  = 0;

  private Timer                 tuner_state_start_tmr = null;

  private BluetoothAdapter      m_bt_adapter    = null;

  private AudioManager          m_AM = null;

  @Override
  public void onCreate () {                                             // When service newly created...
    com_uti.logd ("stat_creates: " + stat_creates++);

    try {
      //com_uti.strict_mode_set (true);                                 // Enable strict mode; disabled for now
      com_uti.strict_mode_set (false);                                  // !!!! Disable strict mode so we can send network packets from Java

      m_AM = (AudioManager) m_context.getSystemService (Context.AUDIO_SERVICE);
      m_NM = (NotificationManager) m_context.getSystemService(Context.NOTIFICATION_SERVICE);

      notif_state_set (true);

      if (m_com_api == null) {
        m_com_api = new com_api (this);                                 // Instantiate Common API   class
        com_uti.logd ("m_com_api: " + m_com_api);
      }

      m_svc_aap = new svc_aud (this, this, m_com_api);                  // Instantiate audio        class

      m_svc_tap = new svc_tnr (this, this, m_com_api);                  // Instantiate tuner        class
    }
    catch (Throwable e) {
      e.printStackTrace ();
    }
  }

  @Override
  public void onDestroy() {
    notif_state_set(false);
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null; // Binding not allowed ; no direct call API, must use Intents
  }

  // Handle command intents sent via startService()
  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    com_uti.logd ("intent: " + intent + "  flags: " + flags + "  startId: " + startId);

    try {
      if (intent == null) {
        com_uti.loge("intent == null");
        return start_type;
      }

      String action = intent.getAction();
      if (action == null) {
        com_uti.loge("action == null");
        return start_type;
      }

      if (!action.equalsIgnoreCase("fm.a2d.sf.action.set")) {
        com_uti.loge("action: " + action);
        return start_type;
      }

      Bundle extras = intent.getExtras();
      com_uti.logd("extras: " + extras);
      if (extras == null) {
        return start_type;
      }

      String val;

      for (int i = 0; i < com_api.PRESET_COUNT; i++) { // Get Presets
        val = extras.getString("radio_name_prst_" + i, "");

        if (!val.isEmpty()) {
          String freq = extras.getString("radio_freq_prst_" + i, "0");
          int ifreq = com_uti.tnru_freq_fix(25 + com_uti.tnru_khz_get(freq));
          com_uti.logd("Set preset val: " + val + "  freq: " + freq);
          if (ifreq >= 50000 && ifreq <= 499999) {
            com_uti.prefs_set(m_context, "radio_freq_prst_" + i, "" + freq);
            com_uti.prefs_set(m_context, "radio_name_prst_" + i, val);
          }
          presets_init(); // Load presets
        }
      }

// radio_freq : preset or seek
      val = extras.getString("radio_freq", "");
      if (val.equalsIgnoreCase("down")) {
        if (preset_num <= 1)
          m_svc_tap.tuner_set("tuner_scan_state", val);
        else
          preset_change(0);
      }
      if (val.equalsIgnoreCase("up")) {
        if (preset_num <= 1)
          m_svc_tap.tuner_set("tuner_scan_state", val);
        else
          preset_change(1);
      }
      if (val.equalsIgnoreCase("scan"))
        m_svc_tap.tuner_set("tuner_scan_state", val);

      // Tuner:
      val = extras.getString("tuner_state", "");
      if (!val.equals(""))
        tuner_state_set(val);

// tuner_scan_state
      val = extras.getString("tuner_scan_state", "");
      if (!val.equals(""))
        m_svc_tap.tuner_set("tuner_scan_state", val);

      val = extras.getString("tuner_freq", "");
      if (!val.equals(""))
        tuner_freq_set(val);

      val = extras.getString("tuner_band", "");
      if (!val.equals("")) {
        m_svc_tap.tuner_set("tuner_band", val);
        com_uti.prefs_set(m_context, "tuner_band", val);
        m_com_api.tuner_band = val;
        com_uti.tnru_band_set(m_com_api.tuner_band);
      }

      val = extras.getString("tuner_stereo", "");
      if (!val.equals("")) {
        m_svc_tap.tuner_set("tuner_stereo", val);
        com_uti.prefs_set(m_context, "tuner_stereo", val);
      }

      val = extras.getString("tuner_extra_cmd", "");
      if (!val.equals("")) {
        m_svc_tap.tuner_set("tuner_extra_cmd", val);
      }

      val = extras.getString("tuner_rds_af_state", "");
      if (!val.equals("")) {
        tuner_rds_af_state_set(val);
      }


      // Audio:
      val = extras.getString("audio_state", "");
      if (!val.equals(""))
        audio_state_set(val);

      val = extras.getString("audio_output", "");
      if (!val.equals("")) {
        m_svc_aap.audio_output_set(val);
        com_uti.prefs_set(m_context, "audio_output", val);
      }

      val = extras.getString("audio_record_state", "");
      if (!val.isEmpty()) {
        m_svc_aap.audio_record_state_set(val);
      }

      val = extras.getString("audio_stereo", "");
      if (!val.equals("")) {
        m_svc_aap.audio_stereo_set(val);
        com_uti.prefs_set(m_context, "audio_stereo", val);
      }

      radio_status_send(); // Return results
    } catch (Throwable e) {
      e.printStackTrace();
    }

    return start_type;
  }

  // For state and status updates:
  private void displays_update (String caller) { // Update all "displays"
    // Update widgets, apps, etc. and get resulting Intent
    m_com_api.radio_update(radio_status_send()); // Get current data in Radio API using Intent (To update all dynamic/external data)
    notif_radio_update(); // Update notification shade
  }


  private void tuner_extras_put (Intent send_intent) {

    send_intent.putExtra ("tuner_state",        m_com_api.tuner_state);//m_svc_tap.tuner_get ("tuner_state"));
    send_intent.putExtra ("tuner_band",         m_com_api.tuner_band);//m_svc_tap.tuner_get ("tuner_band"));
    String freq_khz = m_svc_tap.tuner_get ("tuner_freq");
    int ifreq = com_uti.int_get (freq_khz);
//!! ifreq = com_uti.tnru_freq_fix (ifreq + 25);
    if (ifreq >= 50000 && ifreq < 500000) {
      m_com_api.tuner_freq = ("" + (double) ifreq / 1000);
      m_com_api.int_tuner_freq = ifreq;
    }
    com_uti.logx ("m_com_api.tuner_freq: " + m_com_api.tuner_freq + "  m_com_api.int_tuner_freq: " + m_com_api.int_tuner_freq);
    send_intent.putExtra ("tuner_freq",         m_com_api.tuner_freq);

    //send_intent.putExtra ("tuner_stereo",       m_svc_tap.tuner_get ("tuner_stereo"));
    //send_intent.putExtra ("tuner_thresh",       m_svc_tap.tuner_get ("tuner_thresh"));
    //send_intent.putExtra ("tuner_scan_state",   m_svc_tap.tuner_get ("tuner_scan_state"));

    //send_intent.putExtra ("tuner_rds_state",    m_svc_tap.tuner_get ("tuner_rds_state"));
    //send_intent.putExtra ("tuner_rds_af_state", m_svc_tap.tuner_get ("tuner_rds_af_state"));
    //send_intent.putExtra ("tuner_rds_ta_state", m_svc_tap.tuner_get ("tuner_rds_ta_state"));

    //send_intent.putExtra ("tuner_extra_cmd",    m_svc_tap.tuner_get ("tuner_extra_cmd"));
    //send_intent.putExtra ("tuner_extra_resp",   m_svc_tap.tuner_get ("tuner_extra_resp"));

    send_intent.putExtra ("tuner_rssi",         m_com_api.tuner_rssi);      //m_svc_tap.tuner_get ("tuner_rssi"));
    //send_intent.putExtra ("tuner_qual",         m_svc_tap.tuner_get ("tuner_qual"));
    send_intent.putExtra ("tuner_most",         m_com_api.tuner_most);      //m_svc_tap.tuner_get ("tuner_most"));

    send_intent.putExtra ("tuner_rds_pi",       m_com_api.tuner_rds_pi);    //m_svc_tap.tuner_get ("tuner_rds_pi"));
    send_intent.putExtra ("tuner_rds_picl",     m_com_api.tuner_rds_picl);  //m_svc_tap.tuner_get ("tuner_rds_picl"));
    //send_intent.putExtra ("tuner_rds_pt",       m_com_api.tuner_rds_pt);  //m_svc_tap.tuner_get ("tuner_rds_pt"));
    send_intent.putExtra ("tuner_rds_ptyn",     m_com_api.tuner_rds_ptyn);  //m_svc_tap.tuner_get ("tuner_rds_ptyn"));
    send_intent.putExtra ("tuner_rds_ps",       m_com_api.tuner_rds_ps);    //m_svc_tap.tuner_get ("tuner_rds_ps"));
    send_intent.putExtra ("tuner_rds_rt",       m_com_api.tuner_rds_rt);    //m_svc_tap.tuner_get ("tuner_rds_rt"));

    //send_intent.putExtra ("tuner_rds_af",       m_svc_tap.tuner_get ("tuner_rds_af"));
    //send_intent.putExtra ("tuner_rds_ms",       m_svc_tap.tuner_get ("tuner_rds_ms"));
    //send_intent.putExtra ("tuner_rds_ct",       m_svc_tap.tuner_get ("tuner_rds_ct"));
    //send_intent.putExtra ("tuner_rds_tmc",      m_svc_tap.tuner_get ("tuner_rds_tmc"));
    //send_intent.putExtra ("tuner_rds_tp",       m_svc_tap.tuner_get ("tuner_rds_tp"));
    //send_intent.putExtra ("tuner_rds_ta",       m_svc_tap.tuner_get ("tuner_rds_ta"));
    //send_intent.putExtra ("tuner_rds_taf",      m_svc_tap.tuner_get ("tuner_rds_taf"));
  }

  private Intent radio_status_send () {                                 // Send all radio state & status info

    m_svc_aap.audio_sessid_get (); // Better to update here ?

    com_uti.logx ("audio_state: " + m_com_api.audio_state + "  audio_output: " + m_com_api.audio_output +
                "  audio_stereo: " + m_com_api.audio_stereo + "  audio_record_state: " + m_com_api.audio_record_state);

    Intent send_intent = new Intent ("fm.a2d.sf.result.get");

    send_intent.putExtra ("radio_phase",        m_com_api.radio_phase);
    send_intent.putExtra ("radio_cdown",        m_com_api.radio_cdown);
    send_intent.putExtra ("radio_error",        m_com_api.radio_error);
    for (int ctr = 0; ctr < preset_num; ctr ++) { // Send preset list
      send_intent.putExtra ("radio_freq_prst_" + ctr, plst_freq [ctr]);
      send_intent.putExtra ("radio_name_prst_" + ctr, plst_name [ctr]);
    }

    send_intent.putExtra ("audio_state",        m_com_api.audio_state);
    send_intent.putExtra ("audio_output",       m_com_api.audio_output);
    send_intent.putExtra ("audio_stereo",       m_com_api.audio_stereo);

    send_intent.putExtra ("audio_record_state", m_com_api.audio_record_state);
    send_intent.putExtra ("audio_sessid",       m_com_api.audio_sessid);

    if (m_svc_tap == null) {
      send_intent.putExtra ("tuner_state",      "stop");
    }
    else {
      tuner_extras_put (send_intent);
    }
    try {
      m_context.sendStickyBroadcast (send_intent);                      // Send Sticky Broadcast w/ all info
    }
    catch (Throwable e) {
      e.printStackTrace ();
    }
    return send_intent;
  }


    // Presets:

  private int station_index_get (String freq) {//int freq) {
    preset_curr_fix ();
    if (preset_num > 0) {
      if (freq.equals (plst_freq [preset_curr])) {
        return (preset_curr);
      }
      for (int ctr = 0; ctr < preset_num; ctr ++) {
        if (freq.equals (plst_freq [preset_curr])) {
          return (ctr);
        }
      }
    }
    return (-1);
  }
/*  private static String station_name_get (int freq) {
    int idx = station_index_get (freq);
    if (idx >= 0)
      return (plst_name [idx]);
    String def_name = "" + ((double) freq / 1000);
    return (def_name);
  }*/

  private int preset_curr_fix () {
    if (preset_num < 0)
      preset_num = 0;
    if (preset_num >= com_api.PRESET_COUNT)
      preset_num = com_api.PRESET_COUNT;

    if (preset_curr < 0)
      preset_curr = preset_num - 1;
    if (preset_curr < 0)
      preset_curr = 0;

    if (preset_curr >= preset_num)
      preset_curr = 0;
    return (preset_curr);
  }

  private void preset_next (boolean up) {
    if (preset_num <= 0)
      return;
    preset_curr = station_index_get (m_com_api.tuner_freq);
    preset_curr_fix ();
    if (up)
      preset_curr ++;
    else
      preset_curr --;
    preset_curr_fix ();
    String freq = plst_freq [preset_curr];
    tuner_freq_set (freq);
  }

  private void preset_change (int up) {
    if (up != 0)
      preset_next (true);
    else
      preset_next (false);
  }


    // Phase/Countdown/Error stuff:

  private void phase_cdown_set (String phase, int cdown) {
    com_uti.logd ("phase: " + phase + "  cdown: " + cdown);
    m_com_api.radio_phase = phase;
    m_com_api.radio_cdown = "" + cdown;
    //radio_status_send ();                                            // Update widgets, apps, etc. (displays_update too intense ?)
  }

  private void phase_error_set (String phase, String error) {
    com_uti.loge ("phase: " + phase + "  error: " + error);
    m_com_api.radio_phase = phase;
    m_com_api.radio_error = error;
    m_com_api.radio_cdown = "0";
    //radio_status_send ();                                            // Update widgets, apps, etc. (displays_update too intense ?)
  }


    // AUDIO:

  private String audio_state_set (String state) {                       // Called only by onStartCommand()
    com_uti.logd ("state: " + state);
    if (state.equalsIgnoreCase ("toggle")) {                            // TOGGLE:
      if (m_com_api.audio_state.equalsIgnoreCase ("start"))
        state = "pause";
      else
        state = "start";
    }
    if (state.equalsIgnoreCase ("start")) {                             // If Audio Start...
      if (! m_com_api.audio_state.equalsIgnoreCase ("start")) {         // If audio not started (Could be stopped or paused)
        String stereo = com_uti.prefs_get (m_context, "audio_stereo", "Stereo");
        m_svc_aap.audio_stereo_set (stereo);                            // Set audio stereo from prefs, before audio is started

        if (m_com_api.tuner_state.equalsIgnoreCase ("start")) {         // If tuner started
          m_svc_aap.audio_state_set ("Start");                          // Set Audio State synchronously
        }
        else {                                                          // Else if tuner not started
          m_com_api.audio_state = "Starting";                           // Signal tuner state callback that audio needs to be started
          tuner_state_set ("Start");                                    // Start tuner first, audio will start later via callback
        }
      }
    }
    else                                                                // Else for Audio Stop or Pause...
      m_svc_aap.audio_state_set (state);                                // Set Audio State synchronously

    return (m_com_api.audio_state);                                     // Return current audio state
  }


    // Callback called by svc_aud: audio_start(), audio_stop(), audio_pause()
  public void cb_audio_state (String audio_state) {                     // Audio state changed callback from svc_aud
    com_uti.logd ("audio_state: " + audio_state);

    if (audio_state.equalsIgnoreCase ("start")) {                       // If audio state = Start...

      String audio_output = com_uti.prefs_get (m_context, "audio_output", "headset");
      m_svc_aap.audio_output_set (audio_output);                      // Set Audio Output from prefs

      remote_state_set (true);                                          // Remote State = Playing
    }
    else if (audio_state.equalsIgnoreCase ("stop")) {                   // If audio state = Stop...
      remote_state_set (false);                                         // Remote State = Stopped = Media buttons not needed ?
    }
    else if (audio_state.equalsIgnoreCase ("pause")) {                  // If audio state = Pause...
      // Remote State = Still Playing
    }
    displays_update ("cb_audio_state");                                 // Update all displays/data sinks
  }


    // TUNER:

  private String tuner_state_set (String state) {                       // Called only by onStartCommand(), (maybe onDestroy in future)
    com_uti.logd ("state: " + state);
    if (state.equalsIgnoreCase ("toggle")) {                            // If Toggle...
      if (m_com_api.tuner_state.equalsIgnoreCase ("start"))
        state = "stop";
      else
        state = "start";
    }
    if (state.equalsIgnoreCase ("start")) {                             // If Start...
      tuner_state_start_tmr = new Timer ("t_api start", true);          // One shot Poll timer for file creates, SU commands, Bluedroid Init, then start tuner
      if (tuner_state_start_tmr != null) {
        tuner_state_start_tmr.schedule (new tuner_state_start_tmr_hndlr (), 10);    // Once after 0.01 seconds.
        return (m_com_api.tuner_state);
      }
    }
    else if (state.equalsIgnoreCase ("stop")) {                         // If Stop...
      m_svc_aap.audio_state_set ("Stop");                               // Set Audio State  synchronously to Stop
      m_svc_tap.tuner_set ("tuner_state", "stop");                      // Set Tuner State asynchronously to Stop
      return (m_com_api.tuner_state);                                   // Return new tuner state
    }
                                                                        // Else if not stop...
    return (state);
  }


    // Callback: tuner_state_chngd & tuner_state_set in svc_tap/tnr_afm calls cb_tuner_state indirectly w/ Stop, Starting, Pause
  private void cb_tuner_state (String tuner_state) {
    com_uti.logd ("tuner_state: " + tuner_state);
    if (tuner_state.equalsIgnoreCase ("starting") || tuner_state.equalsIgnoreCase ("start")) {  // If tuner = Start or Starting...
      m_com_api.tuner_state = "start";                                  // Tuner State = Start
      tuner_prefs_init ();                                              // Load tuner prefs
      if (m_com_api.audio_state.equalsIgnoreCase ("starting")) {        // If Audio starting...
        m_svc_aap.audio_state_set ("Start");                            // Set Audio State synchronously
      }
      return;
    }
    if (tuner_state.equalsIgnoreCase ("stopping") || tuner_state.equalsIgnoreCase ("stop")) {   // If tuner = Stop or Stopping...
      m_com_api.tuner_state = "stop";                                   // Tuner State = Stop
      //m_svc_tap = null;
      com_uti.logd ("Before stopSelf()");
      stopSelf ();                                                      // Stop this service entirely
      com_uti.logd ("After  stopSelf()");
    }
  }

  private class tuner_state_start_tmr_hndlr extends TimerTask {

    public void run () {
      int ret;

      phase_cdown_set("Files Init", 5);
      ret = files_init(); // /data/data/fm.a2d.sf/files/: busybox, ssd, s.wav, b1.bin, b2.bin
      if (ret != 0) {
        com_uti.loge ("files_init IGNORE Errors: " + ret);
        //phase_error_set ("Files Init", "File Errors: " + ret);
        //return;
      }
      if (com_uti.device == com_uti.DEV_ONE || com_uti.device == com_uti.DEV_LG2 || com_uti.device == com_uti.DEV_XZ2) {
        phase_cdown_set ("BCom Init", 20);
        ret = bcom_init ();
        if (ret != 0) {
          phase_error_set ("BCom Init", "BCom Error: " + ret);
          return;
        }
      }
      com_uti.logd ("Starting Tuner...");
      phase_cdown_set ("Tuner Start", 20);

      m_svc_tap.tuner_set ("radio_nop",   "Start");                     // 1st packet always fails, so this is a NOP

      m_svc_tap.tuner_set ("tuner_state", "Start");                     // This starts the daemon

      if (tuner_state_start_tmr != null)
        tuner_state_start_tmr.cancel ();                                // Stop one shot poll timer

      com_uti.logd ("done");
    }
  }


    // Other non state machine tuner stuff:

  private void tuner_rds_af_state_set (String val) {
    m_svc_tap.tuner_set ("tuner_rds_af_state", val);
    com_uti.prefs_set (m_context, "tuner_rds_af_state", val);
  }

  private void presets_init() { // Load presets
    preset_num = 0;
    for (int i = 0; i < com_api.PRESET_COUNT; i++) {      // ?? Should use com_api copy !!
      plst_freq[i] = com_uti.prefs_get (m_context, "radio_freq_prst_" + i,  "");
      plst_name[i] = com_uti.prefs_get (m_context, "radio_name_prst_" + i,  "");
      if (!plst_freq [i].isEmpty()) {
        preset_num = i + 1;
      }
    }
  }

  private void tuner_prefs_init () { // Load tuner prefs
    String band = com_uti.prefs_get (m_context, "tuner_band", "EU");
    m_svc_tap.tuner_set ("tuner_band", band);
    m_com_api.tuner_band = band;
    com_uti.tnru_band_set (band);

    String stereo = com_uti.prefs_get (m_context, "tuner_stereo", "Stereo");
    m_svc_tap.tuner_set ("tuner_stereo", stereo);

    tuner_rds_af_state_set (com_uti.prefs_get (m_context, "tuner_rds_af_state", "stop")); // !! Always rewrites pref

    presets_init(); // Load presets

    int freq = com_uti.prefs_get(m_context, "tuner_freq", 87500);
    tuner_freq_set (String.valueOf(freq)); // Set initial frequency
  }


  private void tuner_freq_set (String freq) { // To fix float problems w/ 106.1 becoming 106099
    com_uti.logd ("freq: " + freq);
    int ifreq = com_uti.tnru_band_new_freq_get(freq, m_com_api.int_tuner_freq); // Deal with up, down, etc.
    //int ifreq = com_uti.tnru_khz_get (freq);
    ifreq += 25;        // Round up...
    ifreq = ifreq / 50; // Nearest 50 KHz
    ifreq *= 50;        // Back to KHz scale
    com_uti.logd ("ifreq: " + ifreq);

    m_svc_tap.tuner_set ("tuner_freq", "" + ifreq); // Set frequency
  }



    // Tuner API callbacks:


    // Single Tuner Sub-Service callback expands to other functions:

  public void cb_tuner_key (String key, String val) {
    com_uti.logx ("key: " + key + "  val: " + val);
///*
    if (com_uti.device == com_uti.DEV_QCV && m_svc_aap.audio_blank_get ()) {   // If we need to kickstart audio...
      com_uti.loge ("!!!!!!!!!!!!!!!!!!!!!!!!! Kickstarting stalled audio !!!!!!!!!!!!!!!!!!!!!!!!!!");
      //m_svc_tap.tuner_set ("tuner_stereo", m_com_api.tuner_stereo);     // Set Stereo (Frequency also works, and others ?)
      m_svc_tap.tuner_set ("tuner_freq", m_com_api.tuner_freq);     // Set Frequency
      m_svc_aap.audio_blank_set (false);
    }
//*/
    if (key != null) {
      if (key.equalsIgnoreCase ("tuner_state"))
        cb_tuner_state (val);
      else if (key.equalsIgnoreCase ("tuner_freq"))
        cb_tuner_freq (val);
      else if (key.equalsIgnoreCase ("tuner_rssi"))
        cb_tuner_rssi (val);
      else if (key.equalsIgnoreCase ("tuner_qual"))
        cb_tuner_qual (val);
      else if (key.equalsIgnoreCase ("tuner_rds_pi"))
        cb_tuner_rds_pi (val);
      else if (key.equalsIgnoreCase ("tuner_rds_pt"))
        cb_tuner_rds_pt (val);
      else if (key.equalsIgnoreCase ("tuner_rds_ps"))
        cb_tuner_rds_ps (val);
      else if (key.equalsIgnoreCase ("tuner_rds_rt"))
        cb_tuner_rds_rt (val);
    }

    //else if (key.equalsIgnoreCase ("tuner_rds_picl"))                           // PI callback is good
    //  cb_tuner_rds_picl (val);
    //else if (key.equalsIgnoreCase ("tuner_rds_ptyn"))                           // PT callback is good
    //  cb_tuner_rds_ptyn (val);
  }

// Freq:
  private void cb_tuner_freq (String freq) {

    com_uti.logd ("freq: " + freq);

    m_com_api.tuner_stereo = "";

    m_com_api.tuner_rssi         = "";//999";                            // ro ... ... Values:   RSSI: 0 - 1000
    m_com_api.tuner_qual         = "";//SN 99";                          // ro ... ... Values:   SN 99, SN 30
    //m_com_api.tuner_most         = "";//Mono";                           // ro ... ... Values:   mono, stereo, 1, 2, blend, ... ?      1.5 ?

    m_com_api.tuner_rds_pi       = "";//-1";                             // ro ... ... Values:   0 - 65535
    m_com_api.tuner_rds_picl     = "";//WKBW";                           // ro ... ... Values:   North American Call Letters or Hex PI for tuner_rds_pi
    m_com_api.tuner_rds_pt       = "";//-1";                             // ro ... ... Values:   0 - 31
    m_com_api.tuner_rds_ptyn     = "";//";                               // ro ... ... Values:   Describes tuner_rds_pt (English !)
    m_com_api.tuner_rds_ps       = "";//Spirit2 Free";                        // ro ... ... Values:   RBDS 8 char info or RDS Station
    m_com_api.tuner_rds_rt       = "";//Thanks for Your Support... :)";  // ro ... ... Values:   64 char

    m_com_api.tuner_rds_af       = "";//";                               // ro ... ... Values:   Space separated array of AF frequencies
    m_com_api.tuner_rds_ms       = "";//";                               // ro ... ... Values:   0 - 65535   M/S Music/Speech switch code
    m_com_api.tuner_rds_ct       = "";//";                               // ro ... ... Values:   14 char CT Clock Time & Date

    m_com_api.tuner_rds_tmc      = "";//";                               // ro ... ... Values:   Space separated array of shorts
    m_com_api.tuner_rds_tp       = "";//";                               // ro ... ... Values:   0 - 65535   TP Traffic Program Identification code
    m_com_api.tuner_rds_ta       = "";//";                               // ro ... ... Values:   0 - 65535   TA Traffic Announcement code
    m_com_api.tuner_rds_taf      = "";//";                               // ro ... ... Values:   0 - 2^32-1  TAF TA Frequency

    int new_freq = com_uti.tnru_freq_fix (25 + com_uti.tnru_khz_get (freq));
    m_com_api.tuner_freq = com_uti.tnru_mhz_get (new_freq);
    m_com_api.int_tuner_freq = new_freq;

    displays_update ("cb_tuner_freq");

    com_uti.prefs_set (m_context, "tuner_freq", new_freq);
  }
  private void cb_tuner_rssi (String rssi) {
    displays_update ("cb_tuner_rssi");
  }
// Qual:
  private void cb_tuner_qual (String qual) {
  }
// RDS:
  private void cb_tuner_rds_pi (String pi) {
    displays_update ("cb_tuner_rds_pi");
  }
  private void cb_tuner_rds_pt (String pt) {
    displays_update ("cb_tuner_rds_pt");
  }
  private void cb_tuner_rds_ps (String ps) {
    displays_update ("cb_tuner_rds_ps");
  }
  private void cb_tuner_rds_rt (String rt) {
    displays_update ("cb_tuner_rds_rt");
  }


  private String lib_name_get () {
    switch (com_uti.device) {
      case com_uti.DEV_UNK: return ("libs2t_gen.so");
      case com_uti.DEV_GEN: return ("libs2t_gen.so");
      case com_uti.DEV_GS1: return ("libs2t_ssl.so");
      case com_uti.DEV_GS2: return ("libs2t_ssl.so");
      case com_uti.DEV_GS3: return ("libs2t_ssl.so");
      case com_uti.DEV_QCV: return ("libs2t_qcv.so");
      case com_uti.DEV_ONE: return ("libs2t_bch.so");
      case com_uti.DEV_LG2: return ("libs2t_bch.so");
      case com_uti.DEV_XZ2: return ("libs2t_bch.so");
      case com_uti.DEV_SDR: return ("libs2t_sdr.so");
      //default:      return ("libs2t_gen.so");
    }
    return ("libs2t_gen.so");
  }

  // Hardware / API dependent part of svc_svc:
  private int files_init() {
    com_uti.logd ("starting...");

    if (com_uti.file_get ("/mnt/sdcard/sf/sys_bin")) {
      String lib_name = lib_name_get ();  //"libs2t_ssl.so";
      String cmd = "";
      cmd += ("mount -o remount,rw /system 1>/dev/null 2>/dev/null; ");
      cmd += ("cp /data/data/fm.a2d.sf/lib/libssd.so /system/bin/ssd 1>/dev/null 2>/dev/null; ");
      cmd += ("chmod 755 /system/bin/ssd 1>/dev/null 2>/dev/null; ");
      cmd += ("cp /data/data/fm.a2d.sf/lib/libs2d.so /system/bin/s2d 1>/dev/null 2>/dev/null; ");
      cmd += ("chmod 755 /system/bin/s2d 1>/dev/null 2>/dev/null; ");
      cmd += ("cp /data/data/fm.a2d.sf/lib/" + lib_name + " /system/lib/libs2t.so 1>/dev/null 2>/dev/null; ");
      cmd += ("chmod 644 /system/lib/libs2t.so 1>/dev/null 2>/dev/null; ");
      cmd += ("mount -o remount,ro /system 1>/dev/null 2>/dev/null; ");
      cmd += ("echo -n 1>/dev/null 2>/dev/null");
      com_uti.sys_run (cmd, true);
      com_uti.logd ("Done installing system binaries to /system/bin/");
    }    


    //String bsb_full_filename = com_uti.file_create (m_context, R.raw.busybox,  "busybox",       true);
    //    String ssd_full_filename = "";
    //if (com_uti.ssd_via_sys_run)
    //      ssd_full_filename = com_uti.file_create (m_context, R.raw.ssd,           "ssd",           true);
    //String wav_full_filename = com_uti.file_create (m_context, R.raw.s_wav,    "s.wav",         false);             // Not executable


    String add_full_filename = com_uti.file_create (m_context, R.raw.spirit_sh,  "99-spirit.sh");
//    String bb1_full_filename = com_uti.file_create (m_context, R.raw.b1_bin,     "b1.bin",        false);             // Not executable
//    String bb2_full_filename = com_uti.file_create (m_context, R.raw.b2_bin,     "b2.bin",        false);             // Not executable

        // Check:
    int ret = 0;
        //if (! com_uti.access_get (bsb_full_filename, false, false, true)) { // rwX
        //  com_uti.loge ("error unexecutable busybox utility");
        //  ret ++;
        //}
        //      if (/*com_uti.ssd_via_sys_run &&*/ ! com_uti.access_get (ssd_full_filename, false, false, true)) { // rwX
        //        com_uti.loge ("error unexecutable ssd utility");                  // !!!! Get this sometimes, so must ignore errors.
        //        ret ++;
        //      }

      if (! com_uti.access_get (add_full_filename, false, false, true)) { // rwX
        com_uti.loge ("error unexecutable addon.d script 99-spirit.sh");
        ret ++;
      }
/*
      if (! com_uti.access_get (bb1_full_filename, true, false, false)) { // Rwx
        com_uti.loge ("error inaccessible bb1 file");
        ret ++;
      }
      if (! com_uti.access_get (bb2_full_filename, true, false, false)) { // Rwx
        com_uti.loge ("error inaccessible bb2 file");
        ret ++;
      }
*/
    com_uti.logd ("done ret: " + ret);
    return (ret);
  }





/* OLD: 4 states:
    BT Off                                                            Use UART  (or BT on and install/use shim if possible)
    BT On & (Shim Not Installed or Shim Old)    Install Shim, BT Off, Use UART      First run before reboot or first boot after ROM update with no addon.d fix
    BT On &  Shim     Installed & NOT Active                  BT Off, Use UART      Need reboot & BT to be active
    BT On &  Shim     Installed &     Active                          Use SHIM
*/

  // Install shim if needed (May change BT state & warm restart). Determine UART or SHIM Mode & create/delete "use_shim" flag file for s2d. Turn BT off if needed.
  private int bcom_init () {
    com_uti.logd ("start");

    String short_filename = "use_shim";
    String full_filename = m_context.getFilesDir () + "/" + short_filename;

    if (com_uti.file_get (full_filename)) {                               // If use_shim flag is set...
      com_uti.logd ("Removing file: " + full_filename);
      com_uti.sys_run ("rm " + full_filename, true);                      // Remove file/flag
    }

      if (bt_get ()) {
        com_uti.logd ("UART mode needed but BT is on; turn BT Off");
        bt_set (false, true);                                           // Bluetooth off, and wait for off
        com_uti.logd ("Start 4 second delay after BT Off");
        com_uti.ms_sleep (4000);                                        // Extra 4 second delay to ensure BT is off !!
        com_uti.logd ("End 4 second delay after BT Off");
      }

// BT is? KitKat:   service call bluetooth_manager 4                                                TRANSACTION_isEnabled
// BT On  KitKat:   service call bluetooth_manager 6        Older:  service call bluetooth 3
// BT On- KitKat:   service call bluetooth_manager 7                                                TRANSACTION_enableNoAutoConnect
// BT Off KitKat:   service call bluetooth_manager 8        Older:  service call bluetooth 4


    return 0;
  }

  private void bt_wait (boolean wait_on) {
    int ctr = 0;
    boolean done = false;

    while (! done && ctr ++ < 90 ) {                                    // While not done and 9 seconds has not elapsed...
      com_uti.ms_sleep (100);                                             // Wait 0.1 second
      if (wait_on)                                                      // If waiting for BT on...
        done = bt_get ();                                               // Done if BT on
      else                                                              // Else if waiting for BT off...
        done = ! bt_get ();                                             // Done if BT off
    }
    return;
  }


  private boolean bta_get () {
    m_bt_adapter = BluetoothAdapter.getDefaultAdapter ();                // Just do this once, shouldn't change
    if (m_bt_adapter == null) {
      com_uti.loge ("BluetoothAdapter.getDefaultAdapter () returned null");
      return (false);
    }
    return (true);
  }

  private boolean bt_get () {                            // !! Should check with pid_get ("bluetoothd"), "brcm_patchram_plus", "btld", "hciattach" etc. for consistency w/ fm_hrdw
    boolean ret = false;                                                //      BUT: if isEnabled () doesn't work, m_bt_adapter.enable () and m_bt_adapter.disable () may not work either.
    if (m_bt_adapter == null)
      if (! bta_get ())
        return (false);
    ret = m_bt_adapter.isEnabled ();
    com_uti.logd ("bt_get isEnabled (): " + ret);
    return (ret);
  }

// bttest is_enabled
// bttest enable
// bttest disable
// Alternate/libbluedroid uses rfkill and ctl.stop/ctl.start bluetoothd

  private int bt_set ( boolean bt_pwr, boolean wait ) {
    if (m_bt_adapter == null)
      if (! bta_get ())
        return (-1);

    boolean bt = bt_get ();
    if (bt_pwr && bt) {
      com_uti.logd ("bt_set BT already on");
      return (0);
    }
    if (! bt_pwr && ! bt) {
      com_uti.logd ("bt_set BT already off");
      return (0);
    }
    if (bt_pwr) {                                                       // If request for BT on
      com_uti.logd ("bt_set BT turning on");

      try {
        m_bt_adapter.enable ();                                       // Start enable BT
      }
      catch (Throwable e) {
        com_uti.loge ("bt_set m_bt_adapter.disable () Exception");
      }

      if (! wait)                                                       // If no wait
        return (0);                                                     // Done w/ no error

      bt_wait (true);                                                   // Wait until BT is on or times out
      bt = bt_get ();
      if (bt) {
        com_uti.logd ("bt_set BT on success");
        return (0);
      }
      com_uti.loge ("bt_set BT on error");
      return (-1);
    }
    else {
      com_uti.logd ("bt_set BT turning off");
      try {
        m_bt_adapter.disable ();                                        // Start disable BT
      }
      catch (Throwable e) {
        com_uti.loge ("bt_set m_bt_adapter.disable () Exception");
      }

      if (! wait)                                                       // If no wait
        return (0);                                                     // Done w/ no error

      bt_wait (false);                                                  // Wait until BT is off or times out
      bt = bt_get ();
      if (! bt) {
        com_uti.logd ("bt_set BT off success");
        return (0);
      }
      com_uti.loge ("bt_set BT off error");
      return (-1);
    }
  }


    // Remote control client, for lockscreen and BT AVRCP:

  private void remote_state_set (boolean state) {                       // Called only by cb_audio_state w/ state=true for start, false for stop
  }

  // Notification shade
  private void notif_radio_update () { // Called only by displays_update() which is called only by cb_* callbacks
    if (m_com_api.audio_state.equalsIgnoreCase("start")) { // If audio started...
      if (need_startfg) {
        need_startfg = false;
        com_uti.logd ("startForeground");
        showNotificationAndStartForeground(true);
      } else {
        showNotificationAndStartForeground(false);
      }
    } else {
      need_startfg = true;
      //notif_state_set (false);
      //stopForeground (true);                                          // !! Need FG for lockscreen play !! So can't unset foreground when paused,,,   Only in onDestroy
//stopForeground (false);   // !!!! ???? Can stopForeground but keep active notification ??
    }
  }
 
   // Start/stop service = foreground, FM Notification in status bar, Expanded message in "Notifications" window.

  private void notif_state_set (boolean state) {                        // Called only by onCreate w/ state = true and onDestroy w/ state = false
    com_uti.logd ("state: " + state);
    if (!state) { // Notifications off, go to idle non-foreground state
      com_uti.logd ("false");
      stopForeground (true); // Service not in foreground state and remove notification (true)
      return;
    }

    ///*    !!!! Need this or bad audio !!!!
    showNotificationAndStartForeground(true);
  }

  private void showNotificationAndStartForeground(boolean needStartForeground) {
    Notification.Builder notify = new Notification.Builder(this)
            .setContentTitle("Spirit2")
            .setContentText("Frequency")
            .setContentInfo("Unknown")
            .setSmallIcon(R.drawable.ic_radio);

/*    if (m_com_api.tuner_rssi != null && m_com_api.tuner_freq != null) {
      notify.setContentInfo(m_com_api.tuner_rssi)
            .setContentText(String.format("%sMhz", m_com_api.tuner_freq));
    }*/

    mynot = notify.build();

    if (needStartForeground) {
      startForeground(S2_NOTIF_ID, mynot); // Now in audio foreground
    }

    m_NM.notify(S2_NOTIF_ID, mynot);
  }
}