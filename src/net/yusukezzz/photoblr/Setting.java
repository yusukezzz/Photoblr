package net.yusukezzz.photoblr;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class Setting extends PreferenceActivity {
    private static final String SETTING_ID   = "id";
    private static final String SETTING_PASS = "password";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        addPreferencesFromResource(R.xml.setting);
    }

    public static String getId(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(SETTING_ID, "");
    }

    public static String getPass(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(SETTING_PASS, "");
    }
}
