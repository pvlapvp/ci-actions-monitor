package ru.pvlapvp.ciactionsmonitor.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobLogTextReaderTest {

    @Test
    void readsFullStream() throws IOException {
        JobLogTextReader.Result result = read("line one\nline two\n", 1024);

        assertEquals("line one\nline two\n", result.text());
        assertFalse(result.truncated());
    }

    @Test
    void stopsAtTheLimit() throws IOException {
        JobLogTextReader.Result result = read("abcdefghij", 4);

        assertEquals("abcd", result.text());
        assertTrue(result.truncated());
    }

    @Test
    void exactlyMaxBytesIsNotTruncated() throws IOException {
        JobLogTextReader.Result result = read("abcd", 4);

        assertEquals("abcd", result.text());
        assertFalse(result.truncated());
    }

    @Test
    void cancellationBeforeFirstReadReturnsNull() throws IOException {
        AtomicInteger reads = new AtomicInteger();
        InputStream input = new OneByteInputStream(reads, 100);

        JobLogTextReader.Result result = JobLogTextReader.read(input, 1024, () -> true);

        assertNull(result);
        assertEquals(0, reads.get());
    }

    @Test
    void cancellationMidStreamReturnsNull() throws IOException {
        AtomicInteger reads = new AtomicInteger();
        InputStream input = new OneByteInputStream(reads, 100);
        AtomicInteger checks = new AtomicInteger();

        JobLogTextReader.Result result = JobLogTextReader.read(input, 1024, () -> checks.incrementAndGet() > 3);

        assertNull(result);
        assertEquals(3, reads.get());
    }

    @Test
    void zeroLimitReportsTruncationForNonEmptyLog() throws IOException {
        JobLogTextReader.Result result = read("abc", 0);

        assertEquals("", result.text());
        assertTrue(result.truncated());
    }

    private static JobLogTextReader.Result read(String text, int maxBytes) throws IOException {
        InputStream input = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        JobLogTextReader.Result result = JobLogTextReader.read(input, maxBytes, () -> false);
        if (result == null) {
            throw new AssertionError("The reader was unexpectedly cancelled");
        }
        return result;
    }

    private static final class OneByteInputStream extends InputStream {
        private final AtomicInteger reads;
        private final int available;

        private OneByteInputStream(AtomicInteger reads, int available) {
            this.reads = reads;
            this.available = available;
        }

        @Override
        public int read() {
            return readByte();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (length <= 0) {
                return 0;
            }
            int value = readByte();
            if (value < 0) {
                return -1;
            }
            buffer[offset] = (byte) value;
            return 1;
        }

        private int readByte() {
            if (reads.get() >= available) {
                return -1;
            }
            reads.incrementAndGet();
            return 'x';
        }
    }
}
