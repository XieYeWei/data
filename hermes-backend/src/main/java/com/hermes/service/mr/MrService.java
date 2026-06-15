package com.hermes.service.mr;

import com.hermes.config.HadoopConfig;
import com.hermes.entity.JobTemplate;
import com.hermes.entity.OperationLog;
import com.hermes.mapper.JobTemplateMapper;
import com.hermes.mapper.OperationLogMapper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MrService {

    @Autowired
    private HadoopConfig hadoopConfig;

    @Autowired(required = false)
    private JobTemplateMapper jobTemplateMapper;

    @Autowired(required = false)
    private OperationLogMapper operationLogMapper;

    public List<JobTemplate> getAllTemplates() {
        return jobTemplateMapper.selectList(null);
    }

    public JobTemplate getTemplate(Long id) {
        return jobTemplateMapper.selectById(id);
    }

    public JobTemplate saveTemplate(JobTemplate template) {
        jobTemplateMapper.insert(template);
        return template;  // return the saved entity
    }

    @SuppressWarnings("unchecked")
    public String submitJobFromTemplate(String clusterId, Long templateId, Long userId) throws Exception {
        JobTemplate template = jobTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new RuntimeException("Job template not found");
        }

        Configuration conf = hadoopConfig.getHadoopConf(clusterId);
        Job job = Job.getInstance(conf, template.getName());

        Class<?> mapperClass = Class.forName(template.getMainClass() + "$Mapper");
        Class<?> reducerClass = Class.forName(template.getMainClass() + "$Reducer");

        job.setMapperClass((Class<? extends Mapper>) mapperClass);
        job.setReducerClass((Class<? extends Reducer>) reducerClass);
        job.setJarByClass(Class.forName(template.getMainClass()));
        job.setOutputKeyClass(org.apache.hadoop.io.Text.class);
        job.setOutputValueClass(org.apache.hadoop.io.IntWritable.class);

        FileInputFormat.addInputPath(job, new Path(template.getInputPath()));
        FileOutputFormat.setOutputPath(job, new Path(template.getOutputPath()));

        boolean success = job.waitForCompletion(true);
        logOperation(userId, clusterId, "mr", "submitTemplate", template.getName(), success ? "success" : "failed", null);

        return job.getJobID().toString();
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
}