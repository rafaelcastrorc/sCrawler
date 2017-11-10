package com.rc.crawler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashMap;
import java.util.Optional;
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
    private SearchEngine.SupportedSearchEngine engine;

    MultipleSearchResultsFinder(Document doc, boolean isMultipleSearch, GUILabelManagement guiLabels, String type,
                                Crawler crawler, SearchEngine.SupportedSearchEngine engine) {
        this.doc = doc;
        this.isMultipleSearch = isMultipleSearch;
        this.guiLabels = guiLabels;
        this.type = type;
        this.crawler = crawler;
        this.engine = engine;
    }

    MultipleSearchResultsFinder(Crawler crawler) {
        this.crawler = crawler;
    }

    boolean findMultipleSearchResult(Elements links, HashMap<String, String[]> searchResultToLink) {
        if (engine == SearchEngine.SupportedSearchEngine.MicrosoftAcademic) {
            return findMultipleResultsMSFT(links, searchResultToLink);
        } else if (engine == SearchEngine.SupportedSearchEngine.GoogleScholar) {
            return findMultipleResultsGoogle(links, searchResultToLink);
        }
        return false;
    }

    private boolean findMultipleResultsMSFT(Elements links, HashMap<String, String[]> searchResultToLink) {
        Pattern msftAcademicSearchResult = Pattern.compile("(<paper-tile)([^∞])+?(?=(</paper-tile))");
        Matcher msftAcademicSRMatcher = msftAcademicSearchResult.matcher(doc.html());
        int numOfResults = 0;
        while (msftAcademicSRMatcher.find()) {
            numOfResults++;
        }
        if (numOfResults < 2) {
            return false;
        }
        if (!isMultipleSearch) {
            guiLabels.setSearchResultLabel(type + ",There was more than 1 result found");
        }
        if (type.equals("searchForCitedBy")) {
            return findSearchForCitedByMSFT(links, searchResultToLink);
        }
        else {
            return findSearchForTheArticle(links, searchResultToLink);
        }
    }

    /**
     * Finds all the search result links when we are looking for the article itself and there is more than 1 search
     * result
     */
    private boolean findSearchForTheArticle(Elements links, HashMap<String, String[]> searchResultToLink) {
        Pattern msftAcademicSearchResult = Pattern.compile("(<paper-tile)([^∞])+?(?=(</paper-tile))");
        Matcher msftAcademicSRMatcher = msftAcademicSearchResult.matcher(doc.html());
        String searchResult = "";
        while (msftAcademicSRMatcher.find()) {
            String msftSearchResult = msftAcademicSRMatcher.group();
            //Get the title section of the search result
            Pattern titleSectionPattern = Pattern.compile("(<section class=\"paper-title)([^∞])+?(?=(</section))");
            Matcher titleSectionMatcher = titleSectionPattern.matcher(msftSearchResult);
            searchResult = "";
            if (titleSectionMatcher.find()) {
                searchResult = titleSectionMatcher.group();
                searchResult = searchResult.replaceAll("(<section class=\"paper-title)([^∞])+?(?=(title=))", "");
                searchResult = searchResult.replaceAll("title=\"", "");
                searchResult = searchResult.replaceAll("\" target.*", "");
                searchResult = searchResult.trim();
                if (searchResult.isEmpty()) {
                    //If there is no title move to the next search result
                    continue;
                }
            }

            //See if it contains the pdf of the file itself, if not, get the link to the search result itself
            Pattern downloadLinkPattern = Pattern.compile("(<a class=\"source-grab\")([^∞])+?(?=(</li))");
            Matcher downloadLinkMatcher = downloadLinkPattern.matcher(msftSearchResult);
            String url = "";
            if (downloadLinkMatcher.find()) {
                url = downloadLinkMatcher.group();
                url = url.replaceAll("(<a class=\"source-grab\")([^∞])+?(?=(href=)" +
                        ")", "");
                url = url.replaceAll("href=\"", "");
                url = url.replaceAll("\".*", "");
                if (url.isEmpty()) {
                    //Get the link of the search result instead
                    Pattern firstSearchResultPattern = Pattern.compile("#/detail/[^\"]*");
                    Matcher firstSearchResultMatcher = firstSearchResultPattern.matcher(msftSearchResult);
                    if (firstSearchResultMatcher.find()) {
                        String link  = SearchEngine.getBaseURL(engine)+firstSearchResultMatcher.group();
                        Document doc = crawler.changeIP(link, true, false, engine, Optional.empty());
                        String firstSearchResultLink = firstSearchResultMatcher.group();
                        if (!firstSearchResultLink.isEmpty()) {
                            url = new SingleSearchResultFinder().findFirstResultSource(doc.html());
                        }
                    }
                }
                if (url.isEmpty()) continue;
                searchResultToLink.put(searchResult, new String[]{url, searchResult});
                System.out.println(searchResult);
                System.out.println(url);
                //Adds a search result to the search result window
                guiLabels.setMultipleSearchResult(searchResult);
            }
        }
        return (searchResultToLink.size() > 0);
    }

    /**
     * Finds all the search result links when we are looking for the article that cite the article and there is more
     * than 1 search
     * result
     */
    private boolean findSearchForCitedByMSFT(Elements links, HashMap<String, String[]> searchResultToLink) {
        Pattern msftAcademicSearchResult = Pattern.compile("(<paper-tile)([^∞])+?(?=(</paper-tile))");
        Matcher msftAcademicSRMatcher = msftAcademicSearchResult.matcher(doc.html());
        String searchResult = "";
        while (msftAcademicSRMatcher.find()) {
            String msftSearchResult = msftAcademicSRMatcher.group();
            //Get the title section of the search result
            Pattern titleSectionPattern = Pattern.compile("(<section class=\"paper-title)([^∞])+?(?=(</section))");
            Matcher titleSectionMatcher = titleSectionPattern.matcher(msftSearchResult);
            searchResult = "";
            if (titleSectionMatcher.find()) {
                searchResult = titleSectionMatcher.group();
                searchResult = searchResult.replaceAll("(<section class=\"paper-title)([^∞])+?(?=(title=))", "");
                searchResult = searchResult.replaceAll("title=\"", "");
                searchResult = searchResult.replaceAll("\" target.*", "");
                searchResult = searchResult.trim();
                if (searchResult.isEmpty()) {
                    //If there is no title move to the next search result
                    continue;
                }
            }
            Pattern citedByPattern = Pattern.compile("<li>.*citation\".*");
            Matcher citedByMatcher = citedByPattern.matcher(msftSearchResult);
            if (citedByMatcher.find()) {
                Pattern urlPattern = Pattern.compile("href=\"[^\"]*");
                Matcher urlMatcher = urlPattern.matcher(citedByMatcher.group());
                if (urlMatcher.find()) {
                    String url = urlMatcher.group();
                    url = url.replaceAll("href=\"", "");
                    String absLink = SearchEngine.getBaseURL(engine) + url;
                    searchResultToLink.put(searchResult, new String[]{absLink, searchResult});
                    System.out.println(searchResult);
                    System.out.println(absLink);
                    //Adds a search result to the search result window
                    guiLabels.setMultipleSearchResult(searchResult);
                }
            }
        }
        return (searchResultToLink.size() > 0);
    }

    private boolean findMultipleResultsGoogle(Elements links, HashMap<String, String[]> searchResultToLink) {
        boolean result = false;
        String searchResultURL = "";
        String url = "";
        Pattern searchFor1Result = Pattern.compile("\\b1 result\\b");
        Matcher resultMatcher = searchFor1Result.matcher(doc.toString());
        if (!resultMatcher.find() && !doc.toString().contains("Showing the best result for " +
                "this search")) {
            if (!isMultipleSearch) {
                guiLabels.setSearchResultLabel(type + ",There was more than 1 result found");
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
        }
        return result;
    }


    /**
     * Verifies if there are multiple search results for a google search
     */
    String verifyIfMultipleSearchResult(String url, boolean isMultipleSearch) {
        if (!isMultipleSearch) {
            Document doc;
            try {
                doc = crawler.changeIP(url, true, false, engine, Optional.empty());
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
        } else return url;
    }
}
