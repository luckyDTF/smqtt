package io.github.quickmsg.core.protocol;

import io.github.quickmsg.common.auth.PasswordAuthentication;
import io.github.quickmsg.common.event.message.ConnectEvent;
import io.github.quickmsg.common.integrate.channel.ChannelRegistry;
import io.github.quickmsg.common.channel.MqttChannel;
import io.github.quickmsg.common.context.ReceiveContext;
import io.github.quickmsg.common.enums.ChannelStatus;
import io.github.quickmsg.common.enums.Event;
import io.github.quickmsg.common.spi.registry.EventRegistry;
import io.github.quickmsg.common.spi.registry.MessageRegistry;
import io.github.quickmsg.common.utils.MqttMessageUtils;
import io.github.quickmsg.common.message.SmqttMessage;
import io.github.quickmsg.common.protocol.Protocol;
import io.github.quickmsg.common.integrate.topic.SubscribeTopic;
import io.github.quickmsg.common.integrate.topic.TopicRegistry;
import io.github.quickmsg.core.mqtt.MqttReceiveContext;
import io.github.quickmsg.metric.counter.WindowMetric;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.*;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.ContextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author luxurong
 */
@Slf4j
public class ConnectProtocol implements Protocol<MqttConnectMessage, ConnectEvent> {

    private final static List<MqttMessageType> MESSAGE_TYPE_LIST = new ArrayList<>();

    private static final int MILLI_SECOND_PERIOD = 1_000;


    static {
        MESSAGE_TYPE_LIST.add(MqttMessageType.CONNECT);
    }

    private static void accept(MqttChannel mqttChannel1) {
        WindowMetric.WINDOW_METRIC_INSTANCE.recordConnect(-1);
    }

    @Override
    public Mono<ConnectEvent> parseProtocol(SmqttMessage<MqttConnectMessage> smqttMessage, MqttChannel mqttChannel, ContextView contextView) {
        try {
            MqttConnectMessage message = smqttMessage.getMessage();
            MqttReceiveContext mqttReceiveContext = (MqttReceiveContext) contextView.get(ReceiveContext.class);
            EventRegistry eventRegistry = mqttReceiveContext.getEventRegistry();
            MqttConnectVariableHeader mqttConnectVariableHeader = message.variableHeader();
            MqttConnectPayload mqttConnectPayload = message.payload();
            String clientIdentifier = mqttConnectPayload.clientIdentifier();
            ChannelRegistry channelRegistry = mqttReceiveContext.getChannelRegistry();
            TopicRegistry topicRegistry = mqttReceiveContext.getTopicRegistry();
            PasswordAuthentication passwordAuthentication = mqttReceiveContext.getPasswordAuthentication();
            /*check clientIdentifier exist*/
            if (channelRegistry.exists(clientIdentifier)) {
                return mqttChannel.write(
                        MqttMessageUtils.buildConnectAck(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED),
                        false).then(mqttChannel.close()).then(new ConnectEvent());
            }
            /*protocol version support*/
            if (MqttVersion.MQTT_3_1_1.protocolLevel() != (byte) mqttConnectVariableHeader.version()
                    && MqttVersion.MQTT_3_1.protocolLevel() != (byte) mqttConnectVariableHeader.version()) {
                return mqttChannel.write(
                        MqttMessageUtils.buildConnectAck(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION),
                        false).then(mqttChannel.close());
            }
            /*password check*/
            if (passwordAuthentication.auth(mqttConnectPayload.userName(), mqttConnectPayload.passwordInBytes(), clientIdentifier)) {
                /*cancel  defer close not authenticate channel */
                mqttChannel.disposableClose();
                mqttChannel.setClientIdentifier(mqttConnectPayload.clientIdentifier());
                if (mqttConnectVariableHeader.isWillFlag()) {
                    mqttChannel.setWill(MqttChannel.Will.builder()
                            .isRetain(mqttConnectVariableHeader.isWillRetain())
                            .willTopic(mqttConnectPayload.willTopic())
                            .willMessage(mqttConnectPayload.willMessageInBytes())
                            .mqttQoS(MqttQoS.valueOf(mqttConnectVariableHeader.willQos()))
                            .build());
                }
                mqttChannel.setAuthTime(System.currentTimeMillis());
                mqttChannel.setKeepalive(mqttConnectVariableHeader.keepAliveTimeSeconds());
                mqttChannel.setSessionPersistent(!mqttConnectVariableHeader.isCleanSession());
                mqttChannel.setStatus(ChannelStatus.ONLINE);
                mqttChannel.setUsername(mqttConnectPayload.userName());
                /*registry unread event close channel */

                mqttChannel.getConnection()
                        .onReadIdle(mqttConnectVariableHeader.keepAliveTimeSeconds() * MILLI_SECOND_PERIOD << 1,
                                () -> close(mqttChannel, mqttReceiveContext, eventRegistry));

                /*registry will message send */
                mqttChannel.registryClose(channel -> Optional.ofNullable(mqttChannel.getWill())
                        .ifPresent(will ->
                                topicRegistry.getSubscribesByTopic(will.getWillTopic(), will.getMqttQoS())
                                        .forEach(subscribeTopic -> {
                                            MqttChannel subscribeChannel = subscribeTopic.getMqttChannel();
                                            subscribeChannel.write(
                                                    MqttMessageUtils
                                                            .buildPub(false,
                                                                    subscribeTopic.getQoS(),
                                                                    subscribeTopic.getQoS() == MqttQoS.AT_MOST_ONCE
                                                                            ? 0 : subscribeChannel.generateMessageId(),
                                                                    will.getWillTopic(),
                                                                    Unpooled.wrappedBuffer(will.getWillMessage())
                                                            ), subscribeTopic.getQoS().value() > 0
                                            ).subscribe();
                                        })));
                /* do session message*/
                doSession(mqttChannel, channelRegistry, topicRegistry);


                /* registry new channel*/
                channelRegistry.registry(mqttChannel.getClientIdentifier(), mqttChannel);

                /* registry close mqtt channel event*/
                mqttChannel.registryClose(channel -> this.close(mqttChannel, mqttReceiveContext, eventRegistry));

                WindowMetric.WINDOW_METRIC_INSTANCE.recordConnect(1);

                mqttChannel.registryClose(ConnectProtocol::accept);

                eventRegistry.registry(Event.CONNECT, mqttChannel, message, mqttReceiveContext);

                return mqttChannel.write(MqttMessageUtils.buildConnectAck(MqttConnectReturnCode.CONNECTION_ACCEPTED), false)
                        .then(Mono.fromRunnable(() -> sendOfflineMessage(mqttReceiveContext.getMessageRegistry(), mqttChannel)));
            } else {
                return mqttChannel.write(
                        MqttMessageUtils.buildConnectAck(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD),
                        false).then(mqttChannel.close());
            }
        } catch (Exception e) {
            log.error("connect error ", e);
        }
        return Mono.empty();
    }

