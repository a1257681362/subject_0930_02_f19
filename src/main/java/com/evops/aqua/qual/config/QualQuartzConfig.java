package com.evops.aqua.qual.config;

import com.evops.aqua.qual.service.QualScanService;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean;

/**
 * 人员资质定时扫描：证书到期提醒 + 过期证书/临时授权置位。
 * 业务幂等由 uk(证书, 提醒日期) 与状态判定保证——重启后重新注册、重跑都不多写。
 * misfire 不补触发风暴：错过即等下一周期。
 */
@Configuration
public class QualQuartzConfig {

    @Bean
    public MethodInvokingJobDetailFactoryBean qualScanJobDetail(QualScanService service) {
        MethodInvokingJobDetailFactoryBean factory = new MethodInvokingJobDetailFactoryBean();
        factory.setTargetObject(service);
        factory.setTargetMethod("scanNow");
        factory.setName("qualCertScanJob");
        factory.setGroup("aqua-qual");
        factory.setConcurrent(false);
        return factory;
    }

    @Bean
    public CronTriggerFactoryBean qualScanTrigger(
            @Qualifier("qualScanJobDetail") JobDetail jobDetail,
            @Value("${aqua.qual.scan-cron:0 40 2 * * ?}") String cron) {
        CronTriggerFactoryBean factory = new CronTriggerFactoryBean();
        factory.setJobDetail(jobDetail);
        factory.setName("qualCertScanTrigger");
        factory.setGroup("aqua-qual");
        factory.setCronExpression(cron);
        factory.setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        return factory;
    }
}
