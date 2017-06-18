package com.rc.crawler;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by rafaelcastro on 6/16/17.
 * Thread safe counter.
 */
class AtomicCounter {
    private int maxNumber = 0;
    private AtomicInteger c = new AtomicInteger(0);

    /**
     * Constructor. Initializes and resets the counter.
     */
    AtomicCounter() {
        reset();
    }

    /**
     * Gets the biggest number this counter can get to
     */
    int getMaxNumber() {
        return maxNumber;
    }

    /**
     * Sets the biggest number this counter can get to
     *
     * @param maxNumber the biggest number this counter can get to.
     */
    void setMaxNumber(int maxNumber) {
        this.maxNumber = maxNumber;
    }

    /**
     * Increments the counter.
     */
    void increment() {
        c.incrementAndGet();
    }

    /**
     * Resets the counter to 0
     */
    void reset() {
        c.set(0);
    }

    /**
     * Gets the value of the counter
     *
     * @return int
     */
    int value() {
        return c.get();
    }
}
