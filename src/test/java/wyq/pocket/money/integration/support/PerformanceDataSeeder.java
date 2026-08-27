package wyq.pocket.money.integration.support;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import wyq.pocket.money.common.crypto.DataEncryptor;

/**
 * 性能基准数据种子器（M2 设计 §15 DoD，PerformanceBaselineIT 配套）：
 * 绕过 API，经 JdbcTemplate 批量直插 50 家庭 × 8 成员 × 36 月 ≈ 5 万流水。
 *
 * <p>幂等：显式 id 段（≥ 1,000,000）已存在则直接跳过；插入后对五张表
 * 序列 setval 至当前最大 id，保证后续 API 注册数据不与本段冲突。
 * 余额不变式：账户 balance = ΣIN − ΣOUT，且末笔流水 balance_after 与
 * balance 一致（对账服务不会误报）。
 */
public final class PerformanceDataSeeder {

    /**
     * 种子结果（幂等跳过时全为 0）。
     *
     * @param families     种子家庭数
     * @param members      种子成员数
     * @param transactions 种子流水数
     */
    public record SeedResult(int families, int members, int transactions) {
    }

    private static final long ID_BASE = 1_000_000L;

    private static final int FAMILIES = 50;

    private static final int CHILDREN_PER_FAMILY = 7;

    private static final int MONTHS = 36;

    private static final int TX_PER_CHILD_MONTH = 4;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 每孩每月流水方向：月规则、手工存入、学习奖励、取出。 */
    private static final String[] DIRECTIONS = {"IN", "IN", "IN", "OUT"};

    private static final String[] BIZ_TYPES =
            {"MONTHLY_RULE", "MANUAL_ADD", "LEARNING_REWARD", "WITHDRAW"};

    private static final BigDecimal[] AMOUNTS = {new BigDecimal("20.00"),
            new BigDecimal("2.00"), new BigDecimal("5.00"), new BigDecimal("3.00")};

    private final JdbcTemplate jdbc;

    private final DataEncryptor encryptor;

    /**
     * 构造种子器。
     *
     * @param jdbc      测试库 JdbcTemplate
     * @param encryptor 手机号落库加密器（与应用同密钥）
     */
    public PerformanceDataSeeder(JdbcTemplate jdbc, DataEncryptor encryptor) {
        this.jdbc = jdbc;
        this.encryptor = encryptor;
    }

    /**
     * 第 index（1 起）个种子家庭 id。
     *
     * @param index 家庭序号
     * @return 显式 id 段内的家庭 id
     */
    public static long familyId(int index) {
        return ID_BASE + index;
    }

    /**
     * 第 index（1 起）个种子家庭的家长用户 id（与家庭 id 同偏移）。
     *
     * @param index 家庭序号
     * @return 家长用户 id
     */
    public static long parentUserId(int index) {
        return ID_BASE + index;
    }

    /**
     * 设计总流水量：50 × 7 × 36 × 4 = 50,400。
     *
     * @return 种子流水总条数
     */
    public static int totalTransactions() {
        return FAMILIES * CHILDREN_PER_FAMILY * MONTHS * TX_PER_CHILD_MONTH;
    }

