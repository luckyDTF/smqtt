package io.github.quickmsg.common.event.message;

import lombok.Getter;
import lombok.Setter;
import org.apache.ignite.cache.query.annotations.QuerySqlField;

/**
 * @author luxurong
 */
@Getter
@Setter
public class PingEvent extends MessageEvent {

    @QuerySqlField(index = true)
    private String type;

    @QuerySqlField(index = true)
    private String clientId;

    @QuerySqlField(index = true, descending = true)
    private long timestamp;


}
