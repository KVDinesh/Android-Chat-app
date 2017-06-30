package com.example.android.wifidirect;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;


public class Demo_Key_Display extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo__key__display);

        // Store device name and device address in shared preferences
        storeTrust();
        TextView textView = (TextView) findViewById(R.id.rsapublic);
        textView.setText(Crypto.rsapublickey.toString());
        textView = (TextView) findViewById(R.id.rsaprivate);
        if(Crypto.rsaprivatekey != null)
            textView.setText(Crypto.rsaprivatekey.toString());
        else
            textView.setText("I am Group Owner. Client has private key.");
        textView = (TextView) findViewById(R.id.aeskey);
        textView.setText(Base64.encodeToString(Crypto.AESKEY.getEncoded(),Base64.DEFAULT));
        //Log.d(WiFiDirectActivity.MYTAG,"Device name: "+Crypto.getDeviceName() + " Device address: "+ Crypto.getDeviceAddress());
    }

    public void chat(View view){
        Intent intent = new Intent(this,Message_Activity.class);
        startActivity(intent);
    }

    void storeTrust(){
        Log.d(WiFiDirectActivity.MYTAG,"Inside store trust");
        SharedPreferences trust = getSharedPreferences(WiFiDirectActivity.TRUST, 0);
        String device_key = Crypto.deviceName + Crypto.deviceAddress;

        SharedPreferences.Editor editor = trust.edit();
        editor.clear();
        if(trust.contains(device_key))
        {
            editor.putInt(device_key,trust.getInt(device_key,0)+1);
        }
        else
        {
            editor.putInt(device_key,1);
            editor.putString(Crypto.deviceAddress,Crypto.deviceName);
        }
        editor.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_demo__key__display, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
