package org.apache.axis.attachments;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import junit.framework.TestCase;

import org.apache.axiom.testutils.activation.InstrumentedDataSource;
import org.apache.axiom.testutils.activation.RandomDataSource;
import org.apache.commons.io.output.NullOutputStream;

public class TestDimeBodyPart extends TestCase {

    private static jakarta.activation.DataSource asJakartaDataSource(final InstrumentedDataSource ds) {
        return new jakarta.activation.DataSource() {
            @Override
            public InputStream getInputStream() throws IOException {
                return ds.getInputStream();
            }

            @Override
            public OutputStream getOutputStream() throws IOException {
                return ds.getOutputStream();
            }

            @Override
            public String getContentType() {
                return ds.getContentType();
            }

            @Override
            public String getName() {
                return ds.getName();
            }
        };
    }

    public void testWriteToWithDynamicContentDataHandlerClosesInputStreams() throws Exception {
        InstrumentedDataSource ds = new InstrumentedDataSource(new RandomDataSource(1000));
        DimeBodyPart bp = new DimeBodyPart(new DynamicContentDataHandler(asJakartaDataSource(ds)), "1234");
        bp.write(new NullOutputStream(), (byte) 0);
        assertEquals(0, ds.getOpenStreamCount());
    }
}
