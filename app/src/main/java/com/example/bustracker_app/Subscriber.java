package com.example.bustracker_app;

import android.content.Context;
import android.os.Message;
import android.util.Log;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import cn.pedant.SweetAlert.SweetAlertDialog;

public class Subscriber implements Runnable, Serializable {

    public HashMap<Integer, Broker> brokers = new HashMap<>(); // <BrokerID,Broker>
    private Topic preferedTopic;
    private boolean isRunning = false;
    //Context to handle android components and others
    private Context context;
    public static final long serialVersionUID = 22149313046710534L;

    /*public static void main(String args[]){
        //Subscriber subscriber1=new Subscriber(new Topic("022"));
        Subscriber subscriber2=new Subscriber(new Topic("026"));
        //Thread t1=new Thread(subscriber1);
        Thread t2=new Thread(subscriber2);
        //t1.start();
        t2.start();
    }*/

    public Subscriber(Topic topic) {
        this.preferedTopic = topic;
    }

    //Connects to the appropriate Broker and then starts listening for updates
    public void register(Broker broker, Topic topic) {
        //System.out.println("Connecting to Broker"+broker.getBrokerID()+": "+broker.getIPv4()+":"+broker.getPort()+"...");
        Socket requestSocket = null;
        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        isRunning = true;

        try {
            requestSocket = new Socket(broker.getIPv4(), broker.getPort());

            //--Sending Message to UI Thread--//
            Message msg = new Message();
            msg.arg1=1;
            String[] text = new String[2];
            text[0]="Επιτυχής Σύνδεση!";
            text[1]="Έγινε σύνδεση στον Broker" + broker.getBrokerID() + ": " + broker.getIPv4() + ":" + broker.getPort();
            msg.obj= text;
            MainActivity.mHandler.sendMessage(msg);
            // --- End of Sending message to UI Thread--- //
            //System.out.println("Connection established! --> Listening for updates...");

            out = new ObjectOutputStream(requestSocket.getOutputStream());
            in = new ObjectInputStream(requestSocket.getInputStream());

            out.writeObject(Subscriber.this);
            out.flush();

        } catch (UnknownHostException unknownHost) {
            System.err.println("You are trying to connect to an unknown host!");
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
        while (isRunning) {
            try {
                Object recievedValue = in.readObject();
                if (recievedValue instanceof Value) {
                    //System.out.println("Recieved from Broker" + broker.getBrokerID() + ": " + broker.getIPv4() + ":" + broker.getPort() + "---> Lat:" + ((Value) recievedValue).getLatitude() + " , Long:" + ((Value) recievedValue).getLongitude());
                    Message msg = new Message();
                    msg.arg1=3;
                    Object[] responseObject = new Object[2];
                    String text;
                    text = ("Received from Broker" + broker.getBrokerID() + ": " + broker.getIPv4() + ":" + broker.getPort() + "---> Lat:" + ((Value) recievedValue).getLatitude() + " , Long:" + ((Value) recievedValue).getLongitude());
                    responseObject[0]=text;
                    responseObject[1]=recievedValue;
                    msg.obj= responseObject;
                    MainActivity.mHandler.sendMessage(msg);
                } else if (recievedValue.equals("Stopped")) {
                    Message msg = new Message();
                    msg.arg1=2;
                    msg.obj="Η μετάδοση σταμάτησε";
                    MainActivity.mHandler.sendMessage(msg);
                    //System.out.println("Received from Broker" + broker.getBrokerID() + ": " + broker.getIPv4() + ":" + broker.getPort() + "---> Transmission stopped working ");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            in.close();
            out.close();
            requestSocket.close();
            Message msg = new Message();
            msg.arg1=4;
            String[] text = new String[2];
            text[0]="Αποσύνδεση";
            text[1]="Έγινε αποσύνδεση απο τον Broker" + broker.getBrokerID() + ": " + broker.getIPv4() + ":" + broker.getPort();
            msg.obj= text;
            MainActivity.mHandler.sendMessage(msg);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

    }


    public void visualizeData(Topic topic, Value value) {

    }

    public void disconnect(){
        isRunning=false;
    }

    //Connects to MasterServer in order to receive a list of all running brokers so to find the appropriate one
    public void connectToMasterServer(int port, String message) {
        Socket requestSocket = null;
        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        try {
            requestSocket = new Socket("192.168.2.3", port);

            out = new ObjectOutputStream(requestSocket.getOutputStream());
            in = new ObjectInputStream(requestSocket.getInputStream());


            out.writeObject(message);
            out.flush();

            try {
                brokers = (HashMap<Integer, Broker>) in.readObject();
                //String obj = (String) in.readObject();
                //Message msg = new Message();
                //msg.arg1=2;
                //msg.obj= obj;
                //MainActivity.mHandler.sendMessage(msg);
            } catch (Exception e) {
                Log.d("NONONONONO", brokers.size() + "");
                e.printStackTrace();
            }
            Log.d("HEEYYYY", brokers.size() + "");

        } catch (UnknownHostException unknownHost) {
            new SweetAlertDialog(context)
                    .setTitleText("You are trying to connect to an unknown host!")
                    .show();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } finally {
            try {
                if (in!=null && out!=null){
                    in.close();
                    out.close();
                }

                //System.out.println("Disconnected");
                if (requestSocket!=null){
                    requestSocket.close();
                }
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    /*
        Searching brokers list (taken through connectToMasterServer()) and returns the Broker who is responsible for
        the preferedTopic
    */
    private Broker findMyBroker() {
        for (Integer brokerid : brokers.keySet()) {
            for (Topic topic : brokers.get(brokerid).getResponsibilityLines()) {
                if (topic.getBusLine().equals(preferedTopic.getBusLine())) {
                    return brokers.get(brokerid);
                }
            }
        }
        return null;
    }

    @Override
    public void run() {
        connectToMasterServer(8085, "connect");
        if (brokers.size() == 0) {
            Log.d("msg", "No brokers available");
            Message msg = new Message();
            msg.arg1=2;
            msg.obj="Δεν υπάρχουν διαθέσιμοι Brokers";
            MainActivity.mHandler.sendMessage(msg);

        } else {
            Broker myBroker = findMyBroker();
            if (myBroker == null) {
                new SweetAlertDialog(context, SweetAlertDialog.ERROR_TYPE)
                        .setTitleText("Ούπς")
                        .setContentText("Δεν υπάρχει διαθέσιμος Broker για αυτή τη γραμμή:" + preferedTopic.getBusLine())
                        .setConfirmText("Oκ")
                        .show();
            } else {
                for (Integer brokerid : brokers.keySet()) {
                    Log.d("...", "Broker" + brokerid + ":" + brokers.get(brokerid).getIPv4() + ":" + brokers.get(brokerid).getPort() + " has: ");
                    for (Topic topic : brokers.get(brokerid).getResponsibilityLines()) {
                        System.out.print(topic.getBusLine() + " , ");
                    }
                    System.out.println();
                }
                register(findMyBroker(), preferedTopic);
            }
        }
    }

    public Topic getPreferedTopic() {
        return preferedTopic;
    }
}
