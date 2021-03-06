package fm.a2d.sf;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import fm.a2d.sf.helper.L;

// GUI Activity:

public class MainActivity extends Activity {

  public static com_api m_com_api = null;
  private static BroadcastReceiver mBroadcastListener = null;

  private gui_gui m_gui = null;
  private Context mContext = null;

  private L ml;

  // Lifecycle:

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ml = L.getInstance();

    ml.write("Activity::onCreate");

    mContext = this;

    // Must be done from an Activity
    setVolumeControlStream(AudioManager.STREAM_MUSIC);

    if (m_com_api == null) {
      m_com_api = new com_api(mContext);
    }

    gui_start();
    initBroadcastListener();
  }

  // Create        Start,Resume       Pause,Resume        Pause,Stop,Restart       Start,Resume

  // Launch:   Create      Start       Resume
  // Home:                                         Pause       Stop
  // Return:   Restart     Start       Resume
  // Back:                                         Pause       Stop        Destroy


  // !! Resume can happen with the FM power off, so try not to do things needing power on (this was about onResume)
  // Restart comes between Stop and Start or when returning to the app (this was about onRestart)

  @Override
  public void onDestroy() {
    // One of these caused crashes:
    stopBroadcastListener();
    gui_stop();
    ml.write("Activity::onDestroy");

    // super.onDestroy dismisses any dialogs or cursors the activity was managing. If the logic in onDestroy has something to do with these things, then order may matter.
    super.onDestroy();
  }

  private void gui_start() {
    try {
      ml.write("gui_start in");
      m_gui = new gui_gui(mContext, m_com_api); // Instantiate UI
      if (!m_gui.setState("start")) { // Start UI. If error...
        com_uti.loge("gui_start error");
        m_gui = null;
      } else {
        com_uti.logd("gui_start OK");
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  private void gui_stop() {
    try {
      ml.write("gui_stop in");
      if (m_gui == null)
        com_uti.loge("already stopped");
      else if (!m_gui.setState("stop"))                          // Stop UI. If error...
        com_uti.loge("gui_stop error");
      else
        com_uti.logd("gui_stop OK");
      m_gui = null;
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }


  private void initBroadcastListener() {
    if (mBroadcastListener == null) {
      ml.write("Initializing broadcast listener...");
      mBroadcastListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();

          if (action == null || !action.equalsIgnoreCase("fm.a2d.sf.result.get")) {
            return;
          }

          if (m_com_api != null && m_gui != null) {
            m_com_api.radio_update(intent);
            m_gui.onReceivedUpdates(intent);
          }
        }
      };

      IntentFilter intFilter = new IntentFilter();
      intFilter.addAction("fm.a2d.sf.result.get"); // Can add more actions if needed
      intFilter.addCategory(Intent.CATEGORY_DEFAULT);

      Intent lastStateIntent = null;
      if (mContext != null) {
        // No permission, no handler scheduler thread.
        lastStateIntent = mContext.registerReceiver(mBroadcastListener, intFilter, null, null);
        ml.write("Broadcast registered");
      }

      if (lastStateIntent != null) {
        ml.write("Last broadcast: " + lastStateIntent);
      }
    }
  }

  private void stopBroadcastListener() {
    ml.write("Broadcast listener unregister");
    if (mBroadcastListener != null) { // Remove the State listener
      if (mContext != null) {
        mContext.unregisterReceiver(mBroadcastListener);
      }
      mBroadcastListener = null;
      ml.write("Broadcast listener unregistered successfully");
    }
  }

  public void gap_gui_clicked(View v) {
    m_gui.onClickView(v);
  }

}