/** @package cz @brief cz */
/** @package cz.mosra @brief cz.mosra */
/** @package cz.mosra.magnum @brief cz.mosra.magnum */
/** @brief Stanford importer Java implementation */
package cz.mosra.magnum.StanfordImporter;

/** @file
 * @brief Class cz.mosra.magnum.StanfordImporter.StanfordImporter
 */

import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

/**
@brief Stanford PLY file format importer

Currently supports only binary little and big-endian files with XYZ vertex
data and triangle/quad faces. Example usage:
@code
StanfordImporter importer = new StanfordImporter();
importer.open("file.ply");
float[] vertices = importer.getVertices();
int[] indices = importer.getIndices();
importer.close();
@endcode
*/
public class StanfordImporter {
    /** @brief Buffered reader with character counting */
    private static class CountingBufferedReader extends BufferedReader {
        public CountingBufferedReader(InputStreamReader isr) {
            super(isr);
            position = 0;
        }

        public int getPosition() { return position; }

        @Override
        public void mark(int readAheadLimit) throws IOException {
            super.mark(readAheadLimit);

            marked = position;
        }

        @Override
        public void reset() throws IOException {
            super.reset();

            if(marked != null) {
                position = marked;
                marked = null;
            }
        }

        @Override
        public String readLine() throws IOException {
            String s = super.readLine();
            if(s != null)
                position += s.length()+1; /** @todo Support for CR-LF */
            return s;
        }

        private int position;
        private Integer marked;
    }

    /** @brief Parsing exception for Stanford PLY file format importer */
    public static class Exception extends java.lang.Exception {
        private static final long serialVersionUID = 1L;

        /** @brief Constructor */
        Exception(String message) {
            super("StanfordImporter: " + message);
        }
    };

    /** @brief File format */
    public enum Format {
        Ascii10,                /**< @brief ASCII 1.0 (not supported) */
        BinaryLittleEndian10,   /**< @brief Binary little-endian 1.0 */
        BinaryBigEndian10       /**< @brief Binary big-endian 1.0 */
    }

    /** @brief Element property type */
    public enum Type {
        UnsignedChar,           /**< @brief Unsigned char (8bit) */
        Char,                   /**< @brief Char (8bit) */
        UnsignedShort,          /**< @brief Unsigned short (16bit) */
        Short,                  /**< @brief Short (16bit) */
        UnsignedInt,            /**< @brief Unsigned integer (32bit) */
        Int,                    /**< @brief Integer (32bit) */
        Float,                  /**< @brief Float (32bit) */
        Double;                 /**< @brief Double (64bit) */

        /** @brief Convert string type to enum type */
        static Type from(String type) throws StanfordImporter.Exception {
            if(type.equals("uchar") || type.equals("uint8")) return Type.UnsignedChar;
            if(type.equals("char") || type.equals("int8")) return Type.Char;
            if(type.equals("ushort") || type.equals("uint16")) return Type.UnsignedShort;
            if(type.equals("short") || type.equals("int16")) return Type.Short;
            if(type.equals("uint") || type.equals("uint32")) return Type.UnsignedInt;
            if(type.equals("int") || type.equals("int32")) return Type.Int;
            if(type.equals("float") || type.equals("float32")) return Type.Float;
            if(type.equals("double")) return Type.Double;

            throw new StanfordImporter.Exception("unknown property type " + type);
        }

        /** @brief Size (in bytes) for given type */
        int size() {
            if(this == Type.UnsignedChar || this == Type.Char) return 1;
            if(this == Type.UnsignedShort || this == Type.Short) return 2;
            if(this == Type.Double) return 8;
            return 4;
        }
    }

    /** @brief File header */
    public static class Header {
        /** @brief Constructor */
        Header(Format format, VertexElementHeader vertexElementHeader, FaceElementHeader faceElementHeader, int size) {
            this.format = format;
            this.vertexElementHeader = vertexElementHeader;
            this.faceElementHeader = faceElementHeader;
            this.size = size;
        }

        /** @brief File format */
        public Format getFormat() { return format; }

        /** @brief Vertex element header */
        public VertexElementHeader getVertexElementHeader() { return vertexElementHeader; }

