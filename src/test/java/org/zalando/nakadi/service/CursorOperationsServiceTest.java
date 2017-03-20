package org.zalando.nakadi.service;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.zalando.nakadi.domain.NakadiCursor;
import org.zalando.nakadi.domain.NakadiCursorDistanceQuery;
import org.zalando.nakadi.domain.NakadiCursorDistanceResult;
import org.zalando.nakadi.domain.ShiftedNakadiCursor;
import org.zalando.nakadi.domain.Timeline;
import org.zalando.nakadi.exceptions.InternalNakadiException;
import org.zalando.nakadi.exceptions.NoSuchEventTypeException;
import org.zalando.nakadi.exceptions.runtime.InvalidCursorOperation;
import org.zalando.nakadi.service.timeline.TimelineService;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.zalando.nakadi.exceptions.runtime.InvalidCursorOperation.Reason.CURSORS_WITH_DIFFERENT_PARTITION;
import static org.zalando.nakadi.exceptions.runtime.InvalidCursorOperation.Reason.INVERTED_OFFSET_ORDER;
import static org.zalando.nakadi.exceptions.runtime.InvalidCursorOperation.Reason.INVERTED_TIMELINE_ORDER;
import static org.zalando.nakadi.exceptions.runtime.InvalidCursorOperation.Reason.PARTITION_NOT_FOUND;
import static org.zalando.nakadi.exceptions.runtime.InvalidCursorOperation.Reason.TIMELINE_NOT_FOUND;

public class CursorOperationsServiceTest {
    private TimelineService timelineService = mock(TimelineService.class);
    private CursorOperationsService service = new CursorOperationsService(timelineService);
    private final Timeline timeline = mock(Timeline.class);

    @Before
    public void setUp() {
        when(timeline.getOrder()).thenReturn(0);
    }

    @Test
    public void whenCursorsInSameVersionAndSameTimeline() throws Exception {
        final NakadiCursor initialCursor = new NakadiCursor(timeline, "0", "0000000000000001");
        final NakadiCursor finalCursor = new NakadiCursor(timeline, "0", "0000000000000002");
        final NakadiCursorDistanceQuery distanceQuery = new NakadiCursorDistanceQuery(initialCursor, finalCursor);
        final NakadiCursorDistanceResult distanceResult = service.calculateDistance(distanceQuery);
        assertThat(distanceResult.getDistance(), equalTo(1L));
    }

    @Test
    public void whenCursorsOffsetsAreInvertedThenException() throws Exception {
        final NakadiCursor initialCursor = new NakadiCursor(timeline, "0", "0000000000000002");
        final NakadiCursor finalCursor = new NakadiCursor(timeline, "0", "0000000000000001");
        final NakadiCursorDistanceQuery distanceQuery = new NakadiCursorDistanceQuery(initialCursor, finalCursor);

        expectException(distanceQuery, INVERTED_OFFSET_ORDER);
    }

    @Test
    public void whenCursorTimelinesAreInvertedThenException() throws Exception {
        final Timeline initialTimeline = mock(Timeline.class);
        final Timeline finalTimeline = mock(Timeline.class);

        when(initialTimeline.getOrder()).thenReturn(1);
        when(finalTimeline.getOrder()).thenReturn(0);

        final NakadiCursor initialCursor = new NakadiCursor(initialTimeline, "0", "0000000000000001");
        final NakadiCursor finalCursor = new NakadiCursor(finalTimeline, "0", "0000000000000002");
        final NakadiCursorDistanceQuery distanceQuery = new NakadiCursorDistanceQuery(initialCursor, finalCursor);

        expectException(distanceQuery, INVERTED_TIMELINE_ORDER);
    }

    @Test
    public void whenPartitionsDontMatch() {
        final NakadiCursor initialCursor = new NakadiCursor(timeline, "1", "0000000000000001");
        final NakadiCursor finalCursor = new NakadiCursor(timeline, "0", "0000000000000002");
        final NakadiCursorDistanceQuery distanceQuery = new NakadiCursorDistanceQuery(initialCursor, finalCursor);

        expectException(distanceQuery, CURSORS_WITH_DIFFERENT_PARTITION);
    }

