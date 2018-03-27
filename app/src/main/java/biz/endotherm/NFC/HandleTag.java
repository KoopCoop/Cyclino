package biz.endotherm.NFC;

import android.nfc.Tag;
import android.nfc.tech.NfcV;
import android.util.Log;
import android.widget.Switch;

import java.util.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.lang.Math;
import java.util.Date;

import java.io.IOException;

public class HandleTag {
    private String text_val;
    private String[] missionStatus_val = {"", "", "", "", ""};
    private String frequencyStringFromMs = "0";
    private int frequency_ms = 0;
    private byte[] block0 = {0, 0, 0, 0, 0, 0, 0, 0};
    private byte[] block2 = {0, 0, 0, 0, 0, 0, 0, 0};
    private byte[] block8 = {0, 0, 0, 0, 0, 0, 0, 0};
    private byte[] block236 = {0, 0, 0, 0, 0, 0, 0, 0};
    //private byte[] cmd;
    private int currentMeasurementNumber;
    private Calendar configuredMissionTimestamp = Calendar.getInstance();
    private long delay_ms;
    private long delayCountdown;
    private int numberPassesConfigured;
    private NfcV nfcv_senseTag;
    private ArrayList<DataPoint> data;

    private long firstMeasurementTime = 0; //mission start time in ms (Unix time)
    private long lastTime = 0; //mission end time in ms (Unix time)

    //Getter und Setter
    public String getText_val() {
        return text_val;
    }

    public String[] get_MissionStatus_val() {
        return missionStatus_val;
    }

    public String get_frequencyStringFromMs() {
        return frequencyStringFromMs;
    }

    public int get_anzahl() {
        return currentMeasurementNumber;
    }

    public int get_numberOfPasses() {
        return numberPassesConfigured;
    }

    public long get_configuredDelay_ms() {
        return delay_ms;
    }

    public long get_delayCountdown() {
        return delayCountdown;
    }

    public Calendar get_configuredMissionTimestamp() {
        return configuredMissionTimestamp;
    }

    public HandleTag() {
        data = new ArrayList<>();
    }

    //read data
    public void readTagData(Tag tag, boolean roundTemp) {
        if (tag == null) {
            text_val = "Tag not connected";
            return;
        } else {
            byte[] id = tag.getId();
            // checking for NfcV
            for (String tech : tag.getTechList()) {
                if (tech.equals(NfcV.class.getName())) {

                    // Get an instance of NfcV for the given tag:
                    nfcv_senseTag = NfcV.get(tag);

                    try {
                        nfcv_senseTag.connect();
                        text_val = "Sensor verbunden";
                    } catch (IOException e) {
                        text_val = "Verbindung zum Sensor verloren!";
                        return;
                    }

                    block0 = readTag((byte) 0x0);
                    block8 = readTag((byte) 0x8);
                    block2 = readTag((byte) 0x2);
                    block236 = readTag((byte) 0xEC);//Block contains custom data: Mission Timestamp and Calibration

                    //Read Data at index of Data (Anzahl Daten)
                    GetSampleCount();
                    frequency_ms = GetFrequency_ms();
                    frequencyStringFromMs = GetFrequencyStringFromMs(frequency_ms);
                    numberPassesConfigured = GetNumberOfPassesFromRegister();
                    missionStatus_val = GetMissionStatus();
                    configuredMissionTimestamp = GetConfiguredMissionTimestamp();
                    delay_ms = GetConfiguredDelay_ms();
                    delayCountdown = GetDelayCountdown();

                    data.clear();

                    lastTime = GetCurrentUnixTime() * 1000;//current time, approx. time of last measurement
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(GetSetUnixTime() * 1000);
                    cal.add(Calendar.MILLISECOND, (int) delay_ms);//Mission start time
                    firstMeasurementTime = cal.getTimeInMillis();

                    if (currentMeasurementNumber > 908) {//default for factory fresh chips is bigger
                        currentMeasurementNumber = 0;
                    }
                    int pagesToRead = (currentMeasurementNumber + 3) / 4;
                    int sample = 0;
                    boolean timingCorrect = CheckIfMissionTimingIsCorrect(frequency_ms, currentMeasurementNumber);
                    if (timingCorrect) {
                        for (int i = 0; i < pagesToRead; i++) {
                            byte[] buffer = readTag((byte) (0x09 + i));
                            for (int j = 0; j < 4; j++) {
                                if (sample++ < currentMeasurementNumber) {
                                    DataPoint dataPoint = new DataPoint();
                                    dataPoint.temp = ConvertValue(buffer[j * 2 + 1], buffer[j * 2 + 2], roundTemp);
                                    if (sample != 1) {
                                        GetNextDataTime(cal, frequency_ms);
                                    }
                                    dataPoint.date = cal.getTime();
                                    data.add(dataPoint);
                                }
                            }
                        }
                    } else {
                        text_val = "Suspekte Sensorwerte!";
                        currentMeasurementNumber = 0;
                    }
                }
            }
            try {
                nfcv_senseTag.close();
            } catch (IOException e) {
                Log.i("Tag data", "transceive failed and stopped");
                text_val = "Trennung des Sensors fehlgeschlagen!";
                return;
            }
            //text_val = "Tag disconnected";
        }
    }


