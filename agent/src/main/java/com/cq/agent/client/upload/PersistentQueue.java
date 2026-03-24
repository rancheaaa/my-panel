package com.cq.agent.client.upload;

import com.google.gson.Gson;
import org.rocksdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class PersistentQueue<T> {

    private static final Logger logger = LoggerFactory.getLogger(PersistentQueue.class);

    private final RocksDB db;
    private final ReentrantLock lock = new ReentrantLock();
    private final String queueName;
    private final Gson gson;
    private final Class<T> type;
    private long headIndex = 0;
    private long tailIndex = 0;

    private static final String HEAD_KEY = "head";
    private static final String TAIL_KEY = "tail";
    private static final String DATA_PREFIX = "data:";

    public PersistentQueue(String dbPath, String queueName, Class<T> type) throws RocksDBException {
        this.queueName = queueName;
        this.type = type;
        this.gson = new Gson();
        
        DBOptions options = new DBOptions();
        options.setCreateIfMissing(true);
        options.setCreateMissingColumnFamilies(true);
        
        List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();
        columnFamilyDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, new ColumnFamilyOptions()));
        List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
        
        try {
            this.db = RocksDB.open(options, dbPath, columnFamilyDescriptors, columnFamilyHandles);
            
            ColumnFamilyHandle defaultHandle = columnFamilyHandles.get(0);
            
            byte[] headBytes = db.get(defaultHandle, HEAD_KEY.getBytes(StandardCharsets.UTF_8));
            if (headBytes != null) {
                this.headIndex = bytesToLong(headBytes);
            }
            
            byte[] tailBytes = db.get(defaultHandle, TAIL_KEY.getBytes(StandardCharsets.UTF_8));
            if (tailBytes != null) {
                this.tailIndex = bytesToLong(tailBytes);
            }
            
            logger.info("PersistentQueue '{}' initialized: head={}, tail={}, size={}", 
                    queueName, headIndex, tailIndex, size());
        } catch (RocksDBException e) {
            logger.error("Failed to open RocksDB for queue '{}': {}", queueName, e.getMessage(), e);
            throw e;
        }
    }

    public boolean offer(T item) {
        lock.lock();
        try {
            byte[] data = serialize(item);
            String key = DATA_PREFIX + tailIndex;
            db.put(key.getBytes(StandardCharsets.UTF_8), data);
            tailIndex++;
            saveHeadTail();
            logger.debug("Queue '{}' offered item at index {}, new tail={}", queueName, tailIndex - 1, tailIndex);
            return true;
        } catch (Exception e) {
            logger.error("Failed to offer item to queue '{}': {}", queueName, e.getMessage(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public T poll() {
        lock.lock();
        try {
            if (headIndex >= tailIndex) {
                return null;
            }
            
            String key = DATA_PREFIX + headIndex;
            byte[] data = db.get(key.getBytes(StandardCharsets.UTF_8));
            
            if (data != null) {
                T item = deserialize(data);
                db.delete(key.getBytes(StandardCharsets.UTF_8));
                headIndex++;
                saveHeadTail();
                logger.debug("Queue '{}' polled item at index {}, new head={}", queueName, headIndex - 1, headIndex);
                return item;
            }
            
            return null;
        } catch (Exception e) {
            logger.error("Failed to poll item from queue '{}': {}", queueName, e.getMessage(), e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    public T peek() {
        lock.lock();
        try {
            if (headIndex >= tailIndex) {
                return null;
            }
            
            String key = DATA_PREFIX + headIndex;
            byte[] data = db.get(key.getBytes(StandardCharsets.UTF_8));
            
            if (data != null) {
                return deserialize(data);
            }
            
            return null;
        } catch (Exception e) {
            logger.error("Failed to peek item from queue '{}': {}", queueName, e.getMessage(), e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return (int) (tailIndex - headIndex);
        } finally {
            lock.unlock();
        }
    }

    void clear() {
        lock.lock();
        try {
            RocksIterator iterator = db.newIterator();
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                String key = new String(iterator.key(), StandardCharsets.UTF_8);
                if (key.startsWith(DATA_PREFIX)) {
                    db.delete(iterator.key());
                }
            }
            iterator.close();
            
            headIndex = 0;
            tailIndex = 0;
            saveHeadTail();
            
            logger.info("Queue '{}' cleared", queueName);
        } catch (Exception e) {
            logger.error("Failed to clear queue '{}': {}", queueName, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        lock.lock();
        try {
            return headIndex >= tailIndex;
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            if (db != null) {
                db.close();
                logger.info("Queue '{}' closed", queueName);
            }
        } catch (Exception e) {
            logger.error("Failed to close queue '{}': {}", queueName, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    private void saveHeadTail() throws RocksDBException {
        ColumnFamilyHandle defaultHandle = db.getDefaultColumnFamily();
        db.put(defaultHandle, HEAD_KEY.getBytes(StandardCharsets.UTF_8), longToBytes(headIndex));
        db.put(defaultHandle, TAIL_KEY.getBytes(StandardCharsets.UTF_8), longToBytes(tailIndex));
    }

    private byte[] longToBytes(long value) {
        return new byte[] {
            (byte) (value >>> 56),
            (byte) (value >>> 48),
            (byte) (value >>> 40),
            (byte) (value >>> 32),
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
    }

    private long bytesToLong(byte[] bytes) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (bytes[i] & 0xFF);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private T deserialize(byte[] data) {
        String json = new String(data, StandardCharsets.UTF_8);
        return gson.fromJson(json, type);
    }

    private byte[] serialize(T item) {
        String json = gson.toJson(item);
        return json.getBytes(StandardCharsets.UTF_8);
    }
}