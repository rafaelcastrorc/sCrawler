# sCrawler
Smart crawler for academic papers.
Fully scalable solution to find and download PDFs of research papers that cite a given paper, or attempt to find the paper itself and download it.
Works on Windows and OSX.
 
**Main features:**

*There is no limit on the number of papers you can download.

*Allows up to 8 simultaneous downloads.

*Keeps all the crawlers synchronized by using a database.

*Fully scalable!

*Access and parse JavaScript enabled websites.

*Features a full fledged proxy scraper.

*Allows the user to unlock blocked proxies.

*Resume connections if program is closed, or if connection is lost.

*Verifies the integrity of each PDF file.

*Modern GUI designed using JavaFX.

*Reports blocked proxies, and allows the user to unlock them.

 
 
To be able to download multiple papers at the same time without being banned, the program searches for publicly available proxies, and selects the ones that are working.
 
**Screenshots:**

<img src="https://github.com/rafaelcastrorc/sCrawler/blob/master/imgs/p3.png" width="400">
<img src="https://github.com/rafaelcastrorc/sCrawler/blob/master/imgs/p2.png" width="400">
<img src="https://github.com/rafaelcastrorc/sCrawler/blob/master/imgs/p1.png" width="400">
<img src="https://github.com/rafaelcastrorc/sCrawler/blob/master/imgs/p4.png" width="400">

**Program download link:**

https://github.com/rafaelcastrorc/sCrawler/releases 
 
**How does the program avoid getting caught?**

The program uses multiple mechanisms to avoid getting caught, from connecting to websites using a headless browser, which helps to avoid raising red flags, to providing the right cookies, and introducing random pauses between searches. It also keeps track of the number of request send from each proxy to each website, and once one proxy has sent more than 40 request to a given website, it is saved for later use.
 
**How scalable is this program?**

If you need to download 100K+ papers, you can use multiple instances, and thanks to a lot of changes in the new version, you can connect all the instances into a database and keep them all fully synchronized. Furthermore, you can directly send commands to each individual sCrawler instance to perform specific actions, such as updating, cleaning the database, or closing the current instance.
 

**What happens if a website blocks a proxy?**

Usually they just give the message “Please show you are not a robot”. The program can identify all blocked proxies, and will display an Alert whenever there is one. You just press it and solve the captcha, and the proxy will be unlocked after that.
 
**How does it find the proxies?**

The program crawls multiple websites known for hosting list of public proxies, it downloads 1000 every 24 hours, and it looks for new ones in case there are less than 100 proxies left.  
 
**Aren’t free proxies unreliable?**

Yes, a lot of proxies do not work, that is why the program is verifying in the background up to 10 proxies simultaneously, looking for only those that work.
  
**Will my IP get banned from these websites?**

Not at all, as long as you are searching and downloading using proxies, which is the default.
 
