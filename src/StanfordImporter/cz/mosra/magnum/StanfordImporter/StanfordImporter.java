package cz.mosra.magnum.StanfordImporter;

import java.io.BufferedReader;
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
    public static Header parseHeader(BufferedReader in) throws StanfordImporter.Exception, IOException {
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

        return new Header(format, vertexElementHeader, faceElementHeader);
    }

    /** @brief Parse header for vertex elements */
    public static VertexElementHeader parseVertexElementHeader(BufferedReader in) {
        return null;
    }

    /** @brief Parse header for face elements */
    public static FaceElementHeader parseFaceElementHeader(BufferedReader in) {
        return null;
    }

    private static String[] splitLine(String line) throws StanfordImporter.Exception {
        if(line == null) throw new StanfordImporter.Exception("the file is too short");
        return line.split("\\s+");
    }
}
