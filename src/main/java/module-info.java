module yakclient.boot {
    requires kotlin.stdlib;
    requires durganmcbroom.artifact.resolver;
    requires yakclient.archives;
    requires java.logging;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.kotlin;
    requires yakclient.common.util;
    requires durganmcbroom.artifact.resolver.simple.maven;
    requires kotlinx.cli.jvm;
    requires arrow.core.jvm;
    requires jdk.unsupported;
    requires kotlin.reflect;
    requires transitive durganmcbroom.event.api;

    opens net.yakclient.boot.dependency to com.fasterxml.jackson.databind, kotlin.reflect;
    opens net.yakclient.boot to com.fasterxml.jackson.databind, kotlin.reflect;
    opens net.yakclient.boot.maven to com.fasterxml.jackson.databind, kotlin.reflect;
    opens net.yakclient.boot.component to com.fasterxml.jackson.databind, kotlin.reflect;

    exports net.yakclient.boot.store;
    exports net.yakclient.boot;
    exports net.yakclient.boot.maven;
    exports net.yakclient.boot.loader;
    exports net.yakclient.boot.dependency;
    exports net.yakclient.boot.container;
    exports net.yakclient.boot.security;
    exports net.yakclient.boot.component;
    exports net.yakclient.boot.archive;
    exports net.yakclient.boot.event;
    exports net.yakclient.boot.container.volume;
}