    /**
     * 种入基准数据（幂等）。
     *
     * @return 种子结果；已种过则返回全 0
     */
    public SeedResult seed() {
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM family WHERE id >= ?)",
                Boolean.class, ID_BASE))) {
            return new SeedResult(0, 0, 0);
        }
        String passwordHash = new BCryptPasswordEncoder().encode("PerfPassw0rd!");
        List<Object[]> users = new ArrayList<>();
        List<Object[]> families = new ArrayList<>();
        List<Object[]> members = new ArrayList<>();
        buildIdentityRows(users, families, members, passwordHash);
        List<Object[]> accounts = new ArrayList<>();
        List<Object[]> transactions = new ArrayList<>();
        buildLedgerRows(accounts, transactions);
        jdbc.batchUpdate("INSERT INTO app_user (id, username, phone_hash, phone_encrypted,"
                + " password_hash, nickname, role, consented_by)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)", users);
        jdbc.batchUpdate("INSERT INTO family (id, family_name, owner_user_id)"
                + " VALUES (?, ?, ?)", families);
        jdbc.batchUpdate("INSERT INTO family_member (id, family_id, user_id)"
                + " VALUES (?, ?, ?)", members);
        jdbc.batchUpdate("INSERT INTO money_account (id, family_id, user_id, balance,"
                + " total_income, total_expense) VALUES (?, ?, ?, ?, ?, ?)", accounts);
        jdbc.batchUpdate("INSERT INTO money_transaction (id, family_id, account_id, user_id,"
                + " direction, biz_type, amount, balance_after, operator_user_id, remark,"
                + " created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", transactions);
        realignSequences();
        return new SeedResult(FAMILIES,
                FAMILIES * (CHILDREN_PER_FAMILY + 1), transactions.size());
    }

    private void buildIdentityRows(List<Object[]> users, List<Object[]> families,
                                   List<Object[]> members, String passwordHash) {
        long memberSeq = ID_BASE;
        for (int f = 1; f <= FAMILIES; f++) {
            long parentId = parentUserId(f);
            String phone = String.format("1395%07d", f);
            families.add(new Object[]{familyId(f), "PerfFamily" + f, parentId});
            users.add(new Object[]{parentId, null, sha256Hex(phone),
                    encryptor.encrypt(phone), passwordHash,
                    "PerfParent" + f, "PARENT", null});
            for (int c = 1; c <= CHILDREN_PER_FAMILY; c++) {
                long childId = childUserId(f, c);
                users.add(new Object[]{childId, String.format("perf%07d", childId),
                        null, null, passwordHash,
                        "PerfChild" + f + "-" + c, "CHILD", parentId});
                members.add(new Object[]{memberSeq++, familyId(f), childId});
            }
            members.add(new Object[]{memberSeq++, familyId(f), parentId});
        }
    }

    private static long childUserId(int familyIndex, int childIndex) {
        return ID_BASE + FAMILIES
                + (long) (familyIndex - 1) * CHILDREN_PER_FAMILY + childIndex;
    }

    private void buildLedgerRows(List<Object[]> accounts, List<Object[]> transactions) {
        long accountSeq = ID_BASE;
        long txSeq = ID_BASE;
        for (int f = 1; f <= FAMILIES; f++) {
            for (int c = 1; c <= CHILDREN_PER_FAMILY; c++) {
                long accountId = ++accountSeq;
                BigDecimal[] running = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
                for (int m = 0; m < MONTHS; m++) {
                    Instant monthStart = YearMonth.now(BUSINESS_ZONE)
                            .minusMonths(MONTHS - 1L - m)
                            .atDay(1).atStartOfDay(BUSINESS_ZONE).toInstant();
                    txSeq = appendMonth(transactions, txSeq, familyId(f), accountId,
                            childUserId(f, c), parentUserId(f), monthStart, running);
                }
                accounts.add(new Object[]{accountId, familyId(f), childUserId(f, c),
                        running[0], running[1], running[2]});
            }
        }
    }

    private static long appendMonth(List<Object[]> transactions, long txSeq, long familyId,
                                    long accountId, long childId, long operatorId,
                                    Instant monthStart, BigDecimal[] running) {
        for (int i = 0; i < TX_PER_CHILD_MONTH; i++) {
            boolean income = "IN".equals(DIRECTIONS[i]);
            running[0] = income ? running[0].add(AMOUNTS[i])
                    : running[0].subtract(AMOUNTS[i]);
            if (income) {
                running[1] = running[1].add(AMOUNTS[i]);
            } else {
                running[2] = running[2].add(AMOUNTS[i]);
            }
            transactions.add(new Object[]{++txSeq, familyId, accountId, childId,
                    DIRECTIONS[i], BIZ_TYPES[i], AMOUNTS[i], running[0], operatorId,
                    "性能种子数据", Timestamp.from(monthStart.plus(i, ChronoUnit.HOURS))});
        }
        return txSeq;
    }

    /** 批量插入后重对齐自增序列，避免与显式 id 段冲突。 */
    private void realignSequences() {
        for (String[] pair : new String[][]{{"app_user_id_seq", "app_user"},
                {"family_id_seq", "family"}, {"family_member_id_seq", "family_member"},
                {"money_account_id_seq", "money_account"},
                {"money_transaction_id_seq", "money_transaction"}}) {
            jdbc.queryForObject("SELECT setval('" + pair[0]
                    + "', (SELECT max(id) FROM " + pair[1] + "))", Long.class);
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
