module yakclient.boot {
    requires kotlin.stdlib;
    requires durganmcbroom.artifact.resolver;
    requires yakclient.archives;
    requires java.logging;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.kotlin;
    requires yakclient.common.util;
    requires artifact.resolver.simple.maven;
    requires kotlinx.cli.jvm;

    opens net.yakclient.boot.dependency to com.fasterxml.jackson.databind;
    opens net.yakclient.boot to com.fasterxml.jackson.databind;
}