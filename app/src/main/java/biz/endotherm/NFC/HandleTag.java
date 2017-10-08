package biz.endotherm.NFC;

import android.nfc.Tag;
import android.nfc.tech.NfcV;
import android.util.Log;
import java.util.Date;
import java.util.ArrayList;
import java.util.Calendar;

import java.io.IOException;

public class HandleTag {
    private String text_val;
    private String[] missionStatus_val={"Bitte Sensor scannen!", "","","",""};
    private String f_val="00 00 00 00 00 00 00 00";
    private String g_val="00 00 00 00 00 00 00 00";
    private String frequencyStringFromMs="0";
    private int frequency_ms=0;
    private byte[] block0={0,0,0,0,0,0,0,0};
    private byte[] block8={0,0,0,0,0,0,0,0};
    //private byte[] cmd;
    private int anzahlMesswerte;
    private int numberOfPasses;
    NfcV nfcv_senseTag;
    private ArrayList<DataPoint> data;

    //Getter und Setter
    public String getText_val(){return text_val;}
    public String[] get_MissionStatus_val(){return missionStatus_val;}
    public String get_frequencyStringFromMs(){return frequencyStringFromMs;}
    public String get_f_val(){return f_val;};
    public int get_anzahl(){return anzahlMesswerte;}
    public int get_numberOfPasses(){return numberOfPasses;}

    public HandleTag() {
        data = new ArrayList<DataPoint>();
    }
    //read data
    public void readTagData(Tag tag) {
        byte[] id = tag.getId();
        boolean techFound = false;
        // checking for NfcV
        for (String tech : tag.getTechList())
            if (tech.equals(NfcV.class.getName())) {
                techFound = true;


                // Get an instance of NfcV for the given tag:
                nfcv_senseTag = NfcV.get(tag);

                try {
                    nfcv_senseTag.connect();
                    text_val = "Tag connected";
                } catch (IOException e) {
                    text_val = "Tag connection lost";
                    return;
                }

                block0 = readTag((byte) 0x0);
                block8 = readTag((byte) 0x8);

                //Read Data at index of Data (Anzahl Daten)
                anzahlMesswerte = GetSampleCount();
                frequency_ms = GetFrequencyms();
                frequencyStringFromMs = GetFrequencyStringFromMs(frequency_ms);
                numberOfPasses = GetNumberOfPasses();

                missionStatus_val = GetMissionStatus();

                data.clear();
                Calendar cal = Calendar.getInstance();
                int pagesToRead = (anzahlMesswerte+3)/4;
                int sample = 0;
                for (int i=0; i<pagesToRead; i++) {
                    byte[] buffer = readTag((byte)(0x09+i));
                    for (int j=0; j<4; j++) {
                        if (sample++ < anzahlMesswerte) {
                            DataPoint dataPoint = new DataPoint();
                            dataPoint.temp = ConvertValue(buffer[j*2+1], buffer[j*2+2]);
                            cal.setTime(GetMissionTimestamp());
                            cal.add(Calendar.SECOND, -1*(GetFrequencyms()/1000)*anzahlMesswerte);
                            cal.add(Calendar.SECOND, (GetFrequencyms()/1000) * sample);
                            dataPoint.date = cal.getTime();
                            data.add(dataPoint);
                        }
                    }
                }

                f_val = bytesToHex(readTag((byte) 0x09));
                Log.i("Tag data", "ADC result1= " + f_val);
                g_val = bytesToHex(readTag((byte) 0x0A));
                Log.i("Tag data", "ADC result1= " + f_val);


                }
                try {
                    nfcv_senseTag.close();
                } catch (IOException e) {
                    Log.i("Tag data", "transceive failed and stopped");
                    text_val = "Tag disconnection failed";
                    return;
                }
                text_val = "Tag disconnected";
            }


    public void writeBlock0(Tag tag, int cic, int frequencyRegister, int passesRegister, int reset) {
        // to block 0

        byte[] id = tag.getId();
        boolean techFound = false;
        // checking for NfcV
        for (String tech : tag.getTechList())
            if (tech.equals(NfcV.class.getName())) {
                techFound = true;

                // Get an instance of NfcV for the given tag:
                nfcv_senseTag = NfcV.get(tag);

                try {
                    nfcv_senseTag.connect();
                    text_val = "Tag connected";
                } catch (IOException e) {
                    text_val = "Tag connection lost";
                    return;
                }
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
                byte[] ack = writeTag(cmd, (byte) 0);
                Log.i("Tag data", "ack= " + bytesToHex(ack));
                try {
                    nfcv_senseTag.close();
                } catch (IOException e) {
                    Log.i("Tag data", "transceive failed and stopped");
                    text_val = "Tag disconnection failed";
                    return;
                }
                text_val = "Tag disconnected";
            }
    }

