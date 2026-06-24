package controller;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import data.ScrapedDocument;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import workers.MongoWriterWorker;
import workers.ScraperWorker;

/**
 *
 * @author mihdjo
 */
public class CrawlerController {
    
    public static final BlockingQueue<String> URL_QUEUE = new LinkedBlockingQueue<>();
    
    public static final Set<String> VISITED_SET = ConcurrentHashMap.newKeySet();
    
    public static final BlockingQueue<ScrapedDocument> RESULTS_QUEUE = new LinkedBlockingQueue<>();
    
    public static final int MAX_ARTICLES = 25;
    
    public static final AtomicInteger ACCEPTED_ARTICLE_COUNT = new AtomicInteger(0);
    
    public static final AtomicInteger STORED_DOCUMENT_COUNT = new AtomicInteger(0);
    
    public static final Object URL_LIMIT_LOCK = new Object();
    
    public static void main(String[] args){
        String seedURL = "https://en.wikipedia.org/wiki/Ayrton_Senna";
        
        URL_QUEUE.add(seedURL);
        VISITED_SET.add(seedURL);
        ACCEPTED_ARTICLE_COUNT.incrementAndGet();

        System.out.println("Initializing Column Crawler engine....");

        String mongoURI = "mongodb://mongoadmin:student1@localhost:27017/?authSource=admin";

        try (MongoClient mongoClient = MongoClients.create(mongoURI)) {
            MongoDatabase database = mongoClient.getDatabase("column_crawler");
            MongoCollection<Document> collection = database.getCollection("scraped_documents");

            collection.createIndex(
                    Indexes.ascending("url"),
                    new IndexOptions().unique(true)
            );

            int numberOfScrapers = 5;
            int numberOfMongoWriters = 3;

            ExecutorService scraperPool = Executors.newFixedThreadPool(numberOfScrapers);
            ExecutorService mongoWriterPool = Executors.newFixedThreadPool(numberOfMongoWriters);

            for (int i = 1; i <= numberOfScrapers; i++) {
                String workerId = "SCRAPER THREAD " + i;
                scraperPool.execute(new ScraperWorker(workerId));
            }

            for (int i = 1; i <= numberOfMongoWriters; i++) {
                String workerId = "MONGO WRITER " + i;
                mongoWriterPool.execute(new MongoWriterWorker(workerId, collection));
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n Shutdown signal received. Stopping crawler...");
                scraperPool.shutdownNow();
                mongoWriterPool.shutdownNow();
            }));

            System.out.println(numberOfScrapers + " scraper threads started successfully!");
            System.out.println(numberOfMongoWriters + " Mongo writer threads started successfully!");
            System.out.println("Crawler running!!!\n");

            while (STORED_DOCUMENT_COUNT.get() < MAX_ARTICLES) {
                Thread.sleep(500);
            }

            System.out.println("\nArticle limit reached. Stopping crawler...");

            scraperPool.shutdownNow();
            mongoWriterPool.shutdownNow();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Main thread interrupted.");
        } catch (MongoException e) {
            System.out.println("MongoDB error: " + e.getMessage());
        }
    }
}
