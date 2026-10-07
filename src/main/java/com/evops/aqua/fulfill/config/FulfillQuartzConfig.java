package com.evops.aqua.fulfill.config;

import com.evops.aqua.fulfill.service.OutboxService;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean;

/**
 * 发件箱补偿扫描：周期性把 PENDING 拆单事件重新投递海水养殖管理。
 * 只有 PENDING 记录会被发送，SENT 跳过，对端按 eventId 幂等——应用重启/重跑都不重复通知。
 * misfire 不补触发风暴；cron 可由 aqua.fulfill.outbox-scan-cron 覆盖。
 */
@Configuration
public class FulfillQuartzConfig {

    @Bean
    public MethodInvokingJobDetailFactoryBean fulfillOutboxScanJobDetail(OutboxService outboxService) {
        MethodInvokingJobDetailFactoryBean factory = new MethodInvokingJobDetailFactoryBean();
        factory.setTargetObject(outboxService);
        factory.setTargetMethod("scanNow");
        factory.setName("fulfillOutboxScanJob");
        factory.setGroup("aqua-fulfill");
        factory.setConcurrent(false);
        return factory;
    }

    @Bean
    public CronTriggerFactoryBean fulfillOutboxScanTrigger(
            @Qualifier("fulfillOutboxScanJobDetail") JobDetail jobDetail,
            @Value("${aqua.fulfill.outbox-scan-cron:0 0/10 * * * ?}") String cron) {
        CronTriggerFactoryBean factory = new CronTriggerFactoryBean();
        factory.setJobDetail(jobDetail);
        factory.setName("fulfillOutboxScanTrigger");
        factory.setGroup("aqua-fulfill");
        factory.setCronExpression(cron);
        factory.setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        return factory;
    }
}
