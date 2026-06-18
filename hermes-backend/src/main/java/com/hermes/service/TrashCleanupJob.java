package com.hermes.service;

import com.hermes.config.HermesProperties;
import com.hermes.service.hdfs.HdfsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class TrashCleanupJob {

    @Autowired
    private HdfsService hdfsService;

    @Autowired
    private HermesProperties props;

    private final String trashRoot = "/user/root/.Trash/Current";

    /**
     * Scheduled job that runs every day at 3:00 AM and cleans up expired trash items.
     * Items older than retentionDays are permanently deleted.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredTrash() {
        int retentionDays = props.getTrash().getRetentionDays();
        log.info("Trash auto-cleanup started, retention days: {}", retentionDays);

        String clusterId = props.getCluster().getDefaultId();

        try {
            FileSystem fs = getFileSystem(clusterId);
            Path trashRootPath = new Path(trashRoot);

            if (!fs.exists(trashRootPath)) {
                log.info("Trash directory does not exist, nothing to clean up");
                return;
            }

            long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
            int deletedCount = 0;
            long deletedSize = 0;

            // Collect all trash items
            List<FileStatus> allItems = new ArrayList<>();
            collectTrashItems(fs, trashRootPath, allItems);

            // Find and delete expired items
            for (FileStatus item : allItems) {
                if (item.getModificationTime() < cutoffTime) {
                    deletedSize += item.getLen();
                    try {
                        fs.delete(item.getPath(), true);
                        deletedCount++;
                    } catch (IOException e) {
                        log.warn("Failed to delete expired trash item: {}", item.getPath(), e);
                    }
                }
            }

            log.info("Trash auto-cleanup completed: deleted {} items ({} bytes), retention days: {}",
                    deletedCount, deletedSize, retentionDays);
        } catch (Exception e) {
            log.error("Trash auto-cleanup failed", e);
        }
    }

    /**
     * Get the FileSystem for the given cluster ID, using the HdfsService's
     * underlying HadoopConfig.
     */
    private FileSystem getFileSystem(String clusterId) throws IOException {
        // Access the HadoopConfig through the already-autowired HdfsService
        // The hdfsService internally uses hadoopConfig to get the FileSystem.
        // Since we need listTrash/emptyTrash-like operations, we use
        // org.apache.hadoop.fs.FileSystem directly via the same mechanism.
        org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        HermesProperties.Hdfs hdfs = props.getHdfs();
        conf.set("fs.defaultFS", "hdfs://" + hdfs.getNameservices());
        conf.set("dfs.nameservices", hdfs.getNameservices());
        conf.set("dfs.ha.namenodes." + hdfs.getNameservices(), hdfs.getHaNamenodes());
        conf.set("dfs.namenode.rpc-address." + hdfs.getNameservices() + ".nn1", hdfs.getNn1Rpc());
        conf.set("dfs.namenode.rpc-address." + hdfs.getNameservices() + ".nn2", hdfs.getNn2Rpc());
        conf.set("dfs.client.failover.proxy.provider." + hdfs.getNameservices(),
                "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider");

        org.apache.hadoop.security.UserGroupInformation ugi =
                org.apache.hadoop.security.UserGroupInformation.createRemoteUser("hadoop");
        try {
            return ugi.doAs((java.security.PrivilegedExceptionAction<FileSystem>) () ->
                    FileSystem.get(new java.net.URI("hdfs://" + hdfs.getNameservices()), conf)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while getting FileSystem", e);
        }
    }

    private void collectTrashItems(FileSystem fs, Path dir, List<FileStatus> items) throws IOException {
        FileStatus[] statuses = fs.listStatus(dir);
        for (FileStatus status : statuses) {
            items.add(status);
            if (status.isDirectory()) {
                collectTrashItems(fs, status.getPath(), items);
            }
        }
    }
}
