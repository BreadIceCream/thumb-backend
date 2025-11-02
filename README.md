# **👍 BreadThumb \- High-Performance Like Service System**

[中文版本](./README-zh.md)

This project is a high-performance backend service designed to handle a "like" feature, similar to what you might find on platforms like Twitter or TikTok. It's built with Spring Boot and employs a sophisticated architecture using Redis, message queues (Pulsar), and various caching strategies to ensure high availability, data consistency, and real-time responsiveness, even under heavy load.

The system has evolved through three main implementation stages to showcase different architectural approaches for handling high-concurrency scenarios:

1. **1️⃣ Database-Only:** A basic implementation where all like/unlike operations directly read from and write to the MySQL database.  
2. **2️⃣ Redis \+ Scheduled Tasks:** An optimized version that uses Redis to cache like data, with scheduled jobs to periodically synchronize this data back to the database.  
3. **3️⃣ Redis \+ Message Queue:** The most robust implementation, which uses Redis for initial operations and a message queue (Pulsar) to asynchronously persist data to the database, ensuring durability and decoupling.

## **🏛️ Architecture Overview**

The system is built upon a microservices-friendly architecture, leveraging several key technologies to achieve its goals.

### **Core Technologies**

* 🍃 **Framework:** Spring Boot 3  
* 🐬 **Database:** MySQL  
* ⚡ **In-Memory Cache:** Redis (for caching user likes, hot posts, and temporary data)  
* ☕ **Local Cache:** Caffeine (for in-memory caching of frequently accessed blog posts)  
* 📬 **Message Queue:** Apache Pulsar (for asynchronous processing of like/unlike events)  
* 🗺️ **ORM:** MyBatis-Plus  
* 🔥 **Hot Content Detection:** HeavyKeeper algorithm to identify trending posts.  
* 📄 **Data Export:** EasyExcel for handling data exports (e.g., from the dead-letter queue).

### **Project Structure**

The project is organized into standard Maven directories with the following key packages:

* 🎮 controller: Handles incoming HTTP requests for users, blogs, and likes.  
* 🛠️ service: Contains the core business logic. It includes multiple implementations of ThumbService to represent different architectural stages.  
* 🔗 mapper: Defines the database interfaces for MyBatis.  
* 📦 model: Includes entity classes (database tables), DTOs (Data Transfer Objects), and VO (View Objects).  
* 📨 mq: Contains the message queue consumer and event definitions.  
* ⏰ job: Holds scheduled tasks for synchronizing data from Redis to the database.  
* ⚙️ config: Configuration for Redis, MyBatis, Pulsar, and global exception handling.  
* 🔧 util: Utility classes, including the BlogCacheManager and Redis key helpers.  
* 🧱 common: Common classes like the HeavyKeeper implementation and standardized API response formats.

## **🧠 Core Business Logic and Implementation**

### **👍 1\. Like/Unlike Functionality**

The central feature of the system is the ability to like or unlike a blog post. The project provides three distinct implementations for this process, each with its own trade-offs.

#### **ThumbServiceMQImpl (Redis \+ Message Queue) \- ✅ Recommended Approach**

This is the most advanced and resilient implementation. When a user likes a post:

1. **Atomic Redis Operation:** A Lua script is executed on the Redis server to ensure atomicity. This script checks if the user has already liked the post. If not, it adds the user's ID to a Redis Hash associated with the user's likes and increments the like count for the blog post if it's a "hot" post cached in Redis.  
2. **Message Production:** An event (e.g., THUMB\_INCR) is sent to an Apache Pulsar topic. This message contains the userId, blogId, and the timestamp of the event.  
3. **Asynchronous Consumption:** A Pulsar consumer listens to the topic. Upon receiving a message, it processes it in batches. It updates the thumb table in the MySQL database (inserting or deleting records) and updates the thumbCount in the blog table.  
4. **Immediate Feedback:** The user receives an immediate confirmation that their "like" was successful, without waiting for the database write to complete.

