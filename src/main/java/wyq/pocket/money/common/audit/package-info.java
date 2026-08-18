/**
 * 审计日志与安全日志基础设施（M1 设计 §9）。
 *
 * <p>审计日志落库（合规追溯），安全日志走独立 SECURITY logger（实时告警），
 * 与运行日志（排障）三者分离，traceId 贯穿关联。
 */
package wyq.pocket.money.common.audit;
