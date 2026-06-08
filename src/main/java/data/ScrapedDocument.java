package data;

import java.time.Instant;

/**
 *
 * @author mihdjo
 */
public record ScrapedDocument(
        String url, 
        int charCount, 
        int imgCount, 
        String scrapedBy, // ID that belongs to the worker that scraped
        Instant timestamp // when it was scraped
                          // INFO needed for the audit log
        ) {

    public ScrapedDocument(String url, int charCount, int imgCount, String scrapedBy, Instant timestamp) {
        this.url = url;
        this.charCount = charCount;
        this.imgCount = imgCount;
        this.scrapedBy = scrapedBy;
        this.timestamp = timestamp;
    }
}
