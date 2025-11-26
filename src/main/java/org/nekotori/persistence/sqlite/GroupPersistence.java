package org.nekotori.persistence.sqlite;

import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class GroupPersistence {

    private static final String DB_URL = "jdbc:sqlite:bot/bot.db";
    private static final ConcurrentHashMap<Long, ReentrantLock> groupLocks = new ConcurrentHashMap<>();
    private static volatile Connection connection;
    private static final ReentrantLock connectionLock = new ReentrantLock();

    public static void main(String[] args) {
        GroupPersistence groupPersistence = new GroupPersistence();
        groupPersistence.createTable();
        groupPersistence.save(123456789L, "测试群组", false, false, 100, "test-api-key", "voice,chat");
        List<GroupEntity> allGroups = groupPersistence.getAllGroups();
        allGroups.forEach(System.out::println);
    }

    public GroupPersistence(){
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
            var sql = "CREATE TABLE IF NOT EXISTS groups(id INTEGER PRIMARY KEY AUTOINCREMENT,group_id BIGINT UNIQUE, group_name TEXT, is_voice_chat BOOLEAN, is_black_list BOOLEAN, credits INTEGER, nano_api_key TEXT, features TEXT)";
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

    public List<GroupEntity> getAllGroups(){
        connectionLock.lock();
        try {
            var sql = "SELECT * FROM groups";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                var resultSet = preparedStatement.executeQuery();
                var res = new ArrayList<GroupEntity>();
                while (resultSet.next()){
                    var groupEntity = new GroupEntity();
                    groupEntity.setGroupId(resultSet.getLong("group_id"));
                    groupEntity.setGroupName(resultSet.getString("group_name"));
                    groupEntity.setVoiceChat(resultSet.getBoolean("is_voice_chat"));
                    groupEntity.setBlackList(resultSet.getBoolean("is_black_list"));
                    groupEntity.setCredits(resultSet.getInt("credits"));
                    groupEntity.setNanoApiKey(resultSet.getString("nano_api_key"));
                    groupEntity.setFeatures(resultSet.getString("features"));
                    res.add(groupEntity);
                }
                return res;
            }
        }catch (SQLException se){
            log.error("get groups error: {}",se.getMessage());
            return new ArrayList<>();
        } finally {
            connectionLock.unlock();
        }
    }

    public void updateGroup(GroupEntity groupEntity){
        ReentrantLock lock = getGroupLock(groupEntity.getGroupId());
        lock.lock();
        try {
            var sql = "UPDATE groups SET group_name = ?, is_voice_chat = ?, is_black_list = ?, credits = ?, nano_api_key = ?, features = ? WHERE group_id = ?";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.setString(1,groupEntity.getGroupName());
                preparedStatement.setBoolean(2,groupEntity.isVoiceChat());
                preparedStatement.setBoolean(3,groupEntity.isBlackList());
                preparedStatement.setInt(4,groupEntity.getCredits());
                preparedStatement.setString(5,groupEntity.getNanoApiKey());
                preparedStatement.setString(6,groupEntity.getFeatures());
                preparedStatement.setLong(7,groupEntity.getGroupId());
                preparedStatement.executeUpdate();
                conn.commit();
            }
        }catch (SQLException se){
            log.error("update group error: {}",se.getMessage());
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException e) {
                log.error("rollback error: {}", e.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }

    public GroupEntity getGroupById(Long groupId){
        ReentrantLock lock = getGroupLock(groupId);
        lock.lock();
        try {
            var sql = "SELECT * FROM groups WHERE group_id = ?";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.setLong(1, groupId);
                var resultSet = preparedStatement.executeQuery();
                if (resultSet.next()){
                    var groupEntity = new GroupEntity();
                    groupEntity.setGroupId(resultSet.getLong("group_id"));
                    groupEntity.setGroupName(resultSet.getString("group_name"));
                    groupEntity.setVoiceChat(resultSet.getBoolean("is_voice_chat"));
                    groupEntity.setBlackList(resultSet.getBoolean("is_black_list"));
                    groupEntity.setCredits(resultSet.getInt("credits"));
                    groupEntity.setNanoApiKey(resultSet.getString("nano_api_key"));
                    groupEntity.setFeatures(resultSet.getString("features"));
                    return groupEntity;
                }
            }
        }catch (SQLException se){
            log.error("get group error: {}",se.getMessage());
        } finally {
            lock.unlock();
        }
        return null;
    }

    public void save(Long groupId, String groupName, boolean isVoiceChat, boolean isBlackList, Integer credits, String nanoApiKey, String features){
        ReentrantLock lock = getGroupLock(groupId);
        lock.lock();
        try {
            var sql = "INSERT OR REPLACE INTO groups(group_id, group_name, is_voice_chat, is_black_list, credits, nano_api_key, features) VALUES (?,?,?,?,?,?,?)";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.setLong(1,groupId);
                preparedStatement.setString(2,groupName);
                preparedStatement.setBoolean(3,isVoiceChat);
                preparedStatement.setBoolean(4,isBlackList);
                preparedStatement.setInt(5,credits);
                preparedStatement.setString(6,nanoApiKey);
                preparedStatement.setString(7,features);
                preparedStatement.executeUpdate();
                conn.commit();
            }
        }catch (SQLException se){
            log.error("save group error: {}",se.getMessage());
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException e) {
                log.error("rollback error: {}", e.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }

    public void delete(Long groupId){
        ReentrantLock lock = getGroupLock(groupId);
        lock.lock();
        try {
            var sql = "DELETE FROM groups WHERE group_id = ?";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.setLong(1, groupId);
                preparedStatement.executeUpdate();
                conn.commit();
            }
        }catch (SQLException se){
            log.error("delete group error: {}",se.getMessage());
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException e) {
                log.error("rollback error: {}", e.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }
}
