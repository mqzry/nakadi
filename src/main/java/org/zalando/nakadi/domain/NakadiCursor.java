package org.zalando.nakadi.domain;

import com.google.common.base.Preconditions;

import java.util.Objects;

public class NakadiCursor implements Comparable<NakadiCursor> {
    public static final int VERSION_LENGTH = 3;
    /**
     * - ZERO is reserved for old offset format, e.g. those previous to timelines: "000000000000000010"
     * - ONE is reserved for the first version of timeline offsets: "001-0001-0000000000000001"
     **/
    public enum Version {
        ZERO("000"),
        ONE("001"),;
        public final String code;

        Version(final String code) {
            Preconditions.checkArgument(
                    code.length() == VERSION_LENGTH,
                    "Version field length should be equal to " + VERSION_LENGTH);
            this.code = code;
        }
    }

    private final Timeline timeline;
    private final String partition;
    // NO BEGIN HERE - only real offset!
    private final String offset;

    public NakadiCursor(
            final Timeline timeline,
            final String partition,
            final String offset) {
        this.timeline = timeline;
        this.partition = partition;
        this.offset = offset;
    }

    public Timeline getTimeline() {
        return timeline;
    }

    public String getTopic() {
        return timeline.getTopic();
    }

    public String getEventType() {
        return timeline.getEventType();
    }

    public String getPartition() {
        return partition;
    }

    public String getOffset() {
        return offset;
    }

    public EventTypePartition getEventTypePartition() {
        return new EventTypePartition(timeline.getEventType(), partition);
    }

    public TopicPartition getTopicPartition() {
        return new TopicPartition(timeline.getTopic(), partition);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NakadiCursor)) {
            return false;
        }

        final NakadiCursor that = (NakadiCursor) o;
        return Objects.equals(this.timeline, that.timeline)
                && Objects.equals(this.partition, that.partition)
                && Objects.equals(this.offset, that.offset);
    }

    @Override
    public int compareTo(final NakadiCursor other) {
        final int orderDiffers = Integer.compare(this.getTimeline().getOrder(), other.getTimeline().getOrder());
        if (0 != orderDiffers) {
            return orderDiffers;
        }
        return this.getOffset().compareTo(other.getOffset());
    }

    @Override
    public int hashCode() {
        int result = timeline.hashCode();
        result = 31 * result + partition.hashCode();
        result = 31 * result + offset.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "NakadiCursor{" +
                "partition='" + partition + '\'' +
                ", offset='" + offset + '\'' +
                ", timeline='" + timeline + '\'' +
                '}';
    }

}
