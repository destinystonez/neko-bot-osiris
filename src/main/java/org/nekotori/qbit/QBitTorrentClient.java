package org.nekotori.qbit;// QBitTorrentClient.java
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class QBitTorrentClient {
    private final String baseUrl;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    
    private static final String DEFAULT_BASE_URL = "http://localhost:8000";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    public QBitTorrentClient() {
        this(DEFAULT_BASE_URL, DEFAULT_TIMEOUT);
    }
    
    public QBitTorrentClient(String baseUrl) {
        this(baseUrl, DEFAULT_TIMEOUT);
    }
    
    public QBitTorrentClient(String baseUrl, Duration timeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = timeout;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * 检查服务状态
     */
    public ApiResponse<String> getStatus() {
        try {
            String response = executeGet("/");
            JSONObject json = JSONUtil.parseObj(response);
            return ApiResponse.success("服务运行正常", json.getStr("message"));
        } catch (Exception e) {
            return ApiResponse.error("检查服务状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查 qBittorrent 连接状态
     */
    public ApiResponse<QBitTorrentStatus> getQBitTorrentStatus() {
        try {
            String response = executeGet("/api/status");
            QBitTorrentStatus status = objectMapper.readValue(response, QBitTorrentStatus.class);
            return ApiResponse.success(status);
        } catch (Exception e) {
            return ApiResponse.error("获取 qBittorrent 状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 添加磁力链接下载
     */
    public ApiResponse<String> addMagnetDownload(MagnetRequest request) {
        try {
            validateMagnetLink(request.magnetLink());
            
            String body = objectMapper.writeValueAsString(Map.of(
                "magnet_link", request.magnetLink(),
                "category", request.category() != null ? request.category() : ""
            ));
            
            String response = executePost("/api/download", body);
            JSONObject json = JSONUtil.parseObj(response);
            return ApiResponse.success(json.getStr("message"));
        } catch (Exception e) {
            return ApiResponse.error("添加下载失败: " + e.getMessage());
        }
    }
    
    /**
     * 添加磁力链接下载（简化方法）
     */
    public ApiResponse<String> addMagnetDownload(String magnetLink) {
        return addMagnetDownload(new MagnetRequest(magnetLink, null));
    }
    
    /**
     * 获取所有 torrent 列表
     */
    public ApiResponse<TorrentListResponse> getTorrents() {
        try {
            String response = executeGet("/api/torrents");
            TorrentListResponse torrents = objectMapper.readValue(response, TorrentListResponse.class);
            return ApiResponse.success(torrents);
        } catch (Exception e) {
            return ApiResponse.error("获取 torrent 列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取特定 torrent 详情
     */
    public ApiResponse<TorrentInfo> getTorrentDetails(String torrentHash) {
        try {
            validateTorrentHash(torrentHash);
            
            String response = executeGet("/api/torrents/" + torrentHash);
            TorrentInfo torrent = objectMapper.readValue(response, TorrentInfo.class);
            return ApiResponse.success(torrent);
        } catch (Exception e) {
            return ApiResponse.error("获取 torrent 详情失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取 torrent 属性
     */
    public ApiResponse<Map<String, Object>> getTorrentProperties(String torrentHash) {
        try {
            validateTorrentHash(torrentHash);
            
            String response = executeGet("/api/torrents/" + torrentHash + "/properties");
            Map<String, Object> properties = objectMapper.readValue(response, new TypeReference<>() {});
            return ApiResponse.success(properties);
        } catch (Exception e) {
            return ApiResponse.error("获取 torrent 属性失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取 torrent 跟踪器
     */
    public ApiResponse<List<Map<String, Object>>> getTorrentTrackers(String torrentHash) {
        try {
            validateTorrentHash(torrentHash);
            
            String response = executeGet("/api/torrents/" + torrentHash + "/trackers");
            List<Map<String, Object>> trackers = objectMapper.readValue(response, new TypeReference<>() {});
            return ApiResponse.success(trackers);
        } catch (Exception e) {
            return ApiResponse.error("获取 torrent 跟踪器失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除 torrent
     */
    public ApiResponse<String> deleteTorrent(String torrentHash, boolean deleteFiles) {
        try {
            validateTorrentHash(torrentHash);
            
            String url = "/api/torrents/" + torrentHash + "?delete_files=" + deleteFiles;
            String response = executeDelete(url);
            JSONObject json = JSONUtil.parseObj(response);
            return ApiResponse.success(json.getStr("message"));
        } catch (Exception e) {
            return ApiResponse.error("删除 torrent 失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除 torrent（保留文件）
     */
    public ApiResponse<String> deleteTorrent(String torrentHash) {
        return deleteTorrent(torrentHash, false);
    }
    
    /**
     * 搜索正在下载的 torrent
     */
    public ApiResponse<List<TorrentInfo>> getDownloadingTorrents() {
        try {
            ApiResponse<TorrentListResponse> response = getTorrents();
            if (!response.success()) {
                return ApiResponse.error(response.message());
            }
            
            List<TorrentInfo> downloading = response.data().torrents().stream()
                .filter(t -> t.progress() < 1.0 && !t.state().contains("paused"))
                .toList();
            
            return ApiResponse.success(downloading);
        } catch (Exception e) {
            return ApiResponse.error("搜索下载中的 torrent 失败: " + e.getMessage());
        }
    }
    
    /**
     * 搜索已完成的 torrent
     */
    public ApiResponse<List<TorrentInfo>> getCompletedTorrents() {
        try {
            ApiResponse<TorrentListResponse> response = getTorrents();
            if (!response.success()) {
                return ApiResponse.error(response.message());
            }
            
            List<TorrentInfo> completed = response.data().torrents().stream()
                .filter(t -> t.progress() >= 1.0)
                .toList();
            
            return ApiResponse.success(completed);
        } catch (Exception e) {
            return ApiResponse.error("搜索已完成的 torrent 失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据名称搜索 torrent
     */
    public ApiResponse<List<TorrentInfo>> searchTorrentsByName(String name) {
        try {
            ApiResponse<TorrentListResponse> response = getTorrents();
            if (!response.success()) {
                return ApiResponse.error(response.message());
            }
            
            List<TorrentInfo> results = response.data().torrents().stream()
                .filter(t -> t.name().toLowerCase().contains(name.toLowerCase()))
                .toList();
            
            return ApiResponse.success(results);
        } catch (Exception e) {
            return ApiResponse.error("搜索 torrent 失败: " + e.getMessage());
        }
    }
    
    private String executeGet(String endpoint) {
        String url = baseUrl + endpoint;
        
        HttpResponse response = HttpRequest.get(url)
            .timeout((int) timeout.toMillis())
            .execute();
        
        return handleResponse(response);
    }
    
    private String executePost(String endpoint, String body) {
        String url = baseUrl + endpoint;
        
        HttpResponse response = HttpRequest.post(url)
            .timeout((int) timeout.toMillis())
            .body(body)
            .contentType("application/json")
            .execute();
        
        return handleResponse(response);
    }
    
    private String executeDelete(String endpoint) {
        String url = baseUrl + endpoint;
        
        HttpResponse response = HttpRequest.delete(url)
            .timeout((int) timeout.toMillis())
            .execute();
        
        return handleResponse(response);
    }
    
    private String handleResponse(HttpResponse response) {
        int status = response.getStatus();
        String body = response.body();
        
        if (status >= 200 && status < 300) {
            return body;
        } else if (status >= 400 && status < 500) {
            JSONObject error = JSONUtil.parseObj(body);
            throw new QBitTorrentException(
                error.getStr("detail", "客户端错误"), 
                status
            );
        } else if (status >= 500) {
            JSONObject error = JSONUtil.parseObj(body);
            throw new QBitTorrentException(
                error.getStr("detail", "服务器错误"), 
                status
            );
        } else {
            throw new QBitTorrentException("HTTP错误: " + status, status);
        }
    }
    
    private void validateMagnetLink(String magnetLink) {
        if (magnetLink == null || magnetLink.trim().isEmpty()) {
            throw new IllegalArgumentException("磁力链接不能为空");
        }
        if (!magnetLink.startsWith("magnet:?")) {
            throw new IllegalArgumentException("无效的磁力链接格式");
        }
    }
    
    private void validateTorrentHash(String torrentHash) {
        if (torrentHash == null || torrentHash.trim().isEmpty()) {
            throw new IllegalArgumentException("Torrent hash 不能为空");
        }
        if (torrentHash.length() < 10) {
            throw new IllegalArgumentException("无效的 torrent hash");
        }
    }


    public static void main(String[] args) {
        // 创建客户端实例
        QBitTorrentClient client = new QBitTorrentClient("http://localhost:8002");

        try {
            // 1. 检查服务状态
            System.out.println("=== 检查服务状态 ===");
            var statusResponse = client.getStatus();
            if (statusResponse.success()) {
                System.out.println("服务状态: " + statusResponse.data());
            } else {
                System.out.println("错误: " + statusResponse.message());
                return;
            }

            // 2. 检查 qBittorrent 状态
            System.out.println("\n=== 检查 qBittorrent 状态 ===");
            var qbtStatus = client.getQBitTorrentStatus();
            if (qbtStatus.success()) {
                System.out.println("qBittorrent 状态: " + qbtStatus.data().status());
                System.out.println("版本: " + qbtStatus.data().version());
            } else {
                System.out.println("错误: " + qbtStatus.message());
            }

            // 3. 添加磁力链接下载
            System.out.println("\n=== 添加下载 ===");
            String magnetLink = "magnet:?xt=urn:btih:5742273d032718b1186216f74e03c43cf4eea847&dn=[javdb.com]MIUM-925";
            var downloadResponse = client.addMagnetDownload(
                    new MagnetRequest(magnetLink, "movies")
            );

            if (downloadResponse.success()) {
                System.out.println("下载添加成功: " + downloadResponse.message());
            } else {
                System.out.println("下载添加失败: " + downloadResponse.message());
            }

            // 4. 获取所有 torrent 列表
            System.out.println("\n=== 获取 torrent 列表 ===");
            var torrentsResponse = client.getTorrents();
            if (torrentsResponse.success()) {
                var torrents = torrentsResponse.data();
                System.out.println("总 torrent 数: " + torrents.total());
                System.out.println("下载中: " + torrents.downloading());
                System.out.println("已完成: " + torrents.completed());
                System.out.println("已暂停: " + torrents.paused());

                // 显示每个 torrent 的详细信息
                for (TorrentInfo torrent : torrents.torrents()) {
                    System.out.println("\n--- " + torrent.name() + " ---");
                    System.out.println("进度: " + String.format("%.2f%%", torrent.getProgressPercentage()));
                    System.out.println("状态: " + torrent.state());
                    System.out.println("下载速度: " + torrent.getFormattedDownloadSpeed());
                    System.out.println("大小: " + torrent.getFormattedSize());
                    System.out.println("ETA: " + torrent.getFormattedETA());
                }
            } else {
                System.out.println("获取 torrent 列表失败: " + torrentsResponse.message());
            }

            // 5. 获取下载中的 torrent
            System.out.println("\n=== 下载中的 torrent ===");
            var downloadingResponse = client.getDownloadingTorrents();
            if (downloadingResponse.success()) {
                System.out.println("下载中的任务数: " + downloadingResponse.data().size());
            }

            // 6. 搜索特定名称的 torrent
            System.out.println("\n=== 搜索 torrent ===");
            var searchResponse = client.searchTorrentsByName("ubuntu");
            if (searchResponse.success()) {
                System.out.println("搜索结果数: " + searchResponse.data().size());
            }

        } catch (Exception e) {
            System.err.println("发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}