package controller;

import data.ScrapedDocument;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

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
        
        //TODO: zapocni scraper workere 
        //TODO: zapocni mongo writer workere
    }
}
