load("@io_grpc_grpc_java//:java_grpc_library.bzl", "java_grpc_library")
load("@io_bazel_rules_go//proto:def.bzl", "go_grpc_library", "go_proto_library")

proto_library(
    name = "intellijext_proto",
    srcs = [
        "build_cleaner.proto",
        "build_service.proto",
        "chatbotmodel.proto",
        "codereview.proto",
        "codesearch.proto",
        "depserver.proto",
        "experiments.proto",
        "fileapi.proto",
        "intellijext.proto",
        "issuetracker.proto",
        "kythe.proto",
        "linter.proto",
    ],
)

java_proto_library(
    name = "intellijext_java_proto",
    visibility = ["//visibility:public"],
    deps = [":intellijext_proto"],
)

java_grpc_library(
    name = "intellijext_java_grpc",
    srcs = [":intellijext_proto"],
    visibility = ["//visibility:public"],
    deps = [":intellijext_java_proto"],
)

go_proto_library(
    name = "intellijext_go_proto",
    visibility = ["//visibility:public"],
    deps = [":intellijext_proto"],
)

go_grpc_library(
    name = "intellijext_go_grpc",
    visibility = ["//visibility:public"],
    deps = [":intellijext_go_proto"],
)
