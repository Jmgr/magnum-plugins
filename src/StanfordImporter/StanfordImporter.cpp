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

#include "StanfordImporter.h"

#include <dlfcn.h>
#include <jni.h>
#include <jni_md.h>

#include "configure.h"

using namespace std;
using namespace Corrade::Utility;

PLUGIN_REGISTER(ColladaImporter, Magnum::Trade::StanfordImporter::StanfordImporter,
                "cz.mosra.magnum.Trade.AbstractImporter/0.1")

namespace Magnum { namespace Trade { namespace StanfordImporter {

bool StanfordImporter::StanfordImporter::open(const std::string& filename) {
    JNIEnv* env;
    JavaVM* vm;

    /* Check if the VM is already running */
    jsize alreadRunningVMs = 0;
    JNI_GetCreatedJavaVMs(&vm, 1, &alreadRunningVMs);
    if(alreadRunningVMs) {
        jint result = vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr);
        if(result < 0) {
            Error() << "StanfordImporter: cannot attach current thread to already running JVM:" << result;
            return false;
        }

    /* Create VM */
    } else {
        /* Add class path to StanfordImporter JAR */
        JavaVMOption options[1];
        char optionString[] = STANFORDIMPORTER_CLASS_PATH;
        options[0].optionString = optionString;

        /* VM arguments */
        JavaVMInitArgs vmArgs;
        vmArgs.version = JNI_VERSION_1_2;
        vmArgs.ignoreUnrecognized = true;
        vmArgs.options = options;
        vmArgs.nOptions = sizeof(options) / sizeof(options[0]);

        jint result = JNI_CreateJavaVM(&vm, reinterpret_cast<void**>(&env), &vmArgs);
        if(result < 0) {
            Error() << "StanfordImporter: cannot create Java VM:" << result;
            return false;
        }
    }

    jboolean opened = false;

    /* Get StanfordImporter class and methods */
    jclass StanfordImporter = env->FindClass("cz/mosra/magnum/StanfordImporter/StanfordImporter");
    if(!StanfordImporter)
        Error() << "StanfordImporter: cannot find class cz/mosra/magnum/StanfordImporter";

    else {
        jmethodID constructor = env->GetMethodID(StanfordImporter, "<init>", "()V");
        jmethodID open = env->GetMethodID(StanfordImporter, "open", "(Ljava/lang/String;)Z");
        jmethodID close = env->GetMethodID(StanfordImporter, "close", "()V");
        jmethodID getVertices = env->GetMethodID(StanfordImporter, "getVertices", "()[F");
        jmethodID getIndices = env->GetMethodID(StanfordImporter, "getIndices", "()[I");

        /* Construct StanfordImporter */
        jobject importer = env->NewObject(StanfordImporter, constructor);

        /* Open file */
        jstring filename_ = env->NewStringUTF(filename.c_str());
        opened = env->CallBooleanMethod(importer, open, filename_);

        if(opened) {
            /* Vertices */
            jfloatArray vertexArray = static_cast<jfloatArray>(env->CallObjectMethod(importer, getVertices));
            jsize vertexArrayLength = env->GetArrayLength(vertexArray);
            jfloat* vertices_ = env->GetFloatArrayElements(vertexArray, nullptr);

            vector<Vector4>* vertices = new vector<Vector4>;
            vertices->reserve(vertexArrayLength);
            for(size_t i = 0; i != size_t(vertexArrayLength/3); ++i)
                vertices->push_back({vertices_[i*3], vertices_[i*3+1], vertices_[i*3+2]});

            env->ReleaseFloatArrayElements(vertexArray, vertices_, JNI_ABORT);

            /* Indices */
            jintArray indexArray = static_cast<jintArray>(env->CallObjectMethod(importer, getIndices));
            jsize indexArrayLength = env->GetArrayLength(indexArray);
            jint* indices_ = env->GetIntArrayElements(indexArray, nullptr);

            vector<unsigned int>* indices = new vector<unsigned int>;
            indices->reserve(indexArrayLength);
            for(size_t i = 0; i != size_t(indexArrayLength); ++i)
                indices->push_back(indices_[i]);

            env->ReleaseIntArrayElements(indexArray, indices_, JNI_ABORT);

            /* Close the file */
            env->CallVoidMethod(importer, close);

            d = new MeshData(Mesh::Primitive::Triangles, indices, {vertices}, {}, {});
        }
    }

    /* Detach current thread from VM */
    if(alreadRunningVMs) vm->DetachCurrentThread();

    /* Otherwise destroy Java VM */
    else vm->DestroyJavaVM();

    return opened;
}

}}}