    private void writeBlock(byte block, Tag tag, byte[] cmd) {
        if (tag == null) {
            text_val = "Sensor nicht verbunden!";
            return;
        } else {
            byte[] id = tag.getId();
            // checking for NfcV
            for (String tech : tag.getTechList())
                if (tech.equals(NfcV.class.getName())) {

                    // Get an instance of NfcV for the given tag:
                    nfcv_senseTag = NfcV.get(tag);

                    try {
                        nfcv_senseTag.connect();
                        text_val = "Sensor verbunden";
                    } catch (IOException e) {
                        text_val = "Verbindung zum Sensor verloren!";
                        return;
                    }
                    byte[] ack = writeTag(cmd, block);

                    Log.i("Tag data", "ack= " + bytesToHex(ack));
                    try {
                        nfcv_senseTag.close();
                    } catch (IOException e) {
                        Log.i("Tag data", "transceive failed and stopped");
                        text_val = "Trennung des Sensors fehlgeschlagen!";
                        return;
                    }
                    text_val = "Sensor getrennt";
                }
        }
    }

    private byte[] cmdBlock0(int frequencyRegister, int passesRegister, int reset, int cic) {
        byte[] cmd = new byte[]{  //Start Bit in Table39  gesetzt.
                (byte) 0x0D, //Table39 Start bit is set, after this is written this starts the sampling process, interrupt enabled for On/Off
                (byte) 0x00, //Table41 Status byte
                (byte) 0x09, //Table43 INTERNAL sensor and ADC1 Sensor selected
                (byte) frequencyRegister,
                //(byte) 0x64, //Table47 100 passes, 16h
                (byte) passesRegister,
                (byte) 0x01, //Table49 No averaging selected
                (byte) 0x00, //Table51
                (byte) 0x00, //Table53 no thermistor
        };
        if (reset == 1)
            cmd[0] |= 0x40; //Table 39 Software Reset without battery
        //cmd[0] |= 0x8C;//0x8C; //Table39 Software Reset with Battery
        if (cic == 0)
            cmd[2] = 0x08;//only internal Sensor Table 43, no CIC available
        return cmd;
    }

    private byte[] cmdBlock2(int cic, long delay) {
        byte[] cmd = new byte[]{
                //(byte) 0x11, //Table 71 Reference-ADC1 Configuration Register DECIMATION 12 BIT
                (byte) 0x40, //Table 71 Reference-ADC1 Configuration Register DECIMATION 12 BIT
                //(byte) 0x11, //Table 73 ADC2 Sensor Configuration Register
                (byte) 0x40, //Table 73 ADC2 Sensor Configuration Register
                //(byte) 0x10, //Table 75 ADC0 Sensor Configuration Register
                (byte) 0x40, //Table 75 ADC0 Sensor Configuration Register
                (byte) 0x78,//Cic höchste Genauigkeit, //Table 77 Internal Sensor Configuration Register
                (byte) 0x01, //Table 79 Initial Delay Period Setup Register, nonzero enables delay
                (byte) 0x00, //Table 81  JTAG Enable Password Register
                (byte) delay, //Table 83 Initial Delay Period Register
                (byte) (delay >> 8), //Table 83 Initial Delay Period Register
        };
        if (cic == 0)
            cmd[3] = 0x5C; //höchste Genauigkeit für moving average
        return cmd;
    }

