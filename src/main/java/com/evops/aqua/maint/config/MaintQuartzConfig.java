package com.evops.aqua.maint.config;

import com.evops.aqua.maint.service.OrderEscalationService;
import com.evops.aqua.maint.service.OrderGenerationService;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean;

/**
 * 保养定时任务：
 * - 工单生成扫描：按运行小时/日历周期到期生成工单；
 * - 逾期升级扫描：逾期未完工单自动升级 NORMAL → WARNING → ESCALATED。
 * 业务幂等由唯一约束与同轨未结单判定保证——重启后重新注册、重跑都不多写。
 * misfire 不补触发风暴：错过即等下一周期。
 */
@Configuration
public class MaintQuartzConfig {

    @Bean
    public MethodInvokingJobDetailFactoryBean maintGenerateJobDetail(OrderGenerationService service) {
        MethodInvokingJobDetailFactoryBean factory = new MethodInvokingJobDetailFactoryBean();
        factory.setTargetObject(service);
        factory.setTargetMethod("scanNow");
        factory.setName("maintOrderGenerateScanJob");
        factory.setGroup("aqua-maint");
        factory.setConcurrent(false);
        return factory;
    }

    @Bean
    public CronTriggerFactoryBean maintGenerateTrigger(
            @Qualifier("maintGenerateJobDetail") JobDetail jobDetail,
            @Value("${aqua.maint.generate-scan-cron:0 0/30 * * * ?}") String cron) {
        CronTriggerFactoryBean factory = new CronTriggerFactoryBean();
        factory.setJobDetail(jobDetail);
        factory.setName("maintOrderGenerateScanTrigger");
        factory.setGroup("aqua-maint");
        factory.setCronExpression(cron);
        factory.setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        return factory;
    }

    @Bean
    public MethodInvokingJobDetailFactoryBean maintEscalateJobDetail(OrderEscalationService service) {
        MethodInvokingJobDetailFactoryBean factory = new MethodInvokingJobDetailFactoryBean();
        factory.setTargetObject(service);
        factory.setTargetMethod("runScheduledScan");
        factory.setName("maintOrderEscalateScanJob");
        factory.setGroup("aqua-maint");
        factory.setConcurrent(false);
        return factory;
    }

    @Bean
    public CronTriggerFactoryBean maintEscalateTrigger(
            @Qualifier("maintEscalateJobDetail") JobDetail jobDetail,
            @Value("${aqua.maint.escalate-scan-cron:0 0/15 * * * ?}") String cron) {
        CronTriggerFactoryBean factory = new CronTriggerFactoryBean();
        factory.setJobDetail(jobDetail);
        factory.setName("maintOrderEscalateScanTrigger");
        factory.setGroup("aqua-maint");
        factory.setCronExpression(cron);
        factory.setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        return factory;
    }
}
