package biz.endotherm.NFC;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class SettingsActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        Preference startTimePref = findPreference(getString(R.string.preference_start_time_key));
        startTimePref.setOnPreferenceChangeListener(this); // set OnPreferenceChange listener which calls method onPreferenceChange

        Preference numberPref = findPreference(getString(R.string.preference_number_key));
        numberPref.setOnPreferenceChangeListener(this);

        Preference intervalPref = findPreference(getString(R.string.preference_interval_key));
        intervalPref.setOnPreferenceChangeListener(this);

        // call onPreferenceChange immediately
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String startTime = sharedPrefs.getString(startTimePref.getKey(), "");
        onPreferenceChange(startTimePref, startTime);
        String numberOfMeasurements = sharedPrefs.getString(numberPref.getKey(), "");
        onPreferenceChange(numberPref, numberOfMeasurements);
        String interval = sharedPrefs.getString(intervalPref.getKey(), "");
        onPreferenceChange(intervalPref, interval);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        preference.setSummary(value.toString()); //show summary of newly set preferences
        return true;
    }
}