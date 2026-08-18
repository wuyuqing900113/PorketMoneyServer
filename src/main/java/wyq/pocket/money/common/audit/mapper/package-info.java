/**
 * 审计数据访问：audit_log 写入 Mapper（M1 设计 §9.1），
 * 仅写不读，查询走 DBA 通道。
 */
package wyq.pocket.money.common.audit.mapper;
