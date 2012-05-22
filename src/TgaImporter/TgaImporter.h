#ifndef Magnum_Trade_TgaImporter_TgaImporter_h
#define Magnum_Trade_TgaImporter_TgaImporter_h
/*
    Copyright © 2010, 2011, 2012 Vladimír Vondruš <mosra@centrum.cz>

    This file is part of Magnum.

    Magnum is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License version 3
    only, as published by the Free Software Foundation.

    Magnum is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU Lesser General Public License version 3 for more details.
*/

/** @file
 * @brief Class Magnum::Trade::TgaImporter::TgaImporter
 */

#include "Trade/AbstractImporter.h"

namespace Magnum { namespace Trade { namespace TgaImporter {

/** @brief TGA importer plugin */
class TgaImporter: public AbstractImporter {
    public:
        /** @copydoc AbstractImporter::AbstractImporter() */
        TgaImporter(Corrade::PluginManager::AbstractPluginManager* manager = 0, const std::string& plugin = ""): AbstractImporter(manager, plugin), _image(nullptr) {}
        inline virtual ~TgaImporter() { close(); }

        inline int features() const { return OpenFile|OpenStream; }

        bool open(std::istream& in);
        bool open(const std::string& filename);
        void close();

        inline size_t image2DCount() const { return _image ? 1 : 0; }
        ImageData2D* image2D(size_t id);

        #pragma pack(1)
        /** @brief TGA file header */
        struct Header {
            GLbyte  identsize;              /**< @brief Size of ID field that follows header (0) */
            GLbyte  colorMapType;           /**< @brief 0 = None, 1 = paletted */
            GLbyte  imageType;              /**< @brief 0 = none, 1 = indexed, 2 = rgb, 3 = grey, +8=rle */
            unsigned short  colorMapStart;  /**< @brief First color map entry */
            unsigned short  colorMapLength; /**< @brief Number of colors */
            unsigned char   colorMapBpp;    /**< @brief Bits per palette entry */
            unsigned short  beginX;         /**< @brief Image x origin */
            unsigned short  beginY;         /**< @brief Image y origin */
            unsigned short  width;          /**< @brief Image width */
            unsigned short  height;         /**< @brief Image height */
            GLbyte  bpp;                    /**< @brief Bits per pixel (8, 16, 24, 32) */
            GLbyte  descriptor;             /**< @brief Image descriptor */
        };
        #pragma pack(8)

    private:
        ImageData2D* _image;
};

}}}

#endif