    @Test
    public void whenTimelinesAreAdjacent() throws Exception {
        final Timeline initialTimeline = mock(Timeline.class);
        final Timeline.KafkaStoragePosition initialTimilinePositions = mock(Timeline.KafkaStoragePosition.class);

        when(initialTimeline.getOrder()).thenReturn(0);
        when(initialTimilinePositions.getOffsets()).thenReturn(Lists.newArrayList(10L));
        when(initialTimeline.getLatestPosition()).thenReturn(initialTimilinePositions);

        final Timeline finalTimeline = mock(Timeline.class);
        when(finalTimeline.getOrder()).thenReturn(1);

        final NakadiCursor initialCursor = new NakadiCursor(initialTimeline, "0", "0000000000000003");
        final NakadiCursor finalCursor = new NakadiCursor(finalTimeline, "0", "0000000000000001");
        final NakadiCursorDistanceQuery distanceQuery = new NakadiCursorDistanceQuery(initialCursor, finalCursor);

        final NakadiCursorDistanceResult distanceResult = service.calculateDistance(distanceQuery);
        assertThat(distanceResult.getDistance(), equalTo(8L));
    }

    @Test
    public void whenTimelinesAreNotAdjacent() throws Exception {
        final Timeline initialTimeline = mockTimeline(0, 10L);
        final Timeline intermediaryTimeline = mockTimeline(1, 9L);
        final Timeline finalTimeline = mockTimeline(2, 0L);

        when(timelineService.getActiveTimelinesOrdered(any()))
                .thenReturn(Lists.newArrayList(initialTimeline, intermediaryTimeline, finalTimeline));

        final NakadiCursor initialCursor = new NakadiCursor(initialTimeline, "0", "0000000000000003");
        final NakadiCursor finalCursor = new NakadiCursor(finalTimeline, "0", "0000000000000001");
        final NakadiCursorDistanceQuery distanceQuery = new NakadiCursorDistanceQuery(initialCursor, finalCursor);

        final NakadiCursorDistanceResult distanceResult = service.calculateDistance(distanceQuery);
        assertThat(distanceResult.getDistance(), equalTo(7L + 9L + 1L));
    }

    @Test(expected = InvalidCursorOperation.class)
    public void whenTimelineExpired() throws InternalNakadiException, NoSuchEventTypeException {
        final Timeline expiredTimeline = timeline; // order is zero
        final Timeline initialTimeline = mockTimeline(1, 10L);
        final Timeline finalTimeline = mockTimeline(2, 0L);

        when(timelineService.getActiveTimelinesOrdered(any()))
                .thenReturn(Lists.newArrayList(initialTimeline, finalTimeline));

        final NakadiCursor initialCursor = new NakadiCursor(expiredTimeline, "0", "0000000000000001");
        final NakadiCursor finalCursor = new NakadiCursor(expiredTimeline, "2", "0000000000000002");
        final NakadiCursorDistanceQuery distanceQuery = new NakadiCursorDistanceQuery(initialCursor, finalCursor);

        final NakadiCursorDistanceResult distanceResult = service.calculateDistance(distanceQuery);
    }

    @Test
    public void whenTimelineInTheFutureAndDoesntExist() throws InternalNakadiException, NoSuchEventTypeException {
        final Timeline initialTimeline = mockTimeline(1, 10L);
        final Timeline finalTimeline = mockTimeline(2, 0L);
        final Timeline futureTimeline = mockTimeline(4, 0L);

        when(timelineService.getActiveTimelinesOrdered(any()))
                .thenReturn(Lists.newArrayList(initialTimeline, finalTimeline));

        final NakadiCursor initialCursor = new NakadiCursor(initialTimeline, "0", "0000000000000001");
        final NakadiCursor finalCursor = new NakadiCursor(futureTimeline, "0", "0000000000000002");
        final NakadiCursorDistanceQuery distanceQuery = new NakadiCursorDistanceQuery(initialCursor, finalCursor);

        expectException(distanceQuery, TIMELINE_NOT_FOUND);
    }

    @Test
    public void missingPartitionInTimeline() throws Exception {
        final Timeline initialTimeline = mockTimeline(0, 10L);
        final Timeline intermediaryTimeline = mockTimeline(1, 9L);
        final Timeline finalTimeline = mockTimeline(2, 0L);

        when(timelineService.getActiveTimelinesOrdered(any()))
                .thenReturn(Lists.newArrayList(initialTimeline, intermediaryTimeline, finalTimeline));

        final NakadiCursor initialCursor = new NakadiCursor(initialTimeline, "1", "0000000000000003");
        final NakadiCursor finalCursor = new NakadiCursor(finalTimeline, "1", "0000000000000001");
        final NakadiCursorDistanceQuery distanceQuery = new NakadiCursorDistanceQuery(initialCursor, finalCursor);

        expectException(distanceQuery, PARTITION_NOT_FOUND);
    }

    @Test
    public void shiftCursorBackInTheSameTimelineClosed() {
        final Timeline initialTimeline = mockTimeline(0, 10L);
        final ShiftedNakadiCursor shiftedCursor = new ShiftedNakadiCursor(initialTimeline, "0", "000000000000000003",
                -3L);

        final NakadiCursor cursor = service.unshiftCursor(shiftedCursor);

        assertThat(cursor.getOffset(), equalTo("000000000000000000"));
    }

