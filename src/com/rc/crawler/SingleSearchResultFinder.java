package com.rc.crawler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rafaelcastro on 7/4/17.
 * Analyzes a Google scholar search result that contains 1 result. If there are more than 1, it finds just the first
 * one.
 */
class SingleSearchResultFinder {
    private Crawler crawler;
    private final GUILabelManagement guiLabels;
    private final SimultaneousDownloadsGUI simultaneousDownloadsGUI;
    private Document doc;
    private boolean found = false;
    private String text = "";
    private String paperVersionsURL = "";
    private String absLink = "";


    SingleSearchResultFinder(Crawler crawler, GUILabelManagement guiLabels, SimultaneousDownloadsGUI
            simultaneousDownloadsGUI,
                             Document doc) {
        this.crawler = crawler;
        this.guiLabels = guiLabels;
        this.simultaneousDownloadsGUI = simultaneousDownloadsGUI;
        this.doc = doc;
    }

    /**
     * Finds a google search result and gets its link and text
     * @param links All links of the current page
     * @param type type of search performed
     * @param url URL of the doc we are analyzing
     * @param isMultipleSearch true if it is being performed in multiple search mode
     * @return true if a search result was found, false otherwise
     */
    boolean findSingleSearchResult(Elements links, String type, String url,
                                   boolean isMultipleSearch) {
        String firstSearchResultURL = "";
        String fullViewURL = "";
        String htmlFileURL = "";
        if (type.equals("searchForCitedBy")) {
            //If we are searching for the articles that cite the article
            for (Element link : links) {
                text = link.text();
                this.absLink = link.attr("abs:href");
                if (text.contains("Cited by")) {
                    found = true;
                    break;
                }
            }
        } else {
            //If we are searching for the article
            Pattern gScholarSearchResult = Pattern.compile("(<div class=\"gs_r\">)([^∞])+?(?=(<div " +
                    "class=\"gs_r\">)|(<div id=\"gs_ccl_bottom\">))");
            Matcher gScholarSRMatcher = gScholarSearchResult.matcher(doc.html());
            if (gScholarSRMatcher.find()) {
                String gsSearchResult = gScholarSRMatcher.group();
                //Get the link of the search result and the text
                Pattern linkAndTextPattern = Pattern.compile("http(s)?:/[^<]*");
                Matcher linkAndTextMatcher = linkAndTextPattern.matcher(gsSearchResult);
                text = "";
                while (linkAndTextMatcher.find()) {
                    //Retrieve the link of the first search result website
                    String linkAndText = linkAndTextMatcher.group();
                    Pattern linkPattern = Pattern.compile("http(s)?:/[^\"]*");
                    Matcher linkPatternMatcher = linkPattern.matcher(linkAndText);
                    if (linkPatternMatcher.find()) {
                        try {
                            firstSearchResultURL = linkPatternMatcher.group();
                            text = linkAndText;
                            text = text.replace(firstSearchResultURL, "");
                            text = text.replaceAll("\"[^>]*>", "");
                            text = text.replaceAll("<(/)?\\D>", "");
                            if (text.contains("span class")) {
                                text = "";
                            }
                            if (text.contains("Full View")) {
                                firstSearchResultURL = firstSearchResultURL.replaceAll("amp;", "");
                                firstSearchResultURL = firstSearchResultURL.replaceAll("&nossl=1", "");
                                fullViewURL = firstSearchResultURL;
                                fullViewURL = formatFullView(fullViewURL);
                                text = "";
                            }
                            if (!text.isEmpty()) {
                                break;
                            }
                        } catch (IllegalStateException ignored) {
                        }
                    }
                }
            }

            //Capture the Version link
            String baseURI = "https://scholar.google.com";
            Pattern pattern = Pattern.compile("<a href=([^<])*All \\d* versions</a>");
            Matcher matcher = pattern.matcher(doc.html());
            //Get the first match
            if (matcher.find()) {
                //Get just the link
                Pattern linkPattern = Pattern.compile("/[^\"]*");
                Matcher linkMatcher = linkPattern.matcher(matcher.group());
                if (linkMatcher.find()) {
                    paperVersionsURL = baseURI + linkMatcher.group();
                    //Put the Version URL and the first search result URL, and the search result URL
                    paperVersionsURL = paperVersionsURL + "∆" + firstSearchResultURL + "∆" + url;
                    //If there is a full view url, then add it as well
                    if (!fullViewURL.isEmpty()) {
                        paperVersionsURL = paperVersionsURL + "∆" + fullViewURL;
                    }
                    text = "found";
                    found = true;
                }

            }
            //When there is no "Version" feature for the article
            if (paperVersionsURL.isEmpty()) {

                //Check if there is 1 search result, if so, get the link
                if (gScholarSRMatcher.find()) {
                    text = "found";
                    found = true;
                    //Add the url of the google search and the url of the first search result
                    this.paperVersionsURL = url + "∆" + firstSearchResultURL;
                }
                else {
                    text = "";
                    found = false;
                    //We could not retrieve a URL, so we won't be able to get the pdf from gscholar
                    if (!isMultipleSearch) {
                        guiLabels.setSearchResultLabel(type + ",Could not find the PDF versions " +
                                "of this paper");
                    } else {
                        simultaneousDownloadsGUI.updateStatus("Could not find the PDF version of " +
                                "this paper");
                    }
                }
            }
        }
        return found;
    }

    private String formatFullView(String fullViewURL) {
        Document doc = crawler.changeIP(fullViewURL, true, false);
        Pattern redirectPattern =  Pattern.compile("<script>location\\.replace[^©]*</script>");
        Matcher redirectMatcher = redirectPattern.matcher(doc.html());
        if (redirectMatcher.find()) {
            Pattern linkPattern = Pattern.compile("http(s)?:/[^\"']*");
            Matcher linkPatternMatcher = linkPattern.matcher(redirectMatcher.group());
            if (linkPatternMatcher.find()) {
                fullViewURL = linkPatternMatcher.group();
            }

        }
        return fullViewURL;
    }

    String getText() {
        return text;
    }

    String getPaperVersionsURL() {
        return paperVersionsURL;
    }

    String getAbsLink() {
        return absLink;
    }
}
