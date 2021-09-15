package io.github.quickmsg.common.message;

import io.github.quickmsg.common.utils.JacksonUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * @author luxurong
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class HeapMqttMessage {

    private String clientIdentifier;

    private String topic;

    private int qos;

    private boolean retain;

    private byte[] message;


    public Map<String, Object> getKeyMap() {
        Map<String, Object> keys = new HashMap<>(5);
        keys.put("clientIdentifier", this.clientIdentifier);
        keys.put("topic", this.topic);
        keys.put("qos", this.qos);
        keys.put("retain", this.retain);
        keys.put("msg", JacksonUtil.json2Bean(new String(message), Map.class));
        return keys;
    }

}