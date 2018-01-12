package com.rc.crawler;

import org.jsoup.nodes.Document;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rafaelcastro on 9/7/17.
 * Selects the correct behavior depending on which Search Engine is being used
 * Currently it only supports Microsoft Academic and Google Scholar
 */
class SearchEngine {
    /**
     * Test if proxy can connect to a given url of the search engine the program is currently using
     */
    static String testConnectionToWebsite(SupportedSearchEngine engine) {
        if (engine == SupportedSearchEngine.MicrosoftAcademic) {
            return "https://academic.microsoft" +
                    ".com/#/search?iq=@this%20is%20the%20one@&q=this%20is%20the%20one&filters=&from=0&sort=0";
        }
        else {
            return "https://scholar.google.com/scholar?q=this+is+the+one&btnG=&hl=en&as_sdt=0%2C39";
        }
        //throw new IllegalArgumentException("Invalid Search Engine");
    }

    /**
     * Returns the user query URL formatted correctly, according the engine that is being used
     *
     * @param engine Search Engine
     * @return String
     */
    static String getQuery(SupportedSearchEngine engine, String keyword) {
        if (engine == SupportedSearchEngine.GoogleScholar) {
            return "https://scholar.google.com/scholar?hl=en&q=" + keyword;
        } else if (engine == SupportedSearchEngine.MicrosoftAcademic) {
            return "https://academic.microsoft.com/#/search?iq=@" + keyword + "@&q=" + keyword;
        }
        throw new IllegalArgumentException("Invalid Search Engine");
    }

    /**
     * Returns the base URL of a given search engine
     *
     * @param engine Search Engine
     * @return String
     */
    static String getBaseURL(SupportedSearchEngine engine) {

        if (engine == SupportedSearchEngine.GoogleScholar) {
            return "https://scholar.google.com";
        } else if (engine == SupportedSearchEngine.MicrosoftAcademic) {
            return "https://academic.microsoft.com";
        }
        throw new IllegalArgumentException("Invalid Search Engine");
    }

    /**
     * Returns a URL that the user can use to unlock a proxy
     *
     * @return String
     */
    static String getTestURL(SupportedSearchEngine engine) {
        if (engine == SupportedSearchEngine.GoogleScholar) {
            return "https://scholar.google.com/scholar?q=this+is+the+one&btnG=&hl=en&as_sdt=0%2C39";
        } else if (engine == SupportedSearchEngine.MicrosoftAcademic) {
            return "https://academic.microsoft" +
                    ".com/#/search?iq=@this%20is%20the%20one@&q=this%20is%20the%20one&filters=" +
                    "&from=0&sort=0";
        }
        throw new IllegalArgumentException("Invalid Search Engine");
    }

    /**
     * Gets all the possible search results where the article is cited, based on a URL
     *
     * @return HashSet with all the links
     */
    static Map.Entry<ArrayList<String>, Integer> getAllLinks(String citingPapersURL, String typeOfSearch, boolean
            isMultipleSearch, SupportedSearchEngine engine, Crawler crawler) {
        if (SupportedSearchEngine.GoogleScholar == engine) {
            return getGoogleLinks(typeOfSearch, citingPapersURL, isMultipleSearch, crawler);
        } else if (SearchEngine.SupportedSearchEngine.MicrosoftAcademic == engine) {
            return getmsftAcademicLinks(typeOfSearch, citingPapersURL);
        }
        return new AbstractMap.SimpleEntry<>(new ArrayList<>(), 0);
    }

    private static AbstractMap.SimpleEntry<ArrayList<String>, Integer> getmsftAcademicLinks(String typeOfSearch, String
            citingPapersURL) {

        ArrayList<String> list = new ArrayList<>();
        if (typeOfSearch.equals("searchForCitedBy")) {
            //Add the first result
            list.add(citingPapersURL);
            //Add all possible search results
            for (int i = 8; i < 1000 + 1; i = i + 8) {
                citingPapersURL = citingPapersURL.replaceAll("from=\\d*", "from=" + i);
                citingPapersURL = citingPapersURL.replaceAll("amp;", "");
                list.add(citingPapersURL);
            }
            return new AbstractMap.SimpleEntry<>(list, 0);
        } else {
            //If this is true, then we iterate though all the different versions available plus additional URLs
            String[] array = citingPapersURL.split("∆");
            String versionURL = array[0];
            int counter = 0;
            for (String s : array) {
                if (!list.contains(s) && !s.isEmpty()) {
                    list.add(s);
                    counter++;
                }
            }
            counter--;
            return new AbstractMap.SimpleEntry<>(list, counter);
        }

    }