    @Test
    public void shiftCursorBackToPreviousTimeline() throws Exception {
        final Timeline initialTimeline = mockTimeline(0, 10L);
        final Timeline intermediaryTimeline = mockTimeline(1, 9L);
        final Timeline finalTimeline = mockTimeline(2, 9L);

        when(timelineService.getActiveTimelinesOrdered(any()))
                .thenReturn(Lists.newArrayList(initialTimeline, intermediaryTimeline, finalTimeline));

        final ShiftedNakadiCursor shiftedCursor = new ShiftedNakadiCursor(finalTimeline, "0", "000000000000000003",
                -15L);

        final NakadiCursor cursor = service.unshiftCursor(shiftedCursor);

        assertThat(cursor.getTimeline().getOrder(), equalTo(0));
        assertThat(cursor.getOffset(), equalTo("000000000000000007"));
    }

    @Test
    public void shiftCursorToExpiredTimeline() throws Exception {
        final Timeline initialTimeline = mockTimeline(0, 10L);
        final Timeline finalTimeline = mockTimeline(1, 9L);

        when(timelineService.getActiveTimelinesOrdered(any()))
                .thenReturn(Lists.newArrayList(initialTimeline, finalTimeline));

        final ShiftedNakadiCursor shiftedCursor = new ShiftedNakadiCursor(finalTimeline, "0", "000000000000000003",
                -15L);

        try {
            service.unshiftCursor(shiftedCursor);
            fail();
        } catch (final InvalidCursorOperation e) {
            assertThat(e.getReason(), equalTo(TIMELINE_NOT_FOUND));
        } catch (final Exception e) {
            fail();
        }
    }

    @Test
    public void shiftCursorToTheRightSameClosedTimeline() throws Exception {
        final Timeline initialTimeline = mockTimeline(0, 10L);
        when(timelineService.getActiveTimelinesOrdered(any()))
                .thenReturn(Lists.newArrayList(initialTimeline));
        final ShiftedNakadiCursor shiftedCursor = new ShiftedNakadiCursor(initialTimeline, "0", "000000000000000003",
                2L);

        final NakadiCursor cursor = service.unshiftCursor(shiftedCursor);

        assertThat(cursor.getOffset(), equalTo("000000000000000005"));
    }

    @Test
    public void shiftCursorRightToNextTimeline() throws Exception {
        final Timeline initialTimeline = mockTimeline(0, 10L);
        final Timeline nextTimeline = mockTimeline(1, 10L);
        when(timelineService.getActiveTimelinesOrdered(any()))
                .thenReturn(Lists.newArrayList(initialTimeline, nextTimeline));
        final ShiftedNakadiCursor shiftedCursor = new ShiftedNakadiCursor(initialTimeline, "0", "000000000000000003",
                10L);

        final NakadiCursor cursor = service.unshiftCursor(shiftedCursor);

        assertThat(cursor.getTimeline().getOrder(), equalTo(1));
        assertThat(cursor.getOffset(), equalTo("000000000000000003"));
    }

    @Test
    public void shiftCursorForwardInTheSameTimelineOpen() {
        final Timeline initialTimeline = mockTimeline(0, 0L);
        final ShiftedNakadiCursor shiftedCursor = new ShiftedNakadiCursor(initialTimeline, "0", "000000000000000003",
                3L);

        final NakadiCursor cursor = service.unshiftCursor(shiftedCursor);

        assertThat(cursor.getOffset(), equalTo("000000000000000006"));
    }

    private void expectException(final NakadiCursorDistanceQuery distanceQuery,
                                 final InvalidCursorOperation.Reason invertedOffsetOrder) {
        try {
            service.calculateDistance(distanceQuery);
            fail();
        } catch (final InvalidCursorOperation e) {
            assertThat(e.getReason(), equalTo(invertedOffsetOrder));
        } catch (final Exception e) {
            fail();
        }
    }

    private Timeline mockTimeline(final int order, final long latestOffset) {
        final Timeline intermediaryTimeline = mock(Timeline.class);
        when(intermediaryTimeline.getOrder()).thenReturn(order);
        final Timeline.KafkaStoragePosition intermediaryTimilinePositions = mock(Timeline.KafkaStoragePosition.class);
        when(intermediaryTimilinePositions.getOffsets()).thenReturn(Lists.newArrayList(latestOffset));
        when(intermediaryTimeline.getLatestPosition()).thenReturn(intermediaryTimilinePositions);
        when(intermediaryTimeline.isActive()).thenReturn(latestOffset == 0);
        return intermediaryTimeline;
    }
}