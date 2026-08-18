package wyq.pocket.money.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * 端点 × 身份权限矩阵（M1 设计 §12.3 / 附录 B，真 PostgreSQL 18）。
 *
 * <p>四种身份：匿名 / 本家庭 CHILD（mcp 已解除）/ 本家庭 PARENT /
 * 跨家庭 PARENT。公共白名单（注册 / 登录 / 刷新）任何身份 200 + 0；
 * 未认证 401 + 100003；CHILD 调写接口与跨家庭访问 403 + 100004
 * （双层守卫：接口级方法安全 + 数据级 FamilyAccessChecker）。
 * 每个用例独立夹具（@BeforeEach 重建），互不污染。
 */
class PermissionMatrixPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    private Fixture fixture;

    protected enum Identity {
        ANONYMOUS, CHILD, OWN_PARENT, CROSS_PARENT
    }

    /** 请求体种类：由实例方法解析，避免静态上下文引用实例夹具。 */
    protected enum BodyKind {
        NONE, REGISTER, LOGIN, REFRESH, LOGOUT, PASSWORD,
        FAMILY_NAME, ADD_CHILD, NICKNAME, CHILD_PASSWORD_RESET
    }

    protected record Expected(int status, int code) {
    }

    protected record Fixture(TestAccount parentA, String parentAPhone, TestAccount parentB,
                             long childAId, String childAToken) {
    }

    protected record MatrixCase(String name, String method, String path, Expected anon,
                                Expected child, Expected own, Expected cross, BodyKind bodyKind) {

        Expected expectedFor(Identity identity) {
            return switch (identity) {
                case ANONYMOUS -> anon;
                case CHILD -> child;
                case OWN_PARENT -> own;
                default -> cross;
            };
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @BeforeEach
    void buildFixture() {
        String parentAPhone = nextPhone();
        TestAccount parentA = registerAndLogin(parentAPhone);
        String childAUsername = nextUsername();
        long childAId = createChild(parentA, childAUsername);
        String childAToken = loginAndChangePassword(childAUsername,
                CHILD_INITIAL_PASSWORD, CHILD_NEW_PASSWORD);
        TestAccount parentB = registerAndLogin(nextPhone());
        fixture = new Fixture(parentA, parentAPhone, parentB, childAId, childAToken);
    }

    static Stream<Arguments> matrixCases() {
        Expected ok = new Expected(200, 0);
        Expected unauthenticated = new Expected(401, 100003);
        Expected forbidden = new Expected(403, 100004);
        List<MatrixCase> cases = List.of(
                new MatrixCase("POST /auth/register", "POST", "/api/v1/auth/register",
                        ok, ok, ok, ok, BodyKind.REGISTER),
                new MatrixCase("POST /auth/login", "POST", "/api/v1/auth/login",
                        ok, ok, ok, ok, BodyKind.LOGIN),
                new MatrixCase("POST /auth/refresh", "POST", "/api/v1/auth/refresh",
                        ok, ok, ok, ok, BodyKind.REFRESH),
                new MatrixCase("POST /auth/logout", "POST", "/api/v1/auth/logout",
                        unauthenticated, ok, ok, ok, BodyKind.LOGOUT),
                new MatrixCase("GET /users/me", "GET", "/api/v1/users/me",
                        unauthenticated, ok, ok, ok, BodyKind.NONE),
                new MatrixCase("PUT /users/me", "PUT", "/api/v1/users/me",
                        unauthenticated, ok, ok, ok, BodyKind.NICKNAME),
                new MatrixCase("POST /users/me/password", "POST", "/api/v1/users/me/password",
                        unauthenticated, ok, ok, ok, BodyKind.PASSWORD),
                new MatrixCase("GET /users/me/family", "GET", "/api/v1/users/me/family",
                        unauthenticated, ok, ok, ok, BodyKind.NONE),
                new MatrixCase("GET /families/{familyId}", "GET", "/api/v1/families/{familyId}",
                        unauthenticated, ok, ok, forbidden, BodyKind.NONE),
                new MatrixCase("GET /families/{familyId}/members", "GET",
                        "/api/v1/families/{familyId}/members",
                        unauthenticated, ok, ok, forbidden, BodyKind.NONE),
                new MatrixCase("PUT /families/{familyId}", "PUT", "/api/v1/families/{familyId}",
                        unauthenticated, forbidden, ok, forbidden, BodyKind.FAMILY_NAME),
                new MatrixCase("POST /families/{familyId}/children", "POST",
                        "/api/v1/families/{familyId}/children",
                        unauthenticated, forbidden, ok, forbidden, BodyKind.ADD_CHILD),
                new MatrixCase("PUT /families/{familyId}/children/{childId}", "PUT",
                        "/api/v1/families/{familyId}/children/{childId}",
                        unauthenticated, forbidden, ok, forbidden, BodyKind.NICKNAME),
                new MatrixCase("POST /families/{familyId}/children/{childId}/password-reset",
                        "POST", "/api/v1/families/{familyId}/children/{childId}/password-reset",
                        unauthenticated, forbidden, ok, forbidden, BodyKind.CHILD_PASSWORD_RESET),
                new MatrixCase("DELETE /families/{familyId}/members/{childId}", "DELETE",
                        "/api/v1/families/{familyId}/members/{childId}",
                        unauthenticated, forbidden, ok, forbidden, BodyKind.NONE));
        return cases.stream().flatMap(matrixCase -> Stream.of(Identity.values())
                .map(identity -> Arguments.of(matrixCase, identity)));
    }

    @ParameterizedTest(name = "{0} × {1}")
    @MethodSource("matrixCases")
    void permissionMatrixShouldHold(MatrixCase matrixCase, Identity identity) {
        String path = matrixCase.path()
                .replace("{familyId}", String.valueOf(fixture.parentA().familyId()))
                .replace("{childId}", String.valueOf(fixture.childAId()));
        Response response = execute(matrixCase.method(), specFor(identity), path,
                bodyFor(matrixCase.bodyKind(), identity));
        Expected expected = matrixCase.expectedFor(identity);
        response.then().statusCode(expected.status()).body("code", equalTo(expected.code()));
    }

    @Test
    void crossFamilyChildShouldBeDenied() {
        String childBUsername = nextUsername();
        createChild(fixture.parentB(), childBUsername);
        String childBToken = loginAndChangePassword(childBUsername,
                CHILD_INITIAL_PASSWORD, CHILD_NEW_PASSWORD);
        withToken(childBToken).when()
                .get("/api/v1/families/{familyId}", fixture.parentA().familyId())
                .then().statusCode(403).body("code", equalTo(100004));
        withToken(childBToken).when()
                .get("/api/v1/families/{familyId}/members", fixture.parentA().familyId())
                .then().statusCode(403).body("code", equalTo(100004));
    }

    private Object bodyFor(BodyKind kind, Identity identity) {
        if (kind == BodyKind.PASSWORD) {
            return passwordBody(identity);
        }
        Object body = sessionBodyFor(kind);
        return body != null ? body : familyBodyFor(kind);
    }

    private Object sessionBodyFor(BodyKind kind) {
        return switch (kind) {
            case REGISTER -> freshRegisterBody();
            case LOGIN -> Map.of("identifier", fixture.parentAPhone(),
                    "password", DEFAULT_PASSWORD);
            case REFRESH -> Map.of("refreshToken", fixture.parentA().refreshToken());
            case LOGOUT -> Map.of("refreshToken", "logout-placeholder-token");
            default -> null;
        };
    }

    private Object familyBodyFor(BodyKind kind) {
        return switch (kind) {
            case FAMILY_NAME -> Map.of("familyName", "矩阵改名");
            case ADD_CHILD -> freshAddChildBody();
            case NICKNAME -> Map.of("nickname", "矩阵改昵称");
            case CHILD_PASSWORD_RESET -> Map.of("newPassword", "ChildNew789");
            default -> null;
        };
    }

    private static Object passwordBody(Identity identity) {
        if (identity == Identity.CHILD) {
            return Map.of("oldPassword", CHILD_NEW_PASSWORD, "newPassword", "ChildNew456");
        }
        return Map.of("oldPassword", DEFAULT_PASSWORD, "newPassword", "Passw0rd!2");
    }

    private RequestSpecification specFor(Identity identity) {
        return switch (identity) {
            case ANONYMOUS -> given();
            case CHILD -> withToken(fixture.childAToken());
            case OWN_PARENT -> withToken(fixture.parentA());
            default -> withToken(fixture.parentB());
        };
    }

    private Response execute(String method, RequestSpecification spec, String path,
                             Object body) {
        return switch (method) {
            case "GET" -> spec.when().get(path);
            case "DELETE" -> spec.when().delete(path);
            case "POST" -> spec.contentType(ContentType.JSON).body(body).when().post(path);
            case "PUT" -> spec.contentType(ContentType.JSON).body(body).when().put(path);
            default -> throw new IllegalStateException("Unsupported method: " + method);
        };
    }

    private static Map<String, Object> freshRegisterBody() {
        return Map.of("phone", nextPhone(), "password", DEFAULT_PASSWORD, "nickname", "家长",
                "childPrivacyPolicyAccepted", true);
    }

    private static Map<String, Object> freshAddChildBody() {
        return Map.of("username", nextUsername(), "password", CHILD_INITIAL_PASSWORD,
                "nickname", "孩子");
    }

    private static String nextPhone() {
        return String.format("1392%07d", COUNTER.incrementAndGet());
    }

    private static String nextUsername() {
        return String.format("pgm%08d", COUNTER.incrementAndGet());
    }
}