**Key Features of this approach:**

* 🚀 **High Throughput:** The system can handle a massive number of write operations because the initial request only involves a quick Redis operation.  
* 💪 **Resilience:** If the database is temporarily unavailable, the message queue will hold the events and retry processing later, preventing data loss.  
* 🛡️ **Data Consistency:** The Key\_Shared subscription type in Pulsar ensures that all events for the same post are processed in order by the same consumer, preventing race conditions.  
* 🪦 **Dead-Letter Queue (DLQ):** If a message consistently fails to be processed, it's moved to a dead-letter topic. A separate listener (consumeDlq) processes these failed messages and archives them into an Excel file for manual inspection.

#### **📜 Lua Scripts for Atomicity**

The use of Lua scripts in Redis is critical. It allows multiple commands to be executed as a single atomic transaction on the Redis server, preventing race conditions. For example, checking if a user has liked a post and then adding their like must be done in one step. The project includes scripts like ThumbMQ.lua and UnthumbMQ.lua for these operations.

### **🔥 2\. Multi-Level Caching for Hot Content**

To reduce database load for read operations, the system employs a multi-level caching strategy for blog posts.

#### **BlogCacheManager**

This utility class manages the caching logic:

1. ☕ **Local Cache (Caffeine):** A high-speed in-memory cache stores the most frequently accessed blog posts. This is the first place the system looks for a blog post.  
2. ⚡ **Remote Cache (Redis):** If the post is not in the local cache, the system checks Redis. Redis stores "hot" blog posts—those that have been recently liked or viewed frequently.  
3. 🐬 **Database:** If the post is not found in either cache, it's fetched from the MySQL database.

#### **🔥 Hot Content Detection with HeavyKeeper**

The system uses the **HeavyKeeper** algorithm to identify which blog posts are "hot" or trending.

* HeavyKeeper is a probabilistic algorithm that can efficiently find the most frequent items in a massive data stream with minimal memory usage.  
* Every time a blog post is accessed, its ID is fed into the HeavyKeeper instance.  
* A scheduled job (syncHotBlog2Redis) periodically queries HeavyKeeper for the top K trending posts and caches them in Redis. This ensures that the Redis cache is always populated with the most relevant content, improving read performance.

### **💾 3\. Data Persistence and Synchronization**

#### **Asynchronous Persistence via Message Queue**

As described above, the primary method for data persistence in the recommended architecture is through the message queue. The ThumbConsumer is responsible for batch processing messages and updating the database. This decouples the write operation from the user request and allows for graceful handling of database load.

#### **Scheduled Jobs for Data Synchronization (ThumbServiceRedisImpl)**

In an alternative architecture provided in the project, scheduled jobs are used for synchronization.

* **Temporary Data in Redis:** When a user likes a post, the like information is stored in a temporary Redis Hash, keyed by a time slice (e.g., a 10-second window).  
* **Periodic Sync:** A scheduled job (SyncThumb2DBJob) runs every 10 seconds. It reads the data from the previous time slice in Redis.  
* **Batch Database Update:** The job then performs batch updates to the MySQL database—inserting new thumb records and updating the thumbCount on the blog table.  
* **Compensatory Job:** A daily compensatory job (SyncThumb2DBCompensatoryJob) runs to ensure that any data that might have been missed by the regular job is processed, guaranteeing eventual consistency.

This approach is simpler than a message queue but is less resilient to database downtime and can introduce slight delays in data persistence.

## **🚀 Getting Started**

To run this project, you will need:

* ☕ Java 21  
* 📦 Maven  
* 🐬 MySQL  
* ⚡ Redis  
* 📬 Apache Pulsar

**🔧 Configuration:**

1. Set up your MySQL database and run the schema defined in table-ddl.sql.  
2. Update the application.yaml file with your database, Redis, and Pulsar connection details.  
3. Run the BreadThumbApplication main class to start the server.