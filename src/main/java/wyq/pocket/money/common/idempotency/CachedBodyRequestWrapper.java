package wyq.pocket.money.common.idempotency;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * 可重复读取请求体的包装器（M3 设计 §5）。
 *
 * <p>过滤器为计算请求指纹需提前消费请求体，本包装器缓存字节供下游
 * {@code @RequestBody} 再次读取，避免流一次性耗尽。
 */
class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() {
                return input.read();
            }

            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // 同步读取，无需异步监听
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
