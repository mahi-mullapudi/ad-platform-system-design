package com.tutorialq.systemdesign.processor.job;

import com.tutorialq.systemdesign.processor.model.AdClickEventRecord;
import org.apache.flink.api.common.functions.AggregateFunction;

/**
 * Counts events per key within a Flink window.
 */
public class ClickCountAggregator implements AggregateFunction<AdClickEventRecord, Long, Long> {

    @Override
    public Long createAccumulator() {
        return 0L;
    }

    @Override
    public Long add(AdClickEventRecord value, Long accumulator) {
        return accumulator + 1;
    }

    @Override
    public Long getResult(Long accumulator) {
        return accumulator;
    }

    @Override
    public Long merge(Long a, Long b) {
        return a + b;
    }
}
