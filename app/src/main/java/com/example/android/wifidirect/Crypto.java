package com.example.android.wifidirect;

import android.util.Base64;

import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;


public class Crypto {

    public static SecretKey AESKEY;
    public static PublicKey rsapublickey;
    public static PrivateKey rsaprivatekey;

    public static String getDeviceAddress() {
        return deviceAddress;
    }

    public static void setDeviceAddress(String deviceAddress) {
        Crypto.deviceAddress = deviceAddress;
    }

    public static String getDeviceName() {
        return deviceName;
    }

    public static void setDeviceName(String deviceName) {
        Crypto.deviceName = deviceName;
    }

    public static String deviceAddress;
    public static String deviceName;

    Cipher cipher;

    public static SecretKey getAESKEY() {
        return AESKEY;
    }

    public static void setAESKEY(SecretKey AESKEY) {
        Crypto.AESKEY = AESKEY;
    }

    public String decrypt(String paramString)
    {
        try
        {
            this.cipher = Cipher.getInstance("AES");
            this.cipher.init(Cipher.DECRYPT_MODE, AESKEY);
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
            this.cipher.init(Cipher.ENCRYPT_MODE, AESKEY);
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
