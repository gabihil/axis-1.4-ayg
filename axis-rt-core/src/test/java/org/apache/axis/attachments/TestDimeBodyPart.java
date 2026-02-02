package org.apache.axis.attachments;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FilterInputStream;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import org.apache.commons.io.output.NullOutputStream;

public class TestDimeBodyPart extends TestCase {

    private static final class InstrumentedDataSource implements jakarta.activation.DataSource {
        private final byte[] data;
        private final AtomicInteger openStreamCount = new AtomicInteger();

        private InstrumentedDataSource(int size) {
            this.data = new byte[size];
            for (int i = 0; i < size; i++) {
                data[i] = (byte)(i & 0xFF);
            }
        }

        @Override
        public InputStream getInputStream() {
            openStreamCount.incrementAndGet();
            return new FilterInputStream(new ByteArrayInputStream(data)) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        openStreamCount.decrementAndGet();
                    }
                }
            };
        }

        @Override
        public OutputStream getOutputStream() {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public String getContentType() {
            return "application/octet-stream";
        }

        @Override
        public String getName() {
            return "InstrumentedDataSource";
        }

        private int getOpenStreamCount() {
            return openStreamCount.get();
        }
    }

    public void testWriteToWithDynamicContentDataHandlerClosesInputStreams() throws Exception {
        InstrumentedDataSource ds = new InstrumentedDataSource(1000);
        DimeBodyPart bp = new DimeBodyPart(new DynamicContentDataHandler(ds), "1234");
        bp.write(new NullOutputStream(), (byte) 0);
        assertEquals(0, ds.getOpenStreamCount());
    }
}
