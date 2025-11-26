package org.nekotori.persistence.sqlite;

import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class ScheduledTaskPersistence {

    private static final String DB_URL = "jdbc:sqlite:bot/bot.db";
    private static final ConcurrentHashMap<Long, ReentrantLock> groupLocks = new ConcurrentHashMap<>();
    private static volatile Connection connection;
    private static final ReentrantLock connectionLock = new ReentrantLock();

    public static void main(String[] args) {
        ScheduledTaskPersistence scheduledTaskPersistence = new ScheduledTaskPersistence();
        scheduledTaskPersistence.createTable();
        scheduledTaskPersistence.save(123456789L, 123456789L, "定时任务信息");
        List<ScheduledTaskEntity> allTasks = scheduledTaskPersistence.getAllTasks(123456789L);
        allTasks.forEach(System.out::println);
    }

    public ScheduledTaskPersistence() {
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

    public void createTable() {
        connectionLock.lock();
        try {
            var sql = "CREATE TABLE IF NOT EXISTS scheduled_task(id INTEGER PRIMARY KEY AUTOINCREMENT,group_id BIGINT, sender_id BIGINT, task_info TEXT, time TIMESTAMP)";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.executeUpdate();
                conn.commit();
            }
        } catch (SQLException se) {
            log.error("create table error: {}", se.getMessage());
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException e) {
                log.error("rollback error: {}", e.getMessage());
            }
        } finally {
            connectionLock.unlock();
        }
    }

    public List<ScheduledTaskEntity> getAllTasks(Long groupId) {
        ReentrantLock lock = getGroupLock(groupId);
        lock.lock();
        try {
            var sql = "SELECT * FROM scheduled_task WHERE group_id = ? ORDER BY id DESC LIMIT 100";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.setLong(1, groupId);
                try (var resultSet = preparedStatement.executeQuery()) {
                    var res = new ArrayList<ScheduledTaskEntity>();
                    while (resultSet.next()) {
                        var scheduledTaskEntity = new ScheduledTaskEntity();
                        scheduledTaskEntity.setId(resultSet.getInt("id"));
                        scheduledTaskEntity.setGroupId(resultSet.getLong("group_id"));
                        scheduledTaskEntity.setSenderId(resultSet.getLong("sender_id"));
                        scheduledTaskEntity.setTaskInfo(resultSet.getString("task_info"));
                        scheduledTaskEntity.setTime(resultSet.getLong("time"));
                        res.add(scheduledTaskEntity);
                    }
                    return res;
                }
            }
        } catch (SQLException se) {
            log.error("get tasks error: {}", se.getMessage());
            return new ArrayList<>();
        } finally {
            lock.unlock();
        }
    }

    // 获取某人的全部定时任务
    public List<ScheduledTaskEntity> getAllTasksBySender(Long groupId, Long senderId) {
        ReentrantLock lock = getGroupLock(groupId);
        lock.lock();
        try {
            var sql = "SELECT * FROM scheduled_task WHERE group_id = ? AND sender_id = ? ORDER BY id DESC LIMIT 20";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.setLong(1, groupId);
                preparedStatement.setLong(2, senderId);
                try (var resultSet = preparedStatement.executeQuery()) {
                    var res = new ArrayList<ScheduledTaskEntity>();
                    while (resultSet.next()) {
                        var scheduledTaskEntity = new ScheduledTaskEntity();
                        scheduledTaskEntity.setId(resultSet.getInt("id"));
                        scheduledTaskEntity.setGroupId(resultSet.getLong("group_id"));
                        scheduledTaskEntity.setSenderId(resultSet.getLong("sender_id"));
                        scheduledTaskEntity.setTaskInfo(resultSet.getString("task_info"));
                        scheduledTaskEntity.setTime(resultSet.getLong("time"));
                        res.add(scheduledTaskEntity);
                    }
                    return res;
                }
            }
        } catch (SQLException se) {
            log.error("get sender tasks error: {}", se.getMessage());
            return new ArrayList<>();
        } finally {
            lock.unlock();
        }
    }

    public void save(Long groupId, Long senderId, String taskInfo) {
        ReentrantLock lock = getGroupLock(groupId);
        lock.lock();
        try {
            var sql = "INSERT INTO scheduled_task(group_id,sender_id,task_info,time) VALUES (?,?,?,?)";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.setLong(1, groupId);
                preparedStatement.setLong(2, senderId);
                preparedStatement.setString(3, taskInfo);
                preparedStatement.setLong(4, System.currentTimeMillis());
                preparedStatement.executeUpdate();
                conn.commit();
            }
        } catch (SQLException se) {
            log.error("save task error: {}", se.getMessage());
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException e) {
                log.error("rollback error: {}", e.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }

    public void save(ScheduledTaskEntity scheduledTaskEntity) {
        ReentrantLock lock = getGroupLock(scheduledTaskEntity.getGroupId());
        lock.lock();
        try {
            var sql = "INSERT INTO scheduled_task(group_id,sender_id,task_info,time) VALUES (?,?,?,?)";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.setLong(1, scheduledTaskEntity.getGroupId());
                preparedStatement.setLong(2, scheduledTaskEntity.getSenderId());
                preparedStatement.setString(3, scheduledTaskEntity.getTaskInfo());
                preparedStatement.setLong(4, scheduledTaskEntity.getTime());
                preparedStatement.executeUpdate();
                conn.commit();
            }
        } catch (SQLException se) {
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

    // 根据ID删除任务
    public void deleteById(Long groupId, Integer taskId) {
        ReentrantLock lock = getGroupLock(groupId);
        lock.lock();
        try {
            var sql = "DELETE FROM scheduled_task WHERE group_id = ? AND id = ?";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.setLong(1, groupId);
                preparedStatement.setInt(2, taskId);
                preparedStatement.executeUpdate();
                conn.commit();
            }
        } catch (SQLException se) {
            log.error("delete task error: {}", se.getMessage());
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException e) {
                log.error("rollback error: {}", e.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }

    // 删除某人的所有任务
    public void deleteBySender(Long groupId, Long senderId) {
        ReentrantLock lock = getGroupLock(groupId);
        lock.lock();
        try {
            var sql = "DELETE FROM scheduled_task WHERE group_id = ? AND sender_id = ?";
            try (var conn = getConnection(); var preparedStatement = conn.prepareStatement(sql)) {
                preparedStatement.setLong(1, groupId);
                preparedStatement.setLong(2, senderId);
                preparedStatement.executeUpdate();
                conn.commit();
            }
        } catch (SQLException se) {
            log.error("delete sender tasks error: {}", se.getMessage());
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