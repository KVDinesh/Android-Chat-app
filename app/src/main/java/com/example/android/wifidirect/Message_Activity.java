package com.example.android.wifidirect;

import android.app.Activity;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;


public class Message_Activity extends Activity {

    ServerSocket servSocket;
    private ChatArrayAdapter chatArrayAdapter;
    private ListView listView;
    private EditText chatText;
    private Button buttonSend;
    private Handler uiHandle= new Handler();

    Intent intent;
    private boolean side = false;
    Crypto crypto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_);

        crypto = new Crypto();
        buttonSend = (Button) findViewById(R.id.buttonSend);

        listView = (ListView) findViewById(R.id.listView1);

        chatArrayAdapter = new ChatArrayAdapter(getApplicationContext(), R.layout.activity_chat_singlemessage);
        listView.setAdapter(chatArrayAdapter);

        chatText = (EditText) findViewById(R.id.chatText);
        chatText.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    return sendChatMessage();
                }
                return false;
            }
        });
        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                sendChatMessage();
            }
        });

        listView.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        listView.setAdapter(chatArrayAdapter);

        //to scroll the list view to bottom on data change
        chatArrayAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                listView.setSelection(chatArrayAdapter.getCount() - 1);
            }
        });

        WiFiDirectActivity.visitedmessage = true;
        // Listener
        new Thread(new Runnable() {
            public void run() {
                Socket sock1= null;
                DataInputStream din= null;
                servSocket= null;
                try {
                    servSocket= new ServerSocket(10000);
                    Log.v("Test", "Server Socket port: " + Integer.toString(servSocket.getLocalPort()));
                } catch (IOException e) {
                    Log.v("Test", ""+e.getMessage());
                    e.printStackTrace();
                }

                while(true) {
                    try {
                        if(!servSocket.isClosed())
                            sock1= servSocket.accept();
                        else
                            break;
                        Log.v("Test", "sock1: "+sock1.getInetAddress().getHostAddress()+sock1.isConnected()+Integer.toString(sock1.getLocalPort())+Integer.toString(sock1.getPort()));
                        BufferedReader br= new BufferedReader(new InputStreamReader(sock1.getInputStream()));
                        String str= br.readLine();
                        addIncomingMessages(str);
                        Log.v("Test", "Received msg: "+str);

                    }
                    catch (Exception e) {
                        Log.v("Test", ""+e.getMessage());
                        e.printStackTrace();
                        return;
                    }
                    finally {
                        if (din!= null)
                            try {
                                din.close();
                            } catch (IOException e) {
                                Log.v("Test", ""+e.getMessage());
                                e.printStackTrace();
                            }
                        if(sock1!=null)
                            try {
                                sock1.close();
                            } catch (IOException e) {
                                Log.v("Test", ""+e.getMessage());
                                e.printStackTrace();
                            }
                    }
                }
            }
        }).start();

    }

    private void addIncomingMessages(String msg)
    {
        final String message = crypto.decrypt(msg);
        uiHandle.post(new Runnable() {
            public void run() {
                chatArrayAdapter.add(new ChatMessage(true, message));
            }
        });
    }

    private boolean sendChatMessage(){
        String message = chatText.getText().toString();
        if(!message.isEmpty()) {
            sendMessage(chatText.getText().toString());
            chatArrayAdapter.add(new ChatMessage(false, chatText.getText().toString()));
            chatText.setText("");
        }
        return true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            if (servSocket != null)
                servSocket.close();
        }catch (Exception e)
        {
           Log.d("Test", e.getMessage());
        }

        Intent myintent = new Intent();
        myintent.setAction("MyBroadcast");
        sendBroadcast(myintent);
        onBackPressed();
    }

    @Override
    public void onBackPressed() {
        //super.onBackPressed();
        //Log.d(WiFiDirectActivity.MYTAG,"Back pressed hijack");
        Intent mIntent = new Intent(this, WiFiDirectActivity.class);
        startActivity(mIntent);
    }


    public void sendMessage(String msg1) {
        /*EditText et= (EditText) findViewById(R.id.editText1);
        final String msg= et.getText().toString();
        et.setText("");
        */
        final String msg=crypto.encrypt(msg1);
        new Thread(new Runnable() {
            public void run() {
                Socket sock= null;
                try {
                    sock= new Socket(SocketInfo.getInetAddress(), 10000);
                    Log.v("Test", "send sock: "+sock.getInetAddress().getHostAddress()+sock.isConnected()+Integer.toString(sock.getPort()));
                } catch (UnknownHostException e) {
                    Log.v("Test", ""+e.getMessage());
                    e.printStackTrace();
                } catch (IOException e) {
                    Log.v("Test", ""+e.getMessage());
                    e.printStackTrace();
                }
                try {
                    PrintWriter out= new PrintWriter(sock.getOutputStream(),true);
                    out.println(msg);
                    //updateTextView("<font color=\"#800000\"> <b>Sent: </b></font>" + msg);
                }
                catch (IOException e) {
                    Log.v("Test", ""+e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_message_, menu);
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
