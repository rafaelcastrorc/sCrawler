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
    private final Document doc;
    private final boolean isMultipleSearch;
    private final GUILabelManagement guiLabels;
    private final String type;

    MultipleSearchResultsFinder(Document doc, boolean isMultipleSearch, GUILabelManagement guiLabels, String type) {
        this.doc = doc;
        this.isMultipleSearch = isMultipleSearch;
        this.guiLabels = guiLabels;
        this.type = type;
    }

    boolean findMultipleSearchResult(Elements links, HashMap<String, String[]> searchResultToLink) {
        boolean result = false;
        String searchResultURL = "";
        if (!doc.toString().contains("1 result") && !doc.toString().contains("Showing the best result for " +
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
                    Pattern linkAndTextPattern = Pattern.compile("http(s)?:/[^<]*");
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
                                //Retrieve the tex
                                text = linkAndText;
                                text = text.replace(searchResultURL, "");
                                text = text.replaceAll("\"[^>]*>", "");
                                if (!text.isEmpty()) {
                                    guiLabels.setMultipleSearchResult(text);
                                    result = true;
                                    //Get the url for searching the current selection
                                    String keyword = text.replace(" ", "+");
                                    //Search google scholar
                                    String url = "https://scholar.google.com/scholar?hl=en&q=" + keyword;
                                    //Add the URL of the google search and the URL of the first search result
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
                        //Get the link to the "All x versions" feature of GS for the curr article
                        Pattern linkPattern = Pattern.compile("/[^\"]*");
                        Matcher linkMatcher = linkPattern.matcher(matcher.group());
                        if (linkMatcher.find()) {
                            paperVersionsURL = baseURI + linkMatcher.group();
                            //Put the Version URL and the first search result URL together
                            paperVersionsURL = paperVersionsURL + "∆" + searchResultURL;
                            result = true;
                            searchResultToLink.put(text, new String[]{paperVersionsURL, text});
                        }
                    }
                }
            }
            return result;
        } return false;
    }
}
