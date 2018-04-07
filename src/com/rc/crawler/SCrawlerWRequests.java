package com.rc.crawler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONObject;

/**
 * Handles get and post request for the sCrawler W website
 */
class SCrawlerWRequests {

    static String postRequest(String urlStr, HashMap<String, Object> params) throws Exception {
        URL url = new URL(urlStr);

        StringBuilder postData = new StringBuilder();
        for (String param : params.keySet()) {
            if (postData.length() != 0) {
                postData.append('&');
            }
            postData.append(URLEncoder.encode(param, "UTF-8"));
            postData.append('=');
            postData.append(URLEncoder.encode(String.valueOf(params.get(param)), "UTF-8"));
        }
        byte[] postDataBytes = postData.toString().getBytes("UTF-8");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
        conn.setDoOutput(true);
        conn.getOutputStream().write(postDataBytes);
        Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (int c; (c = in.read()) >= 0; )
            sb.append((char) c);
        String response = sb.toString();
        System.out.println(response);
        return response;
    }


    /**
     * Sends a get request to the sCrawler W website
     */
    static String getRequest(String urlStr, ArrayList<String> params) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(urlStr);
        for (String param : params) {
            sb.append(param).append("/");
        }
        URL obj = new URL(sb.toString());
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        // optional default is GET
        con.setRequestMethod("GET");
        //add request header
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'GET' request to URL : " + urlStr);
        System.out.println("Response Code : " + responseCode);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        //print in String
        System.out.println(response.toString());
        //Read JSON response and print
        return response.toString();
    }
}



