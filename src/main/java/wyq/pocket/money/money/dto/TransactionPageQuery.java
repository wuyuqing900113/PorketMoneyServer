package wyq.pocket.money.money.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * 流水分页查询条件（§8.2 #2，控制器查询参数绑定对象）。
 *
 * <p>各过滤参数均可选；page / size 缺省为 1 / 20，
 * 越界值由 MoneyQueryService 统一钳制。
 */
public class TransactionPageQuery {

    /** 默认页码。 */
    private static final int DEFAULT_PAGE = 1;

    /** 默认页大小。 */
    private static final int DEFAULT_SIZE = 20;

    /** 可选：账户持有人过滤。 */
    private Long userId;

    /** 可选：方向（IN / OUT）。 */
    private String direction;

    /** 可选：业务类型。 */
    private String bizType;

    /** 可选：起始日期（含）。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    /** 可选：截止日期（含）。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    /** 页码。 */
    private int page = DEFAULT_PAGE;

    /** 页大小。 */
    private int size = DEFAULT_SIZE;

    /**
     * 获取账户持有人过滤条件。
     *
     * @return 账户持有人用户 ID，null 表示不过滤
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置账户持有人过滤条件。
     *
     * @param userId 账户持有人用户 ID，null 表示不过滤
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取方向过滤条件。
     *
     * @return 方向（IN / OUT），null 表示不过滤
     */
    public String getDirection() {
        return direction;
    }

    /**
     * 设置方向过滤条件。
     *
     * @param direction 方向（IN / OUT），null 表示不过滤
     */
    public void setDirection(String direction) {
        this.direction = direction;
    }

    /**
     * 获取业务类型过滤条件。
     *
     * @return 业务类型，null 表示不过滤
     */
    public String getBizType() {
        return bizType;
    }

    /**
     * 设置业务类型过滤条件。
     *
     * @param bizType 业务类型，null 表示不过滤
     */
    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    /**
     * 获取起始日期过滤条件。
     *
     * @return 起始日期（含，yyyy-MM-dd），null 表示不限
     */
    public LocalDate getFrom() {
        return from;
    }

    /**
     * 设置起始日期过滤条件。
     *
     * @param from 起始日期（含，yyyy-MM-dd），null 表示不限
     */
    public void setFrom(LocalDate from) {
        this.from = from;
    }

    /**
     * 获取截止日期过滤条件。
     *
     * @return 截止日期（含，yyyy-MM-dd），null 表示不限
     */
    public LocalDate getTo() {
        return to;
    }

    /**
     * 设置截止日期过滤条件。
     *
     * @param to 截止日期（含，yyyy-MM-dd），null 表示不限
     */
    public void setTo(LocalDate to) {
        this.to = to;
    }

    /**
     * 获取页码。
     *
     * @return 页码（默认 1）
     */
    public int getPage() {
        return page;
    }

    /**
     * 设置页码。
     *
     * @param page 页码
     */
    public void setPage(int page) {
        this.page = page;
    }

    /**
     * 获取页大小。
     *
     * @return 页大小（默认 20，上限 50）
     */
    public int getSize() {
        return size;
    }

    /**
     * 设置页大小。
     *
     * @param size 页大小
     */
    public void setSize(int size) {
        this.size = size;
    }
}
