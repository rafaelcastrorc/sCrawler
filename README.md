# sCrawler
Smart crawler for academic papers. 
Can find and download PDFs of research papers that cite a given paper, or attempt to finding the paper itself and download it. the program uses proxies to search and download, and verifies that each proxy works in the background. Furhtermore, the program can download files simultaneously, can resume downloads, download the files that failed to download and be paused automatically in case connection is lost.

The proxies are download form different public sites, and the crawler is constantly finding new ones in the background.The program also analyzes the integrity of the file download, and creates a report file with the location of each file. sCrawler can parse most websites, including javascrip enabled. It performs slow searches, introducing random pauses between searches, as well as random searches through Google. In case a proxy is blocked, the program can identify it and show it to the user so that he or she can solve the captcha. Also the program can email progress reports, as well as blocked proxies, to the user. 
