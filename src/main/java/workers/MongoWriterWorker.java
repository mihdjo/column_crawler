package workers;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.ReplaceOptions;
import controller.CrawlerController;
import data.ScrapedDocument;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.Document;

/**
 *
 * @author mihdjo
 */
public class MongoWriterWorker implements Runnable{

    private final String workerId;
    private final MongoCollection<Document> collection;
    
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public MongoWriterWorker(String workerId, MongoCollection<Document> collection) {
        this.workerId = workerId;
        this.collection = collection;
    }
    
    
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()){
            try {
                ScrapedDocument scrapedDocument = CrawlerController.RESULTS_QUEUE.take();
                
                saveWithRetry(scrapedDocument);
                
                System.out.println(workerId + " stored document: " + scrapedDocument.url());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                System.out.println(workerId + " was interrupted. Shutting down writer.");
            } catch (Exception e){
                System.out.println(workerId + " failed to write document - " + e.getMessage());
            }
        }
    }

    private void saveWithRetry(ScrapedDocument scrapedDocument) throws InterruptedException {
        int attempt = 0;

        while (true) {
            try {
                Document mongoDocument = toMongoDocument(scrapedDocument);

                collection.replaceOne(
                        eq("url", scrapedDocument.url()),
                        mongoDocument,
                        new ReplaceOptions().upsert(true)
                );

                return;
            } catch (MongoException e){
                attempt++;
                
                if (attempt >= MAX_RETRIES){
                    throw e;
                }
                
                System.out.println(workerId + " Mongo write failed. Retrying attempt"
                    + attempt + "/" + MAX_RETRIES);
                
                Thread.sleep(RETRY_DELAY_MS * attempt);
            } 
        }
    }
    
    private Document toMongoDocument(ScrapedDocument scrapedDocument){
        return new Document("url", scrapedDocument.url())
                .append("charCount", scrapedDocument.charCount())
                .append("imgCount", scrapedDocument.imgCount())
                .append("scrapedBy", scrapedDocument.scrapedBy())
                .append("timestamp", Date.from(scrapedDocument.timestamp()));
    }
}
