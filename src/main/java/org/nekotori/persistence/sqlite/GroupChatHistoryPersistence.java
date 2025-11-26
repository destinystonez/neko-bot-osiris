package org.nekotori.persistence.sqlite;

import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class GroupChatHistoryPersistence {

    private static final String DB_URL = "jdbc:sqlite:bot/bot.db";
    private static final ConcurrentHashMap<Long, ReentrantLock> groupLocks = new ConcurrentHashMap<>();
    private static volatile Connection connection;
    private static final ReentrantLock connectionLock = new ReentrantLock();

    public static void main(String[] args) {
        GroupChatHistoryPersistence groupChatHistoryPersistence = new GroupChatHistoryPersistence();
        groupChatHistoryPersistence.createTable();
        groupChatHistoryPersistence.save(123456789L,123456789L,"123456789");
        List<GroupChatHistoryEntity> allMessages = groupChatHistoryPersistence.getAllMessages(123456789L);
        allMessages.forEach(System.out::println);
    }

    public GroupChatHistoryPersistence(){
        initConnection();
    }

    private void initConnection() {
        connectionLock.lock();
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL);
                // 先设置自动提交，然后设置PRAGMA
                connection.setAutoCommit(true);
                // 优化SQLite性能设置（在事务外设置）
                try (var stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");
                    stmt.execute("PRAGMA synchronous=NORMAL");
                    stmt.execute("PRAGMA cache_size=10000");
                    stmt.execute("PRAGMA temp_store=memory");
                    stmt.execute("PRAGMA busy_timeout=30000");
                }
                // 设置为手动提交模式
                connection.setAutoCommit(false);
            }
        } catch (SQLException se) {
            log.error("init sqlite error: {}", se.getMessage());
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
                connection = null;
            } catch (SQLException e) {
                log.error("close connection error: {}", e.getMessage());
            }
        } finally {
            connectionLock.unlock();
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            initConnection();
        }
        return connection;
    }

    // 获取群组级别的锁，减少锁竞争
    private ReentrantLock getGroupLock(Long groupId) {
        return groupLocks.computeIfAbsent(groupId, k -> new ReentrantLock());
    }

    public void createTable(){
        connectionLock.lock();
        try {
            var sql = "CREATE TABLE IF NOT EXISTS chat_history(id INTEGER PRIMARY KEY AUTOINCREMENT,group_id BIGINT, user_id BIGINT, message TEXT, time TIMESTAMP)";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.executeUpdate();
                conn.commit();
            }
        }catch (SQLException se){
            log.error("create table error: {}",se.getMessage());
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException e) {
                log.error("rollback error: {}", e.getMessage());
            }
        } finally {
            connectionLock.unlock();
        }
    }

    public List<GroupChatHistoryEntity> getAllMessages(Long groupId){
        ReentrantLock lock = getGroupLock(groupId);
        lock.lock();
        try {
            var sql = "SELECT * FROM chat_history WHERE group_id = ? ORDER BY time DESC LIMIT 100";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.setLong(1, groupId);
                try (var resultSet = preparedStatement.executeQuery()) {
                    var res = new ArrayList<GroupChatHistoryEntity>();
                    while (resultSet.next()){
                        var groupChatHistoryEntity = new GroupChatHistoryEntity();
                        groupChatHistoryEntity.setId(resultSet.getInt("id"));
                        groupChatHistoryEntity.setGroupId(resultSet.getLong("group_id"));
                        groupChatHistoryEntity.setUserId(resultSet.getLong("user_id"));
                        groupChatHistoryEntity.setMessage(resultSet.getString("message"));
                        groupChatHistoryEntity.setTime(resultSet.getLong("time"));
                        res.add(groupChatHistoryEntity);
                    }
                    return res;
                }
            }
        }catch (SQLException se){
            log.error("get messages error: {}",se.getMessage());
            return new ArrayList<>();
        } finally {
            lock.unlock();
        }
    }

    //获取某人的全部发言
    public List<GroupChatHistoryEntity> getAllMessagesByUser(Long groupId, Long userId){
        ReentrantLock lock = getGroupLock(groupId);
        lock.lock();
        try {
            var sql = "SELECT * FROM chat_history WHERE group_id = ? AND user_id = ? ORDER BY time DESC LIMIT 20";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.setLong(1, groupId);
                preparedStatement.setLong(2, userId);
                try (var resultSet = preparedStatement.executeQuery()) {
                    var res = new ArrayList<GroupChatHistoryEntity>();
                    while (resultSet.next()){
                        var groupChatHistoryEntity = new GroupChatHistoryEntity();
                        groupChatHistoryEntity.setId(resultSet.getInt("id"));
                        groupChatHistoryEntity.setGroupId(resultSet.getLong("group_id"));
                        groupChatHistoryEntity.setUserId(resultSet.getLong("user_id"));
                        groupChatHistoryEntity.setMessage(resultSet.getString("message"));
                        groupChatHistoryEntity.setTime(resultSet.getLong("time"));
                        res.add(groupChatHistoryEntity);
                    }
                    return res;
                }
            }
        }catch (SQLException se){
            log.error("get user messages error: {}",se.getMessage());
            return new ArrayList<>();
        } finally {
            lock.unlock();
        }
    }

    public void save(Long id, Long name, String value){
        ReentrantLock lock = getGroupLock(id);
        lock.lock();
        try {
            var sql = "INSERT INTO chat_history(group_id,user_id,message,time) VALUES (?,?,?,?)";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.setLong(1, id);
                preparedStatement.setLong(2, name);
                preparedStatement.setString(3, value);
                preparedStatement.setLong(4, System.currentTimeMillis());
                preparedStatement.executeUpdate();
                conn.commit();
            }
        }catch (SQLException se){
            log.error("save message error: {}", se.getMessage());
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException e) {
                log.error("rollback error: {}", e.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }

    public void save(GroupChatHistoryEntity groupChatHistoryEntity){
        ReentrantLock lock = getGroupLock(groupChatHistoryEntity.getGroupId());
        lock.lock();
        try {
            var sql = "INSERT INTO chat_history(group_id,user_id,message,time) VALUES (?,?,?,?)";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.setLong(1, groupChatHistoryEntity.getGroupId());
                preparedStatement.setLong(2, groupChatHistoryEntity.getUserId());
                preparedStatement.setString(3, groupChatHistoryEntity.getMessage());
                preparedStatement.setLong(4, groupChatHistoryEntity.getTime());
                preparedStatement.executeUpdate();
                conn.commit();
            }
        }catch (SQLException se){
            log.error("save entity error: {}", se.getMessage());
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException e) {
                log.error("rollback error: {}", e.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }

    // 添加批量插入方法，减少锁竞争
    public void saveBatch(List<GroupChatHistoryEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        
        // 按群组ID分组，减少锁竞争
        var groups = new ConcurrentHashMap<Long, List<GroupChatHistoryEntity>>();
        for (GroupChatHistoryEntity entity : entities) {
            groups.computeIfAbsent(entity.getGroupId(), k -> new ArrayList<>()).add(entity);
        }
        
        // 并行处理不同群组
        groups.entrySet().parallelStream().forEach(entry -> {
            Long groupId = entry.getKey();
            List<GroupChatHistoryEntity> groupEntities = entry.getValue();
            ReentrantLock lock = getGroupLock(groupId);
            
            lock.lock();
            try {
                var sql = "INSERT INTO chat_history(group_id,user_id,message,time) VALUES (?,?,?,?)";
                try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                    for (GroupChatHistoryEntity entity : groupEntities) {
                        preparedStatement.setLong(1, entity.getGroupId());
                        preparedStatement.setLong(2, entity.getUserId());
                        preparedStatement.setString(3, entity.getMessage());
                        preparedStatement.setLong(4, entity.getTime());
                        preparedStatement.addBatch();
                    }
                    preparedStatement.executeBatch();
                    conn.commit();
                }
            } catch (SQLException se) {
                log.error("save batch error for group {}: {}", groupId, se.getMessage());
                try {
                    if (connection != null) connection.rollback();
                } catch (SQLException e) {
                    log.error("rollback error: {}", e.getMessage());
                }
            } finally {
                lock.unlock();
            }
        });
    }

    // 关闭连接的方法
    public void close() {
        connectionLock.lock();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            log.error("close connection error: {}", e.getMessage());
        } finally {
            connectionLock.unlock();
        }
    }
}