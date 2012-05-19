package cz.mosra.magnum.StanfordImporter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class StanfordImporterTest {

    /* Wrapper for all the WTFs. */
    InputStream stringStream(String s) { try {
        return new ByteArrayInputStream(s.getBytes("US-ASCII"));
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
            stringStream("blah\n"));
    }

    @Test
    public void parseNoHeaderEnd() throws StanfordImporter.Exception, IOException {
        expectedEx.expect(StanfordImporter.Exception.class);
        expectedEx.expectMessage("StanfordImporter: the file is too short");

        StanfordImporter.Header header = StanfordImporter.parseHeader(
            stringStream("ply\n"));
    }

    @Test
    public void parseFormat() throws StanfordImporter.Exception, IOException {
        StanfordImporter.Header header = StanfordImporter.parseHeader(
            stringStream("ply\nformat binary_little_endian 1.0\nend_header\n"));
        assertThat(header.getFormat(), equalTo(StanfordImporter.Format.BinaryLittleEndian10));

        header = StanfordImporter.parseHeader(
            stringStream("ply\ncomment blah\nformat binary_big_endian 1.0\nend_header\n"));
        assertThat(header.getFormat(), equalTo(StanfordImporter.Format.BinaryBigEndian10));

        header = StanfordImporter.parseHeader(
            stringStream("ply\nformat ascii 1.0\nelement unsupported anything\nend_header\n"));
        assertThat(header.getFormat(), equalTo(StanfordImporter.Format.Ascii10));
    }

    @Test
    public void parseFormatDuplicate() throws StanfordImporter.Exception, IOException {
        expectedEx.expect(StanfordImporter.Exception.class);
        expectedEx.expectMessage("StanfordImporter: duplicit format line: format another");

        StanfordImporter.Header header = StanfordImporter.parseHeader(
            stringStream("ply\nformat binary_little_endian 1.0\nformat another\nend_header\n"));
    }

    @Test
    public void parseFormatWrongVersion() throws StanfordImporter.Exception, IOException {
        expectedEx.expect(StanfordImporter.Exception.class);
        expectedEx.expectMessage("StanfordImporter: unsupported file version 2.0");

        StanfordImporter.Header header = StanfordImporter.parseHeader(
            stringStream("ply\nformat binary_little_endian 2.0\nend_header\n"));
    }

    @Test
    public void parseFormatWrong() throws StanfordImporter.Exception, IOException {
        expectedEx.expect(StanfordImporter.Exception.class);
        expectedEx.expectMessage("StanfordImporter: unsupported file format pencil_drawing");

        StanfordImporter.Header header = StanfordImporter.parseHeader(
            stringStream("ply\nformat pencil_drawing 1.0\nend_header\n"));
    }

    @Test
    public void parseVertexElementHeader() throws StanfordImporter.Exception, IOException {
        StanfordImporter.VertexElementHeader header = StanfordImporter.parseVertexElementHeader(
            stringStream("element vertex 128\nproperty uchar y\nproperty float x\nproperty int unknown\nproperty short z\nproperty uint anotherUnknown\nend_header"));

        assertThat(header.getCount(), equalTo(128));
        assertThat(header.getStride(), equalTo(15));
        assertThat(header.getXProperty().getType(), equalTo(StanfordImporter.Type.Float));
        assertThat(header.getXProperty().getOffset(), equalTo(1));
        assertThat(header.getYProperty().getType(), equalTo(StanfordImporter.Type.UnsignedChar));
        assertThat(header.getYProperty().getOffset(), equalTo(0));
        assertThat(header.getZProperty().getType(), equalTo(StanfordImporter.Type.Short));
        assertThat(header.getZProperty().getOffset(), equalTo(9));
    }

    @Test
    public void parseVertexElementHeaderWrong() throws StanfordImporter.Exception, IOException {
        expectedEx.expect(StanfordImporter.Exception.class);
        expectedEx.expectMessage("StanfordImporter: wrong vertex element header: element vertex128");

        StanfordImporter.VertexElementHeader header = StanfordImporter.parseVertexElementHeader(
            stringStream("element vertex128"));
    }

    @Test
    public void parseVertexElementHeaderWrongProperty() throws StanfordImporter.Exception, IOException {
        expectedEx.expect(StanfordImporter.Exception.class);
        expectedEx.expectMessage("StanfordImporter: wrong vertex property line: property floaty");

        StanfordImporter.VertexElementHeader header = StanfordImporter.parseVertexElementHeader(
            stringStream("element vertex 64\nproperty floaty"));
    }

    @Test
    public void parseVertexElementHeaderDuplicitProperty() throws StanfordImporter.Exception, IOException {
        expectedEx.expect(StanfordImporter.Exception.class);
        expectedEx.expectMessage("StanfordImporter: duplicit vertex y property line: property double y");

        StanfordImporter.VertexElementHeader header = StanfordImporter.parseVertexElementHeader(
            stringStream("element vertex 64\nproperty char y\nproperty double y"));
    }

    @Test
    public void parseFaceElementHeader() throws StanfordImporter.Exception, IOException {
        StanfordImporter.FaceElementHeader header = StanfordImporter.parseFaceElementHeader(
            stringStream("element face 133\nproperty ushort awesomeness\nproperty list uchar int vertex_indices\nproperty double cuteness\nend_header"));

        assertThat(header.getCount(), equalTo(133));
        assertThat(header.getStride(), equalTo(11));
        assertThat(header.getIndexListSizeProperty().getType(), equalTo(StanfordImporter.Type.UnsignedChar));
        assertThat(header.getIndexListSizeProperty().getOffset(), equalTo(2));
        assertThat(header.getIndexListProperty().getType(), equalTo(StanfordImporter.Type.Int));
        assertThat(header.getIndexListProperty().getOffset(), equalTo(3));
    };

    @Test
    public void parseFaceElementHeaderWrong() throws StanfordImporter.Exception, IOException {
        expectedEx.expect(StanfordImporter.Exception.class);
        expectedEx.expectMessage("StanfordImporter: wrong face element header: element cafe 128");

        StanfordImporter.FaceElementHeader header = StanfordImporter.parseFaceElementHeader(
            stringStream("element cafe 128"));
    }

    @Test
    public void parseFaceElementHeaderWrongProperty() throws StanfordImporter.Exception, IOException {
        expectedEx.expect(StanfordImporter.Exception.class);
        expectedEx.expectMessage("StanfordImporter: wrong face property line: property crappy");

        StanfordImporter.FaceElementHeader header = StanfordImporter.parseFaceElementHeader(
            stringStream("element face 64\nproperty crappy"));
    }

    @Test
    public void parseVertexElements() throws StanfordImporter.Exception, IOException {
        StanfordImporter.Format format = StanfordImporter.Format.BinaryBigEndian10;
        StanfordImporter.VertexElementHeader header = new StanfordImporter.VertexElementHeader(3, 10,
            new StanfordImporter.Property(StanfordImporter.Type.UnsignedChar, 2),
            new StanfordImporter.Property(StanfordImporter.Type.Short, 8),
            new StanfordImporter.Property(StanfordImporter.Type.Int, 3));

        /* I AGGRESIVELY DAMN HATE THIS AMAZINGLY IDIOTICALLY MORONIC LACK OF
           UNSIGNED TYPES. -1 INSTEAD OF 0xFF? REALLY? */
        byte[] data = new byte[] {
            -1, -1, 0x01, 0x00, 0x00, 0x00, 0x03, -1, 0x00, 0x02,
            -1, -1, 0x04, 0x00, 0x00, 0x00, 0x06, -1, 0x00, 0x05,
            -1, -1, 0x07, 0x00, 0x00, 0x00, 0x09, -1, 0x00, 0x08
        };

        ByteArrayInputStream in = new ByteArrayInputStream(data);
        float[] vertices = StanfordImporter.parseVertexElements(in, format, header);

        assertThat(vertices, equalTo(new float[] {
            1, 2, 3,
            4, 5, 6,
            7, 8, 9
        }));
    }

    @Test
    public void parseFaceElements() throws StanfordImporter.Exception, IOException {
        StanfordImporter.Format format = StanfordImporter.Format.BinaryLittleEndian10;
        StanfordImporter.FaceElementHeader header = new StanfordImporter.FaceElementHeader(2, 5,
            new StanfordImporter.Property(StanfordImporter.Type.UnsignedShort, 1),
            new StanfordImporter.Property(StanfordImporter.Type.UnsignedInt, 3));

        byte[] data = new byte[] {
            -1, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, -1, -1,
            -1, 0x04, 0x00, 0x02, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x05, 0x00, 0x00, 0x00, -1
        };

        ByteArrayInputStream in = new ByteArrayInputStream(data);
        int[] faces = StanfordImporter.parseFaceElements(in, format, header);

        assertThat(faces, equalTo(new int[] {
            1, 2, 3,
            2, 3, 4,
            2, 4, 5
        }));
    }
}