    public void writeBlock2(Tag tag, int cic) {
        //write  to block 2
        byte[] id = tag.getId();
        boolean techFound = false;
        // checking for NfcV
        for (String tech : tag.getTechList())
            if (tech.equals(NfcV.class.getName())) {
                techFound = true;


                // Get an instance of NfcV for the given tag:
                nfcv_senseTag = NfcV.get(tag);

                try {
                    nfcv_senseTag.connect();
                    text_val = "Tag connected";
                } catch (IOException e) {
                    text_val = "Tag connection lost";
                    return;
                }
        byte[] cmd = new byte[]{
                //(byte) 0x11, //Table 71 Reference-ADC1 Configuration Register DECIMATION 12 BIT
                (byte) 0x40, //Table 71 Reference-ADC1 Configuration Register DECIMATION 12 BIT
                //(byte) 0x11, //Table 73 ADC2 Sensor Configuration Register
                (byte) 0x40, //Table 73 ADC2 Sensor Configuration Register
                //(byte) 0x10, //Table 75 ADC0 Sensor Configuration Register
                (byte) 0x40, //Table 75 ADC0 Sensor Configuration Register
                (byte) 0x78,//Cic höchste Genauigkeit, //Table 77 Internal Sensor Configuration Register
                (byte) 0x00, //Table 79 Initial Delay Period Setup Register
                (byte) 0x00, //Table 81  JTAG Enable Password Register
                (byte) 0x00, //Table 83 Initial Delay Period Register
                (byte) 0x00, //Table 85 Initial Delay Period Register
        };
                if (cic == 0)
                    cmd[3] = 0x5C; //höchste Genauigkeit für moving average
        byte[] ack = writeTag(cmd, (byte) 0x2);
        Log.i("Tag data", "ack= " + bytesToHex(ack));
                try {
                    nfcv_senseTag.close();
                } catch (IOException e) {
                    Log.i("Tag data", "transceive failed and stopped");
                    text_val = "Tag disconnection failed";
                    return;
                }
                text_val = "Tag disconnected";
            }
    }

    public void writeBlock8(Tag tag) {
        // to block 8

        byte[] id = tag.getId();
        boolean techFound = false;
        // checking for NfcV
        for (String tech : tag.getTechList())
            if (tech.equals(NfcV.class.getName())) {
                techFound = true;

                // Get an instance of NfcV for the given tag:
                nfcv_senseTag = NfcV.get(tag);

                try {
                    nfcv_senseTag.connect();
                    text_val = "Tag connected";
                } catch (IOException e) {
                    text_val = "Tag connection lost";
                    return;
                }
                byte[] cmd = new byte[]{
                        (byte) 0x03,
                        (byte) 0x8C,//Table128: a maximum of 912 samples is possible. With 908 there is still space for user data
                        (byte) 0x00, //Table131
                        (byte) 0x00, //Table131
                        (byte) 0x00, //Table133
                        (byte) 0x00, //Table133
                        (byte) 0xA3, //Table135
                        (byte) 0xA6, //Table135
                };

                byte[] ack = writeTag(cmd, (byte) 8);
                Log.i("Tag data", "ack= " + bytesToHex(ack));
                try {
                    nfcv_senseTag.close();
                } catch (IOException e) {
                    Log.i("Tag data", "transceive failed and stopped");
                    text_val = "Tag disconnection failed";
                    return;
                }
                text_val = "Tag disconnected";
            }
    }