        /** @brief Face element header */
        public FaceElementHeader getFaceElementHeader() { return faceElementHeader; }

        /** @brief %Header size */
        public int getSize() { return size; }

        private Format format;
        private VertexElementHeader vertexElementHeader;
        private FaceElementHeader faceElementHeader;
        private int size;
    }

    /** @brief Element property */
    public static class Property {
        /** @brief Constructor */
        Property(Type type, int offset) {
            this.type = type;
            this.offset = offset;
        }

        /** @brief Element property type */
        public Type getType() { return type; }

        /** @brief Property offset in element, in bytes */
        public int getOffset() { return offset; }

        private Type type;
        private int offset;
    }

    /** @brief %Header for vertex elements */
    public static class VertexElementHeader {
        /** @brief Constructor */
        VertexElementHeader(int count, int stride, Property x, Property y, Property z) {
            this.count = count;
            this.stride = stride;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getCount() { return count; }         /**< @brief Vertex count */
        public int getStride() { return stride; }       /**< @brief Vertex element stride */
        public Property getXProperty() { return x; }    /**< @brief X coordinate property */
        public Property getYProperty() { return y; }    /**< @brief Y coordinate property */
        public Property getZProperty() { return z; }    /**< @brief Z coordinate property */

        private int count;
        private int stride;
        private Property x;
        private Property y;
        private Property z;
    }

    /** @brief %Header for face elements */
    public static class FaceElementHeader {
        /** @brief Constructor */
        FaceElementHeader(int count, int stride, Property indexListSize, Property indexList) {
            this.count = count;
            this.stride = stride;
            this.indexListSize = indexListSize;
            this.indexList = indexList;
        }

        public int getCount() { return count; }         /**< @brief Face count */
        public int getStride() { return stride; }       /**< @brief Face element stride */

        /** @brief Size of index list property */
        public Property getIndexListSizeProperty() { return indexListSize; }

        /** @brief Index list property */
        public Property getIndexListProperty() { return indexList; }

        private int count;
        private int stride;
        private Property indexListSize;
        private Property indexList;
    }

    /** @brief Parse file header */
    public static Header parseHeader(InputStream is) throws StanfordImporter.Exception, IOException {
        CountingBufferedReader in = new CountingBufferedReader(new InputStreamReader(is));

        /* Check file signature */
        String signature = in.readLine();
        if(!signature.equals("ply"))
            throw new StanfordImporter.Exception("wrong file signature " + signature);

        /* Initial values for the header */
        Format format = null;
        VertexElementHeader vertexElementHeader = null;
        FaceElementHeader faceElementHeader = null;

        /* Try to read file line by line */
        for(;;) {
            /* Save position for possible resetting */
            in.mark(80);

            String line = in.readLine();
            String[] tokens = splitLine(line);

            /* Skip empty lines and comments */
            if(tokens.length == 0 || tokens[0].equals("comment"))
                continue;

            /* File format */
            if(tokens[0].equals("format")) {
                if(format != null)
                    throw new StanfordImporter.Exception("duplicit format line: " + line);
                if(tokens.length != 3)
                    throw new StanfordImporter.Exception("wrong format line: " + line);

                if(!tokens[2].equals("1.0"))
                    throw new StanfordImporter.Exception("unsupported file version " + tokens[2]);

                if(tokens[1].equals("ascii"))
                    format = Format.Ascii10;
                else if(tokens[1].equals("binary_little_endian"))
                    format = Format.BinaryLittleEndian10;
                else if(tokens[1].equals("binary_big_endian"))
                    format = Format.BinaryBigEndian10;
                else throw new StanfordImporter.Exception("unsupported file format " + tokens[1]);

            /* Elements */
            } else if(tokens[0].equals("element")) {
                if(tokens.length != 3)
                    throw new StanfordImporter.Exception("wrong element line: " + line);

                /* Vertex element header */
                if(tokens[1].equals("vertex")) {
                    in.reset();
                    vertexElementHeader = parseVertexElementHeader(in);

                /* Face element header */
                } else if(tokens[1].equals("face")) {
                    in.reset();
                    faceElementHeader = parseFaceElementHeader(in);

                /* Unknown element */
                } else System.out.println("StanfordImporter: ignoring unknown element " + tokens[1]);

            /* Header end */
            } else if(tokens[0].equals("end_header"))
                break;

            /* Something else */
            else System.out.println("StanfordImporter: ignoring unknown line: " + line);
        }

        if(format == null) throw new StanfordImporter.Exception("format line missing!");

        return new Header(format, vertexElementHeader, faceElementHeader, in.getPosition());
    }

