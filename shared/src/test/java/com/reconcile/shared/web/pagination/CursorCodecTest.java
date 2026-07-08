package com.reconcile.shared.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CursorCodecTest {

    private static final String FH = CursorCodec.filterHash(Map.of("status", "OPEN"));

    @Test
    void roundTripsSortKeyIdAndFilterHash() {
        Instant sortKey = Instant.parse("2026-07-07T09:30:00.123456Z");
        UUID id = UUID.randomUUID();

        Cursor decoded = CursorCodec.decode(CursorCodec.encode(sortKey, id, FH), FH);

        assertThat(decoded.sortKey()).isEqualTo(sortKey);
        assertThat(decoded.id()).isEqualTo(id);
        assertThat(decoded.filterHash()).isEqualTo(FH);
    }

    @Test
    void truncatesNanosToMicrosSoItMatchesTimestamptz() {
        // Postgres TIMESTAMPTZ stores microseconds; the extra nanos must not survive the round-trip,
        // otherwise the keyset boundary compares against a value the DB never stored.
        Instant withNanos = Instant.parse("2026-07-07T09:30:00Z").plusNanos(123_456_789L);
        UUID id = UUID.randomUUID();

        Cursor decoded = CursorCodec.decode(CursorCodec.encode(withNanos, id, FH), FH);

        assertThat(decoded.sortKey()).isEqualTo(withNanos.truncatedTo(ChronoUnit.MICROS));
        assertThat(decoded.sortKey().getNano() % 1000).isZero();
    }

    @Test
    void twoRowsOneMicrosecondApartPageCorrectlyAcrossBoundary() {
        // The Delta-1 guarantee: adjacent rows one microsecond apart must remain strictly ordered and
        // distinguishable through the cursor, so neither is skipped nor repeated at a page boundary.
        UUID idA = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID idB = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        Instant older = Instant.parse("2026-07-07T09:30:00.000001Z");
        Instant newer = older.plus(1, ChronoUnit.MICROS);

        Cursor cOlder = CursorCodec.decode(CursorCodec.encode(older, idA, FH), FH);
        Cursor cNewer = CursorCodec.decode(CursorCodec.encode(newer, idB, FH), FH);

        assertThat(cOlder.sortKey()).isBefore(cNewer.sortKey());
        assertThat(cNewer.sortKey().toEpochMilli()).isEqualTo(cOlder.sortKey().toEpochMilli());
        assertThat(CursorCodec.encode(older, idA, FH)).isNotEqualTo(CursorCodec.encode(newer, idB, FH));
    }

    @Test
    void rejectsCursorMintedUnderDifferentFilterSet() {
        String cursor = CursorCodec.encode(Instant.now(), UUID.randomUUID(), FH);
        String otherHash = CursorCodec.filterHash(Map.of("status", "RESOLVED"));

        assertThatThrownBy(() -> CursorCodec.decode(cursor, otherHash))
                .isInstanceOf(InvalidCursorException.class)
                .hasMessageContaining("filter set");
    }

    @Test
    void rejectsNonBase64Cursor() {
        assertThatThrownBy(() -> CursorCodec.decode("!!!not-base64!!!", FH)).isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void rejectsStructurallyWrongOrEmptyCursor() {
        String twoParts = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("1|only-two".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> CursorCodec.decode(twoParts, FH)).isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> CursorCodec.decode("", FH)).isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> CursorCodec.decode(null, FH)).isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void rejectsMalformedInstantOrUuidInPayload() {
        String badInstant = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(("1|not-a-date|" + UUID.randomUUID() + "|" + FH)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> CursorCodec.decode(badInstant, FH)).isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void filterHashIsOrderIndependentAndFullLength() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("status", "OPEN");
        a.put("type", "UNMATCHED");
        Map<String, String> b = new LinkedHashMap<>();
        b.put("type", "UNMATCHED");
        b.put("status", "OPEN");

        assertThat(CursorCodec.filterHash(a)).isEqualTo(CursorCodec.filterHash(b));
        assertThat(CursorCodec.filterHash(a)).hasSize(64);
    }

    @Test
    void filterHashIgnoresNullValuedFilters() {
        Map<String, String> withNull = new LinkedHashMap<>();
        withNull.put("status", "OPEN");
        withNull.put("runId", null);

        assertThat(CursorCodec.filterHash(withNull)).isEqualTo(CursorCodec.filterHash(Map.of("status", "OPEN")));
    }
}
