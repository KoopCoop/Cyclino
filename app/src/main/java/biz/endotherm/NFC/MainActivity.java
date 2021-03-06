package biz.endotherm.NFC;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Calendar;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.widget.Switch;
import android.widget.TabWidget;
import android.widget.TableLayout;
import android.widget.TimePicker;
import android.widget.DatePicker;
import android.app.PendingIntent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import android.nfc.NfcAdapter;
import android.widget.BaseAdapter;
import android.view.ViewGroup;
import android.nfc.Tag;
import android.nfc.tech.NfcV;
import android.os.AsyncTask;
import android.os.Handler;


import android.widget.TabHost;
import android.widget.AdapterView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.w3c.dom.Text;

import java.io.StringBufferInputStream;

public class MainActivity extends AppCompatActivity {

    TabHost tabHost;
    TextView text_view;
    TextView wiederholungen;
    TextView missionStatus;
    TextView startStopText;
    TextView calibrationText;
    TextView cyclinoValueEdit;
    TextView calibrationTempEdit;
    TextView missionStatusText;
    ListView messwerteliste;
    Spinner frequenzSpinner;

    private NfcAdapter nfc;
    private PendingIntent mpendingIntent;
    private final Handler mHandler = new Handler();
    private Runnable mTimer;

    //display variables
    String f_val="00 00 00 00 00 00 00 00", text_val="Place phone on Tag", frequencyFromSpinner="",
            frequencyStringFromMs="0", numberPassesFromEdit="";
    int currentMeasurementNumber = 0;
    boolean missionTimingRight=false;
    Calendar configuredMissionTimestamp;
    long delayActual_ms=0;
    long delayCountdown=0;
    int numberPassesConfigured = 0;
    String gesetztesIntervall = "";
    int cic = 0;
    String[] missionStatus_val = {"","","","",""};
    TempDataAdapter adapter;
    long newCalibrationOffset=0;
    double cyclinoValue = 0;
    double calibrationTemp = 0;
    long nowMillis=0;

    public final static String EXTRA_MESSAGE = "biz.endotherm.NFC.MESSAGE";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TabHost host = (TabHost)findViewById(R.id.tabHost);
        host.setup();

        //Tab 1
        TabHost.TabSpec spec = host.newTabSpec(getString(R.string.mission));
        spec.setContent(R.id.Mission);
        spec.setIndicator(getString(R.string.mission));
        host.addTab(spec);

        //Tab 2
        spec = host.newTabSpec(getString(R.string.config));
        spec.setContent(R.id.Konfig);
        spec.setIndicator(getString(R.string.config));
        host.addTab(spec);

        //Tab 3
        spec = host.newTabSpec(getString(R.string.calibration2));
        spec.setContent(R.id.Kalibration);
        spec.setIndicator(getString(R.string.calibration2));
        host.addTab(spec);

        text_view = (TextView) findViewById(R.id.textView);
        missionStatus = (TextView) findViewById(R.id.MissionStatusText);
        wiederholungen = (TextView) findViewById(R.id.wiederholungen);
        cyclinoValueEdit = (TextView) findViewById(R.id.cyclinoValueEdit);
        calibrationTempEdit = (TextView) findViewById(R.id.calibrationTempEdit);
        startStopText = (TextView) findViewById(R.id.startStop);
        calibrationText= (TextView) findViewById(R.id.calibrationText);
        missionStatusText = (TextView) findViewById(R.id.missionStatus);

        Date now = new Date();
        long millisNow = now.getTime();

        DatePicker datePicker = (DatePicker) findViewById(R.id.datePicker);
        TimePicker timePicker = (TimePicker) findViewById(R.id.startTimePicker);

        timePicker.setIs24HourView(true);
        datePicker.setCalendarViewShown(false);
        timePicker.setCurrentHour(21);
        timePicker.setCurrentMinute(0);


        messwerteliste = (ListView) findViewById(R.id.messwerteList);
        frequenzSpinner = (Spinner) findViewById(R.id.frequenzspinner);

        frequenzSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                frequencyFromSpinner = frequenzSpinner.getSelectedItem().toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });


        adapter = new TempDataAdapter(this);

        nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc == null) {
            text_view.setText(getString(R.string.no_nfc));
            text_val=getString(R.string.no_nfc);}
        if (!nfc.isEnabled()) {
            text_view.setText(getString(R.string.nfc_disabled));
            text_val=getString(R.string.nfc_disabled);
        }
        mpendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

    }

    @Override
    protected void onNewIntent(Intent intent) {
        /**
         * This method gets called, when a new Intent gets associated with the current activity instance.
         * Instead of creating a new activity, onNewIntent will be called. For more information have a look
         * at the documentation.
         *
         * In our case this method gets called, when the user attaches a Tag to the device.
         */
        resolveIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("life cycle", "Called onResume");


        if (nfc != null) {
            //Declare intent filters to handle the intents that you want to intercept.
            IntentFilter tech_intent = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
            IntentFilter[] intentFiltersArray = new IntentFilter[] {tech_intent, };
            //Set up an array of tag technologies that your application wants to handle
            String[][] techListsArray = new String[][] { new String[] { NfcV.class.getName() } };
            //Enable foreground dispatch to stop restart of app on detection
            nfc.enableForegroundDispatch(this, mpendingIntent, intentFiltersArray, techListsArray);
            nowMillis=System.currentTimeMillis();
        }

        final Button ausleseButton = (Button) findViewById(R.id.AusleseButton);
        ausleseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                missionTimingRight = handleTag.get_missionTimingRight();

                if (missionTimingRight) {
                    missionStatus_val = handleTag.get_MissionStatus_val();
                    currentMeasurementNumber = handleTag.get_anzahl();
                    numberPassesConfigured = handleTag.get_numberOfPasses();
                    frequencyStringFromMs = handleTag.get_frequencyStringFromMs();

                    //text_val=handleTag.getText_val();
                    text_val = null;
                    int text_id = handleTag.getText_id();
                    if (text_id != 0) {
                        text_val = getString(text_id);
                    }
                    delayActual_ms = handleTag.get_actualDelay_ms();
                    delayCountdown = handleTag.get_delayCountdown();
                    configuredMissionTimestamp = handleTag.get_configuredMissionTimestamp();

                    Switch round = (Switch) findViewById(R.id.roundingSwitch);
                    boolean switchStatus = round.isChecked();
                    if (missionTimingRight) {
                        handleTag.readTagData(currentTag, switchStatus);
                    }

                    text_view.setText(text_val);
                    missionStatus.setText(missionStatus_val[0] + missionStatus_val[1] + missionStatus_val[2] + missionStatus_val[3] + missionStatus_val[4]);


                    ListView listView = (ListView) findViewById(R.id.messwerteList);
                    listView.setAdapter(adapter);
                    // prevent listview from scrolling
                    if (adapter.getCount() > 0) {
                        View item = adapter.getView(0, null, listView);
                        item.measure(0, 0);
                        ViewGroup.LayoutParams lp = listView.getLayoutParams();
                        lp.height = (item.getMeasuredHeight() + listView.getDividerHeight())
                                * adapter.getCount();
                        listView.setLayoutParams(lp);
                    }
                    adapter.setData(handleTag.GetData());


                }
            }
        });


        final Button clipboardButton = (Button) findViewById(R.id.ClipboardButton);
        clipboardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                missionTimingRight = handleTag.get_missionTimingRight();
                String textToCopy = "";

                if (missionTimingRight) {
                    for (int i = 0; i < currentMeasurementNumber; i++) {
                        textToCopy = textToCopy + adapter.data[i].date.toString() + " " + String.format("%.5f", adapter.data[i].temp) +
                                System.getProperty("line.separator");
                    }
                }
                if (textToCopy.length() != 0) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) // check SDK version
                    {
                        android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setText(textToCopy);
                        Toast.makeText(getApplicationContext(), getString(R.string.data_copied), Toast.LENGTH_SHORT).show();
                    } else {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clipData = android.content.ClipData.newPlainText("Clip", textToCopy);
                        Toast.makeText(getApplicationContext(), getString(R.string.data_copied), Toast.LENGTH_SHORT).show();
                        clipboard.setPrimaryClip(clipData);
                    }
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.no_data_selected), Toast.LENGTH_SHORT).show();
                }
            }
        });

        final Button startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                numberPassesFromEdit = wiederholungen.getText().toString();
                missionStatus_val=handleTag.get_MissionStatus_val();
                if (numberPassesFromEdit.equals("0") || numberPassesFromEdit.equals("") || Integer.parseInt(numberPassesFromEdit) > 908) {
                    if(!missionStatus_val[4].equals("BatError/BatOFF ")) {
                        handleTag.stopDevice(currentTag, cic);//Batterie wird ausgeschaltet
                        startStopText.setText(getString(R.string.invalid_measurement_number));
                        handleTag.readTagData(currentTag,true);
                        missionStatus_val=handleTag.get_MissionStatus_val();
                    } else{
                        startStopText.setText(getString(R.string.invalid_measurement_number));
                    }
                }
                else{

                    Date now = new Date();
                    long millisNow = now.getTime();

                    DatePicker datePicker = (DatePicker) findViewById(R.id.datePicker);
                    TimePicker timePicker = (TimePicker) findViewById(R.id.startTimePicker);

                    int startYear = datePicker.getYear() - 1900;
                    int startMonth = datePicker.getMonth();
                    int startDay = datePicker.getDayOfMonth();
                    int startHour = timePicker.getCurrentHour();
                    int startMinute = timePicker.getCurrentMinute();

                    Date missionStart = new Date(startYear, startMonth, startDay, startHour, startMinute);
                    long millisStart = missionStart.getTime();
                    long delay_min = (millisStart - millisNow) / 60000;
                    if (delay_min == 0) {
                        delay_min = 1;
                    }
                    if (delay_min > 0) {
                        handleTag.startDevice(currentTag, numberPassesFromEdit, frequencyFromSpinner, delay_min, cic);
                        startStopText.setText(getString(R.string.start_mission_text));
                    } else {
                        startStopText.setText(getString(R.string.mission_in_past) + " " + getString(R.string.start_mission_text));
                    }
                }
            }
        });

        final Button stopButton = (Button) findViewById(R.id.stopButton);
        if(!frequencyStringFromMs.equals("0")) {stopButton.setVisibility(View.VISIBLE);}

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentTag == null) {
                    startStopText.setText(getString(R.string.tag_not_connected));
                } else {
                    if(numberPassesConfigured!=0 && !missionStatus_val[0].equals("Mission fertig ")) {
                        handleTag.stopDevice(currentTag, cic);
                        if (handleTag.get_numberOfPasses() == 0) {
                            startStopText.setText(getString(R.string.mission_stopped));
                        } else {
                            startStopText.setText(getString(R.string.mission_stop_failed) + " (" + getString(handleTag.getText_id()) + "). " +  getString(R.string.try_again));
                        }

                        ausleseButton.callOnClick();
                    }else{
                        startStopText.setText(getString(R.string.mission_already_stopped));
                    }
                }
            }
        });


        final Button calibrationButton = (Button) findViewById(R.id.calibrationButton);
        calibrationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calibrationTemp = Double.parseDouble(calibrationTempEdit.getText().toString());
                cyclinoValue = Double.parseDouble(cyclinoValueEdit.getText().toString());
                newCalibrationOffset=handleTag.GetNewCalibrationOffset(calibrationTemp,cyclinoValue);

                if(newCalibrationOffset > 0 & newCalibrationOffset < 32767 /*11 bit reserved*/ & !(calibrationTempEdit.getText().toString()).equals("") & !(cyclinoValueEdit.getText().toString()).equals("")) {
                    handleTag.setCalibrationOffset(currentTag, newCalibrationOffset);
                    ausleseButton.callOnClick();
                    if (handleTag.GetSetCalibrationOffset() == newCalibrationOffset) {
                        calibrationText.setText(getString(R.string.calibration_success)+newCalibrationOffset);
                    } else {
                        calibrationText.setText(getString(R.string.calibration_error)+handleTag.GetSetCalibrationOffset());
                    }
                }else{
                    calibrationText.setText(getString(R.string.calibration_invalid));
                }
            }
        });


        mTimer = new Runnable() {
            @Override
            public void run() {

                missionStatus_val = handleTag.get_MissionStatus_val();

                currentMeasurementNumber = handleTag.get_anzahl();
                numberPassesConfigured = handleTag.get_numberOfPasses();
                frequencyStringFromMs = handleTag.get_frequencyStringFromMs();
                missionTimingRight = handleTag.get_missionTimingRight();
                //text_val=handleTag.getText_val();
                //text_val=getString(handleTag.getText_id());
                text_val = null;
                int text_id = handleTag.getText_id();
                if(text_id != 0){
                    text_val = getString(text_id);
                }
                delayActual_ms=handleTag.get_actualDelay_ms();
                delayCountdown=handleTag.get_delayCountdown();
                configuredMissionTimestamp=handleTag.get_configuredMissionTimestamp();

                String startTimeConfigured=getStartTimeString(configuredMissionTimestamp.getTimeInMillis(),delayActual_ms);//add conversion time (Table 4 Firmware User Guide)
                if(!handleTag.get_frequencyStringFromMs().equals("0")){
                    if (startStopText.getText().equals(getString(R.string.scan_again))){ // | startStopText.getText().equals(getString(R.string.start_mission_text))) {
                        startStopText.setText(getString(R.string.start_mission_text));
                    }
                    else if(missionTimingRight & currentMeasurementNumber!=0 & numberPassesConfigured!=0 & !missionStatus_val[4].equals("BatError/BatOFF ")) {
                        missionStatusText.setText(getString(R.string.mission_status) + " " + currentMeasurementNumber + " " + getString(R.string.of) + " " +
                                numberPassesConfigured + " " + getString(R.string.values) + " " + frequencyStringFromMs + " " + getString(R.string.interval));
                    }
                    /*else if (!missionTimingRight & !missionStatus_val[4].equals("BatError/BatOFF ")){                            missionStatusText.setText(getString(R.string.mission_status) + " " + getString(R.string.first_val) + " "
                            + startTimeConfigured + getString(R.string.deviating_val) + " (" + frequencyStringFromMs + ").");
                    }*/
                    else if (missionTimingRight & missionStatus_val[4].equals("BatError/BatOFF ") & currentMeasurementNumber!=0){
                        missionStatusText.setText(getString(R.string.mission_status) + " " +  getString(R.string.no_new_mission) + " " +  getString(R.string.last_mission_had)
                                + " " + currentMeasurementNumber + " " + getString(R.string.see_previous_data));
                        //missionStatusText.setText("Missionsstatus: Keine neue Mission. Letzte Mission hatte " +currentMeasurementNumber+
                        // " Messwert(e). Siehe Daten unten (nach Auslesen)");
                    }
                    else if (!missionTimingRight){
                        missionStatusText.setText(getString(R.string.mission_status) + " " +  getString(R.string.no_new_mission) + " " +  getString(R.string.last_mission_had) + " "
                                + getString(R.string.suspicious_values2));
                        //missionStatusText.setText("Missionsstatus: Keine neue Mission. Letzte Mission hatte suspekte Sensorwerte "
                    }
                    else if (numberPassesConfigured==0){
                        missionStatusText.setText(getString(R.string.mission_status) + " " +  getString(R.string.no_new_mission));
                    }
                    else {

                        missionStatusText.setText(getString(R.string.mission_status) + " " + getString(R.string.interval) + " " + frequencyStringFromMs + ", "
                                +currentMeasurementNumber + " " + getString(R.string.values_of) + " "  + numberPassesConfigured + getString(R.string.first_val_expected) + " "
                                + startTimeConfigured + " (" + getString(R.string.still) + " "  + delayCountdown + " " + getString(R.string.minutes));
                    }
                }
                text_view.setText(text_val);
                if(missionStatus_val[0].equals("Mission fertig ")||(numberPassesConfigured==0 && !missionStatus_val[4].equals(""))) {
                    missionStatus.setText(missionStatus_val[3] + missionStatus_val[1] + missionStatus_val[2] + getString(R.string.battery_off) + "\n"+missionStatus_val[0] /*missionStatus_val[4]*/);
                }else if(!missionStatus_val[4].equals("")){
                    missionStatus.setText(missionStatus_val[3] + missionStatus_val[1] + missionStatus_val[2] + getString(R.string.battery_error) + "\n"+missionStatus_val[0]/*missionStatus_val[4]*/);
                } else{
                    missionStatus.setText(missionStatus_val[0] + missionStatus_val[1] + missionStatus_val[2] + missionStatus_val[3] + missionStatus_val[4]);
                }

                mHandler.postDelayed(this, 50);
            }
        };
        mHandler.postDelayed(mTimer, 100);

    }

    private class TempDataAdapter extends BaseAdapter {
        HandleTag.DataPoint[] data;
        Context context;

        TempDataAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getCount() {
            return data != null ? data.length : 0;
        }

        @Override
        public Object getItem(int position) {
            return data[position];
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater)
                        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(android.R.layout.simple_list_item_2, parent, false);
            }

            TextView text1 = (TextView) convertView.findViewById(android.R.id.text1);
            TextView text2 = (TextView) convertView.findViewById(android.R.id.text2);
            HandleTag.DataPoint item = (HandleTag.DataPoint) getItem(position);

            text1.setText(String.format("%.4f ", item.temp));
            text2.setText(DateFormat.getDateTimeInstance().format(item.date));
            return convertView;
        }

        public void setData(HandleTag.DataPoint[] data) {
            this.data = data;
        }
    }
    //communication and tag methods
    private Tag currentTag;
    private HandleTag handleTag=new HandleTag();

    private void resolveIntent(Intent intent) {
        String action = intent.getAction();
        //check if the tag is ISO15693 and display message
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

            Log.i("life cycle", "NfcAdapter.ACTION_TECH_DISCOVERED");
            currentTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            new NfcVReaderTask().execute(currentTag);// read ADC data in background
        }
    }
    /**
     *
     * Background task for reading the data. Do not block the UI thread while reading.
     *
     */
    private class NfcVReaderTask extends AsyncTask<Tag, Void, String> {
        @Override
        protected void onPostExecute(String result) {
            Log.i("Life cycle", "NFC thread start");
        }
        @Override
        protected String doInBackground(Tag... params) {

            Tag tag = params[0];

            Switch round = (Switch) findViewById(R.id.roundingSwitch);
            boolean switchStatus = round.isChecked();

            handleTag.readTagData(tag, switchStatus);
            adapter.setData(handleTag.GetData());
            missionStatus_val = handleTag.get_MissionStatus_val();
            currentMeasurementNumber = handleTag.get_anzahl();
            numberPassesConfigured = handleTag.get_numberOfPasses();
            gesetztesIntervall = handleTag.get_frequencyStringFromMs();
            configuredMissionTimestamp=handleTag.get_configuredMissionTimestamp();
            delayActual_ms=handleTag.get_actualDelay_ms();
            //text_val = handleTag.getText_val();
            text_val = null;
            int text_id = handleTag.getText_id();
            if(text_id != 0){
                text_val = getString(text_id);
            }
            if(text_id==R.string.suspicious_values){
                handleTag.setMissionTimingRight(currentTag, false);
                Log.v("", "bllaaaa");
            }
            missionTimingRight = handleTag.get_missionTimingRight();
            Log.v("life cycle", "missiontimingneu "+missionTimingRight);


            return null;
        }

    }
    private String getStartTimeString(long unixTime_ms, long delay) {
        String startTimeSeconds;
        String startTimeMinute;
        String startTimeHour;
        Calendar startTime = Calendar.getInstance();
        startTime.setTimeInMillis(unixTime_ms + delay);
        int startTimeMonth = startTime.get(Calendar.MONTH) + 1;
        if(startTime.get(Calendar.MINUTE)<10){
            startTimeMinute="0"+startTime.get(Calendar.MINUTE);
        }
        else {
            startTimeMinute=""+startTime.get(Calendar.MINUTE);
        }
        if(startTime.get(Calendar.HOUR_OF_DAY)<10){
            startTimeHour="0"+startTime.get(Calendar.HOUR_OF_DAY);
        }
        else {
            startTimeHour=""+startTime.get(Calendar.HOUR_OF_DAY);
        }
        if(startTime.get(Calendar.SECOND)<10){
            startTimeSeconds="0"+startTime.get(Calendar.SECOND);
        }
        else {
            startTimeSeconds=""+startTime.get(Calendar.SECOND);
        }

        String startTimeString = startTime.get(Calendar.DAY_OF_MONTH) + "." + startTimeMonth + "." + startTime.get(Calendar.YEAR) + " " + startTimeHour + ":" + startTimeMinute+ ":" + startTimeSeconds;
        return startTimeString;
    }

}