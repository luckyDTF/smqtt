package io.github.quickmsg.http;

import io.github.quickmsg.common.rule.source.Source;
import io.github.quickmsg.common.rule.source.SourceBean;
import io.github.quickmsg.common.utils.JacksonUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.handler.codec.http.HttpHeaderNames;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.Map;

/**
 * @author luxurong
 * @date 2021/9/15 14:07
 */
@Slf4j
public class HttpSourceBean implements SourceBean {

    private HttpParam httpParam;

    private HttpClient httpClient;


    @Override
    public Boolean support(Source source) {
        return source == Source.HTTP;
    }

    @Override
    @SuppressWarnings("Unchecked")
    public Boolean bootstrap(Map<String, Object> sourceParam) {
        httpParam = new HttpParam();
        httpParam.setUrl(String.valueOf(sourceParam.get("url")));
        httpParam.setHeaders((Map<String, Object>) sourceParam.get("headers"));
        httpParam.setAdditions((Map<String, Object>) sourceParam.get("additions"));
        httpClient = HttpClient.create().headers(heads -> {
            httpParam.getHeaders().forEach(heads::add);
            heads.add(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        });
        return true;
    }


    @Override
    public void transmit(Map<String, Object> object) {
        if (httpParam.getAdditions() != null && httpParam.getAdditions().size() > 0) {
            httpParam.getAdditions().forEach(object::put);
        }
        httpClient
                .post()
                .uri(httpParam.getUrl())
                .send(Mono.just(PooledByteBufAllocator.DEFAULT.directBuffer().writeBytes(JacksonUtil.bean2Json(object).getBytes())))
                .response()
                .log()
                .subscribe();
    }

    @Override
    public void close() {
        httpClient.configuration().connectionProvider().dispose();
    }


}
