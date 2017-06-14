package com.rc.crawler;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Manages the different objects that the controller is listening to, to update the GUI.
 */
class GUILabelManagement {

    private StringProperty numberOfWorkingIPs = new SimpleStringProperty();
    private StringProperty alertPopUp = new SimpleStringProperty();
    private StringProperty searchResultLabel = new SimpleStringProperty();
    private DoubleProperty loadBar = new SimpleDoubleProperty();
    private StringProperty output = new SimpleStringProperty();
    private StringProperty connectionOutput = new SimpleStringProperty();
    private StringProperty numberOfPDF = new SimpleStringProperty();
    private StringProperty multipleSearchResult = new SimpleStringProperty();


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


    StringProperty getOutput() {
        return output;
    }

    StringProperty getConnectionOutput() {
        return connectionOutput;
    }

    StringProperty getNumberOfPDFs() {
        return numberOfPDF;
    }
    public StringProperty getMultipleSearchResult() {
        return multipleSearchResult;
    }


    /**
     * Adds a new proxy to the queue displayed in the WorkingProxiesLabel
     * @param numberOfWorkingIPs String with the Proxy to add or remove.
     */
    void setNumberOfWorkingIPs(String numberOfWorkingIPs) {
        this.numberOfWorkingIPs.set(numberOfWorkingIPs);
    }

    /**
     * Sets a pop up alert
     * @param alertPopUp String with message to display
     */
    void setAlertPopUp(String alertPopUp) {
        this.alertPopUp.set(alertPopUp);
    }

    /**
     * Sets what the searchResult label will display
     * @param searchResultLabel String with message to display
     */
    void setSearchResultLabel(String searchResultLabel) {
        this.searchResultLabel.set(searchResultLabel);
    }

    /**
     * Sets the current percentage the progress bar has loaded from 0 to 1
     * @param loadBar double from 0 to 1
     */
    void setLoadBar(double loadBar) {
        this.loadBar.set(loadBar);
    }

    /**
     * Sets the output displayed in the status label
     * @param output String with message to display
     */
    void setOutput(String output) {
        this.output.set(output);
    }

    /**
     * Sets all the IO and debuggin output in the connectionOutput label
     * @param connectionOutput String with message to display
     */
    void setConnectionOutput(String connectionOutput) {
        this.connectionOutput.set(connectionOutput);
    }

    /**
     * Sets the current number of PDFs downloaded in the appropiate label
     * @param numberOfPDF int with the number of PDFs downloaded
     */
    void setNumberOfPDF(String numberOfPDF) {
        this.numberOfPDF.set(numberOfPDF);
    }

    /**
     * Adds a search result to a list view
     * @param result string with the search result
     */
    void setMultipleSearchResult(String result) {
        this.multipleSearchResult.setValue(result);
    }


}