    private void sendOfflineMessage(MessageRegistry messageRegistry, MqttChannel mqttChannel) {
        Optional.ofNullable(messageRegistry.getSessionMessage(mqttChannel.getClientIdentifier()))
                .ifPresent(sessionMessages -> {
                    sessionMessages.forEach(sessionMessage -> {
                        mqttChannel
                                .write(sessionMessage.toPublishMessage(mqttChannel),
                                        sessionMessage.getQos() > 0)
                                .subscribeOn(Schedulers.single())
                                .subscribe();
                    });
                });
    }

    private void close(MqttChannel mqttChannel, MqttReceiveContext mqttReceiveContext, EventRegistry eventRegistry) {
        log.info(" 【{}】【{}】 【{}】",
                Thread.currentThread().getName(),
                "CLOSE",
                mqttChannel);
        mqttChannel.setStatus(ChannelStatus.OFFLINE);
        if (!mqttChannel.isSessionPersistent()) {
            mqttReceiveContext.getTopicRegistry().clear(mqttChannel);
            mqttReceiveContext.getChannelRegistry().close(mqttChannel);
        }
        eventRegistry.registry(Event.CLOSE, mqttChannel, null, mqttReceiveContext);
        mqttChannel.close().subscribe();
    }

    /**
     * session
     *
     * @param mqttChannel     new channel      {@link MqttChannel}
     * @param channelRegistry {@link ChannelRegistry}
     * @param topicRegistry   {@link TopicRegistry}
     */
    private void doSession(MqttChannel mqttChannel,
                           ChannelRegistry channelRegistry,
                           TopicRegistry topicRegistry) {
        Optional.ofNullable(channelRegistry.get(mqttChannel.getClientIdentifier()))
                .ifPresent(sessionChannel -> {
                    Set<SubscribeTopic> topics = sessionChannel.getTopics().stream().map(subscribeTopic ->
                            new SubscribeTopic(
                                    subscribeTopic.getTopicFilter(),
                                    subscribeTopic.getQoS(),
                                    mqttChannel))
                            .collect(Collectors.toSet());
                    // registry new chanel
                    topicRegistry.registrySubscribesTopic(topics);
                    // remove old channel
                    channelRegistry.close(sessionChannel);
                    topicRegistry.clear(sessionChannel);
                });
    }


    @Override
    public List<MqttMessageType> getMqttMessageTypes() {
        return MESSAGE_TYPE_LIST;
    }


}
