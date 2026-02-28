package com.tutorialq.systemdesign.processor.job;

import com.tutorialq.systemdesign.processor.model.ClickAggregation;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

/**
 * Wraps the aggregated count into a ClickAggregation with window metadata.
 * The key is "campaignId|eventType".
 */
public class ClickCountWindowFunction extends ProcessWindowFunction<Long, ClickAggregation, String, TimeWindow> {

    @Override
    public void process(String key, Context context, Iterable<Long> elements, Collector<ClickAggregation> out) {
        long count = elements.iterator().next();
        String[] parts = key.split("\\|", 2);
        String campaignId = parts[0];
        String eventType = parts.length > 1 ? parts[1] : "UNKNOWN";

        TimeWindow window = context.window();
        out.collect(new ClickAggregation(campaignId, eventType, count, window.getStart(), window.getEnd()));
    }
}
