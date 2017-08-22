# sCrawler
Smart crawler for academic papers.
Can find and download PDFs of research papers that cite a given paper, or attempt to find the paper itself and download it.
Works on Windows and OSX.
 
**Main features:

*There is no limit on the number of papers you can download; however, we do recommend not using more than 2 instance of the program to avoid getting proxies blocked.

*Allows up to 8 simultaneous downloads.

*Access and parse JavaScript enabled websites.

*Allows the user to unlock blocked proxies.

*Resume connections if program is closed, or if connection is lost.

*Verifies the integrity of each PDF file.

*Modern GUI designed using JavaFX.

*Email progress reports and blocked proxies.

 
 
To be able to download multiple papers at the same time without being banned, the program searches for publicly available proxies, and selects the ones that are working.
 
**Screenshots:
 
 
**Program download link:

https://github.com/blog/1547-release-your-software\
 
 
**How does the program avoid getting caught?

The program uses multiple mechanisms to avoid getting caught, from connecting to websites using a headless browser, which helps to avoid raising red flags, to providing the right cookies, and introducing random pauses between searches. It also keeps track of the number of request send from each proxy to each website, and once one proxy has sent more than 40 request to a given website, it is saved for later use.
 
 
**What happens if a website blocks a proxy?

Usually they just give the message “Please show you are not a robot”. The program can identify all blocked proxies, and will display an Alert whenever there is one. You just press it and solve the captcha, and the proxy will be unlocked after that.
 
**How does it find the proxies?

The program crawls multiple websites known for hosting list of public proxies, it downloads 1000 every 24 hours, and it looks for new ones in case there are less than 100 proxies left.  
 
**Aren’t free proxies unreliable?

Yes, a lot of proxies do not work, that is why the program is verifying in the background up to 10 proxies simultaneously, looking for only those that work.
  
**Will my IP get banned from these websites?

Not at all, as long as you are searching and downloading using proxies, which is the default.
 