    /** @brief Parse header for vertex elements */
    public static VertexElementHeader parseVertexElementHeader(BufferedReader in) throws StanfordImporter.Exception, IOException {
        String line = in.readLine();
        String[] tokens = splitLine(line);

        if(tokens.length != 3 || !tokens[0].equals("element") || !tokens[1].equals("vertex"))
            throw new StanfordImporter.Exception("wrong vertex element header: " + line);

        int count = Integer.parseInt(tokens[2]);
        int offset = 0;
        Property x = null;
        Property y = null;
        Property z = null;

        for(;;) {
            /* Save position for possible resetting */
            in.mark(80);

            line = in.readLine();
            tokens = splitLine(line);

            /* Skip empty lines and comments */
            if(tokens.length == 0 || tokens[0].equals("comment"))
                continue;

            /* No more property lines, end */
            if(!tokens[0].equals("property")) {
                in.reset();
                break;
            }

            if(tokens.length != 3)
                throw new StanfordImporter.Exception("wrong vertex property line: " + line);

            /* Property type */
            Type type = Type.from(tokens[1]);

            /* XYZ properties */
            if(tokens[2].equals("x")) {
                if(x != null) throw new StanfordImporter.Exception("duplicit vertex x property line: " + line);
                x = new Property(type, offset);
            } else if(tokens[2].equals("y")) {
                if(y != null) throw new StanfordImporter.Exception("duplicit vertex y property line: " + line);
                y = new Property(type, offset);
            } else if(tokens[2].equals("z")) {
                if(z != null) throw new StanfordImporter.Exception("duplicit vertex z property line: " + line);
                z = new Property(type, offset);

            /* Ignore unsupported properties */
            } else System.out.println("StanfordImporter: ignoring unsupported vertex property: " + line);

            offset += type.size();
        }

        return new VertexElementHeader(count, offset, x, y, z);
    }

    /** @brief Parse header for face elements */
    public static FaceElementHeader parseFaceElementHeader(BufferedReader in) throws StanfordImporter.Exception, IOException {
        String line = in.readLine();
        String[] tokens = splitLine(line);

        if(tokens.length != 3 || !tokens[0].equals("element") || !tokens[1].equals("face"))
            throw new StanfordImporter.Exception("wrong face element header: " + line);

        int count = Integer.parseInt(tokens[2]);
        int offset = 0;
        Property indexListSize = null;
        Property indexList = null;

        for(;;) {
            /* Save position for possible resetting */
            in.mark(80);

            line = in.readLine();
            tokens = splitLine(line);

            /* Skip empty lines and comments */
            if(tokens.length == 0 || tokens[0].equals("comment"))
                continue;

            /* No more property lines, end */
            if(!tokens[0].equals("property")) {
                in.reset();
                break;
            }

            /* List property */
            if(tokens.length == 5 && tokens[1].equals("list")) {

                /* Vertex indices */
                if(tokens[4].equals("vertex_indices")) {
                    indexListSize = new Property(Type.from(tokens[2]), offset);
                    offset += indexListSize.getType().size();
                    indexList = new Property(Type.from(tokens[3]), offset);
                    /* The offset is now varying from face to face */

                /* Ignore unknown list property */
                } else System.out.println("StanfordImporter: ignoring unknown face list property " + tokens[4]);

            /* Classic property, only add to offset */
            } else if(tokens.length == 3) {
                System.out.println("StanfordImporter: ignoring unknown face property " + tokens[2]);
                offset += Type.from(tokens[1]).size();

            /* Something other */
            } else throw new StanfordImporter.Exception("wrong face property line: " + line);
        }

        return new FaceElementHeader(count, offset, indexListSize, indexList);
    }

