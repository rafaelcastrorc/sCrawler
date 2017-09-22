package com.rc.crawler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rafaelcastro on 7/4/17.
 * Analyzes a Google scholar search result that contains 1 result. If there are more than 1, it finds just the first
 * one.
 */
class SingleSearchResultFinder {
    private Crawler crawler;
    private GUILabelManagement guiLabels;
    private SimultaneousDownloadsGUI simultaneousDownloadsGUI;
    private Document doc;
    private boolean found = false;
    private String text = "";
    private String paperVersionsURL = "";
    private String absLink = "";
    private String firstSearchResultURL;
    private String fullViewURL;
    private SearchEngine.SupportedSearchEngine engine;


    SingleSearchResultFinder(Crawler crawler, GUILabelManagement guiLabels, SimultaneousDownloadsGUI
            simultaneousDownloadsGUI,
                             Document doc) {
        this.crawler = crawler;
        this.guiLabels = guiLabels;
        this.simultaneousDownloadsGUI = simultaneousDownloadsGUI;
        this.doc = doc;
    }

    public SingleSearchResultFinder() {

    }

    /**
     * Finds a google search result and gets its link and text
     *
     * @param links            All links of the current page
     * @param type             type of search performed
     * @param url              URL of the doc we are analyzing
     * @param isMultipleSearch true if it is being performed in multiple search mode
     * @return true if a search result was found, false otherwise
     */
    boolean findSingleSearchResult(Elements links, String type, String url,
                                   boolean isMultipleSearch, SearchEngine.SupportedSearchEngine engine) {
        this.engine = engine;
        firstSearchResultURL = "";
        fullViewURL = "";
        if (type.equals("searchForCitedBy")) {
            //If we are searching for the articles that cite the article
            searchForCitedBy(links);
        } else {
            searchForTheArticle(isMultipleSearch, type, url);
        }
        return found;
    }

    /**
     * Finds the "All Version" URL, the URL of the first search result, and if possible, the "Full View"
     * URL which likely contains a PDF
     */
    private void searchForTheArticle(boolean isMultipleSearch, String type, String url) {
        //If we are searching for the article itself
        if (engine == SearchEngine.SupportedSearchEngine.GoogleScholar) {
            getGoogleScholarResult(url, isMultipleSearch, type);
        } else if (engine == SearchEngine.SupportedSearchEngine.MicrosoftAcademic) {
            getMicrosoftAcademicResult(url, isMultipleSearch, type);
        }
    }

    /**
     * Finds the appropriate link in a Microsoft Academic search
     */
    private void getMicrosoftAcademicResult(String url, boolean isMultipleSearch, String type) {
        Pattern msftAcademicSearchResult = Pattern.compile("(<paper-tile)([^∞])+?(?=(</paper-tile))");
        Matcher msftAcademicSRMatcher = msftAcademicSearchResult.matcher(doc.html());
        while (msftAcademicSRMatcher.find()) {
            String msftSearchResult = msftAcademicSRMatcher.group();
            //Get the title section of the search result
            Pattern titleSectionPattern = Pattern.compile("(<section class=\"paper-title)([^∞])+?(?=(</section))");
            Matcher titleSectionMatcher = titleSectionPattern.matcher(msftSearchResult);
            text = "";
            if (titleSectionMatcher.find()) {
                text = titleSectionMatcher.group();
                text = text.replaceAll("(<section class=\"paper-title)([^∞])+?(?=(title=))", "");
                text = text.replaceAll("title=\"", "");
                text = text.replaceAll("\" target.*", "");
                if (text.isEmpty()) {
                    break;
                }
            }
            //Get the download link and save it as the paper version URL
            Pattern downloadLinkPattern = Pattern.compile("(<a class=\"source-grab\")([^∞])+?(?=(</li))");
            Matcher downloadLinkMatcher = downloadLinkPattern.matcher(msftSearchResult);
            if (downloadLinkMatcher.find()) {
                paperVersionsURL = downloadLinkMatcher.group();
                paperVersionsURL = paperVersionsURL.replaceAll("(<a class=\"source-grab\")([^∞])+?(?=(href=)" +
                        ")", "");
                paperVersionsURL = paperVersionsURL.replaceAll("href=\"", "");
                paperVersionsURL = paperVersionsURL.replaceAll("\".*", "");
                if (paperVersionsURL.isEmpty()) {
                    break;
                }
            }

            //Get the first search result url
            Pattern firstSearchResultPattern = Pattern.compile("#/detail/[^\"]*");
            Matcher firstSearchResultMatcher = firstSearchResultPattern.matcher(msftSearchResult);
            if (firstSearchResultMatcher.find()) {
                String link  = SearchEngine.getBaseURL(engine)+firstSearchResultMatcher.group();
                Document doc = crawler.changeIP(link, true, false, engine);
                String firstSearchResultLink = firstSearchResultMatcher.group();
                if (!firstSearchResultLink.isEmpty()) {
                    findFirstResultSource(doc.html());
                    found = true;
                    break;
                }
            }
        }


        //Still need the first search result url. Since not all links are the sames, then you need a dif method
        paperVersionsURL = paperVersionsURL + "∆" + firstSearchResultURL + "∆" + url;


        //When there is no "Version" feature for the article, or there is no direct link to get the paper
        if (paperVersionsURL.isEmpty() && !text.isEmpty()) {
            //Check if there is 1 search result, if so, get the link
            directPDFlinkNotFound(msftAcademicSRMatcher, url, isMultipleSearch, type);

        }

    }

