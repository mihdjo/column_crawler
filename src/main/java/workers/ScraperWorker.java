package workers;

import controller.CrawlerController;
import data.ScrapedDocument;
import java.time.Instant;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 * @author mihdjo
 */
public class ScraperWorker implements Runnable {

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

                try {
                    Document doc = Jsoup.connect(currentURL)
                            .userAgent("ColumnCrawler/1.0")
                            .timeout(5000)
                            .get();

                    int charCount = doc.text().length();
                    int imgCount = doc.select("img").size();

                    ScrapedDocument result = new ScrapedDocument(
                            currentURL,
                            charCount,
                            imgCount,
                            workerId,
                            Instant.now()
                    );

                    CrawlerController.RESULTS_QUEUE.put(result);

                    Elements links = doc.select("a[href]");

                    int addedLinks = 0;

                    for (Element link : links) {
                        String nextUrl = normalizeUrl(link.absUrl("href"));

                        if (isAllowedWikipediaArticle(nextUrl)) {
                            if (tryAddUrl(nextUrl)) {
                                CrawlerController.URL_QUEUE.put(nextUrl);
                                addedLinks++;
                            }
                        }
                    }

                    if (addedLinks > 0) {
                        System.out.println(workerId + " added " + addedLinks + " new URLs from " + currentURL);
                    } else if (CrawlerController.ACCEPTED_ARTICLE_COUNT.get() >= CrawlerController.MAX_ARTICLES) {
                        System.out.println(workerId + " reached article limit. No new URLs accepted from " + currentURL);
                    } else {
                        System.out.println(workerId + " found no new valid URLs from " + currentURL);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();

                } catch (Exception e) {
                    System.out.println(workerId + " failed to fetch "
                            + currentURL + " - " + e.getMessage());
                }

                Thread.sleep(POLITENESS_DELAY);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(workerId + " was interrupted. Shutting down process.");
        }
    }

    private String normalizeUrl(String url) {
        int hashIndex = url.indexOf("#");

        if (hashIndex != -1) {
            url = url.substring(0, hashIndex);
        }

        int queryIndex = url.indexOf("?");

        if (queryIndex != -1) {
            url = url.substring(0, queryIndex);
        }

        return url;
    }

    private boolean isAllowedWikipediaArticle(String url) {
        String prefix = "https://en.wikipedia.org/wiki/";

        if (!url.startsWith(prefix)) {
            return false;
        }

        String title = url.substring(prefix.length());

        if (title.isBlank()) {
            return false;
        }

        if (title.contains(":")) {
            return false;
        }

        if (title.equals("Main_Page")) {
            return false;
        }

        return true;
    }
    
    private boolean tryAddUrl(String nextUrl) {
        synchronized (CrawlerController.URL_LIMIT_LOCK) {
            if (CrawlerController.ACCEPTED_ARTICLE_COUNT.get() >= CrawlerController.MAX_ARTICLES) {
                return false;
            }

            if (!CrawlerController.VISITED_SET.add(nextUrl)) {
                return false;
            }

            CrawlerController.ACCEPTED_ARTICLE_COUNT.incrementAndGet();
            return true;
        }
    }
}