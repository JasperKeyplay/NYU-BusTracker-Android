package com.palsulich.nyubustracker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

public class MainActivity extends Activity {

    boolean created = false;

    String charset = "UTF-8";
    String agencies = "72";
    String query = makeQuery("agencies", agencies, charset);

    private String stopsURL = "http://api.transloc.com/1.2/stops.json?" + query;
    private String routesURL = "http://api.transloc.com/1.2/routes.json?" + query;
    private String segmentsURL = "http://api.transloc.com/1.2/segments.json?" + query;
    private String vehiclesURL = "http://api.transloc.com/1.2/vehicles.json?" + query;
    private String timesURL = "https://s3.amazonaws.com/nyubustimes/1.0/";
    private String versionURL = "https://s3.amazonaws.com/nyubustimes/1.0/version.json";

    private String makeQuery(String param, String value, String charset) {
        try {
            return String.format(param + "=" + URLEncoder.encode(agencies, charset));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "";
    }

    private static final String TAG_DATA = "data";
    private static final String TAG_LONG_NAME = "long_name";
    private static final String TAG_LOCATION = "location";
    private static final String TAG_LAT = "lat";
    private static final String TAG_LNG = "lng";
    private static final String TAG_HEADING = "heading";
    private static final String TAG_STOP_NAME = "name";
    private static final String TAG_STOP_ID = "stop_id";
    private static final String TAG_ROUTES = "routes";
    private static final String TAG_ROUTE = "route";
    private static final String TAG_ROUTE_ID = "route_id";
    private static final String TAG_WEEKDAY = "Weekday";
    private static final String TAG_FRIDAY = "Friday";
    private static final String TAG_WEEKEND = "Weekend";
    private static final String TAG_VEHICLE_ID = "vehicle_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        if (!created) {
            created = true;
            final ListView listView = (ListView) findViewById(R.id.mainActivityList);

            final BusManager sharedManager = BusManager.getBusManager();
            if (!sharedManager.hasStops() && !sharedManager.hasRoutes()) {
                JSONArray jStops = null;
                JSONArray jRoutes = null;
                JSONArray jSegments = null;
                JSONArray jVehicles = null;

                FileGrabber mFileGrabber = new FileGrabber(getCacheDir());

                // getting JSON string from URL
                JSONObject stopsJson = mFileGrabber.getJSON(stopsURL, "stopsJSON");
                JSONObject routesJson = mFileGrabber.getJSON(routesURL, "routesJSON");
//            JSONObject segmentsJson = mFileGrabber.getJSON(segmentsURL, "segmentsJSON");
                JSONObject vehiclesJson = mFileGrabber.getJSON(vehiclesURL, "vehiclesJSON");
                JSONObject versionJson = mFileGrabber.getJSON(versionURL, "versionJSON");

                try {
                    jStops = stopsJson.getJSONArray(TAG_DATA);
                    Log.v("JSONDebug", "Number of stops: " + jStops.length());
                    for (int i = 0; i < jStops.length(); i++) {
                        JSONObject stopObject = jStops.getJSONObject(i);
                        String stopID = stopObject.getString(TAG_STOP_ID);
                        String stopName = stopObject.getString(TAG_STOP_NAME);
                        JSONObject location = stopObject.getJSONObject(TAG_LOCATION);
                        String stopLat = location.getString(TAG_LAT);
                        String stopLng = location.getString(TAG_LNG);
                        JSONArray stopRoutes = stopObject.getJSONArray(TAG_ROUTES);
                        String[] routes = new String[stopRoutes.length()];
                        String routesString = "";
                        for (int j = 0; j < stopRoutes.length(); j++) {
                            routes[j] = stopRoutes.getString(j);
                            routesString += routes[j];
                            if (j != stopRoutes.length() - 1) routesString += ", ";
                        }
                        Stop s = new Stop(stopName, stopLat, stopLng, stopID, routes);
                        sharedManager.addStop(s);
                        Log.v("JSONDebug", "Stop name: " + stopName + ", stop ID: " + stopID + ", routes: " + routesString);
                    }

                    jRoutes = routesJson.getJSONObject(TAG_DATA).getJSONArray("72");
                    for (int j = 0; j < jRoutes.length(); j++) {
                        JSONObject routeObject = jRoutes.getJSONObject(j);
                        String routeLongName = routeObject.getString(TAG_LONG_NAME);
                        String routeID = routeObject.getString(TAG_ROUTE_ID);
                        sharedManager.addRoute(new Route(routeLongName, routeID));
                        Log.v("JSONDebug", "Route name: " + routeLongName + " | ID:" + routeID + " | Number of stops: " + sharedManager.getRouteByID(routeID).getStops().size());
                    }

                    ArrayList<Stop> stops = sharedManager.getStops();
                    Log.v("Debugging", "Looking for times for " + stops.size() + " stops.");
                    JSONArray jVersion = versionJson.getJSONArray("versions");
                    for (int j = 0; j < jVersion.length(); j++) {
                        JSONObject stopObject = jVersion.getJSONObject(j);
                        String file = stopObject.getString("file");
                        Log.v("Debugging", "Looking for times at " + timesURL + file);
                        JSONObject timesJson = mFileGrabber.getJSON(timesURL + file, file);
                        JSONObject routes = timesJson.getJSONObject(TAG_ROUTES);
                        Stop s = sharedManager.getStopByID(file.substring(0, file.indexOf(".")));
                        for (int i = 0; i < s.routes.size(); i++) {
                            if (routes.has(s.routes.get(i).routeID)) {
                                JSONObject routeTimes = routes.getJSONObject(s.routes.get(i).routeID);
                                if (routeTimes.has(TAG_WEEKDAY)) {
                                    JSONArray weekdayTimesJson = routeTimes.getJSONArray(TAG_WEEKDAY);
                                    String[] weekdayTimes = new String[weekdayTimesJson.length()];
                                    if (weekdayTimesJson != null) {
                                        for (int k = 0; k < weekdayTimes.length; k++) {
                                            weekdayTimes[k] = weekdayTimesJson.getString(k);
                                        }
                                        String weekdayRoute = routeTimes.getString(TAG_ROUTE);
                                        s.addTime(weekdayRoute.substring(weekdayRoute.indexOf("Route ") + "Route ".length()), "Weekday", weekdayTimes);

                                    }
                                }
                                if (routeTimes.has(TAG_FRIDAY)) {
                                    JSONArray fridayTimesJson = routeTimes.getJSONArray(TAG_FRIDAY);
                                    String[] fridayTimes = new String[fridayTimesJson.length()];
                                    if (fridayTimesJson != null) {
                                        for (int k = 0; k < fridayTimes.length; k++) {
                                            fridayTimes[k] = fridayTimesJson.getString(k);
                                        }
                                        String fridayRoute = routeTimes.getString(TAG_ROUTE);
                                        s.addTime(fridayRoute.substring(fridayRoute.indexOf("Route ") + "Route ".length()), "Friday", fridayTimes);

                                    }
                                }
                                if (routeTimes.has(TAG_WEEKEND)) {
                                    JSONArray weekendTimesJson = routeTimes.getJSONArray(TAG_WEEKEND);
                                    String[] weekendTimes = new String[weekendTimesJson.length()];
                                    if (weekendTimesJson != null) {
                                        for (int k = 0; k < weekendTimes.length; k++) {
                                            weekendTimes[k] = weekendTimesJson.getString(k);
                                        }
                                        String weekendRoute = routeTimes.getString(TAG_ROUTE);
                                        s.addTime(weekendRoute.substring(weekendRoute.indexOf("Route ") + "Route ".length()), "Weekend", weekendTimes);

                                    }
                                }
                            }
                        }

                    }

                    jVehicles = vehiclesJson.getJSONObject(TAG_DATA).getJSONArray("72");
                    for (int j = 0; j < jVehicles.length(); j++) {
                        JSONObject busObject = jVehicles.getJSONObject(j);
                        JSONObject busLocation = busObject.getJSONObject(TAG_LOCATION);
                        String busLat = busLocation.getString(TAG_LAT);
                        String busLng = busLocation.getString(TAG_LNG);
                        String busRoute = busObject.getString(TAG_ROUTE_ID);
                        String vehicleID = busObject.getString(TAG_VEHICLE_ID);
                        String busHeading = busObject.getString(TAG_HEADING);
                        sharedManager.addBus(new Bus(vehicleID).setHeading(busHeading).setLocation(busLat, busLng).setRoute(busRoute));
                        Log.v("JSONDebug", "Bus ID: " + vehicleID + " | Heading: " + busHeading + " | (" + busLat + ", " + busLng + ")");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            ArrayAdapter<String> mAdapter =
                    new ArrayAdapter<String>(this,
                            android.R.layout.simple_list_item_1,
                            sharedManager.getRoutesAsArray());
            listView.setOnItemClickListener(new OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    String routeName = listView.getItemAtPosition(position).toString();
                    Log.v("Debugging", "Clicked on route:" + routeName);
                    Intent myIntent = new Intent(MainActivity.this, RouteActivity.class);
                    myIntent.putExtra("route_name", routeName);
                    startActivity(myIntent);
                }
            });
            listView.setAdapter(mAdapter);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


}
