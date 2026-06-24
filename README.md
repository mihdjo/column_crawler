# Column Crawler

Column Crawler is a concurrent web crawler written in Java.

The application starts from a seed Wikipedia article, extracts metadata from visited pages, discovers new Wikipedia article links, and stores the scraped results in a MongoDB database.

The project demonstrates the use of Java concurrency mechanisms such as worker threads, blocking queues, thread pools, concurrent collections, atomic counters, and producer-consumer communication.

## Project Overview

The crawler is designed around a producer-consumer architecture.

Scraper workers consume URLs from a shared URL queue, fetch the corresponding Wikipedia pages, extract metadata, and place the results into a shared results queue. Mongo writer workers consume scraped documents from the results queue and persist them into MongoDB.

The application currently limits the crawl to 25 accepted Wikipedia articles.

## Technologies Used

* Java 
* Jsoup
* MongoDB
* MongoDB Java Driver
* Docker
* Java `ExecutorService`
* Java `BlockingQueue`
* Java `ConcurrentHashMap`
* Java `AtomicInteger`

## Architecture

The application follows this pipeline:

```text
URL_QUEUE → ScraperWorker → RESULTS_QUEUE → MongoWriterWorker → MongoDB
```

### Main Components

* `URL_QUEUE` stores discovered URLs that still need to be scraped.
* `VISITED_SET` stores already accepted URLs and prevents duplicate crawling.
* `RESULTS_QUEUE` stores scraped metadata waiting to be written to MongoDB.
* `ScraperWorker` fetches web pages and extracts metadata.
* `MongoWriterWorker` writes scraped metadata into MongoDB.
* `CrawlerController` initializes the crawler, starts worker pools, and controls shutdown.
* `ScrapedDocument` represents the scraped page metadata.

## Concurrent Design Explanation

The crawler is built using two separate worker groups.

The first group consists of scraper workers. These workers take URLs from `URL_QUEUE`, scrape the corresponding Wikipedia pages, extract metadata, and produce `ScrapedDocument` objects. These objects are then placed into `RESULTS_QUEUE`.

The second group consists of Mongo writer workers. These workers take `ScrapedDocument` objects from `RESULTS_QUEUE` and write them into MongoDB.

This separates the scraping logic from the database writing logic. Scraping is mostly network-bound, while MongoDB writing is database-bound. By separating these responsibilities, the application can continue scraping pages while other threads are writing results to the database.

The communication between workers is handled through `BlockingQueue`, which allows threads to safely wait when there is no work available. Shared data such as visited URLs and article counters is protected using concurrent data structures and atomic counters.

The crawler also uses a maximum article limit to prevent infinite crawling. Once 25 articles are accepted and stored, the application shuts down automatically.

## Java Classes

## CrawlerController

`CrawlerController` is the main entry point of the application.

Its responsibilities are:

* defining shared queues and concurrent data structures
* adding the initial seed URL
* creating the MongoDB connection
* creating a unique index on the `url` field
* starting scraper and Mongo writer thread pools
* monitoring the number of stored documents
* shutting down the application after the article limit is reached

Important shared structures:

```java
public static final BlockingQueue<String> URL_QUEUE = new LinkedBlockingQueue<>();

public static final Set<String> VISITED_SET = ConcurrentHashMap.newKeySet();

public static final BlockingQueue<ScrapedDocument> RESULTS_QUEUE = new LinkedBlockingQueue<>();
```

The crawler uses `MAX_ARTICLES` to control how many articles are accepted into the crawling process:

```java
public static final int MAX_ARTICLES = 25;
```

Two atomic counters are used:

```java
public static final AtomicInteger ACCEPTED_ARTICLE_COUNT = new AtomicInteger(0);

public static final AtomicInteger STORED_DOCUMENT_COUNT = new AtomicInteger(0);
```

`ACCEPTED_ARTICLE_COUNT` tracks how many URLs have been accepted for crawling.

