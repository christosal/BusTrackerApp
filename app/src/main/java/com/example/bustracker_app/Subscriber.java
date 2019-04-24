package com.example.bustracker_app;

import android.content.Context;
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

    /*public static void main(String args[]){
        //Subscriber subscriber1=new Subscriber(new Topic("022"));
        Subscriber subscriber2=new Subscriber(new Topic("026"));
        //Thread t1=new Thread(subscriber1);
        Thread t2=new Thread(subscriber2);
        //t1.start();
        t2.start();
    }*/

    public Subscriber(Topic topic, Context context) {
        this.preferedTopic = topic;
        this.context = context;
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
            new SweetAlertDialog(context, SweetAlertDialog.SUCCESS_TYPE)
                    .setTitleText("Επιτυχής Σύνδεση!")
                    .setContentText("Έγινε σύνδεση στον Broker" + broker.getBrokerID() + ": " + broker.getIPv4() + ":" + broker.getPort() + "...")
                    .setConfirmText("Οκ")
                    .show();
            //System.out.println("Connection established! --> Listening for updates...");
            MainActivity.status.setText("Connected");
            MainActivity.status.setTextColor((-500145));
            out = new ObjectOutputStream(requestSocket.getOutputStream());
            in = new ObjectInputStream(requestSocket.getInputStream());

            out.writeObject(this);
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
                    System.out.println("Recieved from Broker" + broker.getBrokerID() + ": " + broker.getIPv4() + ":" + broker.getPort() + "---> Lat:" + ((Value) recievedValue).getLatitude() + " , Long:" + ((Value) recievedValue).getLongitude());
                } else if (recievedValue.equals("Stopped")) {
                    System.out.println("Recieved from Broker" + broker.getBrokerID() + ": " + broker.getIPv4() + ":" + broker.getPort() + "---> Transmission stopped working ");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            in.close();
            out.close();
            //System.out.println("Disconnected");
            requestSocket.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

    }


    public void visualizeData(Topic topic, Value value) {

    }


    //Connects to MasterServer in order to receive a list of all running brokers so to find the appropriate one
    public void connectToMasterServer(int port, String message) {
        Socket requestSocket = null;
        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        try {
            requestSocket = new Socket("192.168.1.85", port);

            out = new ObjectOutputStream(requestSocket.getOutputStream());
            in = new ObjectInputStream(requestSocket.getInputStream());


            out.writeObject(message);
            out.flush();

            try {
                brokers = (HashMap<Integer, Broker>) in.readObject();
                wait(5000);
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
                in.close();
                out.close();
                //System.out.println("Disconnected");
                requestSocket.close();
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
                    System.out.println("");
                }
                register(findMyBroker(), preferedTopic);
            }
        }
    }

    public Topic getPreferedTopic() {
        return preferedTopic;
    }
}
