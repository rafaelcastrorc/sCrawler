package com.rc.crawler;

/**
 * Created by rafaelcastro on 6/12/17.
 */
class Proxy {

    private final String proxy;
    private final int port;

    Proxy(String proxy, int port) {
        this.proxy = proxy;
        this.port = port;
    }

    String getProxy() {
        return proxy;
    }

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
}