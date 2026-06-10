package workers;

import controller.CrawlerController;
import data.ScrapedDocument;
import java.time.Instant;
import org.jsoup.nodes.Document;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 * @author mihdjo
 */
public class ScraperWorker implements Runnable{
    
    private final String workerId;

    private static final int POLITENESS_DELAY = 1500;
    
    public ScraperWorker(String workerId) {
        this.workerId = workerId;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                String currentURL = CrawlerController.URL_QUEUE.take();
                System.out.println(workerId + " is scraping: " + currentURL);
                
                try{
                    Document doc = (Document) Jsoup.connect(currentURL).timeout(5000).get();
                    
                    int charCount = doc.text().length();
                    int imgCount = doc.select("img").size();
                    
                    ScrapedDocument result = new ScrapedDocument(currentURL, charCount, imgCount, workerId, Instant.now());
                    CrawlerController.RESULTS_QUEUE.put(result);
                    
                    Elements links = (Elements) doc.select("a[href]");
                    
                    for(Element link: links){
                        String nextUrl = link.absUrl("href");
                        
                        if(nextUrl.startsWith("https://en.wikipedia.org/wiki") && !nextUrl.contains("#")) {
                            if (CrawlerController.VISITED_SET.add(nextUrl)){
                                CrawlerController.URL_QUEUE.put(nextUrl);
                            }
                        }
                    }
                } catch (Exception e){
                    System.out.println(workerId + " failed to fetch " + currentURL + " - " + e.getMessage()); 
                }
                
                Thread.sleep(POLITENESS_DELAY);
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            System.out.println(workerId + " was interrupted. Shutting down process.");
        }
    }
}
