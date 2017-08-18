package com.rc.crawler;

import javafx.beans.property.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the different objects that the controller is listening to, to update the main GUI.
 */
class GUILabelManagement {

    private StringProperty numberOfWorkingIPs = new SimpleStringProperty();
    private StringProperty alertPopUp = new SimpleStringProperty();
    private StringProperty searchResultLabel = new SimpleStringProperty();
    private DoubleProperty loadBar = new SimpleDoubleProperty();
    private DoubleProperty loadBarMultiple = new SimpleDoubleProperty();
    private StringProperty output = new SimpleStringProperty();
    private StringProperty outputMultiple = new SimpleStringProperty();
    private StringProperty connectionOutput = new SimpleStringProperty();
    private StringProperty numberOfPDF = new SimpleStringProperty();
    private StringProperty numberOfPDFsMultiple = new SimpleStringProperty();
    private Map<Long, SearchResultWindow> mapThreadToSearchResultW = Collections.synchronizedMap(new HashMap<Long,
            SearchResultWindow>());
    private BooleanProperty isThereAnAlert = new SimpleBooleanProperty();

    BooleanProperty getIsThereAnAlert() {
        return isThereAnAlert;
    }

    StringProperty getOutputMultiple() {
        return outputMultiple;
    }

    StringProperty getNumberOfWorkingIPs() {
        return numberOfWorkingIPs;
    }

    StringProperty getAlertPopUp() {
        return alertPopUp;
    }

    StringProperty getSearchResultLabel() {
        return searchResultLabel;
    }

    DoubleProperty getLoadBar() {
        return loadBar;
    }

    DoubleProperty getLoadBarMultiple() {
        return loadBarMultiple;
    }

    StringProperty getOutput() {
        return output;
    }

    StringProperty getConnectionOutput() {
        return connectionOutput;
    }

    StringProperty getNumberOfPDFs() {
        return numberOfPDF;
    }

    StringProperty getNumberOfPDFsMultiple() {
        return numberOfPDFsMultiple;
    }

    Map<Long, SearchResultWindow> getMapThreadToSearchResultW() {
        return mapThreadToSearchResultW;
    }

    /**
     * Adds a new proxy to the queue displayed in the WorkingProxiesLabel
     *
     * @param numberOfWorkingIPs String with the Proxy to add or remove.
     */
    void setNumberOfWorkingIPs(String numberOfWorkingIPs) {
        this.numberOfWorkingIPs.set(numberOfWorkingIPs);
    }

    /**
     * Sets a pop up alert
     *
     * @param alertPopUp String with message to display
     */
    void setAlertPopUp(String alertPopUp) {
        this.alertPopUp.set(alertPopUp);
    }

    /**
     * Sets what the searchResult label will display
     *
     * @param searchResultLabel String with message to display
     */
    void setSearchResultLabel(String searchResultLabel) {
        this.searchResultLabel.set(searchResultLabel);
    }

    /**
     * Sets the current percentage the progress bar has loaded from 0 to 1
     *
     * @param loadBar double from 0 to 1
     */
    void setLoadBar(double loadBar) {
        this.loadBar.set(loadBar);
    }

    /**
     * Sets the output displayed in the status label
     *
     * @param output String with message to display
     */
    void setOutput(String output) {
        this.output.set(output);
    }

    /**
     * Sets all the IO and debugging output in the connectionOutput label
     *
     * @param connectionOutput String with message to display
     */
    void setConnectionOutput(String connectionOutput) {
        this.connectionOutput.set(connectionOutput);
    }

    /**
     * Sets the current number of PDFs downloaded in the appropriate label
     *
     * @param numberOfPDF int with the number of PDFs downloaded
     */
    void setNumberOfPDF(String numberOfPDF) {
        this.numberOfPDF.set(numberOfPDF);
    }

    /**
     * Adds a search result to a list view
     *
     * @param result string with the search result
     */
    void setMultipleSearchResult(String result) {
        SearchResultWindow curr = mapThreadToSearchResultW.get(Thread.currentThread().getId());
        curr.addItemToListView(result);
    }

    /**
     * Sets the output displayed in the status label in multiple article mode
     *
     * @param outputMultiple String with message to display
     */
    void setOutputMultiple(String outputMultiple) {
        this.outputMultiple.set(outputMultiple);
    }


    /**
     * Sets the number of PDFs downloaded in multiple article mode
     *
     * @param number typeOfSearch,intWithNumOfPDFs
     */
    void setNumberOfPDFsMultiple(String number) {
        this.numberOfPDFsMultiple.set(number);
    }


    /**
     * Sets the load bar percentage to 1
     */
    void setLoadBarMultiple() {
        this.loadBarMultiple.set((double) 1);
    }

    /**
     * Associates a thread to a search result window obj
     *
     * @param threadID ID of the current thread
     * @param window   SearchResultWindow obj
     */
    void associateThreadToSearchResultW(Long threadID, SearchResultWindow window) {
        this.mapThreadToSearchResultW.put(threadID, window);
    }


    /**
     * Changes GUI if there is an alert to display
     *
     * @param isThereAnAlert true if it needs to display alert button
     */
    void setIsThereAnAlert(boolean isThereAnAlert) {
        this.isThereAnAlert.set(isThereAnAlert);
    }

}