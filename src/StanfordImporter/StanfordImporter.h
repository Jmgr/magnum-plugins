#ifndef Magnum_Trade_StanfordImporter_StanfordImporter_h
#define Magnum_Trade_StanfordImporter_StanfordImporter_h
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
 * @brief Class Magnum::Trade::StanfordImporter::StanfordImporter
 */

#include "Trade/AbstractImporter.h"
#include "Trade/MeshData.h"

namespace Magnum { namespace Trade {

/** @brief Stanford importer */
namespace StanfordImporter {

/**
@brief Stanford importer plugin
*/
class StanfordImporter: public AbstractImporter {
    public:
        /** @copydoc AbstractImporter::AbstractImporter() */
        inline StanfordImporter(Corrade::PluginManager::AbstractPluginManager* manager = nullptr, const std::string& plugin = ""): d(nullptr) {}
        inline virtual ~StanfordImporter() { close(); }

        inline int features() const { return OpenFile; }

        bool open(const std::string& filename);

        inline void close() {
            delete d;
            d = nullptr;
        }

        inline size_t meshCount() const { return d ? 1 : 0; }

        inline MeshData* mesh(size_t id) {
            if(id != 0) return nullptr;
            return d;
        }

    private:
        MeshData* d;
};

}}}

#endif
