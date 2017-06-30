/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wifidirect;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.android.wifidirect.DeviceListFragment.DeviceActionListener;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    ProgressDialog progressDialog = null;



    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mContentView = inflater.inflate(R.layout.device_detail, null);
        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;

                config.wps.setup = WpsInfo.PBC;
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true
//                        new DialogInterface.OnCancelListener() {
//
//                            @Override
//                            public void onCancel(DialogInterface dialog) {
//                                ((DeviceActionListener) getActivity()).cancelDisconnect();
//                            }
//                        }
                        );
                ((DeviceActionListener) getActivity()).connect(config);

            }
        });

        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        ((DeviceActionListener) getActivity()).disconnect();
                    }
                });

        mContentView.findViewById(R.id.btn_start_client).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
                    }
                });

        return mContentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        // User has picked an image. Transfer it to group owner i.e peer using
        // FileTransferService.
        Uri uri = data.getData();
        TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
        statusText.setText("Sending: " + uri);
        Log.d(WiFiDirectActivity.TAG, "Intent----------- " + uri);

    }

    public void startClientSocket()
    {
        Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, "some file path");
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                info.groupOwnerAddress.getHostAddress());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
        getActivity().startService(serviceIntent);


    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);

        // Insert the device address to Crypto class
        if(device!=null) {
            Crypto.setDeviceAddress(device.deviceAddress);
            Crypto.setDeviceName(device.deviceName);
        }

        // The owner IP is now known.
        TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(getResources().getString(R.string.group_owner_text)
                + ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
                        : getResources().getString(R.string.no)));


        // InetAddress from WifiP2pInfo struct.
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if (info.groupFormed && info.isGroupOwner) {
            new FileServerAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text))
                    .execute();
        } else if (info.groupFormed) {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            // Iam a client, I know GO's Inetaddress
            SocketInfo.setInetAddress(info.groupOwnerAddress);
            startClientSocket();
            mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
            ((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
                    .getString(R.string.client_text));
        }

        // hide the connect button
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
    }

    /**
     * Updates the UI with device data
     * 
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText("MAC address: " +device.deviceAddress);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        verifyTrust(view);
    }

    void verifyTrust(TextView view){
        SharedPreferences trust = getActivity().getSharedPreferences(WiFiDirectActivity.TRUST, 0);

        StringBuilder status= new StringBuilder();

        String device_key = device.deviceName + device.deviceAddress;
        if(trust.contains(device_key))
        {
            status.append("Number of previous successful connections: ");
            int count = trust.getInt(device_key, 0);
            status.append(count);
            if(count<5)
                view.setTextColor(Color.YELLOW);
            else
                view.setTextColor(Color.GREEN);
        }
        else
        {
            if(trust.contains(device.deviceAddress))
            {
                status.append("The user has changed the device name. Previously, the device you connected to had name "+ trust.getString(device.deviceAddress,"device_name"));
            }
            else {
                status.append("You have not connected with this device previously!");
            }
            view.setTextColor(Color.RED);
        }
        view.setText(status);
    }
    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.status_text);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;
        ServerSocket serverSocket;


        OutputStream out;
        PrintWriter printWriter;

        InputStream inputStream;
        InputStreamReader inputStreamReader;
        BufferedReader bufferedReader;

        PublicKey publicKey_converted;
        String publicKey_exponent;
        String publicKey_modulus;

        Cipher cipher;
        SecretKey aeskey;


        /**
         * @param context
         * @param statusText
         */
        public FileServerAsyncTask(Context context, View statusText) {
            this.context = context;
            this.statusText = (TextView) statusText;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                serverSocket = new ServerSocket(8988);
                serverSocket.setReuseAddress(true);

                Log.d(WiFiDirectActivity.TAG, "Server: Socket opened");
                Socket client = serverSocket.accept();

                // Iam GO, set clients inetaddress.
                SocketInfo.setInetAddress(client.getInetAddress());
                Log.d(WiFiDirectActivity.TAG, "Server: Client connection done");
                out = client.getOutputStream();
                printWriter =  new PrintWriter(out,true);

                inputStream = client.getInputStream();
                inputStreamReader = new InputStreamReader(inputStream);
                bufferedReader = new BufferedReader(inputStreamReader);

                String START_MSG = bufferedReader.readLine();
                Log.d(WiFiDirectActivity.MYTAG,START_MSG);


                publicKey_modulus = bufferedReader.readLine();
                if(publicKey_modulus!=null){
                    Log.d(WiFiDirectActivity.MYTAG,publicKey_modulus);
                }

                publicKey_exponent = bufferedReader.readLine();
                if(publicKey_exponent!=null){
                    Log.d(WiFiDirectActivity.MYTAG,publicKey_exponent);
                }

                publicKey_converted = convertBackToPublicKey();
                Crypto.rsapublickey = publicKey_converted;
                Crypto.rsaprivatekey = null;
                byte[] session_key_byte = encrypt_session_key();
                DataOutputStream dataOutputStream = new DataOutputStream(out);
                dataOutputStream.writeInt(session_key_byte.length);
                if (session_key_byte.length > 0)
                    dataOutputStream.write(session_key_byte, 0, session_key_byte.length);

                printWriter.println(encrypt("Message from Server to Client"));


                //printWriter.println(encrypt("Here's another message"));
                /*try {
                    Log.d(WiFiDirectActivity.MYTAG,"Going to sleep");
                    Thread.sleep(10000);
                }catch (Exception e)
                {

                }
                printWriter.println(encrypt("Last one"));
                */
                //Log.d(WiFiDirectActivity.MYTAG, decrypt(encrypt("Plainttext")));
                /*
                final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();

                Log.d(WiFiDirectActivity.TAG, "server: copying files " + f.toString());
                InputStream inputstream = client.getInputStream();
                copyFile(inputstream, new FileOutputStream(f));
                */
                client.close();
                serverSocket.close();

                return null;
            } catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                statusText.setText("File copied - " + result);
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("file://" + result), "image/*");
                context.startActivity(intent);
            }

            Intent intent = new Intent(context, Demo_Key_Display.class);
            context.startActivity(intent);

        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            statusText.setText("Opening a server socket");
        }


        public PublicKey convertBackToPublicKey()
        {
            try
            {
                BigInteger localBigInteger1 = new BigInteger(this.publicKey_modulus);
                BigInteger localBigInteger2 = new BigInteger(this.publicKey_exponent);
                RSAPublicKeySpec localRSAPublicKeySpec = new RSAPublicKeySpec(localBigInteger1, localBigInteger2);
                PublicKey localPublicKey = KeyFactory.getInstance("RSA").generatePublic(localRSAPublicKeySpec);
                return localPublicKey;
            }
            catch (Exception localException)
            {
                localException.printStackTrace();
            }
            return null;
        }

        public byte[] encrypt_session_key()
        {
            generateAESKey();
            byte[] arrayOfByte1 = this.aeskey.getEncoded();
            try
            {
                Cipher localCipher = Cipher.getInstance("RSA");
                localCipher.init(1, this.publicKey_converted);
                byte[] arrayOfByte2 = localCipher.doFinal(arrayOfByte1);
                return arrayOfByte2;
            }
            catch (Exception localException)
            {
                localException.printStackTrace();
            }
            return null;
        }

        void generateAESKey()
        {
            try
            {
                KeyGenerator localKeyGenerator = KeyGenerator.getInstance("AES");
                localKeyGenerator.init(128);
                this.aeskey = localKeyGenerator.generateKey();
                String str = new String(this.aeskey.getEncoded());
                Crypto.AESKEY = aeskey;
                return;
            }
            catch (Exception localException)
            {
                localException.printStackTrace();
            }
        }

        public String decrypt(String paramString)
        {
            try
            {
                this.cipher = Cipher.getInstance("AES");
                this.cipher.init(Cipher.DECRYPT_MODE, this.aeskey);
                byte[] arrayOfByte = this.cipher.doFinal(Base64.decode(paramString.getBytes(), 0));
                String str1 = new String(arrayOfByte);
                String str2 = new String(Base64.decode(str1, 0));
                return str2;
            }
            catch (Exception localException)
            {
                localException.printStackTrace();
            }
            return null;
        }

        public String encrypt(String paramString)
        {
            try
            {
                this.cipher = Cipher.getInstance("AES");
                paramString.getBytes("UTF-8");
                this.cipher.init(Cipher.ENCRYPT_MODE, this.aeskey);
                String str = Base64.encodeToString(this.cipher.doFinal(Base64.encode(paramString.getBytes(), 0)), 0);
                return str;
            }
            catch (Exception localException)
            {
                localException.printStackTrace();
            }
            return null;
        }


    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);

            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(WiFiDirectActivity.TAG, e.toString());
            return false;
        }
        return true;
    }

}