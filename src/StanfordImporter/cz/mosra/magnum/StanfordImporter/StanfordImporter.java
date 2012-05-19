package cz.mosra.magnum.StanfordImporter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
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
            if(type.equals("uchar")) return Type.UnsignedChar;
            if(type.equals("char")) return Type.Char;
            if(type.equals("ushort")) return Type.UnsignedShort;
            if(type.equals("short")) return Type.Short;
            if(type.equals("uint")) return Type.UnsignedInt;
            if(type.equals("int")) return Type.Int;
            if(type.equals("float")) return Type.Float;
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
        Header(Format format, VertexElementHeader vertexElementHeader, FaceElementHeader faceElementHeader) {
            this.format = format;
            this.vertexElementHeader = vertexElementHeader;
            this.faceElementHeader = faceElementHeader;
        }

        /** @brief File format */
        public Format getFormat() { return format; }

        /** @brief Vertex element header */
        public VertexElementHeader getVertexElementHeader() { return vertexElementHeader; }

        /** @brief Face element header */
        public FaceElementHeader getFaceElementHeader() { return faceElementHeader; }

        private Format format;
        private VertexElementHeader vertexElementHeader;
        private FaceElementHeader faceElementHeader;
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
        BufferedReader in = new BufferedReader(new InputStreamReader(is));

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
                    vertexElementHeader = parseVertexElementHeader(is);

                /* Face element header */
                } else if(tokens[1].equals("face")) {
                    in.reset();
                    faceElementHeader = parseFaceElementHeader(is);

                /* Unknown element */
                } else System.out.println("StanfordImporter: ignoring unknown element " + tokens[1]);

            /* Header end */
            } else if(tokens[0].equals("end_header"))
                break;

            /* Something else */
            else System.out.println("StanfordImporter: ignoring unknown line: " + line);
        }

        if(format == null) throw new StanfordImporter.Exception("format line missing!");

        return new Header(format, vertexElementHeader, faceElementHeader);
    }

    /** @brief Parse header for vertex elements */
    public static VertexElementHeader parseVertexElementHeader(InputStream is) throws StanfordImporter.Exception, IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(is));

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
    public static FaceElementHeader parseFaceElementHeader(InputStream is) throws StanfordImporter.Exception, IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(is));

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
            vertices[i*3] = extract(buffer, format, header.getXProperty().getType(), header.getXProperty().getOffset());
            vertices[i*3+1] = extract(buffer, format, header.getYProperty().getType(), header.getYProperty().getOffset());
            vertices[i*3+2] = extract(buffer, format, header.getZProperty().getType(), header.getZProperty().getOffset());
        }

        return vertices;
    }

    private static String[] splitLine(String line) throws StanfordImporter.Exception {
        if(line == null) throw new StanfordImporter.Exception("the file is too short");
        return line.split("\\s+");
    }

    private static float extract(byte[] elements, Format format, Type type, int offset) throws StanfordImporter.Exception, IOException {
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

        /* I don't want to support ASCII. COLLADA has that shit already. */
        } else throw new StanfordImporter.Exception("unsupported format: " + format);

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(reorder(elements, format, type, offset)));

        /* Read the value */
        switch(type) {
            case UnsignedChar:  return dis.readUnsignedByte();
            case Char:          return dis.readByte();
            case UnsignedShort: return dis.readUnsignedShort();
            case Short:         return dis.readShort();
            /* Assholes. http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4077352 */
            case UnsignedInt:   return dis.readInt() & 0xffffffffL;
            case Int:           return dis.readInt();
            case Float:         return dis.readFloat();
            case Double:        return (float) dis.readDouble();
            default:            throw new StanfordImporter.Exception("wtf.");
        }
    }
}
