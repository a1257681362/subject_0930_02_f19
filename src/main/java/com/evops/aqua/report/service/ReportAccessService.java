package com.evops.aqua.report.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.feed.entity.FeedAccessPolicy;
import com.evops.aqua.feed.mapper.FeedAccessPolicyMapper;
import com.evops.aqua.report.entity.ReportChangeEvidence;
import com.evops.aqua.report.entity.ReportSeaGrant;
import com.evops.aqua.report.mapper.ReportSeaGrantMapper;
import com.evops.aqua.report.web.ReportAccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 监管月报准入：
 * 1）口径管理缺权闸门——海水养殖管理权限收紧（t_feed_access_policy=0）时，
 *    「水产监管月报口径管理」在 Shiro 入口过滤链即被拒绝；服务内再做一道与 URL 无关的闸门，
 *    无论换成什么调用地址到达控制器都不放行，拒绝事实独立事务留证。
 * 2）海区数据权限——导出/查询按用户授权海区过滤，无授权记录者看不到任何海区数据。
 */
@Service
public class ReportAccessService {

    private final FeedAccessPolicyMapper feedPolicyMapper;
    private final ReportSeaGrantMapper seaGrantMapper;
    private final ReportEvidenceService evidenceService;

    public ReportAccessService(FeedAccessPolicyMapper feedPolicyMapper,
                               ReportSeaGrantMapper seaGrantMapper,
                               ReportEvidenceService evidenceService) {
        this.feedPolicyMapper = feedPolicyMapper;
        this.seaGrantMapper = seaGrantMapper;
        this.evidenceService = evidenceService;
    }

    /** 海水养殖管理是否已放开（缺权=权限收紧）。 */
    public boolean isCaliberManageAllowed() {
        FeedAccessPolicy policy = feedPolicyMapper.selectById(FeedAccessPolicy.SINGLETON_ID);
        // 无开关行视为初始放开（与饲料模块初始语义一致）
        return policy == null || Boolean.TRUE.equals(policy.getFeedBatchAdvanceEnabled());
    }

    /**
     * 口径管理服务闸门：入口过滤器与控制器方法都调用本方法。
     * 与调用地址无关——任何 URL/转发到达口径管理逻辑，缺权一律拒绝。
     */
    public void requireCaliberManageAllowed(String requestUri, String requestId, String operator) {
        if (isCaliberManageAllowed()) {
            return;
        }
        recordEntryDenied(requestUri, requestId, operator);
        throw new ReportAccessDeniedException(
                "海水养殖管理权限已收紧（缺权），水产监管月报口径管理入口已拒绝");
    }

    /** 入口过滤链拒绝时独立事务留证（此时请求尚未进入控制器事务）。 */
    public void recordEntryDenied(String requestUri, String requestId, String operator) {
        String who = operator == null ? ReportEvidenceService.currentOperator() : operator;
        evidenceService.recordStandalone(ReportChangeEvidence.ENTITY_ACCESS, 0L,
                ReportChangeEvidence.ACTION_DENIED, requestId, who, null, null,
                "海水养殖管理缺权：拒绝访问水产监管月报口径管理，请求地址=" + requestUri
                        + "，换调用地址同样拒绝，需先 POST /api/aqua/feed/access/relax 放开权限");
    }

    /** 授权海区集合；"*" 展开为 null（表示全部，由调用方识别）。 */
    public Set<String> authorizedAreas(String username) {
        if (username == null) {
            return Collections.emptySet();
        }
        ReportSeaGrant grant = seaGrantMapper.selectOne(new QueryWrapper<ReportSeaGrant>()
                .eq("username", username).last("limit 1"));
        if (grant == null || grant.getSeaAreas() == null || grant.getSeaAreas().trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> areas = new LinkedHashSet<>();
        for (String part : Arrays.asList(grant.getSeaAreas().split(","))) {
            String area = part.trim();
            if (!area.isEmpty()) {
                areas.add(area);
            }
        }
        return areas;
    }

    public boolean hasAllAreas(String username) {
        return authorizedAreas(username).contains(ReportSeaGrant.ALL_AREAS);
    }

    /** 查询/导出某海区前校验；无任何海区授权或未授权该海区都拒绝。 */
    public void requireSeaArea(String username, String seaArea) {
        Set<String> areas = authorizedAreas(username);
        if (areas.contains(ReportSeaGrant.ALL_AREAS)) {
            return;
        }
        if (seaArea == null || !areas.contains(seaArea)) {
            throw new ReportAccessDeniedException("用户 " + username
                    + " 无海区「" + seaArea + "」监管月报数据权限，已按海区权限过滤拒绝");
        }
    }

    /** 列表过滤：全海区返回 true（不过滤）；否则仅授权海区可见。 */
    public boolean canSeeSeaArea(String username, String seaArea) {
        Set<String> areas = authorizedAreas(username);
        return areas.contains(ReportSeaGrant.ALL_AREAS) || areas.contains(seaArea);
    }

    public List<ReportSeaGrant> listGrants() {
        return seaGrantMapper.selectList(new QueryWrapper<ReportSeaGrant>().orderByAsc("id"));
    }
}
