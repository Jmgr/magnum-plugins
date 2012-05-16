package cz.mosra.magnum.StanfordImporter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class StanfordImporterTest {

    /* Wrapper for all the WTFs. */
    BufferedReader stringReader(String s) { try {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(s.getBytes("US-ASCII"))));
    } catch(UnsupportedEncodingException e) {
        throw new RuntimeException(e);
    }}

    /* Another WTF. */
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Test
    public void parseNoSignature() throws StanfordImporter.Exception, IOException {
        expectedEx.expect(StanfordImporter.Exception.class);
        expectedEx.expectMessage("StanfordImporter: wrong file signature blah");

        StanfordImporter.Header header = StanfordImporter.parseHeader(
            stringReader("blah\n"));
    }

    @Test
    public void parseNoHeaderEnd() throws StanfordImporter.Exception, IOException {
        expectedEx.expect(StanfordImporter.Exception.class);
        expectedEx.expectMessage("StanfordImporter: the file is too short");

        StanfordImporter.Header header = StanfordImporter.parseHeader(
            stringReader("ply\n"));
    }

    @Test
    public void parseFormat() throws StanfordImporter.Exception, IOException {
        StanfordImporter.Header header = StanfordImporter.parseHeader(
            stringReader("ply\nformat binary_little_endian 1.0\nend_header\n"));
        assertThat(header.getFormat(), equalTo(StanfordImporter.Format.BinaryLittleEndian10));

        header = StanfordImporter.parseHeader(
            stringReader("ply\ncomment blah\nformat binary_big_endian 1.0\nend_header\n"));
        assertThat(header.getFormat(), equalTo(StanfordImporter.Format.BinaryBigEndian10));

        header = StanfordImporter.parseHeader(
            stringReader("ply\nformat ascii 1.0\nelement unsupported anything\nend_header\n"));
        assertThat(header.getFormat(), equalTo(StanfordImporter.Format.Ascii10));
    }

    @Test
    public void parseFormatDuplicate() throws StanfordImporter.Exception, IOException {
        expectedEx.expect(StanfordImporter.Exception.class);
        expectedEx.expectMessage("StanfordImporter: duplicit format line: format another");

        StanfordImporter.Header header = StanfordImporter.parseHeader(
            stringReader("ply\nformat binary_little_endian 1.0\nformat another\nend_header\n"));
    }

    @Test
    public void parseFormatWrongVersion() throws StanfordImporter.Exception, IOException {
        expectedEx.expect(StanfordImporter.Exception.class);
        expectedEx.expectMessage("StanfordImporter: unsupported file version 2.0");

        StanfordImporter.Header header = StanfordImporter.parseHeader(
            stringReader("ply\nformat binary_little_endian 2.0\nend_header\n"));
    }

    @Test
    public void parseFormatWrong() throws StanfordImporter.Exception, IOException {
        expectedEx.expect(StanfordImporter.Exception.class);
        expectedEx.expectMessage("StanfordImporter: unsupported file format pencil_drawing");

        StanfordImporter.Header header = StanfordImporter.parseHeader(
            stringReader("ply\nformat pencil_drawing 1.0\nend_header\n"));
    }
}