    private byte[] cmdBlock8() {
        byte[] cmd = new byte[]{
                (byte) 0x8C, //Table128
                (byte) 0x03,//Table128: a maximum of 912 samples is possible. With 908 there is still space for user data
                (byte) 0x00, //Table131
                (byte) 0x00, //Table131
                (byte) 0x00, //Table133
                (byte) 0x00, //Table133
                (byte) 0xA6, //Table135
                (byte) 0xA3, //Table135
        };
        return cmd;
    }

    private byte[] cmdBlock3(int customTime) {
        byte[] cmd = new byte[]{
                (byte) (customTime), //Table84, 900000ms
                (byte) (customTime >> 8),//Table84
                (byte) (customTime >> 16), //Table84
                (byte) (customTime >> 24), //Table84
                (byte) 0x00, //Table87
                (byte) 0x00, //Table87
                (byte) 0x00, //Table89
                (byte) 0x00, //Table89
        };
        return cmd;
    }

    private byte[] cmdBlock236(long missionTimeStamp, long newCalibrationOffset, long delay_min) {
        byte[] timestamp = new byte[]{
                (byte) (missionTimeStamp >> 24),
                (byte) (missionTimeStamp >> 16),
                (byte) (missionTimeStamp >> 8),
                (byte) (missionTimeStamp),
                (byte) (newCalibrationOffset >> 8),
                (byte) (newCalibrationOffset),
                (byte) (delay_min),
                (byte) (delay_min >> 8),
        };
        return timestamp;
    }

    //Write Tag with a Block to index
    private byte[] writeTag(byte[] cmd, byte index) {
        byte[] cmdWriteTag = new byte[cmd.length + 3];
        byte[] ack;
        cmdWriteTag[0] = 0x02;
        cmdWriteTag[1] = 0x21; // ISO15693 command code, in this case it is Write Single Block
        cmdWriteTag[2] = index;

        for (int i = 0; i < cmd.length; i++) {
            cmdWriteTag[i + 3] = cmd[i];
        }
        try {
            ack = nfcv_senseTag.transceive(cmdWriteTag);
        } catch (IOException e) {
            text_val = "Tag transfer failed";
            //Log.i("Tag data", "transceive failed");
            return null;
        }

        //Log.i("Tag data", "ack= " + bytesToHex(ack));
        return ack;
    }

    //Read Tag at Block no index
    private byte[] readTag(byte index) {
        byte[] cmd = new byte[]{
                //   (byte)0x18, // Always needed, everything after this 18 is sent over the air, response is given in the text box below
                (byte) 0x02, // Flags (always use same)
                (byte) 0x20, // ISO15693 command code, in this case it is Read Single Block
                index, // Block number
        };

        byte[] reading;
        try {
            reading = nfcv_senseTag.transceive(cmd);
            //Value.setText("Value: " + bytesToHex(reading) );
        } catch (IOException e) {
            text_val = "Tag transfer failed"; // new version requires exception handling on each step
            Log.i("Tag data", "transceive failed");
            return null;
        }
        return reading;
    }

    private Calendar GetNextDataTime(Calendar cal, int frequency) {
        cal.add(Calendar.MILLISECOND, frequency);
        return cal;
    }

    private boolean CheckIfMissionTimingIsCorrect(int frequency, int anzahl){
        // check if lowerErrorFrequency differs from frequency (originally set) by more than 10%.
        // That means the mission was unexpectedly stopped or the measurement time intervals were stretched, both due to low battery voltage.
        // In this case, don't show any values, but stop the mission, since we don't know the date/time values of the recorded temperatures.
        if(anzahl!=numberPassesConfigured && anzahl>10) { // either the mission is still running or it stopped/stretched unexpectedly
            int lowerErrorFrequency = Math.round((lastTime - firstMeasurementTime) / (anzahl - 1));
            double frequencyRatio = lowerErrorFrequency / frequency;
            int expectedAnzahl = Math.round((lastTime - firstMeasurementTime) / frequency);
                if(frequencyRatio >= 1.1 || frequencyRatio <= 0.9){ // normally, only >=1.1 should occur. To be sure, include <=0.9 as well.
                    return false; // mission stopped/stretched unexpectedly
                } else {
                    return true; // mission still running correctly
                }
        } else if(anzahl!=numberPassesConfigured && anzahl<=10 && anzahl>0) { // either the mission is still running or it stopped/stretched unexpectedly
            if (frequency != 0) {
                    int expectedAnzahlLowerBorder = 1+(int)((lastTime - firstMeasurementTime-0.1*frequency*(anzahl-1)) / (1.1*frequency));//subtracting timing error (10% maximum) for each measurement
                    Log.v("Tag data", "erwartete Anzahl: " + expectedAnzahlLowerBorder);
                    if (anzahl < expectedAnzahlLowerBorder || anzahl > expectedAnzahlLowerBorder+1) { // normally, only the first should occur. To be sure, include second (weak) check as well.
                        return false; //stretched unexpectedly, this could also mean that it aborted
                        } else {
                            return true; // mission still running correctly. If not, we don't know
                        }
            } else {
                return true;
                    }
        }
        else {
                return true; // mission finished correctly
             }
        }

