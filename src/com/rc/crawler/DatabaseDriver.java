package com.rc.crawler;


import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;
import org.joda.time.DateTime;
import org.openqa.selenium.Cookie;
import sun.rmi.runtime.Log;

import java.io.FileNotFoundException;
import java.sql.*;
import java.util.*;

/**
 * Created by rafaelcastro on 9/7/17.
 * Handles all the queries to the database
 */
class DatabaseDriver {

    private Connection myConnection;
    private GUILabelManagement guiLabelManagement;

    DatabaseDriver(GUILabelManagement guiLabelManagement) {
        this.guiLabelManagement = guiLabelManagement;
        try {
            myConnection = DriverManager.getConnection("jdbc:mysql://sql9.freemysqlhosting.net:3306/sql9212904", "sql9212904",
                    "y53hKm9BQV");
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }

    }

    /**
     * Returns all proxies stored in the db
     *
     * @return HashMap with all the proxies
     */
    HashMap<Proxy, Map<SearchEngine.SupportedSearchEngine, Set<Cookie>>> getAllUnlockedProxies() {
        HashMap<Proxy, Map<SearchEngine.SupportedSearchEngine, Set<Cookie>>> result = new HashMap<>();
        Statement statement;
        try {
            statement = myConnection.createStatement();

            ResultSet res = statement.executeQuery("SELECT * FROM sql9212904.proxies WHERE unlocked");
            //Process result
            while (res.next()) {
                SearchEngine.SupportedSearchEngine engine = null;
                Set<Cookie> cookies = new HashSet<>();
                //Get data from database
                String ip = res.getString("ip");
                int port = res.getInt("port");
                String cookieString = res.getString("cookies");
                String searchEngine = res.getString("search_engine");

                Proxy currProxy = new Proxy(ip, port);
                if (searchEngine.equalsIgnoreCase(SearchEngine.SupportedSearchEngine.GoogleScholar.name())) {
                    engine = SearchEngine.SupportedSearchEngine.GoogleScholar;
                }
                if (searchEngine.equalsIgnoreCase(SearchEngine.SupportedSearchEngine.MicrosoftAcademic.name())) {
                    engine = SearchEngine.SupportedSearchEngine.MicrosoftAcademic;
                }
                Scanner scanner = new Scanner(cookieString);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    String[] cookieInfo = line.split(";");
                    String name = cookieInfo[0];
                    String value = cookieInfo[1];
                    String domain = cookieInfo[2];
                    String path = cookieInfo[3];
                    java.util.Date expiry = null;
                    if (cookieInfo[4] != null) {
                        expiry = new java.util.Date(cookieInfo[4]);
                    }
                    Boolean isSecure = Boolean.valueOf(cookieInfo[4]);
                    Cookie ck = new Cookie(name, value, domain, path, expiry, isSecure);
                    cookies.add(ck);
                }
                if (!result.containsKey(currProxy)) {
                    Map<SearchEngine.SupportedSearchEngine, Set<Cookie>> map = new HashMap<>();
                    map.put(engine, cookies);
                    result.put(currProxy, map);
                } else {
                    Map<SearchEngine.SupportedSearchEngine, Set<Cookie>> map = result.get(currProxy);
                    map.put(engine, cookies);
                    result.put(currProxy, map);
                }

            }
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return result;
    }

