load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# Android
ANDROID_API_LEVEL = 30
ANDROID_BUILD_TOOLS = "30.0.3"
# Rules
RULES_ANDROID_TAG = "0.1.1"
RULES_ANDROID_SHA = "cd06d15dd8bb59926e4d65f9003bfc20f9da4b2519985c27e190cddc8b7a7806"
RULES_JVM_EXTERNAL_TAG = "4.0"
RULES_JVM_EXTERNAL_SHA = "31701ad93dbfe544d597dbe62c9a1fdd76d81d8a9150c2bf1ecf928ecdf97169"
# Maven
ANNOTATION_VERSION = "1.1.0"
APPCOMPAT_VERSION = "1.2.0"
CARDVIEW_VERSION = "1.0.0"
CONSTRAINT_LAYOUT_VERSION = "2.1.0-alpha2"
COORDINATOR_LAYOUT_VERSION = "1.1.0"
CORE_VERSION = "1.3.2"
MATERIAL_VERSION = "1.3.0"
RECYCLERVIEW_VERSION = "1.1.0"

android_sdk_repository(
    name = "androidsdk",
    api_level = ANDROID_API_LEVEL,
    build_tools_version = ANDROID_BUILD_TOOLS,
)

http_archive(
    name = "rules_android",
    sha256 = RULES_ANDROID_SHA,
    strip_prefix = "rules_android-%s" % RULES_ANDROID_TAG,
    url = "https://github.com/bazelbuild/rules_android/archive/v%s.zip" % RULES_ANDROID_TAG,
)

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = [
        "androidx.annotation:annotation:%s" % ANNOTATION_VERSION,
        "androidx.appcompat:appcompat:%s" % APPCOMPAT_VERSION,
        "androidx.cardview:cardview:%s" % CARDVIEW_VERSION,
        "androidx.constraintlayout:constraintlayout:%s" % CONSTRAINT_LAYOUT_VERSION,
        "androidx.coordinatorlayout:coordinatorlayout:%s" % COORDINATOR_LAYOUT_VERSION,
        "androidx.core:core:%s" % CORE_VERSION,
        "androidx.recyclerview:recyclerview:%s" % RECYCLERVIEW_VERSION,
        "com.google.android.material:material:%s" % MATERIAL_VERSION,
    ],
    fetch_sources = True,
    repositories = [
        "https://maven.google.com",
    ],
)
