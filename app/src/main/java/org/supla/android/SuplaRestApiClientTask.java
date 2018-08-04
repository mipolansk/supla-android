package org.supla.android;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.JsonReader;

import org.json.JSONTokener;
import org.supla.android.lib.SuplaClient;
import org.supla.android.lib.SuplaOAuthToken;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;


public class SuplaRestApiClientTask extends AsyncTask {

    private SuplaOAuthToken Token;
    private static final String log_tag = "SuplaRestApiClientTask";

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        Token = SuplaApp.getApp().RegisterRestApiClientTask(this);
    }

    @Override
    protected void onPostExecute(Object o) {
        super.onPostExecute(o);
        SuplaApp.getApp().UnregisterRestApiClientTask(this);
    }


    public synchronized void setToken(SuplaOAuthToken token) {
        Token = token == null ? null : new SuplaOAuthToken(token);
        notify();
    }

    public synchronized SuplaOAuthToken getTokenWhenIsAlive() {
        return Token != null && Token.isAlive() ? new SuplaOAuthToken(Token) : null;
    }

    private void makeTokenRequest() {
        if (Token != null && Token.isAlive()) return;

        SuplaClient client = SuplaApp.getApp().getSuplaClient();
        if (client == null) {
            Trace.d(log_tag, "Client is not available");
            return;
        }


        client.OAuthTokenRequest();

        synchronized (this) {
            try {
                this.wait(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private class ApiRequestResult {

        private Object JObj;
        private int Code;
        private int TotalCount;

        ApiRequestResult(Object jobj, int code, int totalCount) {
            JObj = jobj;
            Code = code;
            TotalCount = totalCount;
        }

        public Object getJObj() {
            return JObj;
        }

        public int getCode() {
            return Code;
        }

        public int getTotalCount() {
            return TotalCount;
        }
    }

    private ApiRequestResult apiRequest(boolean retry, String endpint) {

        makeTokenRequest();
        if (Token == null || Token.getUrl() == null) {
            return null;
        }

        URL url = Token.getUrl();
        Uri.Builder builder = new Uri.Builder();

        builder.scheme(url.getProtocol())
                .encodedAuthority(url.getAuthority())
                .path(url.getPath())
                .appendPath("api")
                .appendPath("2.2.0")
                .appendEncodedPath(endpint);


        try {
            url = new URL(builder.build().toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }

        if (url == null) {
            return null;
        }

        HttpsURLConnection conn = null;
        try {
            conn = (HttpsURLConnection)url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        SSLContext sc;
        try {
            sc = SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
        try {
            sc.init(null, null, new java.security.SecureRandom());
        } catch (KeyManagementException e) {
            e.printStackTrace();
            return null;
        }
        conn.setSSLSocketFactory(sc.getSocketFactory());

        try {
            conn.setRequestMethod("GET");
        } catch (ProtocolException e) {
            e.printStackTrace();
            return null;
        }

        ApiRequestResult result = null;


        try {
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);

            conn.addRequestProperty("Authorization", "Bearer "+Token.getToken());

            conn.connect();
            try
            {
                //Trace.d(log_tag, "CODE: "+conn.getResponseCode());
                //Trace.d(log_tag, "URL: "+url.toString());

                int TotalCount = 0;
                try
                {
                    TotalCount = Integer.parseInt(conn.getHeaderField("X-Total-Count"));
                } catch (NumberFormatException e) {
                    TotalCount = 0;
                }

                JsonReader reader = new JsonReader(
                        new InputStreamReader(conn.getResponseCode() == 200 ?
                                conn.getInputStream() : conn.getErrorStream(),"UTF-8"));

                InputStream ins = new BufferedInputStream(conn.getResponseCode() == 200 ?
                        conn.getInputStream() : conn.getErrorStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(ins));

                String inputLine = "";
                StringBuffer sb = new StringBuffer();

                while ((inputLine = br.readLine()) != null) {
                    sb.append(inputLine);
                }

                //Trace.d(log_tag, sb.toString());
                Object obj = new JSONTokener(sb.toString()).nextValue();
                result = new ApiRequestResult(obj, conn.getResponseCode(), TotalCount);


            } finally {
                conn.disconnect();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (result != null && result.getCode() == 401 && retry) {
            setToken(null);
            result = apiRequest(false, endpint);
        }

        return result;
    }

    private ApiRequestResult apiRequest(String endpint) {

        return apiRequest(true, endpint);
    }

    @Override
    protected Object doInBackground(Object[] objects) {

        ApiRequestResult r = apiRequest("channels/2944/measurement-logs");
        return null;

    }
}