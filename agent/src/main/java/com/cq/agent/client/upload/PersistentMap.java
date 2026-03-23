package com.cq.agent.client.upload;

import com.google.gson.Gson;
import org.rocksdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class PersistentMap<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(PersistentMap.class);

    private final RocksDB db;
    private final ReentrantLock lock = new ReentrantLock();
    private final String mapName;
    private final Gson gson;
    private final Class<K> keyType;
    private final Class<V> valueType;
    private static final String KEY_PREFIX = "map:";

    public PersistentMap(String dbPath, String mapName, Class<K> keyType, Class<V> valueType) throws RocksDBException {
        this.mapName = mapName;
        this.keyType = keyType;
        this.valueType = valueType;
        this.gson = new Gson();
        
        DBOptions options = new DBOptions();
        options.setCreateIfMissing(true);
        options.setCreateMissingColumnFamilies(true);
        
        List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();
        columnFamilyDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, new ColumnFamilyOptions()));
        List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
        
        try {
            this.db = RocksDB.open(options, dbPath, columnFamilyDescriptors, columnFamilyHandles);
            
            logger.info("PersistentMap '{}' initialized: size={}", mapName, size());
        } catch (RocksDBException e) {
            logger.error("Failed to open RocksDB for map '{}': {}", mapName, e.getMessage(), e);
            throw e;
        }
    }

    public void put(K key, V value) {
        lock.lock();
        try {
            String dbKey = KEY_PREFIX + serializeKey(key);
            db.put(dbKey.getBytes(StandardCharsets.UTF_8), serializeValue(value));
            logger.debug("Map '{}' put: key={}, value={}", mapName, key, value);
        } catch (Exception e) {
            logger.error("Failed to put to map '{}': {}", mapName, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public V get(K key) {
        lock.lock();
        try {
            String dbKey = KEY_PREFIX + serializeKey(key);
            byte[] value = db.get(dbKey.getBytes(StandardCharsets.UTF_8));
            if (value != null) {
                return deserializeValue(value);
            }
            return null;
        } catch (Exception e) {
            logger.error("Failed to get from map '{}': {}", mapName, e.getMessage(), e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    public V remove(K key) {
        lock.lock();
        try {
            String dbKey = KEY_PREFIX + serializeKey(key);
            byte[] value = db.get(dbKey.getBytes(StandardCharsets.UTF_8));
            V result = null;
            if (value != null) {
                result = deserializeValue(value);
                db.delete(dbKey.getBytes(StandardCharsets.UTF_8));
                logger.debug("Map '{}' removed key: {}", mapName, key);
            }
            return result;
        } catch (Exception e) {
            logger.error("Failed to remove from map '{}': {}", mapName, e.getMessage(), e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    public boolean containsKey(K key) {
        lock.lock();
        try {
            String dbKey = KEY_PREFIX + serializeKey(key);
            return db.get(dbKey.getBytes(StandardCharsets.UTF_8)) != null;
        } catch (Exception e) {
            logger.error("Failed to check key in map '{}': {}", mapName, e.getMessage(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            int count = 0;
            RocksIterator iterator = db.newIterator();
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                String key = new String(iterator.key(), StandardCharsets.UTF_8);
                if (key.startsWith(KEY_PREFIX)) {
                    count++;
                }
            }
            iterator.close();
            return count;
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public List<V> values() {
        return getAllValues();
    }

    public List<Map.Entry<K, V>> entrySet() {
        lock.lock();
        try {
            List<Map.Entry<K, V>> entries = new ArrayList<>();
            RocksIterator iterator = db.newIterator();
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                String key = new String(iterator.key(), StandardCharsets.UTF_8);
                if (key.startsWith(KEY_PREFIX)) {
                    String keyWithoutPrefix = key.substring(KEY_PREFIX.length());
                    K deserializedKey = deserializeKey(keyWithoutPrefix);
                    V value = deserializeValue(iterator.value());
                    entries.add(new Map.Entry<K, V>() {
                        @Override
                        public K getKey() {
                            return deserializedKey;
                        }

                        @Override
                        public V getValue() {
                            return value;
                        }

                        @Override
                        public V setValue(V value) {
                            throw new UnsupportedOperationException("setValue not supported");
                        }
                    });
                }
            }
            iterator.close();
            logger.debug("Map '{}' entrySet: size={}", mapName, entries.size());
            return entries;
        } finally {
            lock.unlock();
        }
    }

    public List<V> getAllValues() {
        return getValues(0, Integer.MAX_VALUE);
    }

    public List<V> getValues(int offset, int limit) {
        lock.lock();
        try {
            List<V> values = new ArrayList<>();
            int count = 0;
            int skipped = 0;
            RocksIterator iterator = db.newIterator();
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                String key = new String(iterator.key(), StandardCharsets.UTF_8);
                if (key.startsWith(KEY_PREFIX)) {
                    if (skipped < offset) {
                        skipped++;
                        continue;
                    }
                    if (count >= limit) {
                        break;
                    }
                    byte[] value = iterator.value();
                    values.add(deserializeValue(value));
                    count++;
                }
            }
            iterator.close();
            logger.debug("Map '{}' getValues: offset={}, limit={}, returned={}", mapName, offset, limit, values.size());
            return values;
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            RocksIterator iterator = db.newIterator();
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                String key = new String(iterator.key(), StandardCharsets.UTF_8);
                if (key.startsWith(KEY_PREFIX)) {
                    db.delete(iterator.key());
                }
            }
            iterator.close();
            
            logger.info("Map '{}' cleared", mapName);
        } catch (Exception e) {
            logger.error("Failed to clear map '{}': {}", mapName, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            if (db != null) {
                db.close();
                logger.info("Map '{}' closed", mapName);
            }
        } catch (Exception e) {
            logger.error("Failed to close map '{}': {}", mapName, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    private String serializeKey(K key) {
        if (key instanceof String) {
            return (String) key;
        }
        return gson.toJson(key);
    }

    @SuppressWarnings("unchecked")
    private K deserializeKey(String key) {
        if (keyType == String.class) {
            return (K) key;
        }
        return gson.fromJson(key, keyType);
    }

    private byte[] serializeValue(V value) {
        String json = gson.toJson(value);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private V deserializeValue(byte[] data) {
        String json = new String(data, StandardCharsets.UTF_8);
        return gson.fromJson(json, valueType);
    }
}