`STORED_DOCUMENT_COUNT` tracks how many scraped documents have been stored in MongoDB.

The controller starts two thread pools:

```java
ExecutorService scraperPool = Executors.newFixedThreadPool(numberOfScrapers);

ExecutorService mongoWriterPool = Executors.newFixedThreadPool(numberOfMongoWriters);
```

The scraper pool is responsible for fetching and processing pages.

The Mongo writer pool is responsible for writing scraped metadata to MongoDB.

## ScraperWorker

`ScraperWorker` is responsible for fetching and processing Wikipedia pages.

Each scraper worker:

    1. takes a URL from `URL_QUEUE`
    2. downloads the page using Jsoup
    3. calculates the character count
    4. counts the number of images
    5. creates a `ScrapedDocument`
    6. places the result into `RESULTS_QUEUE`
    7. extracts new Wikipedia article links
    8. adds new links to the URL queue if the article limit has not been reached

The worker fetches pages using Jsoup:

```java
Document doc = Jsoup.connect(currentURL)
        .userAgent("ColumnCrawler/1.0")
        .timeout(5000)
        .get();
```

The extracted metadata includes:

```java
int charCount = doc.text().length();
int imgCount = doc.select("img").size();
```

A `ScrapedDocument` is then created:

```java
ScrapedDocument result = new ScrapedDocument(
        currentURL,
        charCount,
        imgCount,
        workerId,
        Instant.now()
);
```

The result is placed into the results queue:

```java
CrawlerController.RESULTS_QUEUE.put(result);
```

The worker also extracts links from the current page:

```java
Elements links = doc.select("a[href]");
```

Only valid Wikipedia article links are accepted. The crawler ignores pages such as:

```text
/wiki/Special:Random
/wiki/Wikipedia:About
/wiki/File:Example.jpg
/wiki/Help:Contents
/wiki/Portal:Current_events
```

This is done by rejecting article titles that contain `:`.

A politeness delay is used between requests:

```java
private static final int POLITENESS_DELAY = 1500;
```

This prevents the crawler from sending requests too aggressively.

## MongoWriterWorker

`MongoWriterWorker` consumes scraped documents from `RESULTS_QUEUE` and writes them to MongoDB.

Each Mongo writer:

    1. takes a `ScrapedDocument` from the results queue
    2. converts it into a MongoDB `Document`
    3. writes it to the `scraped_documents` collection
    4. increments the stored document counter

The writer waits for scraped documents using:

```java
ScrapedDocument scrapedDocument = CrawlerController.RESULTS_QUEUE.take();
```

The scraped document is converted into a MongoDB document:

```java
private Document toMongoDocument(ScrapedDocument scrapedDocument) {
    return new Document("url", scrapedDocument.url())
            .append("charCount", scrapedDocument.charCount())
            .append("imgCount", scrapedDocument.imgCount())
            .append("scrapedBy", scrapedDocument.scrapedBy())
            .append("timestamp", Date.from(scrapedDocument.timestamp()));
}
```

The writer uses `replaceOne` with `upsert(true)`:

```java
collection.replaceOne(
        eq("url", scrapedDocument.url()),
        mongoDocument,
        new ReplaceOptions().upsert(true)
);
```

This means that if a document with the same URL already exists, it is updated.

If it does not exist, a new document is inserted.

This prevents duplicate entries when the crawler is run multiple times.

The writer also contains retry logic. If a MongoDB write fails, the worker retries the operation a limited number of times before reporting the error.

## ScrapedDocument

`ScrapedDocument` is a Java record used to represent scraped metadata.

It contains:

```java
String url
int charCount
int imgCount
String scrapedBy
Instant timestamp
```

Example:

```java
public record ScrapedDocument(
        String url,
        int charCount,
        int imgCount,
        String scrapedBy,
        Instant timestamp
) {
}
```

Example stored document:

