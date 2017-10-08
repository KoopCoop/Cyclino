package biz.endotherm.NFC;

import java.text.DateFormat;

import android.content.Intent;
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

import android.widget.AdapterView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.w3c.dom.Text;

import java.io.StringBufferInputStream;

public class MainActivity extends AppCompatActivity {

    TextView text_view;
    TextView anzahl;
    TextView wiederholungen;
    TextView missionStatus;
    TextView istIntervall;
    TextView istWiederholungen;
    TextView startStopText;
    ListView messwerteliste;
    Spinner frequenzSpinner;

    private NfcAdapter nfc;
    private PendingIntent mpendingIntent;
    private final Handler mHandler = new Handler();
    private Runnable mTimer;

    //display variables
    String f_val="00 00 00 00 00 00 00 00", text_val="Place phone on Tag", frequencyFromSpinner="", frequencyStringFromMs="0", numberPasses="";
    int anzahlMesswerte = 0;
    int gesetzteWiederholungen = 0;
    String gesetztesIntervall = "";
    int cic = 0;
    String[] missionStatus_val = {"","","","",""};
    TempDataAdapter adapter;


    public final static String EXTRA_MESSAGE = "biz.endotherm.NFC.MESSAGE";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        text_view = (TextView) findViewById(R.id.textView);
        missionStatus = (TextView) findViewById(R.id.MissionStatusText);
        anzahl = (TextView) findViewById(R.id.anzahl);
        wiederholungen = (TextView) findViewById(R.id.wiederholungen);
        istIntervall = (TextView) findViewById(R.id.istIntervall);
        istWiederholungen = (TextView) findViewById(R.id.istWiederholungen);
        startStopText = (TextView) findViewById(R.id.startStop);

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
            text_view.setText("No NFC!!");
            text_val="No NFC!!";}
        if (!nfc.isEnabled()) {
            text_view.setText("NFC is disabled.");
            text_val="NFC is disabled.";
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
        }

        mTimer = new Runnable() {
            @Override
            public void run() {
                missionStatus_val = handleTag.get_MissionStatus_val();
                anzahlMesswerte = handleTag.get_anzahl();
                gesetzteWiederholungen = handleTag.get_numberOfPasses();
                frequencyStringFromMs = handleTag.get_frequencyStringFromMs();
                text_val=handleTag.getText_val();

                text_view.setText(text_val);
                anzahl.setText(String.valueOf(anzahlMesswerte));
                istWiederholungen.setText(String.valueOf(gesetzteWiederholungen));
                istIntervall.setText(frequencyStringFromMs);
                missionStatus.setText(missionStatus_val[0]+missionStatus_val[1]+missionStatus_val[2]+missionStatus_val[3]+missionStatus_val[4]);


                ListView listView = (ListView) findViewById(R.id.messwerteList);
                listView.setAdapter(adapter);
                // prevent listview from scrolling
                if (adapter.getCount()>0) {
                    View item = adapter.getView(0, null, listView);
                    item.measure(0, 0);
                    ViewGroup.LayoutParams lp = listView.getLayoutParams();
                    lp.height = (item.getMeasuredHeight() + listView.getDividerHeight())
                            * adapter.getCount();
                    listView.setLayoutParams(lp);
                }

                adapter.setData(handleTag.getData());
                mHandler.postDelayed(this, 200);
            }
        };
        mHandler.postDelayed(mTimer, 300);

        Button startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                numberPasses = wiederholungen.getText().toString();
                handleTag.startDevice(currentTag, numberPasses, frequencyFromSpinner, cic);

                if(missionStatus_val[0]=="Sampling in Progress " /*&& handleTag.getText_val() !="Tag connection lost"*/) {
                    startStopText.setText("Mission started with: " + numberPasses + " passes, " + frequencyFromSpinner + "  interval");
                } else {
                    startStopText.setText("Starten der Mission leider fehlgeschlagen ("+handleTag.getText_val()+" "+missionStatus_val[3]+missionStatus_val[4]+"). Bitte erneut probieren!");
                }
            }
        });

        Button stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleTag.stopDevice(currentTag, cic);
                if(handleTag.get_numberOfPasses()==0 && handleTag.getText_val()!="Tag connection lost"){
                startStopText.setText("Mission gestoppt!");
                } else{
                    startStopText.setText("Stoppen der Mission leider fehlgeschlagen ("+handleTag.getText_val()+") Bitte erneut probieren!");
                }

            }
        });

    }

    protected class TempDataAdapter extends BaseAdapter {
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
            handleTag.readTagData(tag);
            adapter.setData(handleTag.getData());
            missionStatus_val = handleTag.get_MissionStatus_val();
            anzahlMesswerte = handleTag.get_anzahl();
            gesetzteWiederholungen = handleTag.get_numberOfPasses();
            gesetztesIntervall = handleTag.get_frequencyStringFromMs();
            text_val = handleTag.getText_val();
            return null;
        }

    }

}