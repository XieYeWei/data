package com.hermes.service.mr;

import com.hermes.config.HadoopConfig;
import com.hermes.entity.JobTemplate;
import com.hermes.entity.OperationLog;
import com.hermes.mapper.JobTemplateMapper;
import com.hermes.mapper.OperationLogMapper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * MapReduce Service - Deep integration with official org.apache.hadoop.mapreduce.Job
 * Supports template-based submission from HDFS JARs.
 */
@Service
public class MrService {

    private static final Logger log = LoggerFactory.getLogger(MrService.class);

    @Autowired
    private HadoopConfig hadoopConfig;

    @Autowired(required = false)
    private JobTemplateMapper jobTemplateMapper;

    @Autowired(required = false)
    private OperationLogMapper operationLogMapper;

    /**
     * Submit MapReduce job from a saved template
     */
    public String submitJobFromTemplate(String clusterId, Long templateId, Long userId) throws Exception {
        JobTemplate template = jobTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }

        Configuration conf = hadoopConfig.getFileSystem(clusterId).getConf();
        conf.set("mapreduce.job.queuename", template.getQueue() != null ? template.getQueue() : "default");

        Job job = Job.getInstance(conf, template.getName());
        job.setJarByClass(Class.forName(template.getMainClass())); // or use addJar
        job.setJar(template.getJarHdfsPath()); // Load JAR from HDFS

        job.setMapperClass(Class.forName(template.getMainClass() + "$Mapper")); // Convention
        job.setReducerClass(Class.forName(template.getMainClass() + "$Reducer"));

        FileInputFormat.addInputPath(job, new Path(template.getInputPath()));
        FileOutputFormat.setOutputPath(job, new Path(template.getOutputPath()));

        // Parse defaultArgs if JSON
        if (template.getDefaultArgs() != null) {
            // TODO: parse and set job parameters
        }

        boolean success = job.waitForCompletion(true);

        String appId = job.getJobID().toString();
        logOperation(userId, clusterId, "mr", "submitTemplate", template.getName(), success ? "success" : "failed", appId);

        if (!success) {
            throw new RuntimeException("MapReduce job failed: " + appId);
        }
        return appId;
    }

    public JobTemplate saveTemplate(JobTemplate template) {
        jobTemplateMapper.insert(template);
        return template;
    }

    public JobTemplate getTemplate(Long id) {
        return jobTemplateMapper.selectById(id);
    }

    private void logOperation(Long userId, String clusterIdStr, String module, String action, String target, String result, String detail) {
        if (operationLogMapper == null) return;
        try {
            OperationLog entry = new OperationLog();
            entry.setUserId(userId != null ? userId : 1L);
            entry.setClusterId(Long.parseLong(clusterIdStr.replace("cluster", "")));
            entry.setModule(module);
            entry.setAction(action);
            entry.setTarget(target);
            entry.setResult(result);
            entry.setDetail(detail);
            operationLogMapper.insert(entry);
        } catch (Exception ignored) {}
    }

    // Advanced: Support custom args override at submit time
}