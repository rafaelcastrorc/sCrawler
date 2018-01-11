package com.rc.crawler;

import org.joda.time.DateTime;

/**
 * Created by rafaelcastro on 6/12/17.
 * Proxy object. Has an IP and a port number
 */
class Proxy {

    private final String proxy;
    private final int port;
    private DateTime time;

    /**
     * Constructor. Takes an ip and a port number.
     *
     * @param proxy IP address of the proxy, as a string.
     * @param port  port number of the proxy, as an int
     */
    Proxy(String proxy, int port) {
        this.proxy = proxy;
        this.port = port;
    }

    /**
     * Gets the ip, as a string.
     *
     * @return ip address
     */
    String getProxy() {
        return proxy;
    }

    /**
     * Gets the port, as an int.
     *
     * @return port number
     */
    int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Proxy proxy1 = (Proxy) o;

        return port == proxy1.port && (proxy != null ? proxy.equals(proxy1.proxy) : proxy1.proxy == null);
    }

    @Override
    public int hashCode() {
        int result = proxy != null ? proxy.hashCode() : 0;
        result = 31 * result + port;
        return result;
    }

    /**
     * Time this proxy was gathered.
     */
    public void setTime(DateTime time) {
        this.time = time;
    }

    public DateTime getTime() {
        return time;
    }
}