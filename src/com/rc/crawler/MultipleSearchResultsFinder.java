package com.rc.crawler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rafaelcastro on 7/4/17.
 * Analyzes a Google scholar search result that contains multiple search results, and finds the appropriate URL for
 * each result,
 */
class MultipleSearchResultsFinder {
    private Document doc;
    private boolean isMultipleSearch;
    private GUILabelManagement guiLabels;
    private String type;
    private Crawler crawler;

    MultipleSearchResultsFinder(Document doc, boolean isMultipleSearch, GUILabelManagement guiLabels, String type,
                                Crawler crawler) {
        this.doc = doc;
        this.isMultipleSearch = isMultipleSearch;
        this.guiLabels = guiLabels;
        this.type = type;
        this.crawler = crawler;
    }
    MultipleSearchResultsFinder(Crawler crawler) {
        this.crawler = crawler;
    }

    boolean findMultipleSearchResult(Elements links, HashMap<String, String[]> searchResultToLink) {

        boolean result = false;
        String searchResultURL = "";
        String url = "";
        Pattern searchFor1Result = Pattern.compile("\\b1 result\\b");
        Matcher resultMatcher = searchFor1Result.matcher(doc.toString());
        if (!resultMatcher.find() && !doc.toString().contains("Showing the best result for " +
                "this search")) {
            if (!isMultipleSearch) {
                guiLabels.setSearchResultLabel(type+",There was more than 1 result found");
            }
            boolean searchResultFound = false;
            String searchResult = "";
            String text;
            if (type.equals("searchForCitedBy")) {
                for (Element link : links) {
                    text = link.text();
                    String absLink = link.attr("abs:href");
                    if (absLink.isEmpty()) {
                        String baseURL = "https://scholar.google.com";
                        String relativeURL = link.attr("href");
                        absLink = baseURL + relativeURL;
                    }
                    Pattern pattern =
                            Pattern.compile("((www\\.)?scholar\\.google\\.com)|(www\\.(support\\.)?google\\" +
                                    ".com)");

                    Matcher matcher = pattern.matcher(absLink);
                    if (!matcher.find()) {
                        text = link.text();
                        Pattern pattern2 = Pattern.compile("\\[HTML]|\\[PDF]");
                        Matcher matcher2 = pattern2.matcher(text);
                        if (!matcher2.find() && !text.equals("Provide feedback")) {
                            searchResult = text;
                            //Adds a search result to the search result window
                            guiLabels.setMultipleSearchResult(text);
                            searchResultFound = true;

                        }
                    } else if (searchResultFound) {
                        if (text.contains("Cited by")) {
                            searchResultToLink.put(searchResult, new String[]{absLink, text});
                            result = true;
                            searchResultFound = false;
                        }
                    }
                }
            } else {
                //Go through all the different versions, without filtering the type
                Pattern gScholarSearchResult = Pattern.compile("(<div class=\"gs_r\">)([^∞])+?(?=(<div " +
                        "class=\"gs_r\">)|(<div id=\"gs_ccl_bottom\">))");
                Matcher gScholarSRMatcher = gScholarSearchResult.matcher(doc.html());

                String baseURI = "https://scholar.google.com";
                String paperVersionsURL;
                //Look for all the files that contain a version, if they don't, add the URL of the site
                while (gScholarSRMatcher.find()) {
                    String gsSearchResult = gScholarSRMatcher.group();
                    //Get the link of the search result and the text
                    Pattern linkAndTextPattern = Pattern.compile("http(s)?:/.+?(?=</a>)");
                    Matcher linkAndTextMatcher = linkAndTextPattern.matcher(gsSearchResult);
                    text = "";
                    while (linkAndTextMatcher.find()) {
                        //Retrieve the link
                        String linkAndText = linkAndTextMatcher.group();
                        Pattern linkPattern = Pattern.compile("http(s)?:/[^\"]*");
                        Matcher linkPatternMatcher = linkPattern.matcher(linkAndText);
                        if (linkPatternMatcher.find()) {
                            try {
                                searchResultURL = linkPatternMatcher.group();
                                //Retrieve the text
                                text = linkAndText;
                                text = text.replace(searchResultURL, "");
                                text = text.replaceAll("\"[^>]*>", "");
                                text = text.replaceAll("<(/)?\\D>", "");
                                if (text.contains("span class")) {
                                    text = "";
                                }
                                if (!text.isEmpty()) {
                                    guiLabels.setMultipleSearchResult(text);
                                    result = true;
                                    String keyword = text.replace(" ", "+");
                                    //Get the url for searching the current selection
                                    url = "https://scholar.google.com/scholar?hl=en&q=" + keyword;
                                    //Add the URL of the google search and the URL of the first search result (Case 2.2)
                                    paperVersionsURL = url + "∆" + searchResultURL;
                                    searchResultToLink.put(text, new String[]{paperVersionsURL, text});
                                    break;
                                }
                            } catch (IllegalStateException ignored) {
                            }
                        }
                    }
                    //Try finding the "Version" feature for a given search result.
                    Pattern pattern = Pattern.compile("<a href=([^<])*All \\d* versions</a>");
                    Matcher matcher = pattern.matcher(gScholarSRMatcher.group());
                    //Get the first match
                    if (matcher.find()) {
                        //Get the link to the "All x versions" feature of GS for the curr article if it exists (Case
                        //2.1)
                        Pattern linkPattern = Pattern.compile("/[^\"]*");
                        Matcher linkMatcher = linkPattern.matcher(matcher.group());
                        if (linkMatcher.find()) {
                            paperVersionsURL = baseURI + linkMatcher.group();
                            //Put the All x Version URL, the URL of the first search result and the google
                            //search result URL
                            paperVersionsURL = paperVersionsURL + "∆" + searchResultURL + "∆" + url;
                            result = true;
                            searchResultToLink.put(text, new String[]{paperVersionsURL, text});
                        }
                    }
                }
            }
            return result;
        } return false;
    }

    String verifyIfMultipleSearchResult(String url, boolean isMultipleSearch) {
        if (!isMultipleSearch) {
            Document doc;
            try {
                doc = crawler.changeIP(url, true, false);
            } catch (NullPointerException e) {
                return "";
            }
            Pattern gScholarSearchResult = Pattern.compile("(<div class=\"gs_r\">)([^∞])+?(?=(<div " +
                    "class=\"gs_r\">)|(<div id=\"gs_ccl_bottom\">))");
            Matcher gScholarSRMatcher = gScholarSearchResult.matcher(doc.html());
            int counter = 0;
            while (gScholarSRMatcher.find()) {
                counter++;
            }
            if (counter > 1) return "";
            else return url;
        }
        else return url;
    }
}
