package com.evops.aqua.noculture.service;

import com.evops.aqua.entity.Pond;
import com.evops.aqua.noculture.entity.NoCultureRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 禁养空间匹配：判断池塘中心点是否落在禁养范围内。
 * AREA 整海区规则——池塘 seaArea 与规则 seaArea 相等即命中；
 * POLYGON 多边形规则——按池塘经纬度做点-多边形（射线法）判定，
 * 池塘缺经纬度视为不命中（不能据此冻结，避免误伤无坐标池塘）。
 * 顶点取自规则 polygon_json：[[lon,lat],[lon,lat],...]，无需显式闭合。
 *
 * 边界约定：池塘中心点与多边形边界相切（落在任一边或顶点上）按"相切即命中"处理，
 * 即边界点计入禁养范围——宁可多冻由人工核实撤销，不可让边界上的在途批漏网。
 *
 * 缓存：规则只允许新增/撤销、多边形顶点永不修改，故解析出的环按规则id缓存，
 * 发布扫描与三入口守卫共享同一份判定结果，避免重复 JSON 解析且发布后判定口径一致。
 */
@Component
public class NoCultureSpatialMatcher {

    /** 经纬度量级下的浮点容差（约 1e-9 度），边界相切判定允许的误差。 */
    private static final double EPS = 1e-9;

    private final ObjectMapper objectMapper;
    private final Map<Long, double[][]> ringCache = new ConcurrentHashMap<>();

    public NoCultureSpatialMatcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean hits(NoCultureRule rule, Pond pond) {
        if (pond == null) {
            return false;
        }
        if (NoCultureRule.SCOPE_AREA.equals(rule.getScopeType())) {
            return rule.getSeaArea() != null
                    && rule.getSeaArea().equals(pond.getSeaArea());
        }
        if (NoCultureRule.SCOPE_POLYGON.equals(rule.getScopeType())) {
            BigDecimal lon = pond.getLongitude();
            BigDecimal lat = pond.getLatitude();
            if (lon == null || lat == null) {
                return false;
            }
            double[][] ring = cachedRing(rule);
            return ring != null && contains(ring, lon.doubleValue(), lat.doubleValue());
        }
        return false;
    }

    /**
     * 点-多边形：先判边界相切（点落在任一边段/顶点上即命中，按约定计入），
     * 再对严格内部点做射线法。顶点按顺序连成闭合环。
     */
    public boolean contains(double[][] ring, double x, double y) {
        if (ring == null || ring.length < 3) {
            return false;
        }
        if (onBoundary(ring, x, y)) {
            return true;
        }
        boolean inside = false;
        int n = ring.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring[i][0], yi = ring[i][1];
            double xj = ring[j][0], yj = ring[j][1];
            boolean intersect = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** 点是否落在多边形任一边段（含顶点）上：叉积为0且被边的外接矩形包住。 */
    private boolean onBoundary(double[][] ring, double x, double y) {
        int n = ring.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring[j][0], yi = ring[j][1];
            double xj = ring[i][0], yj = ring[i][1];
            double cross = (x - xi) * (yj - yi) - (y - yi) * (xj - xi);
            if (Math.abs(cross) > EPS * Math.max(1.0, Math.abs(xj - xi) + Math.abs(yj - yi))) {
                continue;
            }
            boolean withinBox = x >= Math.min(xi, xj) - EPS && x <= Math.max(xi, xj) + EPS
                    && y >= Math.min(yi, yj) - EPS && y <= Math.max(yi, yj) + EPS;
            if (withinBox) {
                return true;
            }
        }
        return false;
    }

    /** 规则不可变（无修改接口），环解析结果按规则id缓存；非法环不缓存（防御）。 */
    private double[][] cachedRing(NoCultureRule rule) {
        double[][] cached = ringCache.get(rule.getId());
        if (cached != null) {
            return cached;
        }
        double[][] parsed = parseRing(rule.getPolygonJson());
        if (parsed != null) {
            ringCache.putIfAbsent(rule.getId(), parsed);
        }
        return parsed;
    }

    /** 解析 [[lon,lat],...]；非法/不足3点返回 null（登记时已强校验，此处防御）。 */
    public double[][] parseRing(String polygonJson) {
        if (polygonJson == null || polygonJson.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode arr = objectMapper.readTree(polygonJson);
            if (!arr.isArray() || arr.size() < 3) {
                return null;
            }
            double[][] ring = new double[arr.size()][2];
            for (int i = 0; i < arr.size(); i++) {
                JsonNode p = arr.get(i);
                if (!p.isArray() || p.size() < 2) {
                    return null;
                }
                ring[i][0] = p.get(0).asDouble();
                ring[i][1] = p.get(1).asDouble();
            }
            return ring;
        } catch (Exception ex) {
            return null;
        }
    }
}
