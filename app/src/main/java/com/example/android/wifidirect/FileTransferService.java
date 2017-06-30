// Copyright 2011 Google Inc. All Rights Reserved.

package com.example.android.wifidirect;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * A service that process each file transfer request i.e Intent by opening a
 * socket connection with the WiFi Direct Group Owner and writing the file
 */
public class FileTransferService extends IntentService {

    private static final int SOCKET_TIMEOUT = 5000;
    public static final String ACTION_SEND_FILE = "com.example.android.wifidirect.SEND_FILE";
    public static final String EXTRAS_FILE_PATH = "file_url";
    public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
    public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";

    OutputStream out;
    PrintWriter printWriter;

    InputStream inputStream;
    InputStreamReader inputStreamReader;
    BufferedReader bufferedReader;

    PrivateKey privateKey;
    PublicKey publicKey;
    RSAPublicKeySpec rsaPublicKeySpec;
    String encrypted_session_key_string;
    SecretKey aes_key;

    Cipher cipher;
    Boolean messaging_in_progress;

    public static Socket socket;

    Crypto crypto;


    public FileTransferService(String name) {
        super(name);
        messaging_in_progress = true;

    }

    public FileTransferService() {
        super("FileTransferService");
    }

    /*
     * (non-Javadoc)
     * @see android.app.IntentService#onHandleIntent(android.content.Intent)
     */
    @Override
    protected void onHandleIntent(Intent intent) {

        crypto = new Crypto();

        Context context = getApplicationContext();
        if (intent.getAction().equals(ACTION_SEND_FILE)) {
            String fileUri = intent.getExtras().getString(EXTRAS_FILE_PATH);
            String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
            socket = new Socket();
            int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);

            try {
                Log.d(WiFiDirectActivity.TAG, "Opening client socket - ");
                socket.bind(null);
                socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

                Log.d(WiFiDirectActivity.TAG, "Client socket - " + socket.isConnected());

                out = socket.getOutputStream();
                printWriter =  new PrintWriter(out,true);

                inputStream = socket.getInputStream();
                inputStreamReader = new InputStreamReader(inputStream);
                bufferedReader = new BufferedReader(inputStreamReader);


                printWriter.println("KEY_EXCHANGE_STARTED");


                generateRSA_Client();

                printWriter.println(rsaPublicKeySpec.getModulus());
                printWriter.println(rsaPublicKeySpec.getPublicExponent());

                DataInputStream dataInputStream = new DataInputStream(inputStream);
                int j = dataInputStream.readInt();
                byte[] session_key_byte = new byte[j];
                if (j > 0)
                    dataInputStream.readFully(session_key_byte);

                decryptSessionKey(session_key_byte);
                Log.d(WiFiDirectActivity.MYTAG,"AES Client "+new String(aes_key.getEncoded()));

                //Log.d(WiFiDirectActivity.MYTAG,decrypt(bufferedReader.readLine()));

                // Set AES key of Crypto class
                crypto.setAESKEY(aes_key);

                Log.d(WiFiDirectActivity.MYTAG, crypto.decrypt(bufferedReader.readLine()));
                Intent in = new Intent(context, Demo_Key_Display.class);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(in);

                /*
                String in;
                while (socket.isConnected()){
                    if((in=bufferedReader.readLine())!=null) {
                        Log.d(WiFiDirectActivity.MYTAG, decrypt(in));
                    }
                }
                */

                //Log.d(WiFiDirectActivity.MYTAG,"Disconnected from client");


                /*
                OutputStream stream = socket.getOutputStream();
                ContentResolver cr = context.getContentResolver();
                InputStream is = null;
                try {
                    is = cr.openInputStream(Uri.parse(fileUri));
                } catch (FileNotFoundException e) {
                    Log.d(WiFiDirectActivity.TAG, e.toString());
                }
                DeviceDetailFragment.copyFile(is, stream);
                */
                //Log.d(WiFiDirectActivity.TAG, "Client: Data written");
            } catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
            } finally {
                if (socket != null) {
                    if (socket.isConnected()) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            // Give up
                                e.printStackTrace();
                        }
                    }

                }



            }

        }
    }

    public void generateRSA_Client()
    {
        try
        {
            KeyPairGenerator localKeyPairGenerator = KeyPairGenerator.getInstance("RSA");
            localKeyPairGenerator.initialize(1024);
            KeyPair localKeyPair = localKeyPairGenerator.genKeyPair();
            this.publicKey = localKeyPair.getPublic();
            this.privateKey = localKeyPair.getPrivate();
            this.rsaPublicKeySpec = ((RSAPublicKeySpec) KeyFactory.getInstance("RSA").getKeySpec(this.publicKey, RSAPublicKeySpec.class));
            Crypto.rsaprivatekey = privateKey;
            Crypto.rsapublickey = publicKey;
            return;
        }
        catch (Exception localException)
        {
            Log.e("KeyGen", "RSA key pair error");
        }
    }

    public void decryptSessionKey(byte[] paramArrayOfByte)
    {
        try
        {
            Cipher localCipher = Cipher.getInstance("RSA");
            localCipher.init(Cipher.DECRYPT_MODE,privateKey);
            aes_key = getAESKey(localCipher.doFinal(paramArrayOfByte));
            return;
        }
        catch (Exception localException)
        {
            localException.printStackTrace();
        }
    }

    public SecretKey getAESKey(byte[] paramArrayOfByte)
    {
        try
        {
            SecretKeySpec localSecretKeySpec = new SecretKeySpec(paramArrayOfByte, 0, paramArrayOfByte.length, "AES");
            return localSecretKeySpec;
        }
        catch (Exception localException)
        {
            localException.printStackTrace();
        }
        return null;
    }

    public String decrypt(String paramString)
    {
        try
        {
            this.cipher = Cipher.getInstance("AES");
            this.cipher.init(Cipher.DECRYPT_MODE, aes_key);
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
            this.cipher.init(Cipher.ENCRYPT_MODE, aes_key);
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