    static AbstractMap.SimpleEntry<ArrayList<String>, Integer> getGoogleLinks(String typeOfSearch, String
            citingPapersURL,
                                                                              boolean isMultipleSearch, Crawler crawler) {
        ArrayList<String> list = new ArrayList<>();
        Pattern pattern = Pattern.compile("=\\d*");
        Matcher matcher = pattern.matcher(citingPapersURL);
        String paperID = "";
        if (matcher.find()) {
            paperID = matcher.group();
            paperID = paperID.replace("=", "");
        }
        if (typeOfSearch.equals("searchForCitedBy")) {
            //Add the first result
            list.add(citingPapersURL);
            //Add all possible search results
            for (int i = 10; i < 1000 + 1; i = i + 10) {
                String sb = "https://scholar.google.com/scholar?start=" + i +
                        "&hl=en&oe=ASCII&as_sdt=5,39&sciodt=0,39&cites=" + paperID + "&scipsc=";
                list.add(sb);

            }
            return new AbstractMap.SimpleEntry<>(list, 0);
        } else {
            //If this is true, then we iterate though all the different versions available plus additional URLs
            if (citingPapersURL.contains("cluster")) {
                String[] array = citingPapersURL.split("∆");
                String versionURL = array[0];
                int counter = 0;
                try {
                    for (int i = 1; i < 4; i++) {
                        if (!list.contains(array[i])) {
                            if (array[i].contains("scholar")) {
                                MultipleSearchResultsFinder finder = new MultipleSearchResultsFinder(crawler);
                                if (!finder.verifyIfMultipleSearchResult(array[i], isMultipleSearch).isEmpty()) {
                                    list.add(array[i]);
                                    counter++;
                                }
                            } else {
                                list.add(array[i]);
                                counter++;
                            }
                        }
                    }

                } catch (Exception ignored) {
                }

                list.add(versionURL);
                //Add all possible search results
                for (int i = 10; i < 1000 + 1; i = i + 10) {
                    String sb = "https://scholar.google.com/scholar?start=" + i +
                            "&hl=en&as_sdt=0,39&cluster=" + paperID;
                    list.add(sb);

                }
                return new AbstractMap.SimpleEntry<>(list, counter);
            } else {
                //When there is no Version URL from Google Scholar, then we just search for the search result, and
                //the main website URL
                //Add the SR as well as the link of the search result
                String[] array = citingPapersURL.split("∆");
                if (array.length == 2) {
                    list.add(array[1]);
                }
                //Verify if there no multiple search results
                MultipleSearchResultsFinder finder = new MultipleSearchResultsFinder(crawler);
                if (!finder.verifyIfMultipleSearchResult(array[0], isMultipleSearch).isEmpty()) {
                    list.add(array[0]);
                }
                return new AbstractMap.SimpleEntry<>(list, 1);
            }
        }
    }

    /**
     * Checks if there are still search results in the current page
     * @param engine SupportedSearchEngine
     * @param citingPapers Document of the current url
     * @param currUrl
     * @return true if there is at least 1 search result
     */
    static boolean isThereASearchResult(SupportedSearchEngine engine, Document citingPapers, String currUrl) {
        if (engine == SupportedSearchEngine.GoogleScholar) {
            Pattern gScholarSearchResult = Pattern.compile("(<div class=\"gs_r\">)([^∞])+?(?=(<div " +
                    "class=\"gs_r\">)|(<div id=\"gs_ccl_bottom\">))");
            Matcher gScholarSRMatcher = gScholarSearchResult.matcher(citingPapers.html());
            return gScholarSRMatcher.find();

        }
        else if (engine == SupportedSearchEngine.MicrosoftAcademic) {
            if (currUrl.contains(".pdf") || citingPapers == null) return true;
            Pattern msftAcademicSearchResult = Pattern.compile("(<paper-tile)([^∞])+?(?=(</paper-tile))");
            Matcher msftAcademicSRMatcher = msftAcademicSearchResult.matcher(citingPapers.html());
            return msftAcademicSRMatcher.find();
            }
        return false;
    }


    /**
     * Supported search engines
     */
    enum SupportedSearchEngine {
        GoogleScholar, MicrosoftAcademic
    }


}


