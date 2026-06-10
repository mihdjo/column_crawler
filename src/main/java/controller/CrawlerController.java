package controller;

import data.ScrapedDocument;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import workers.ScraperWorker;

/**
 *
 * @author mihdjo
 */
public class CrawlerController {
    
    public static final BlockingQueue<String> URL_QUEUE = new LinkedBlockingQueue<>();
    
    public static final Set<String> VISITED_SET = ConcurrentHashMap.newKeySet();
    
    public static final BlockingQueue<ScrapedDocument> RESULTS_QUEUE = new LinkedBlockingQueue<>();
    
    public static void main(String[] args){
        String seedURL = "https://en.wikipedia.org/wiki/Ayrton_Senna";
        
        URL_QUEUE.add(seedURL);
        VISITED_SET.add(seedURL);
        
        System.out.println("Initializing Column Crawler engine....");
        
        int numberOfScrapers = 3; 
        ExecutorService scraperPool = Executors.newFixedThreadPool(numberOfScrapers);
        
        for(int i = 1; i <= numberOfScrapers; i++){
            String workerId = "SCRAPER THREAD " + i;
            scraperPool.execute(new ScraperWorker(workerId));
        }
        
        System.out.println(numberOfScrapers + " scraper threads started successfully! \n");
        
        while(true){
            try{
                ScrapedDocument doc = RESULTS_QUEUE.take();
                
                System.out.println("\n--------- DATA CAPTURED ---------");
                System.out.println("- URL: " + doc.url() + " -");
                System.out.println("- CHARACTERS: " + doc.charCount() + " -");
                System.out.println("- IMAGES: " + doc.imgCount() + " -");
                System.out.println("- SCRAPED BY: " + doc.scrapedBy() + " -");
                System.out.println("- TIMESTAMP: " + doc.timestamp() + " -");
                System.out.println("-----------------------------------");
            } catch (Exception e){
                System.out.println("Main testing thread interrupted.");
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // ponasanje iznad simulira upis u bazu
        // TODO mongo writeri su preostali
    }
}
