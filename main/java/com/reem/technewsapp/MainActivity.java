package com.reem.technewsapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    JSONArray idsFull;
    ArrayList<String> ids;
    ArrayList<String> urls;
    ArrayList<String> titles;
    int idCount;

    ProgressBar progressBar;

    SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ids = new ArrayList<String>();
        urls = new ArrayList<String>();
        titles = new ArrayList<String>();
        idCount = 0;

        progressBar = findViewById(R.id.progressBar);
        progressBar.setMax(19);
        progressBar.setProgress(0);

        db = this.openOrCreateDatabase("newsdb", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS articles(id VARCHAR, url VARCHAR, title VARCHAR)");

        Cursor c = db.rawQuery("SELECT * FROM articles", null);

        if (c.moveToFirst()) { //if the database is not empty
            populateList(c);
        }

        else {
            DownloadIDs downloadIDs = new DownloadIDs();
            downloadIDs.execute("https://hacker-news.firebaseio.com/v0/topstories.json");
        }

    }

    public void populateList(Cursor c) {

        progressBar.setVisibility(View.GONE);
        findViewById(R.id.button).setVisibility(View.VISIBLE);

        int urlIndex = c.getColumnIndex("url");
        int titleIndex = c.getColumnIndex("title");

        ListView listView = (ListView) findViewById(R.id.listView);
        ArrayList<String> values = new ArrayList<>();

        c.moveToFirst();
        do {
            values.add(c.getString(titleIndex));
        } while (c.moveToNext());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, values);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener( new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                c.moveToPosition(i);
                String url = c.getString(urlIndex);
                Intent intent = new Intent(getApplicationContext(), WebActivity.class);
                intent.putExtra("com.reem.technewsapp.url", url);
                startActivity(intent);
            }
        });

    }

    public class DownloadIDs extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... strings) {

            String result = "";
            URL url;
            HttpURLConnection urlConnection;
            try{

                url = new URL(strings[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = urlConnection.getInputStream();

                InputStreamReader reader = new InputStreamReader(in);
                BufferedReader r = new BufferedReader(reader);
                StringBuilder resultBuilder = new StringBuilder();
                String line = r.readLine();
                while (line != null) {
                    resultBuilder.append(line).append("\n");
                    line = r.readLine();
                }
                result = resultBuilder.toString();

                return result;

            }catch(Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            try {
                idsFull = new JSONArray(s);
                parseIDs();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public void parseIDs() {

        //some extra code was necessary since not all json objects contain a url, and json objects from the api are constantly changing
        try {
            int lastIndex = ids.size();
            while (ids.size() < 20) {
                ids.add(idsFull.getString(idCount));
                idCount++;
            }
            DownloadURLs downloadURLs = new DownloadURLs();
            downloadURLs.execute(String.valueOf(lastIndex));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class DownloadURLs extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... strings) {

            String result = "";
            URL url;
            HttpURLConnection urlConnection;
            int i = Integer.parseInt(strings[0]);

            while (i < ids.size()) {

                try{
                    url = new URL("https://hacker-news.firebaseio.com/v0/item/" + ids.get(i) + ".json?print=pretty");
                    urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = urlConnection.getInputStream();

                    InputStreamReader reader = new InputStreamReader(in);
                    BufferedReader r = new BufferedReader(reader);
                    StringBuilder resultBuilder = new StringBuilder();
                    String line = r.readLine();
                    while (line != null) {
                        resultBuilder.append(line).append("\n");
                        line = r.readLine();
                    }
                    result = resultBuilder.toString();

                    JSONObject jsonObject = new JSONObject(result);

                    if (jsonObject.has("url") && jsonObject.has("title")) {
                        urls.add(jsonObject.getString("url"));
                        titles.add(jsonObject.getString("title"));
                        Log.i("fetched", i + "/19");
                        progressBar.setProgress(i);
                        i++;
                    }
                    else {
                        ids.remove(i);
                    }

                } catch(Exception e) {
                    e.printStackTrace();
                }

            }

            if (ids.size() < 20) {
                parseIDs();
            }

            return null;

        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if (urls.size() == 20 && titles.size() == 20) {
                for (int i = 0; i < 20; i++) {
                    String query ="INSERT INTO articles (id, url, title) VALUES ('" + ids.get(i) + "', '"
                            + urls.get(i) + "', '" + titles.get(i) + "')";
                    db.execSQL(query);
                }
                Cursor c = db.rawQuery("SELECT * FROM articles", null);
                populateList(c);
            }
        }
    }

    public void deleteStorage(View view) {
        db.close();
        this.deleteDatabase("newsdb");
        Toast.makeText(this, "Local storage will be deleted next time you launch the app or view this page.", Toast.LENGTH_LONG).show();
    }

}