project(StanfordImporter C CXX Java)

# CMake 2.8.8 is required for CMAKE_JAVA_JAR_ENTRY_POINT
cmake_minimum_required(VERSION 2.8.8)

find_package(Java REQUIRED)
find_package(JNI REQUIRED)

include(UseJava)

find_jar(JUNIT_JAR junit)
if(JUNIT_JAR-NOTFOUND)
    message(ERROR "JUnit was not found")
endif()

# Macro for JUnit tests
function(add_junit_test test_name source_file)
    foreach(library ${ARGN})
        set(libraries ${library} ${libraries})
    endforeach()

    string(REPLACE ".java" "" class_name ${source_file})
    string(REPLACE "/" "." class_name ${class_name})

    set(CMAKE_JAVA_JAR_ENTRY_POINT ${test_name}Runner)
    set(CMAKE_JAVA_JAR_MANIFEST_FILE ${CMAKE_CURRENT_BINARY_DIR}/${test_name}.manifest)
    set(CMAKE_JAVA_INCLUDE_PATH ${JUNIT_JAR})
    file(WRITE ${CMAKE_CURRENT_BINARY_DIR}/${test_name}Runner.java
"import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class ${test_name}Runner {
    public static void main(String[] args) {
        Result result = JUnitCore.runClasses(${class_name}.class);
        for (Failure failure : result.getFailures())
            System.out.println(failure.toString());
        if(!result.wasSuccessful())
            System.exit(1);
    }
}
")
    file(WRITE ${CMAKE_CURRENT_BINARY_DIR}/${test_name}.manifest
"Class-Path: ${JUNIT_JAR}
")
    add_jar(${test_name} ${source_file}
        ${CMAKE_CURRENT_BINARY_DIR}/${test_name}Runner.java)
    add_test(${test_name} java -jar ${test_name}.jar)
endfunction()

# JAR
set(JStanfordImporter_SRCS
    cz/mosra/magnum/StanfordImporter/StanfordImporter.java
    )

set(CMAKE_JAVA_COMPILE_FLAGS -Xlint)
add_jar(JStanfordImporter ${JStanfordImporter_SRCS})

if(BUILD_TESTS)
    add_junit_test(JStanfordImporterTest cz/mosra/magnum/StanfordImporter/StanfordImporterTest.java)
endif()
