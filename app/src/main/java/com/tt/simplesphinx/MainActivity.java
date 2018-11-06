package com.tt.simplesphinx;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

public class MainActivity extends AppCompatActivity implements RecognitionListener, View.OnClickListener {

    private static final String TAG = "SimpleSphinx";

    private static final int PERMISSIONS_REQUEST_CODE = 1;
    private static final String ACTION_SEARCH = "actions";
    //private static final String FORECAST_SEARCH = "forecast";
    //private static final String PHONE_SEARCH = "phones";
    private SpeechRecognizer recognizer;

    private Map<String, ResolveInfo> appMap = new HashMap<>();
    private Map<String, String> contactMap = new HashMap<>();

    private final static String JSGF_HEAD = "#JSGF V1.0; grammar actions; public <item> = ";

    private ImageView mRecord;
    private TextView mResult;
    private SiriView mWave;
    private boolean recording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.CALL_PHONE,}, PERMISSIONS_REQUEST_CODE);
            return;
        }

        mRecord = findViewById(R.id.record);
        mResult = findViewById(R.id.result);
        mWave = findViewById(R.id.wave);
        mRecord.setEnabled(false);

        /*
        mWave.stop();
        mWave.setWaveHeight(0.4f);
        mWave.setWaveWidth(4f);
        mWave.setWaveColor(Color.rgb(2, 252, 233));
        mWave.setWaveOffsetX(0f);
        mWave.setWaveAmount(4);
        mWave.setWaveSpeed(0.1f);
        */

        new SetupTask(this).execute();

        mRecord.setOnClickListener(this);
        mResult.setText("");
        mWave.setVisibility(View.GONE);
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.e(TAG, "onBeginningOfSpeech");
    }

    @Override
    public void onEndOfSpeech() {
        Log.e(TAG, "onEndOfSpeech");
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        Log.e(TAG, "onPartialResult");
        if (hypothesis == null)
            return;
        startListening();
    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        Log.e(TAG, "onResult");

        if(hypothesis == null) {
            endListening("onResult,error");
        } else {
            endListening("onResult," + hypothesis.getHypstr());
            String text = hypothesis.getHypstr();

            if(text.startsWith("open")) {
                String appName = text.substring("open ".length());
                ResolveInfo info = appMap.get(appName);
                Log.e(TAG, "onResult,open=" + appName  + ".");

                if(info != null) {
                    ActivityInfo activity = info.activityInfo;
                    ComponentName name = new ComponentName(activity.packageName, activity.name);
                    Log.e(TAG, "onResult,name=" + name);
                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.addCategory(Intent.CATEGORY_LAUNCHER);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    i.setComponent(name);
                    startActivity(i);
                }
            } else if(text.startsWith("call")) {
                String name = text.substring("call ".length());
                String number = contactMap.get(name);
                Log.e(TAG, "onResult,call=" + number);

                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_CALL);
                intent.setData(Uri.parse("tel:" + number));
                startActivity(intent);
            } else if(text.startsWith("send")) {
                String name = text.substring("send ".length());
                String number = contactMap.get(name);
                Log.e(TAG, "onResult,send=" + text);

                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number));
                //intent.putExtra("sms_body", message);
                startActivity(intent);
            }
        }
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, "onError");
        endListening("onError");
    }

    @Override
    public void onTimeout() {
        Log.e(TAG, "onTimeout");
        endListening("onTimeout");
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if(id == R.id.record) {
            if(recording) {
                endListening("stop");
            } else {
                startListening();
            }
        }
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<MainActivity> activityReference;

        SetupTask(MainActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            try {
                Log.e(TAG, "doInBackground");
                Assets assets = new Assets(activityReference.get());
                File assetDir = assets.syncAssets();
                activityReference.get().setupRecognizer(assetDir);
            } catch (IOException e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            Log.e(TAG, "onPostExecute");

            if (result != null) {
                //mCaptionView.setText("Failed to init recognizer " + result);
            } else {
                //activityReference.get().startListening();
                activityReference.get().mRecord.setEnabled(true);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.e(TAG, "onRequestPermissionsResult");

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                new SetupTask(this).execute();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        Log.e(TAG, "setupRecognizer");
        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .getRecognizer();
        recognizer.addListener(this);

        String appsGrammar = makeAppsGrammar();
        String contactsGrammar = makeContactsGrammar();
        String grammar = appsGrammar;
        if(!contactsGrammar.isEmpty()) {
            grammar = grammar + " | " + contactsGrammar;
        }
        String menuGrammar2 = JSGF_HEAD + grammar + ";";
        recognizer.addGrammarSearch(ACTION_SEARCH, menuGrammar2);
    }

    private void startListening() {
        recording = true;
        mRecord.setImageResource(R.drawable.recording);
        mResult.setVisibility(View.GONE);
        mWave.setVisibility(View.VISIBLE);
        mWave.setStartAt(System.currentTimeMillis());
        recognizer.stop(); //important
        recognizer.startListening(ACTION_SEARCH, 10000);
    }

    private void endListening(String text) {
        recognizer.cancel();
        mRecord.setImageResource(R.drawable.record_off);
        mResult.setVisibility(View.VISIBLE);
        mWave.setVisibility(View.GONE);
        mResult.setText(text);
        recording = false;
    }

    private String makeAppsGrammar() {
        Intent intent = new Intent();
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setAction(Intent.ACTION_MAIN);

        PackageManager manager = getPackageManager();
        List<ResolveInfo> resolveInfoList = manager.queryIntentActivities(intent, 0);
        List<String> names = new ArrayList<>();
        names.clear();
        //appMap.clear();

        for (ResolveInfo info : resolveInfoList) {
            String name = info.loadLabel(manager).toString().toLowerCase();
            try {
                recognizer.addGrammarSearch("test", JSGF_HEAD + " " + name + " ;");
                names.add(name);
                appMap.put(name, info);
            } catch (RuntimeException e) {
                Log.e(TAG, "e=" + e);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i).toLowerCase();
            Log.e(TAG, name);

            sb.append("open ").append(name);
            if (i < (names.size() - 1)) {
                sb.append(" | ");
            }
        }

        return sb.toString();
    }

    private String makeContactsGrammar() {
        contactMap.clear();
        List<String> names = new ArrayList<>();

        ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
        int idColumn = cursor.getColumnIndex(ContactsContract.Contacts._ID);
        int displayNameColumn = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
        int phoneCountColumn = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER);
        while (cursor.moveToNext()) {
            String id = cursor.getString(idColumn);
            String displayName = cursor.getString(displayNameColumn);
            //TODO Kaidi should check /M
            if (displayName.endsWith("/M"))
                displayName = displayName.substring(0, displayName.length() - 2);
            displayName = displayName.toLowerCase();
            String phoneNumber = "";
            //Log.e(TAG, "displayName=" + displayName);

            int phoneCount = cursor.getInt(phoneCountColumn);
            if (phoneCount > 0) {
                Cursor phoneCursor = resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=" + id, null, null);
                if(phoneCursor != null && phoneCursor.moveToFirst()) {
                    while(!phoneCursor.isAfterLast()) {
                        int phoneNumberColumn = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        phoneNumber = phoneCursor.getString(phoneNumberColumn);
                        //item.phoneNumber = phoneNumber;
                        contactMap.put(displayName, phoneNumber);
                        Log.e(TAG, "phoneNumber=" + phoneNumber);
                        phoneCursor.moveToNext();
                    }
                    phoneCursor.close();
                }
            }

            try {
                if(phoneNumber != null && !phoneNumber.equals("")) {
                    recognizer.addGrammarSearch("test", JSGF_HEAD + " " + displayName + " ;");
                    names.add(displayName);
                    contactMap.put(displayName, phoneNumber);
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "e=" + e);
            }
            names.add(displayName);
        }
        cursor.close();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i).toLowerCase();
            Log.e(TAG, name);

            sb.append("call ").append(name).append(" | ").append("send ").append(name);
            if (i < (names.size() - 1)) {
                sb.append(" | ");
            }
        }

        return sb.toString();
    }}
