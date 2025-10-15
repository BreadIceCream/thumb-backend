package com.bread.breadthumb.common;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HeavyKeeper算法实现（线程安全版本）
 * 用于高效识别数据流中的Top-K频繁项
 * 支持高并发场景下的线程安全操作
 */
public class HeavyKeeper {

    // Bucket类：存储元素指纹和计数（使用原子操作）
    static class Bucket {
        private volatile int fingerprint;  // 元素的指纹（使用volatile保证可见性）
        private final AtomicInteger count; // 原子计数器

        public Bucket() {
            this.fingerprint = 0;
            this.count = new AtomicInteger(0);
        }

        public int getFingerprint() {
            return fingerprint;
        }

        public void setFingerprint(int fingerprint) {
            this.fingerprint = fingerprint;
        }

        public int getCount() {
            return count.get();
        }

        public int incrementAndGet() {
            return count.incrementAndGet();
        }

        public int decrementAndGet() {
            return count.decrementAndGet();
        }

        public void setCount(int value) {
            count.set(value);
        }

        public boolean compareAndSetCount(int expect, int update) {
            return count.compareAndSet(expect, update);
        }
    }

    // 结果类：用于返回Top-K元素
    public static class Item implements Comparable<Item> {
        final String key;
        final int count;

        public Item(String key, int count) {
            this.key = key;
            this.count = count;
        }

        public String getKey() {
            return key;
        }

        public int getCount() {
            return count;
        }

        @Override
        public int compareTo(Item other) {
            // 小根堆：count小的在堆顶
            return Integer.compare(this.count, other.count);
        }

        @Override
        public String toString() {
            return key + ": " + count;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Item item = (Item) o;
            return Objects.equals(key, item.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key);
        }
    }

    private final int width;      // 每行的bucket数量
    private final int depth;      // 行数（哈希函数数量）
    private final double decay;   // 衰减概率参数b
    private final Bucket[][] buckets;
    private final PriorityQueue<Item> minHeap; // 小根堆维护Top-K
    private final Map<String, Item> heapMap;   // 快速查找元素在堆中的位置
    private final int k;          // Top-K的K值

    // 读写锁：buckets使用行级锁，minHeap使用全局读写锁
    private final ReentrantReadWriteLock[] rowLocks;  // 每行一个锁
    private final ReentrantReadWriteLock heapLock;    // 堆的读写锁