    //Write Tag with a Block to index
    public byte[] writeTag(byte[] cmd,byte index)
    {
        byte[] cmdWriteTag=new byte[cmd.length+3];
        byte[] ack = new byte[]{0x01};
        cmdWriteTag[0]=0x02;
        cmdWriteTag[1]=0x21; // ISO15693 command code, in this case it is Write Single Block
        cmdWriteTag[2]=index;

        for (int i=0;i<cmd.length;i++) {
            cmdWriteTag[i+3]=cmd[i];
        }
        try {
            ack = nfcv_senseTag.transceive(cmdWriteTag);
        } catch (IOException e) {
            text_val="Tag transfer failed";
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


    public int GetSampleCount() {
        byte[] idx=block8;
        byte index=idx[5]; //Table133   2 Bytes
        byte index2=idx[4];
        int anzahlMesswerte = ((index2 & 0xff) >> 8) | ((index & 0xff) );
        return anzahlMesswerte;
    }

    public String[] GetMissionStatus() {//table 41
        String[] missionstatus={"Wait for Status", "","","","",""};
        byte statusRegisterByte = block0[2];
        byte statusRegisterByte2 = block0[8];
        int batteryOn = (block0[1]& 0x08);
        int state = (statusRegisterByte & 0x03);
        int missionCompleted = (statusRegisterByte & 0x10);
        int missionOverflow = (statusRegisterByte & 0x04);
        int missionTimingError = (statusRegisterByte & 0x08);
        int missionBatError = (statusRegisterByte2 & 0x02);
        switch (state) {
            case 0:
                missionstatus[0] = "Idle ";
                break;
            case 1:
                missionstatus[0] = "Sampling in Progress ";
                break;
            case 2:
                missionstatus[0] = "Data Available in FRAM ";
                break;
            case 3:
                missionstatus[0] = "Error During Mission: ";
                break;
        }

        if(missionCompleted != 0) {
            missionstatus[1] = "Mission completed ";
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
            missionstatus[3] = "Timing Error ";
        }
        else {
            missionstatus[3] = "";
        }
        if(missionBatError != 0) {
            missionstatus[4] = "BatError ";
        }
        else {
            missionstatus[4] = "";
        }
        return missionstatus;
    }

    public int GetFrequencyms() {
        byte frequencyRegisterByte = block0[4];
        int frequencyIndex = (frequencyRegisterByte & 0x1f);//((frequencyRegisterByte & 0xff) << 3);
        int frequency = 0; //frequency in ms

        switch (frequencyIndex) {//custom time not supported
            case 0:
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
                break;
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
        }

        return frequency;
    }

    public String GetFrequencyStringFromMs(int frequencyms){
        frequencyStringFromMs="";
        switch (frequencyms) {//custom time not supported
            case 250:
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
                break;
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

    public int GetPassesRegister(String numberPassesString) {
        int numberPasses = 0;
        if (!numberPassesString.equals("")) {
            numberPasses = Integer.parseInt(numberPassesString);
        }
        int numberPassesleast = (numberPasses & 0xff); //number of passes, least significant byte, table 47
        return numberPassesleast;
    }

    public int GetFrequencyRegister(String FrequencyString, String numberPassesString) {
        int numberPasses = 0;
        int frequency = 0;

        if (!numberPassesString.equals("")) {
            numberPasses = Integer.parseInt(numberPassesString);
        }
        int frequencyByte = GetFrequencyByteFromString(FrequencyString);
        int frequencyRegister = (((numberPasses & 0x700) << 3) | (frequencyByte));//Table 45, first three bits are the most significant bits of (11 bit) number of passes
        return frequencyRegister;
    }

    private int GetFrequencyByteFromString(String Frequenz) {
        int frequencyByte=0;
        switch (Frequenz) {
            case "250 ms":
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
                break;
            case "30 s":
                frequencyByte=5;
                break;
            case "1 min":
                frequencyByte=6;
                break;
            case "2 min":
                frequencyByte=7;
                break;
            case "5 min":
                frequencyByte=8;
                break;
            case "10 min":
                frequencyByte=9;
                break;
            case "30 min":
                frequencyByte=10;
                break;
            case "1 h":
                frequencyByte=11;
                break;
            case "2 h":
                frequencyByte=12;
                break;
            case "5 h":
                frequencyByte=13;
                break;
            case "10 h":
                frequencyByte=14;
                break;
            case "24 h":
                frequencyByte=15;
                break;
        }
        return frequencyByte;
    }

    public int GetNumberOfPasses() {
        byte numberPassesRegisterByteMost = block0[4];
        byte numberPassesRegisterByteLeast= block0[5];
        int numberOfPassesIndex = (((numberPassesRegisterByteMost & 0xE0) >> 3) | (numberPassesRegisterByteLeast & 0xff));

        return numberOfPassesIndex;
    }

    public void startDevice(Tag tag, String numberPasses, String FrequencyString, int cic) {
        int frequencyRegister = GetFrequencyRegister(FrequencyString, numberPasses);
        int passesRegister = GetPassesRegister(numberPasses);

        writeBlock8(tag);
        writeBlock2(tag, cic);
        writeBlock0(tag, cic, frequencyRegister, passesRegister, 0);
        readTagData(tag);
    }

    public void stopDevice(Tag tag, int cic) {
        writeBlock0(tag, cic, (byte) 0x0, (byte) 0x00, 1);
        readTagData(tag);
    }

    public Date GetMissionTimestamp() {//später soll dieser Wert aus der Datenbank kommen
        Date now = new Date();
        return now;
    }

    public Date SetMissionTimestamp() {//später soll diese Funktion die aktuelle Zeit in die Datenbank schreiben
        Date now = new Date();
        return now;
    } // TODO: 23.09.17

    public class DataPoint {
        public Date date;
        public double temp;
    }

    public DataPoint[] getData() {
        return data.toArray(new DataPoint[data.size()]);
    }


    protected double ConvertValue(byte loByte, byte hiByte) {
        int result = ((hiByte & 0xff)<<8)|(loByte & 0xff);//Internal temperature sensor: bit-Value
        double calibrationOffset=-285-21.35; //varies significantly for different devices, has to be calibrated
        double tempResult = calibrationOffset+(result)/35.7;
        return tempResult;
    }

    //parsing function
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
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