        private void GetSampleCount() {
        byte[] idx=block8;
        byte index=idx[5]; //Table133   2 Bytes
        byte index2=idx[4];
        currentMeasurementNumber = ((index2 & 0xff) << 8) | ((index & 0xff) );
    }

    private long GetConfiguredDelay_ms() {
        long delayFromRegister_ms=GetDelayFromRegister_ms();
        if(currentMeasurementNumber>10 & currentMeasurementNumber!=numberPassesConfigured) {
            delay_ms=(long)((delayFromRegister_ms+(currentMeasurementNumber-1.)*GetFrequency_ms())/(System.currentTimeMillis()-GetSetUnixTime()*1000.)*delayFromRegister_ms);
        } else{
            delay_ms=delayFromRegister_ms;
        }
        return delay_ms;
    }

    private long GetDelayCountdown(){
        byte[] idx=block2;
        byte index=idx[8];//Table78 2 Bytes
        byte index2=idx[7];
        delayCountdown=((index & 0xff) << 8) | ((index2 & 0xff) );
        return delayCountdown;
    }

    private long GetDelayFromRegister_ms(){
        byte[] idx=block236;
        byte index=idx[8];//Table 83, 2 Bytes
        byte index2=idx[7];
        long delay_minutes = ((index & 0xff) << 8) | ((index2 & 0xff) );
        return delay_minutes*60*1000;
    }

    private String[] GetMissionStatus() {//table 41 doesn't quite do what I expect
        String[] missionstatus={"Wait for Status", "","","","",""};
        byte statusRegisterByte = block0[2];
        byte statusRegisterByte2 = block0[8];
        int state = (statusRegisterByte & 0x03);
        int missionCompleted = (statusRegisterByte & 0x10);
        int missionOverflow = (statusRegisterByte & 0x04);
        int missionTimingError = (statusRegisterByte & 0x08);
        int missionBatError = (statusRegisterByte2 & 0x02);//Table53
        switch (state) {
            case 0:
                missionstatus[0] = "Untätig ";
                break;
            case 1:
                missionstatus[0] = "Mission läuft ";
                break;
            case 2:
                missionstatus[0] = "Daten in FRAM ";
                break;
            case 3:
                missionstatus[0] = "Fehler während Mission: ";
                break;
        }

        if(missionCompleted != 0) {
            missionstatus[1] = "Mission vollständig ";// TODO: 08.10.17 Why is this always zero?
        }
        else {
            missionstatus[1] = "";
        }
        if(missionOverflow != 0) {
            missionstatus[2] = "FRAM Overflow ";
        }
        else {
            missionstatus[2] = "";
        }
        if(missionTimingError != 0) {
            missionstatus[3] = "Timing Error ";//this works
        }
        else {
            missionstatus[3] = "";
        }
        if(missionBatError != 0) {
            missionstatus[4] = "BatError ";// this works
        }
        else {
            missionstatus[4] = "";
        }
        return missionstatus;
    }

