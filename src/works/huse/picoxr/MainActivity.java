package works.huse.picoxr;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String TAG = "PicoXRLauncher";
    private static final String BROWSER_PKG = "com.pico.browser.overseas";

    private static long lastLaunchAt = 0L;
    private static final long DEDUP_MS = 4000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunchAt < DEDUP_MS) {
            Log.i(TAG, "skip: launched " + (now - lastLaunchAt) + "ms ago");
            finish();
            return;
        }
        lastLaunchAt = now;

        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            am.killBackgroundProcesses(BROWSER_PKG);
            Log.i(TAG, "killed background processes for " + BROWSER_PKG);
            SystemClock.sleep(500);
        } catch (Exception e) {
            Log.w(TAG, "killBackgroundProcesses failed: " + e.getMessage());
        }

        Intent inbound = getIntent();
        Uri url = inbound.getData();
        if (url == null) {
            String s = inbound.getStringExtra("url");
            url = Uri.parse(s != null ? s : getString(R.string.target_url));
        }

        boolean noAutoXR = inbound.getBooleanExtra("no_auto_xr", false);
        if (!noAutoXR && url.getQueryParameter("xr") == null
                      && url.getQueryParameter("enterxr") == null) {
            url = url.buildUpon().appendQueryParameter("xr", "1").build();
        }

        Intent i = new Intent(Intent.ACTION_VIEW, url);
        i.setPackage(BROWSER_PKG);
        i.addCategory(Intent.CATEGORY_DEFAULT);

        i.putExtra("android.support.customtabs.extra.LAUNCH_AS_TRUSTED_WEB_ACTIVITY", true);
        i.putExtra("android.support.customtabs.extra.SESSION", (Bundle) null);

        Bundle sessionBinderBundle = new Bundle();
        sessionBinderBundle.putBinder("android.support.customtabs.extra.SESSION_BINDER", new Binder());
        i.putExtras(sessionBinderBundle);

        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Log.i(TAG, "launching " + url);
        try {
            startActivity(i);
        } catch (Exception e) {
            Log.e(TAG, "Launch failed", e);
            Toast.makeText(this, "Launch failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
