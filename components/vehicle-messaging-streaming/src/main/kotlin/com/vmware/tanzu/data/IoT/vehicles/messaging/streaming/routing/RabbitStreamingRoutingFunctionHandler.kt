package com.vmware.tanzu.data.IoT.vehicles.messaging.streaming.routing

import com.rabbitmq.stream.*
import com.vmware.tanzu.data.IoT.vehicles.messaging.streaming.creational.StreamSetup
import nyla.solutions.core.patterns.creational.Creator
import java.time.Duration
import java.util.function.Function

/**
 *
 * @author Gregory Green
 */
class RabbitStreamingRoutingFunctionHandler(
    private val inputStreamName: String,
    private val inputStreamSetup: StreamSetup,
    private val inputEnvCreator: Creator<Environment>,
    private val outputStreamName: String,
    private val outputEnvCreator: Creator<Environment>,
    private val outputStreamSetup: StreamSetup,
    private val routingFunction: Function<ByteArray, ByteArray>,
    private val applicationName: String,
    private val offset: Long = 0,
    private val replay: Boolean = false,
    private val handler : ConfirmationHandler = ConfirmationHandler{ status -> if(!status.isConfirmed)
        println("ERROR: $status.code NOT confirmed}")
    }) : MessageHandler, AutoCloseable {

    private var consumer: Consumer;
    private var producer: Producer;

    init {
        var inEnv = inputEnvCreator.create();
        inputStreamSetup.initialize(inputStreamName);
        outputStreamSetup.initialize(outputStreamName);

        if (replay) {
            println("=========== REPLAYING ALL STREAM  MESSAGES ======================");

            consumer = inEnv.consumerBuilder()
                .stream(inputStreamName)
                .offset(OffsetSpecification.offset(offset))
                .messageHandler(this)
                .build();
        } else {
            consumer = inEnv.consumerBuilder()
                .stream(inputStreamName)
                .name(applicationName).autoCommitStrategy()
                .messageCountBeforeCommit(50_000)
                .flushInterval(Duration.ofSeconds(10))
                .builder()
                .messageHandler(this)
                .build();
        }

        producer = outputEnvCreator.create().producerBuilder().stream(outputStreamName).build()
    }
    /**
     * Callback for an inbound message.
     *
     * @param context context on the message
     * @param message the message
     */
    override fun handle(context: MessageHandler.Context?, message: Message?) {

        var output = routingFunction.apply(message!!.bodyAsBinary);
        val msg = producer.messageBuilder().addData(output).build();

        producer.send(msg, handler );
    }

    override fun close() {
        try{producer.close()} finally{}
        try{consumer.close()} finally {}
    }
}