    /**
     * @brief Parse vertex elements
     *
     * Each triple in returned array is three-component XYZ vector.
     */
    public static float[] parseVertexElements(InputStream is, Format format, VertexElementHeader header) throws StanfordImporter.Exception, IOException {
        float[] vertices = new float[header.getCount()*3];

        for(int i = 0; i != header.getCount(); ++i) {
            byte[] buffer = new byte[header.getStride()];
            is.read(buffer, 0, header.getStride());

            /* Extract coordinates */
            vertices[i*3] = extractFloat(buffer, format, header.getXProperty().getType(), header.getXProperty().getOffset());
            vertices[i*3+1] = extractFloat(buffer, format, header.getYProperty().getType(), header.getYProperty().getOffset());
            vertices[i*3+2] = extractFloat(buffer, format, header.getZProperty().getType(), header.getZProperty().getOffset());
        }

        return vertices;
    }

    /**
     * @brief Parse face elements
     *
     * Each triple in returned index array is one face. Quad faces are
     * converted to triangle faces. Other faces than triangle and quad are
     * not supported.
     */
    public static int[] parseFaceElements(InputStream is, Format format, FaceElementHeader header) throws StanfordImporter.Exception, IOException {
        /* Simplification: assuming there is only one list - the one which we
           need. */
        ArrayList<Integer> faces = new ArrayList<Integer>();

        for(int i = 0; i != header.getCount(); ++i) {
            /* Skip data before index list */
            is.skip(header.getIndexListSizeProperty().getOffset());

            /* Read vertex count for the face */
            byte[] buffer = new byte[header.getIndexListSizeProperty().getType().size()];
            is.read(buffer, 0, buffer.length);
            int vertexCount = extractInt(buffer, format, header.getIndexListSizeProperty().getType(), 0);

            Type type = header.getIndexListProperty().getType();
            int size = type.size();

            /* Read list data */
            buffer = new byte[size*vertexCount];
            is.read(buffer, 0, buffer.length);
            int offset = 0;
            if(vertexCount == 3) {
                faces.add(extractInt(buffer, format, type, offset));
                faces.add(extractInt(buffer, format, type, offset+=size));
                faces.add(extractInt(buffer, format, type, offset+=size));
            } else if(vertexCount == 4) {
                int first  = extractInt(buffer, format, type, offset);
                int second = extractInt(buffer, format, type, offset+=size);
                int third  = extractInt(buffer, format, type, offset+=size);
                int fourth = extractInt(buffer, format, type, offset+=size);

                faces.add(first);
                faces.add(second);
                faces.add(third);
                faces.add(first);
                faces.add(third);
                faces.add(fourth);
            } else throw new StanfordImporter.Exception("unsupported count of vertices per face: " + vertexCount);

            /* Skip data after index list */
            is.skip(header.getStride()-header.getIndexListProperty().getOffset());
        }

        /* Convert this awesome arraylist of N pointers to N objects
           encapsulating ints to usable array of N ints. */
        int[] ret = new int[faces.size()];
        for (int i=0; i < ret.length; i++)
            ret[i] = faces.get(i);
        return ret;
    }

    /**
     * @brief Open file and load its contents
     *
     * @see close(), getHeader(), getVertices(), getIndices()
     */
    public boolean open(String filename) { try {
        if(header != null) close();

        FileInputStream fs = new FileInputStream(filename);

        header = parseHeader(fs);

        /*
            BufferedReader reads more data than necessary, so it
            effectively skips end of the header and stops and undefined
            position inside the data block. Thus we need to guess header size
            and then seek to that exact position in the file.

            BUT FileInputStream cannot seek or mark()/reset(), so we need to
            close it, open again and skip the header.
        */
        fs.close();
        fs = new FileInputStream(filename);
        fs.skip(header.getSize());

        vertices = parseVertexElements(fs, header.getFormat(), header.getVertexElementHeader());
        indices = parseFaceElements(fs, header.getFormat(), header.getFaceElementHeader());

        fs.close();

        return true;
    } catch(StanfordImporter.Exception e) {
        e.printStackTrace();
        System.err.println(e.getMessage());
        return false;
    } catch(FileNotFoundException e) {
        e.printStackTrace();
        System.err.println("StanfordImporter: file not found: " + filename);
        return false;
    } catch(IOException e) {
        e.printStackTrace();
        System.err.println("StanfordImporter: I/O exception: " + e.getMessage());
        return false;
    }}

