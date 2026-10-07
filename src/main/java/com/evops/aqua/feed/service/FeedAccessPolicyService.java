package com.evops.aqua.feed.service;

import com.evops.aqua.feed.dto.AccessPolicyToggleRequest;
import com.evops.aqua.feed.entity.FeedAccessPolicy;
import com.evops.aqua.feed.entity.FeedChangeEvidence;
import com.evops.aqua.feed.mapper.FeedAccessPolicyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 海水养殖管理权限开关。
 * 收紧后饲料批号停止推进（新批号入库、锁定占用、执行消耗一律拦截），
 * 已确认的配方版本与历史投喂事实保留不变。
 */
@Service
public class FeedAccessPolicyService {

    private final FeedAccessPolicyMapper policyMapper;
    private final FeedEvidenceService evidenceService;

    public FeedAccessPolicyService(FeedAccessPolicyMapper policyMapper,
                                   FeedEvidenceService evidenceService) {
        this.policyMapper = policyMapper;
        this.evidenceService = evidenceService;
    }

    public FeedAccessPolicy getPolicy() {
        FeedAccessPolicy policy = policyMapper.selectById(FeedAccessPolicy.SINGLETON_ID);
        if (policy == null) {
            policy = new FeedAccessPolicy();
            policy.setId(FeedAccessPolicy.SINGLETON_ID);
            policy.setFeedBatchAdvanceEnabled(Boolean.TRUE);
            policy.setChangedReason("初始放开：允许饲料批号推进");
            policyMapper.insert(policy);
        }
        return policy;
    }

    /** 收紧：饲料批号停止推进。 */
    @Transactional(rollbackFor = Exception.class)
    public FeedAccessPolicy tighten(AccessPolicyToggleRequest request) {
        FeedAccessPolicy before = copyOf(getPolicy());
        FeedAccessPolicy policy = getPolicy();
        policy.setFeedBatchAdvanceEnabled(Boolean.FALSE);
        policy.setChangedReason(request.getReason());
        policyMapper.updateById(policy);
        evidenceService.record(FeedChangeEvidence.ENTITY_ACCESS_POLICY, policy.getId(),
                FeedChangeEvidence.ACTION_TIGHTEN, request.getRequestId(), before, policy,
                "海水养殖管理权限收紧：饲料批号停止推进，原因=" + request.getReason());
        return policy;
    }

    /** 放开：恢复饲料批号推进。 */
    @Transactional(rollbackFor = Exception.class)
    public FeedAccessPolicy relax(AccessPolicyToggleRequest request) {
        FeedAccessPolicy before = copyOf(getPolicy());
        FeedAccessPolicy policy = getPolicy();
        policy.setFeedBatchAdvanceEnabled(Boolean.TRUE);
        policy.setChangedReason(request.getReason());
        policyMapper.updateById(policy);
        evidenceService.record(FeedChangeEvidence.ENTITY_ACCESS_POLICY, policy.getId(),
                FeedChangeEvidence.ACTION_RELAX, request.getRequestId(), before, policy,
                "海水养殖管理权限放开：恢复饲料批号推进，原因=" + request.getReason());
        return policy;
    }

    /** 批号推进类操作（入库/锁定/执行消耗）的统一闸门。 */
    public void requireBatchAdvanceEnabled(String action) {
        if (!Boolean.TRUE.equals(getPolicy().getFeedBatchAdvanceEnabled())) {
            throw new IllegalArgumentException(
                    "海水养殖管理权限已收紧，饲料批号停止推进，不能" + action);
        }
    }

    private FeedAccessPolicy copyOf(FeedAccessPolicy source) {
        FeedAccessPolicy copy = new FeedAccessPolicy();
        copy.setId(source.getId());
        copy.setFeedBatchAdvanceEnabled(source.getFeedBatchAdvanceEnabled());
        copy.setChangedReason(source.getChangedReason());
        return copy;
    }
}