    /**
     * 构造函数
     * @param width 每行的bucket数量
     * @param depth 行数（哈希函数数量）
     * @param k Top-K的K值
     * @param decay 衰减参数b（通常取1.08）
     */
    public HeavyKeeper(int width, int depth, int k, double decay) {
        this.width = width;
        this.depth = depth;
        this.k = k;
        this.decay = decay;
        this.buckets = new Bucket[depth][width];
        this.minHeap = new PriorityQueue<>();  // 小根堆
        this.heapMap = new HashMap<>();        // 辅助快速查找

        // 初始化行级锁
        this.rowLocks = new ReentrantReadWriteLock[depth];
        for (int i = 0; i < depth; i++) {
            rowLocks[i] = new ReentrantReadWriteLock();
        }

        // 初始化堆锁
        this.heapLock = new ReentrantReadWriteLock();

        // 初始化所有bucket
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                buckets[i][j] = new Bucket();
            }
        }
    }

    /**
     * 添加元素到HeavyKeeper（线程安全）
     * @param item 要添加的元素
     * @return 是否添加到TopK当中
     */
    public boolean add(String item) {
        int fingerprint = getFingerprint(item);
        int minCount = Integer.MAX_VALUE;

        // 遍历每一行，使用行级锁
        for (int i = 0; i < depth; i++) {
            int pos = hash(item, i) % width;

            // 获取行级写锁
            rowLocks[i].writeLock().lock();
            try {
                Bucket bucket = buckets[i][pos];
                int currentCount = bucket.getCount();
                int currentFingerprint = bucket.getFingerprint();

                if (currentCount == 0) {
                    // Bucket为空，直接插入
                    bucket.setFingerprint(fingerprint);
                    bucket.setCount(1);
                    minCount = Math.min(minCount, 1);
                } else if (currentFingerprint == fingerprint) {
                    // 指纹匹配，增加计数
                    int newCount = bucket.incrementAndGet();
                    minCount = Math.min(minCount, newCount);
                } else {
                    // 指纹不匹配，进行概率衰减
                    if (shouldDecay(currentCount)) {
                        int newCount = bucket.decrementAndGet();
                        if (newCount == 0) {
                            // 如果衰减到0，插入新元素
                            bucket.setFingerprint(fingerprint);
                            bucket.setCount(1);
                            minCount = Math.min(minCount, 1);
                        } else {
                            minCount = Math.min(minCount, newCount);
                        }
                    } else {
                        minCount = Math.min(minCount, currentCount);
                    }
                }
            } finally {
                rowLocks[i].writeLock().unlock();
            }
        }

        // 更新minHeap（Top-K维护）
        return updateMinHeap(item, minCount);
    }

    /**
     * 获取元素的指纹（使用hashCode的高位）
     */
    private int getFingerprint(String item) {
        return Math.abs(item.hashCode()) >>> 16;
    }

    /**
     * 第i个哈希函数
     */
    private int hash(String item, int i) {
        int hash = item.hashCode();
        // 使用不同的种子生成不同的哈希值
        return Math.abs(hash ^ (i * 0x9e3779b9));
    }

    /**
     * 判断是否应该衰减（使用ThreadLocalRandom保证线程安全）
     * 衰减概率为 b^(-count)
     */
    private boolean shouldDecay(int count) {
        double probability = Math.pow(decay, -count);
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    /**
     * 更新MinHeap（维护Top-K）- 线程安全版本
     * 使用小根堆：堆顶是count最小的元素
     * @param item 要添加的元素
     * @return 是否为Top-K元素，即是否添加到堆中
     */
    private boolean updateMinHeap(String item, int count) {
        heapLock.writeLock().lock();
        try {
            Item existingItem = heapMap.get(item);
            if (existingItem != null) {
                // 元素已在堆中，需要更新
                if (count > existingItem.count) {
                    // 从堆中移除旧的Item
                    minHeap.remove(existingItem);
                    // 创建新的Item并添加
                    Item newItem = new Item(item, count);
                    minHeap.offer(newItem);
                    heapMap.put(item, newItem);
                }
            } else {
                // 元素不在堆中
                if (minHeap.size() < k) {
                    // 堆未满，直接添加
                    Item newItem = new Item(item, count);
                    minHeap.offer(newItem);
                    heapMap.put(item, newItem);
                } else {
                    // 堆已满，检查是否需要替换堆顶
                    Item minItem = minHeap.peek();
                    if (minItem != null && count > minItem.count) {
                        // 移除堆顶（最小元素）
                        minHeap.poll();
                        heapMap.remove(minItem.key);
                        // 添加新元素
                        Item newItem = new Item(item, count);
                        minHeap.offer(newItem);
                        heapMap.put(item, newItem);
                    }
                }
            }
            return heapMap.containsKey(item);
        } finally {
            heapLock.writeLock().unlock();
        }
    }

    /**
     * 判断元素是否在Top-K (MinHeap)中
     * @param item
     * @return
     */
    private boolean isTopK(String item){
        heapLock.readLock().lock();
        try {
            return heapMap.containsKey(item);
        }finally {
            heapLock.readLock().unlock();
        }
    }

    /**
     * 查询元素的估计频率（线程安全）
     */
    public int query(String item) {
        int fingerprint = getFingerprint(item);
        int minCount = Integer.MAX_VALUE;

        for (int i = 0; i < depth; i++) {
            int pos = hash(item, i) % width;

            // 获取行级读锁
            rowLocks[i].readLock().lock();
            try {
                Bucket bucket = buckets[i][pos];
                if (bucket.getFingerprint() == fingerprint) {
                    minCount = Math.min(minCount, bucket.getCount());
                }
            } finally {
                rowLocks[i].readLock().unlock();
            }
        }

        return minCount == Integer.MAX_VALUE ? 0 : minCount;
    }

    /**
     * 获取Top-K元素（按频率降序）- 线程安全版本
     */
    public List<Item> getTopK() {
        heapLock.readLock().lock();
        try {
            List<Item> result = new ArrayList<>(minHeap);
            // 按count降序排序
            result.sort((a, b) -> Integer.compare(b.count, a.count));
            return result;
        } finally {
            heapLock.readLock().unlock();
        }
    }

    /**
     * 打印当前状态（用于调试）- 线程安全版本
     */
    public void printStatus() {
        System.out.println("=== HeavyKeeper状态 ===");
        System.out.println("配置: width=" + width + ", depth=" + depth +
                ", k=" + k + ", decay=" + decay);
        System.out.println("\nTop-" + k + "元素:");
        List<Item> topK = getTopK();
        for (Item item : topK) {
            System.out.println("  " + item);
        }
    }

}