    /**
     * Finds the first search result sources. Only for Microsoft Academic
     */
    String findFirstResultSource(String html) {
        Pattern pattern = Pattern.compile("data\\.sources([^∞])+?(?=(</ul))");
        Matcher matcher = pattern.matcher(html);
        ArrayList<String> listOfLinks = new ArrayList<>();
        if (matcher.find()) {
            Pattern linkPatter = Pattern.compile("href=\".*");
            Matcher linkMatcher = linkPatter.matcher(matcher.group());
            while (linkMatcher.find()) {
                String currLink = linkMatcher.group();
                currLink = currLink.replaceAll("href=\"", "");
                currLink = currLink.replaceAll("\".*", "");
                if (listOfLinks.size() < 3) {
                    listOfLinks.add(currLink);
                }
                else {
                    break;
                }
            }
        }
        for (String s : listOfLinks) {
            firstSearchResultURL  = firstSearchResultURL + "∆" +s;
        }
        return firstSearchResultURL;
    }

    /**
     * Finds the appropriate links in a google scholar search
     * @param url Current URL
     */
    private void getGoogleScholarResult(String url, boolean isMultipleSearch, String type) {
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

        getGoogleScholarVersionURL(url);
        //When there is no "Version" feature for the article, or there is no direct link to get the paper
        if (paperVersionsURL.isEmpty()) {
            directPDFlinkNotFound(gScholarSRMatcher, url, isMultipleSearch, type);
        }
    }

    /**
     * Gets the Version URL, which contains the different versions of the paper available to download
     * @param url Current URL
     */
    private void getGoogleScholarVersionURL(String url) {
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
                paperVersionsURL = paperVersionsURL.replaceAll("∆∆", "∆");
                text = "found";
                found = true;
            }

        }

    }

    /**
     * Handles the logic when there is no direct PDF link found in the search for the article itself
     */
    private void directPDFlinkNotFound(Matcher matcher, String url, boolean isMultipleSearch, String type) {
        //Check if there is 1 search result, if so, get the link
        if (matcher.find()) {
            text = "found";
            found = true;
            //Add the url of the original google search and the url of the first search result
            this.paperVersionsURL = url + "∆" + firstSearchResultURL;
        } else {
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


    /**
     * Finds the "Cited by" link
     *
     * @param links Jsoup Elements
     */
    private void searchForCitedBy(Elements links) {
        if (SearchEngine.SupportedSearchEngine.GoogleScholar == engine) {
            for (Element link : links) {
                text = link.text();
                this.absLink = link.attr("abs:href");
                if (text.contains("Cited by")) {
                    found = true;
                    if (absLink.isEmpty()) {
                        String baseURL = "https://scholar.google.com";
                        String relativeURL = link.attr("href");
                        this.absLink = baseURL + relativeURL;
                    }
                    break;
                }
            }
        }
        else if (SearchEngine.SupportedSearchEngine.MicrosoftAcademic == engine) {
            Pattern msftAcademicSearchResult = Pattern.compile("(<paper-tile)([^∞])+?(?=(</paper-tile))");
            Matcher msftAcademicSRMatcher = msftAcademicSearchResult.matcher(doc.html());
            while (msftAcademicSRMatcher.find()) {
                String msftSearchResult = msftAcademicSRMatcher.group();
                Pattern citedByPattern = Pattern.compile("<li>.*citation\".*");
                Matcher citedByMatcher = citedByPattern.matcher(msftSearchResult);
                if (citedByMatcher.find()) {
                    Pattern urlPattern  = Pattern.compile("href=\"[^\"]*");
                    Matcher urlMatcher = urlPattern.matcher(citedByMatcher.group());
                    if (urlMatcher.find()) {
                        String url = urlMatcher.group();
                        url = url.replaceAll("href=\"", "");
                        text = "found";
                        absLink = SearchEngine.getBaseURL(engine) +url;
                        found = true;
                        break;
                    }

                }
                found = false;
                }

        }

    }

    /**
     * Formats the "Full View" URL correctly. This URL usually contains a PDF
     */
    private String formatFullView(String fullViewURL) {
        Document doc = crawler.changeIP(fullViewURL, true, false, engine);
        Pattern redirectPattern = Pattern.compile("<script>location\\.replace[^©]*</script>");
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
