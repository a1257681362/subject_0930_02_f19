package com.evops.aqua.disease.config;

import com.evops.aqua.disease.service.DiseaseFollowupService;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean;

/**
 * 疫病复查定时任务：调度系统按 cron 扫描未结观察单生成当日复查任务。
 * 业务幂等由 task_no 唯一约束保证——应用重启后定时任务重新注册、补跑，
 * 都不会多写记录；生成的记录固定以 system-scheduler 为操作者并保留原观察人。
 * misfire 不补触发风暴：错过即等下一周期，避免重启后堆积补写。
 */
@Configuration
public class DiseaseQuartzConfig {

    @Bean
    public MethodInvokingJobDetailFactoryBean diseaseFollowupJobDetail(DiseaseFollowupService service) {
        MethodInvokingJobDetailFactoryBean factory = new MethodInvokingJobDetailFactoryBean();
        factory.setTargetObject(service);
        factory.setTargetMethod("runScheduledScan");
        factory.setName("diseaseFollowupScanJob");
        factory.setGroup("aqua-disease");
        factory.setConcurrent(false);
        return factory;
    }

    @Bean
    public CronTriggerFactoryBean diseaseFollowupTrigger(
            @Qualifier("diseaseFollowupJobDetail") JobDetail diseaseFollowupJobDetail,
            @Value("${aqua.disease.followup-scan-cron:0 10 2 * * ?}") String cron) {
        CronTriggerFactoryBean factory = new CronTriggerFactoryBean();
        factory.setJobDetail(diseaseFollowupJobDetail);
        factory.setName("diseaseFollowupScanTrigger");
        factory.setGroup("aqua-disease");
        factory.setCronExpression(cron);
        factory.setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        return factory;
    }
}