    private int GetFrequency_ms() {
        byte frequencyRegisterByte = block0[4];
        int frequencyIndex = (frequencyRegisterByte & 0x1f);//((frequencyRegisterByte & 0xff) << 3);
        int frequency = 0; //frequency in ms

        switch (frequencyIndex) {
            /*case 0:
                frequency = 250;
                break;
            case 1:
                frequency = 500;
                break;
            case 2:
                frequency = 1000;
                break;
            case 3:
                frequency = 5000;
                break;
            case 4:
                frequency = 15000;
                break;*/
            case 5:
                frequency = 30000;
                break;
            case 6:
                frequency = 60000;
                break;
            case 7:
                frequency = 120000;
                break;
            case 8:
                frequency = 300000;
                break;
            case 9:
                frequency = 600000;
                break;
            case 10:
                frequency = 1800000;
                break;
            case 11:
                frequency = 3600000;
                break;
            case 12:
                frequency = 7200000;
                break;
            case 13:
                frequency = 18000000;
                break;
            case 14:
                frequency = 36000000;
                break;
            case 15:
                frequency = 86400000;
                break;
            case 16:
                frequency = 900000;
        }

        return frequency;
    }

    private String GetFrequencyStringFromMs(int frequencyms){
        frequencyStringFromMs="";
        switch (frequencyms) {//custom time not supported
            /*case 250://with moving average measurements take about 16s with highest accuracy
                frequencyStringFromMs = "250 ms";
                break;
            case 500:
                frequencyStringFromMs = "500 ms";
                break;
            case 1000:
                frequencyStringFromMs = "1 s";
                break;
            case 5000:
                frequencyStringFromMs = "5 s";
                break;
            case 15000:
                frequencyStringFromMs = "15 s";
                break;*/
            case 30000:
                frequencyStringFromMs = "30 s";
                break;
            case 60000:
                frequencyStringFromMs = "1 min";
                break;
            case 120000:
                frequencyStringFromMs = "2 min";
                break;
            case 300000:
                frequencyStringFromMs = "5 min";
                break;
            case 600000:
                frequencyStringFromMs = "10 min";
                break;
            case 900000:
                frequencyStringFromMs = "15 min";
                break;
            case 1800000:
                frequencyStringFromMs = "30 min";
                break;
            case 3600000:
                frequencyStringFromMs = "1 h";
                break;
            case 7200000:
                frequencyStringFromMs = "2 h";
                break;
            case 18000000:
                frequencyStringFromMs = "5 h";
                break;
            case 36000000:
                frequencyStringFromMs = "10 h";
                break;
            case 86400000:
                frequencyStringFromMs = "24 h";
                break;
        }

        return frequencyStringFromMs;
    }

    private int GetPassesRegisterFromValue(String numberPassesString) {
        int numberPasses = 0;
        if (!numberPassesString.equals("")) {
            numberPasses = Integer.parseInt(numberPassesString);
        }
        return numberPasses; //number of passes, least significant byte, table 47
    }

    private int GetFrequencyRegister(String FrequencyString, String numberPassesString) {
        int numberPasses = 0;

        if (!numberPassesString.equals("")) {
            numberPasses = Integer.parseInt(numberPassesString);
        }
        int frequencyByte = GetFrequencyByteFromString(FrequencyString)[0];
        return (((numberPasses & 0x700) >> 3) | (frequencyByte & 0xff));//Table 45, first three bits are the most significant bits of (11 bit) number of passes
    }

    private int[] GetFrequencyByteFromString(String Frequenz) {
        int[] frequencyByteArray={0,0};//first entry: frequency register byte, second entry: custom timer table 84
        switch (Frequenz) {
            /*case "250 ms":
                frequencyByte=0;
                break;
            case "500 ms":
                frequencyByte=1;
                break;
            case "1 s":
                frequencyByte=2;
                break;
            case "5 s":
                frequencyByte=3;
                break;
            case "15 s":
                frequencyByte=4;
                break;*/
            case "30 s":
                frequencyByteArray[0]=5;
                break;
            case "1 min":
                frequencyByteArray[0]=6;
                break;
            case "2 min":
                frequencyByteArray[0]=7;
                break;
            case "5 min":
                frequencyByteArray[0]=8;
                break;
            case "10 min":
                frequencyByteArray[0]=9;
                break;
            case "30 min":
                frequencyByteArray[0]=10;
                break;
            case "1 h":
                frequencyByteArray[0]=11;
                break;
            case "2 h":
                frequencyByteArray[0]=12;
                break;
            case "5 h":
                frequencyByteArray[0]=13;
                break;
            case "10 h":
                frequencyByteArray[0]=14;
                break;
            case "24 h":
                frequencyByteArray[0]=15;
                break;
            case "15 min":
                frequencyByteArray[0]=16;
                frequencyByteArray[1]=900000;
                break;
        }
        return frequencyByteArray;
    }