    /**
     * Verifies if proxy can be used (there are less than 3 instances using it at the time)
     *
     * @param proxy Proxy to use
     * @return boolean
     */
    boolean canUseProxy(Proxy proxy) {
        //Create a statement
        try {
            String sql = "SELECT unlocked, num_of_instances FROM sql9212904.proxies WHERE ip=? AND port=?";
            //Execute SQL query
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setString(1, proxy.getProxy());
            statement.setInt(2, proxy.getPort());

            ResultSet res = statement.executeQuery();
            //If there are no records about this proxy in our db, then no crawler can be using it at the moment
            if (res.getFetchSize() == 0) {
                return true;
            }

            //Check if the current proxy is already using it
            if (isCurrentInstanceUsingProxy(Logger.getInstance().getInstanceID(), proxy)) return true;

            //Process result
            while (res.next()) {
                System.out.println(res.getInt("num_of_instances"));
                if (res.getBoolean("unlocked") && res.getInt("num_of_instances") < 3) return true;
            }

        } catch (SQLException | FileNotFoundException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return false;
    }

    /**
     * Inserts an unlocked proxy into the database, if it already exists it updates the value
     *
     * @param proxy Proxy that is unlocked
     * @param stats
     */
    void addUnlockedProxy(Proxy proxy, String cookies, SearchEngine.SupportedSearchEngine searchEngine, StatsGUI stats) {
        try {
            String sql = "INSERT INTO sql9212904.proxies " +
                    "(ip, port, unlocked, cookies, search_engine) " +
                    "VALUES (?, ?, TRUE, ?, ?)";

            PreparedStatement statement = myConnection.prepareStatement(sql);
            //Set params
            statement.setString(1, proxy.getProxy());
            statement.setInt(2, proxy.getPort());
            statement.setString(3, cookies);
            try {
                statement.setString(4, searchEngine.name());
            } catch (NullPointerException e) {
                statement.setString(4, "GoogleScholar");
            }
            statement.executeUpdate();
            stats.updateNumberOfUnlocked(stats.getNumberOfUnlockedProxies().get() + 1);
        } catch (MySQLIntegrityConstraintViolationException e) {
            //If this happens, record already exist so we update it instead

            try {
                String sql = "UPDATE sql9212904.proxies " +
                        "SET cookies = ?, search_engine = ?, unlocked = TRUE " +
                        "WHERE ip = ?  AND port = ?";
                PreparedStatement statement = myConnection.prepareStatement(sql);
                //Set params
                statement.setString(1, cookies);
                try {
                    statement.setString(2, searchEngine.name());
                } catch (NullPointerException e2) {
                    statement.setString(2, "GoogleScholar");
                }
                statement.setString(3, proxy.getProxy());
                statement.setInt(4, proxy.getPort());
                statement.executeUpdate();
                stats.updateNumberOfUnlocked(stats.getNumberOfUnlockedProxies().get() + 1);
            } catch (SQLException e1) {
                guiLabelManagement.setAlertPopUp(e1.getMessage());
            }
        } catch (SQLException e2) {
            guiLabelManagement.setAlertPopUp(e2.getMessage());
        }
    }

    /**
     * Inserts a locked proxy into the database
     *
     * @param proxy Proxy that is unlocked
     */

    void addLockedProxy(Proxy proxy) {
        try {
            String sql = "UPDATE sql9212904.proxies " +
                    "SET cookies = ?, unlocked = FALSE, num_of_instances = 0 " +
                    "WHERE ip = ?  AND port = ?";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setString(1, "");
            statement.setString(2, proxy.getProxy());
            statement.setInt(3, proxy.getPort());
            statement.executeUpdate();

            //Remove it if there is crawler using it.
            sql = "DELETE FROM sql9212904.scrawler_to_proxy " +
                    "WHERE ip = (?) AND port = (?) ";

            statement = myConnection.prepareStatement(sql);
            //Set params
            statement.setString(1, proxy.getProxy());
            statement.setInt(2, proxy.getPort());

            statement.executeUpdate();
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }

    /**
     * Notifies the db that the current instance is using a proxy
     */
    void addProxyToCurrentInstance(String instance, Proxy proxy) {
        try {
            if (!isCurrentInstanceUsingProxy(instance, proxy)) {
                String sql = "INSERT INTO sql9212904.scrawler_to_proxy " +
                        "(scrawler_id, ip, port) " +
                        "VALUES (?, ?, ?)";

                PreparedStatement statement = myConnection.prepareStatement(sql);
                //Set params
                statement.setString(1, instance);
                statement.setString(2, proxy.getProxy());
                statement.setInt(3, proxy.getPort());

                statement.executeUpdate();

                //Increase the count of crawlers using this proxy
                sql = "UPDATE sql9212904.proxies " +
                        "SET num_of_instances = num_of_instances + 1, unlocked = TRUE " +
                        "WHERE ip = ?  AND port = ?";
                statement = myConnection.prepareStatement(sql);
                //Set params
                statement.setString(1, proxy.getProxy());
                statement.setInt(2, proxy.getPort());
                statement.executeUpdate();
            }
        } catch (MySQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException();
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }

    /**
     * Check if the current proxy is already being used by the current instance
     *
     * @return boolean
     */
    boolean isCurrentInstanceUsingProxy(String instance, Proxy proxy) {
        try {
            String sql = "SELECT scrawler_id FROM sql9212904.scrawler_to_proxy WHERE ip=? AND port=?";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setString(1, proxy.getProxy());
            statement.setInt(2, proxy.getPort());

            ResultSet res = statement.executeQuery();
            //If there are no records about this proxy in our db, then no crawler can be using it at the moment
            if (res.getFetchSize() == 0) {
                return false;
            }
            //Process result, if the id appears, then the current crawler is already using it
            while (res.next()) {
                if (res.getString("scrawler_id").equals(instance)) return true;
            }
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return false;
    }


    /**
     * Adds an instance of the current crawler to the database
     */
    void addCrawlerInstance(String instance) {
        //Map current instance to the proxy
        try {
            String sql = "INSERT INTO sql9212904.scrawlers " +
                    "(id) " +
                    "VALUES (?)";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            //Set params
            statement.setString(1, instance);
            statement.executeUpdate();
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }

    /**
     * Removes an instance of the current crawler from the database
     */
    void removeCrawlerInstance(String instance) {
        try {
            //Get all the proxies that are currently using the crawler, and decrease the count
            //Get all the proxies
            String sql = "SELECT ip, port " +
                    "FROM sql9212904.scrawler_to_proxy " +
                    "WHERE scrawler_id = ? ";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setString(1, instance);
            ResultSet res = statement.executeQuery();
            //Iterate through each proxy and port
            while (res.next()) {
                String ip = res.getString("ip");
                int port = res.getInt("port");
                //Decrease counter
                sql = "UPDATE sql9212904.proxies " +
                        "SET num_of_instances = num_of_instances - 1 " +
                        "WHERE ip = ?  AND port = ? AND num_of_instances > 0";
                statement = myConnection.prepareStatement(sql);
                //Set params
                statement.setString(1, ip);
                statement.setInt(2, port);
                statement.executeUpdate();
            }

            //Delete instance from the crawler from the db
            sql = "DELETE " +
                    "FROM sql9212904.scrawlers " +
                    "WHERE id = (?)";
            statement = myConnection.prepareStatement(sql);
            //Set params
            statement.setString(1, instance);
            statement.executeUpdate();
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }

    /**
     * Adds the current download rate and a timestamp of when was this download rate updated
     */
    void addDownloadRateToDB(Double currPercentage){
        //Get the current time
        Calendar calendar = Calendar.getInstance();
        java.sql.Timestamp timestamp = new java.sql.Timestamp(calendar.getTime().getTime());
       try {

           String sql = "UPDATE sql9212904.scrawlers " +
                   "SET download_rate = ?, last_updated = ? " +
                   "WHERE id = ?";
           PreparedStatement statement = myConnection.prepareStatement(sql);
           statement.setDouble(1, currPercentage);
           statement.setTimestamp(2, timestamp);
           statement.setString(3, Logger.getInstance().getInstanceID());
           statement.executeUpdate();
       } catch (SQLException | FileNotFoundException e) {
           guiLabelManagement.setAlertPopUp(e.getMessage());
       }
    }

}