    /** @brief Close file and destroy loaded data */
    public void close() {
        header = null;
        vertices = null;
        indices = null;
    }

    /**
     * @brief File header
     *
     * If no file is currently opened, returns null.
     */
    public Header getHeader() { return header; }

    /**
     * @brief Vertex data
     *
     * If no file is currently opened, returns null.
     */
    public float[] getVertices() { return vertices; }

    /**
     * @brief Index data
     *
     * If no file is currently opened, returns null.
     */
    public int[] getIndices() { return indices; }

    private static String[] splitLine(String line) throws StanfordImporter.Exception {
        if(line == null) throw new StanfordImporter.Exception("the file is too short");
        return line.split("\\s+");
    }

    private static byte[] reorder(byte[] elements, Format format, Type type, int offset) throws StanfordImporter.Exception, IOException {
        byte[] reordered = null;

        /* Bytes for little endian format */
        if(format == Format.BinaryLittleEndian10) {
            switch(type) {
                case UnsignedChar:
                case Char:
                    reordered = new byte[] { elements[offset] }; break;
                case UnsignedShort:
                case Short:
                    reordered = new byte[] { elements[offset+1],
                                             elements[offset] }; break;
                case UnsignedInt:
                case Int:
                case Float:
                    reordered = new byte[] { elements[offset+3],
                                             elements[offset+2],
                                             elements[offset+1],
                                             elements[offset] }; break;
                case Double:
                    reordered = new byte[] { elements[offset+7],
                                             elements[offset+6],
                                             elements[offset+5],
                                             elements[offset+4],
                                             elements[offset+3],
                                             elements[offset+2],
                                             elements[offset+1],
                                             elements[offset] }; break;
            }

        /* Bytes for big endian format */
        } else if(format == Format.BinaryBigEndian10) {
            switch(type) {
                case UnsignedChar:
                case Char:
                    reordered = new byte[] { elements[offset] }; break;
                case UnsignedShort:
                case Short:
                    reordered = new byte[] { elements[offset],
                                             elements[offset+1] }; break;
                case UnsignedInt:
                case Int:
                case Float:
                    reordered = new byte[] { elements[offset],
                                             elements[offset+1],
                                             elements[offset+2],
                                             elements[offset+3] }; break;
                case Double:
                    reordered = new byte[] { elements[offset],
                                             elements[offset+1],
                                             elements[offset+2],
                                             elements[offset+3],
                                             elements[offset+4],
                                             elements[offset+5],
                                             elements[offset+6],
                                             elements[offset+7] }; break;
            }

        /* I don't want to support ASCII. COLLADA has that already. */
        } else throw new StanfordImporter.Exception("unsupported format: " + format);

        return reordered;
    }

    private static float extractFloat(byte[] elements, Format format, Type type, int offset) throws StanfordImporter.Exception, IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(reorder(elements, format, type, offset)));

        /* Read the value */
        switch(type) {
            case UnsignedChar:  return dis.readUnsignedByte();
            case Char:          return dis.readByte();
            case UnsignedShort: return dis.readUnsignedShort();
            case Short:         return dis.readShort();
            case UnsignedInt:   return dis.readInt() & 0xffffffffL;
            case Int:           return dis.readInt();
            case Float:         return dis.readFloat();
            case Double:        return (float) dis.readDouble();
            default:            throw new StanfordImporter.Exception("wtf.");
        }
    }

    private static int extractInt(byte[] elements, Format format, Type type, int offset) throws StanfordImporter.Exception, IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(reorder(elements, format, type, offset)));

        /* Read the value */
        switch(type) {
            case UnsignedChar:  return dis.readUnsignedByte();
            case Char:          return dis.readByte();
            case UnsignedShort: return dis.readUnsignedShort();
            case Short:         return dis.readShort();
            case UnsignedInt:   return (int) (dis.readInt() & 0xffffffffL);
            case Int:           return dis.readInt();
            case Float:         return (int) dis.readFloat();
            case Double:        return (int) dis.readDouble();
            default:            throw new StanfordImporter.Exception("wtf.");
        }
    }

    private Header header = null;
    private float[] vertices = null;
    private int[] indices = null;
}
