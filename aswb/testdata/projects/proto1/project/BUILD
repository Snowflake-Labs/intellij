load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("@rules_android//android:rules.bzl", "android_library")
load("@rules_java//java:java_library.bzl", "java_library")
load(
    "//aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

package(
    default_visibility = ["//src:__subpackages__"],
)

test_project_package(
    name = "test_data",
    all_targets = [
        ":lib_proto",
        ":lib_java_proto",
        ":lib_java_proto_lite",
        ":lib",
        ":android_lib",
    ],
    visibility = [
        "//aswb/testdata/projects:__pkg__",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)

proto_library(
    name = "lib_proto",
    srcs = ["lib.proto"],
    deps = ["//aswb/testdata/projects/proto1/external:external_proto"],
)

java_proto_library(
    name = "lib_java_proto",
    deps = [
        ":lib_proto",
    ],
)

# TODO: b/337007753 - Fix protobuf tests.
#java_lite_proto_library(
java_proto_library(
    name = "lib_java_proto_lite",
    deps = [
        ":lib_proto",
    ],
)

java_library(
    name = "lib",
    srcs = glob(["java/com/example/lib/**/*.java"]),
    deps = [
        ":lib_java_proto",
        "//aswb/testdata/projects/proto1/external:external_java_proto",
    ],
)

android_library(
    name = "android_lib",
    srcs = glob(["java/com/example/android/**/*.java"]),
    deps = [
        ":lib_java_proto_lite",
        "//aswb/testdata/projects/proto1/external:external_java_proto_lite",
    ],
)
