package com.evops.aqua.report.config;

import com.evops.aqua.report.service.ReportJobService;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean;

/**
 * 监管月报 Quartz 生成任务：默认每月 3 日 03:20 为上月各海区生成固化快照。
 * 每个海区独立任务，单条失败不影响其他海区；失败任务保留异常链路，等待人工重试。
 * 不允许并发执行（concurrent=false）；misfire 不补触发风暴。
 * cron 可由 aqua.report.generate-scan-cron 覆盖。
 */
@Configuration
public class ReportQuartzConfig {

    @Bean
    public MethodInvokingJobDetailFactoryBean reportMonthlyScanJobDetail(ReportJobService jobService) {
        MethodInvokingJobDetailFactoryBean factory = new MethodInvokingJobDetailFactoryBean();
        factory.setTargetObject(jobService);
        factory.setTargetMethod("runScheduledScan");
        factory.setName("reportMonthlyScanJob");
        factory.setGroup("aqua-report");
        factory.setConcurrent(false);
        return factory;
    }

    @Bean
    public CronTriggerFactoryBean reportMonthlyScanTrigger(
            @Qualifier("reportMonthlyScanJobDetail") JobDetail jobDetail,
            @Value("${aqua.report.generate-scan-cron:0 20 3 3 * ?}") String cron) {
        CronTriggerFactoryBean factory = new CronTriggerFactoryBean();
        factory.setJobDetail(jobDetail);
        factory.setName("reportMonthlyScanTrigger");
        factory.setGroup("aqua-report");
        factory.setCronExpression(cron);
        factory.setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        return factory;
    }
}