    private int GetNumberOfPassesFromRegister() {
        int numberPassesRegisterByteMost = block0[4];
        int numberPassesRegisterByteLeast= block0[5];
        return  (((numberPassesRegisterByteMost & 0xE0) << 3) | (numberPassesRegisterByteLeast & 0xff));
    }

    public void startDevice(Tag tag, String numberPasses, String FrequencyString, long wantedDelay_min, int cic) {
        int frequencyRegister = GetFrequencyRegister(FrequencyString, numberPasses);
        int passesRegister = GetPassesRegisterFromValue(numberPasses);
        SetMissionTimestamp(tag, GetCurrentUnixTime(), wantedDelay_min);
        writeBlock((byte) 0x08, tag, cmdBlock8());
        writeBlock((byte) 0x02, tag, cmdBlock2(cic, wantedDelay_min));
        writeBlock((byte) 0x03, tag, cmdBlock3(GetFrequencyByteFromString(FrequencyString)[1]));
        writeBlock((byte) 0x00, tag, cmdBlock0(frequencyRegister,passesRegister, 0, cic));
        readTagData(tag, false);
    }

    public void stopDevice(Tag tag, int cic) {
        writeBlock((byte) 0x00, tag, cmdBlock0( (byte) 0x0, (byte) 0x00, 1, cic));
        readTagData(tag, false);
    }

    private Calendar GetConfiguredMissionTimestamp() {//from block 236
        configuredMissionTimestamp.setTimeInMillis(GetSetUnixTime() * 1000);
        return configuredMissionTimestamp;
    }

    private long GetCurrentUnixTime(){
        return System.currentTimeMillis() / 1000L;
    }

    private void SetMissionTimestamp(Tag tag, long missionTimeStamp, long delay_min) {
        writeBlock((byte)0xEC, tag, cmdBlock236(missionTimeStamp, GetSetCalibrationOffset(),delay_min));//Mission timestamp is written to sensor memory
    }

    private long GetSetUnixTime(){
        return ((block236[1]&0xff)<<24)|((block236[2]&0xff)<<16)|((block236[3]&0xff)<<8)|(block236[4]&0xff);
    }

    public class DataPoint {
        public Date date;
        public double temp;
    }

    public DataPoint[] GetData() {
        return data.toArray(new DataPoint[data.size()]);
    }


    private double ConvertValue(byte loByte, byte hiByte, boolean roundTemp) {
        double outputTemperature;
        int result = ((hiByte & 0xff)<<8)|(loByte & 0xff);//Internal temperature sensor: bit-Value
        //int calibrationOffset=-10937; //varies significantly for different devices, has to be calibrated
        double temperature=(result-GetSetCalibrationOffset())/35.7;
        if (roundTemp) {
            outputTemperature = Math.round(temperature * 20.) / 20.;//for NFP temperature needs to be rounded to 0,05°C steps
        }else{
            outputTemperature = temperature;
        }
        //double temperatureRounded=Math.round(temperature*20.)/20.;//for NFP temperature needs to be rounded to 0,05°C steps
        //return temperatureRounded;
        return outputTemperature;
    }

    public long GetNewCalibrationOffset(double calibrationTemp, double cyclinoValue){//actual temperature vs shown Temperature for 1-Point-Calibration
        double calibrationTempRegister = 35.7* calibrationTemp;
        double cyclinoValueRegister = 35.7*cyclinoValue;

        long calibrationTempInt = Math.round(calibrationTempRegister);
        long cyclinoValueInt = Math.round(cyclinoValueRegister);

        long newCalibrationOffset = cyclinoValueInt - calibrationTempInt;
        return GetSetCalibrationOffset()+newCalibrationOffset;
    }

    public void setCalibrationOffset(Tag tag, long newCalibrationOffset){
        writeBlock((byte) 0xEC, tag, cmdBlock236(GetSetUnixTime(), newCalibrationOffset,delay_ms/60/1000));
    }

    public int GetSetCalibrationOffset(){
        //int calibrationOffset=-10937; //varies significantly for different devices, has to be calibrated
        return ((block236[5] & 0xff) << 8)|(block236[6] & 0xff);
    }

    //parsing function
    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3];
        for ( int j = 1; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }
}