```json
{
  "url": "https://en.wikipedia.org/wiki/Ayrton_Senna",
  "charCount": 157962,
  "imgCount": 148,
  "scrapedBy": "SCRAPER THREAD 1",
  "timestamp": "2026-06-23T16:08:23.844Z"
}
```

## Concurrency Concepts Used

## Producer-Consumer Pattern

The project uses the producer-consumer pattern.

Scraper workers act as producers because they produce scraped metadata.

Mongo writer workers act as consumers because they consume scraped metadata and store it in MongoDB.

```text
ScraperWorker produces ScrapedDocument objects.
MongoWriterWorker consumes ScrapedDocument objects.
```

The communication between them is handled through `RESULTS_QUEUE`.

## BlockingQueue

`BlockingQueue` allows worker threads to wait safely when there is no available work.

Scraper workers wait on:

```java
URL_QUEUE.take();
```

Mongo writer workers wait on:

```java
RESULTS_QUEUE.take();
```

This avoids busy waiting and allows threads to block efficiently.

## ConcurrentHashMap.newKeySet()

The visited URL set is shared between multiple scraper threads.

```java
public static final Set<String> VISITED_SET = ConcurrentHashMap.newKeySet();
```

This allows safe concurrent access and prevents multiple threads from adding the same URL at the same time.

## AtomicInteger

Atomic counters are used for tracking accepted and stored articles safely across multiple threads.

```java
ACCEPTED_ARTICLE_COUNT
STORED_DOCUMENT_COUNT
```

`ACCEPTED_ARTICLE_COUNT` is used to limit the number of accepted URLs.

`STORED_DOCUMENT_COUNT` is used to determine when the crawler should stop.

## ExecutorService

The application uses fixed thread pools:

```java
ExecutorService scraperPool = Executors.newFixedThreadPool(numberOfScrapers);

ExecutorService mongoWriterPool = Executors.newFixedThreadPool(numberOfMongoWriters);
```

This allows the application to run multiple scraper workers and multiple Mongo writer workers concurrently.

## Synchronization

The article limit is protected using a lock:

```java
public static final Object URL_LIMIT_LOCK = new Object();
```

This ensures that multiple scraper threads cannot accept more URLs than the defined maximum article limit.

The limit-checking logic is synchronized so that only one thread can reserve a new URL slot at a time.

## MongoDB Storage

The application stores scraped documents in:

```text
Database: column_crawler
Collection: scraped_documents
```

A unique index is created on the `url` field:

```java
collection.createIndex(
        Indexes.ascending("url"),
        new IndexOptions().unique(true)
);
```

This ensures that each URL appears only once in the collection.

## Example Console Output

```text
Initializing Column Crawler engine....
5 scraper threads started successfully!
3 Mongo writer threads started successfully!
Crawler running!!!

SCRAPER THREAD 1 is scraping: https://en.wikipedia.org/wiki/Ayrton_Senna
SCRAPER THREAD 1 added 24 new URLs from https://en.wikipedia.org/wiki/Ayrton_Senna
MONGO WRITER 1 stored document: https://en.wikipedia.org/wiki/Ayrton_Senna (1/25)
MONGO WRITER 2 stored document: https://en.wikipedia.org/wiki/Formula_One (2/25)

Article limit reached. Stopping crawler...
```

## Example MongoDB Document

```json
{
  "_id": {
    "$oid": "6a3aac9b7a34eb51a1a97d0b"
  },
  "url": "https://en.wikipedia.org/wiki/Ayrton_Senna",
  "charCount": 157962,
  "imgCount": 148,
  "scrapedBy": "SCRAPER THREAD 1",
  "timestamp": {
    "$date": "2026-06-23T16:08:23.844Z"
  }
}
```

## Future Improvements

Possible improvements include:

* adding configuration for seed URL and article limit
* adding failed scrape tracking
* storing additional metadata such as page title, description, headings, and outgoing link count
* adding support for multiple seed URLs
* adding unit tests for URL filtering and normalization

