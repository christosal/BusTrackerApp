package com.example.bustracker_app;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import cn.pedant.SweetAlert.SweetAlertDialog;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconSize;

public class MainActivity extends AppCompatActivity implements Serializable {

    private MapView mapView;
    private FloatingActionMenu floatingActionMenu;
    private FloatingActionButton floatingActionButtonSearch;
    private FloatingActionButton floatingActionButtonDelete;
    Subscriber sub1;
    private String[] busLines;
    private List<String> vechicleIDs = new ArrayList<>(); //Stores all diferent vechicles from the same line in order to show them in map
    public static Handler mHandler;
    public static TextView status;
    private MapboxMap map;
    private GeoJsonSource demo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, "pk.eyJ1Ijoic2FsaWFzIiwiYSI6ImNqdXJram9wOTBkenI0OW9jaTQwYmYya2sifQ.W1B0bmLhhwL2QU8andJ5EQ");
        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        status = findViewById(R.id.status);
        floatingActionMenu = findViewById(R.id.floatingActionMenu);
        floatingActionButtonSearch = findViewById(R.id.floatingActionSearch);
        floatingActionButtonDelete = findViewById(R.id.floatingActionDelete);

        busLines = getResources().getStringArray(R.array.bus_lines);



        /*
        *  A Handler for handling communication between the Subscriber thread
        *  and the UI Thread.
        *  A subscriber can send messages with unique codes in order the UI Thread
        *  to identify what action it has to do.
        *  Codes are: 1 , 2 , 3 , 4 . They are explained below
        *
        *  */
        mHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.arg1==2){
                    new SweetAlertDialog(MainActivity.this,SweetAlertDialog.ERROR_TYPE)
                            .setTitleText((String) msg.obj)
                            .setConfirmText("OK")
                            .show();
                }else if(msg.arg1==1){ // Conected to a Broker info pop up message
                    String[] text = (String[]) msg.obj;
                    new SweetAlertDialog(MainActivity.this,SweetAlertDialog.SUCCESS_TYPE)
                            .setTitleText( text[0])
                            .setContentText(text[1])
                            .setConfirmText("OK")
                            .show();
                    status.setText("Connected");
                    status.setTextColor(getResources().getColor(R.color.main_green_color));
                }else if(msg.arg1==4){ // Disconnected from broker due to button click on UI
                    String[] text = (String[]) msg.obj;
                    new SweetAlertDialog(MainActivity.this,SweetAlertDialog.SUCCESS_TYPE)
                            .setTitleText( text[0])
                            .setContentText(text[1])
                            .setConfirmText("OK")
                            .show();
                }else if (msg.arg1==3){ // Info of Coords
                    Object[] response = (Object[]) msg.obj;
                    String text = (String) response[0];
                    Value value = (Value) response[1];

                    //Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
                    //Log.w("COORDSL",text);
                    if (!vechicleIDs.contains(value.getBus().getVechicleId())){
                        vechicleIDs.add(value.getBus().getVechicleId());
                        initBusLayer(map.getStyle(),value.getBus().getVechicleId());
                    }
                    LatLng position = new LatLng(value.getLatitude(), value.getLongitude());
                    updateMarkerPosition(position,value.getBus().getVechicleId());
                }
            }
        };


        floatingActionButtonSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder mBuilder = new AlertDialog.Builder(MainActivity.this, R.style.Theme_AppCompat_Light_Dialog_Alert);
                mBuilder.setTitle("Επιλέξτε διαδρομή");
                mBuilder.setItems(busLines, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sub1 = new Subscriber(new Topic(busLines[which].substring(0, busLines[which].indexOf(",") - 1)));
                        //Toast.makeText(MainActivity.this, sub1.getPreferedTopic().getBusLine(), Toast.LENGTH_LONG).show();
                        Thread t1 = new Thread(sub1);
                        t1.start();
                    }
                });

                mBuilder.setCancelable(false);
                mBuilder.setNegativeButton("Άκυρο", null);
                mBuilder.show();
                floatingActionMenu.close(true);
            }
        });

        floatingActionButtonDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sub1.disconnect();
                status.setTextColor(getResources().getColor(R.color.red_btn_bg_color));
                status.setText("No Connection");
            }
        });


        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull final MapboxMap mapboxMap) {
                map = mapboxMap;
                mapboxMap.setStyle(Style.MAPBOX_STREETS, new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {

// Map is set up and the style has loaded. Now you can add data or make other map adjustments
                        //initBusLayer(style);
                        //LatLng position = new LatLng(37.983624, 23.732667);
                        //updateMarkerPosition(position);


                    }
                });
            }
        });
    }

    private void initBusLayer(@NonNull Style style,String vechicleID) {

        style.addImage("space-station-icon-id", getBitmapFromVectorDrawable(this, R.drawable.ic_bus));

        style.addSource(new GeoJsonSource(vechicleID));

        style.addLayer(new SymbolLayer(vechicleID, vechicleID).withProperties(
                iconImage("space-station-icon-id"),
                iconIgnorePlacement(true),
                iconAllowOverlap(true),
                iconSize(.7f)
        ));
    }


    private void updateMarkerPosition(LatLng position,String vechicleID) {
// This method is were we update the marker position once we have new coordinates. First we
// check if this is the first time we are executing this handler, the best way to do this is
// check if marker is null;
        if (map.getStyle() != null) {
            demo = map.getStyle().getSourceAs(vechicleID);
            if (demo != null) {
                demo.setGeoJson(FeatureCollection.fromFeature(
                        Feature.fromGeometry(Point.fromLngLat(position.getLongitude(), position.getLatitude()))
                ));
            }
        }

// Lastly, animate the camera to the new position so the user
// wont have to search for the marker and then return.
       // map.animateCamera(CameraUpdateFactory.newLatLng(position));
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(position,13));
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    public static Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = (DrawableCompat.wrap(drawable)).mutate();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }
}
