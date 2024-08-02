package dev.extframework.boot.maven

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal val VERSION_REGEX =
    "(?<major>[0-9]+)\\.(?<minor>[0-9]+)(?:\\.(?<patch>[0-9]+))?(?:-(?<scheme>[0-9A-Za-z]*))?".toRegex()

internal val DATE_SCHEME_REGEX = "^\\d{4}\\.\\d{2}\\.\\d{2}(\\.\\d+)?\$".toRegex()

internal fun writeBase(
    major: UByte,
    minor: UByte,
    patch: UByte
): ULong {
    val long = 2UL

    return ((((((long or major.toULong()) shl 8) or minor.toULong()) shl 8) or patch.toULong()) shl 8) shl 31
}

internal fun rankScheme(
    base: ULong,
    _scheme: String
): ULong {
    val scheme = _scheme.lowercase()

    // If it has no scheme (ie the version might be 1.2.3) then that ranks the highest
    return if (scheme.isEmpty()) base
    // Next if its a release candidate
    else if (scheme.startsWith("rc"))
        (base shr 2) or (scheme.removePrefix("rc").toIntOrNull()?.toULong() ?: 0U)
    // Next is milestone versions, ranked just as highly as release candidates
    else if (scheme.startsWith("m"))
        (base shr 2) or (scheme.removePrefix("m").toIntOrNull()?.toULong() ?: 0U)
    // Next, beta versions
    else if (scheme.startsWith("beta"))
        (base shr 3) or (scheme.removePrefix("beta").toIntOrNull()?.toULong() ?: 0U)
    // Then alpha
    else if (scheme.startsWith("alpha"))
        (base shr 4) or (scheme.removePrefix("alpha").toIntOrNull()?.toULong() ?: 0U)
    // Then snapshot
    else if (scheme.startsWith("snapshot")) base shr 5
    else base
}

internal fun rankDate(
    dateStr: String
): Int {
    val parts = dateStr.split(".")

    val year = parts[0].toInt()
    val month = parts[1].toInt()
    val day = parts[2].toInt()
    val versionNumber = if (parts.size > 3) parts[3].toInt() else 0

    return try {
        val date = LocalDate.of(year, month, day)
        val epochDay = date.toEpochDay().toInt() // Convert to days since epoch (1970-01-01)
        (epochDay shl 8) or versionNumber
    } catch (e: DateTimeParseException) {
        0
    }
}

// Given the version (a string) of a simple maven descriptor this function should return a
// ULong that compares the versions of maven descriptors from lowest to highest version
// take into account common practices and naming schemes like major/minor/patch version,
// endings like -SNAPSHOT, -RC1/M1, -beta(##), build numbers, etc. If a version has a schema
// that you do not know (for example 1.0-custom), ignore it.
public fun sortMavenDescriptorVersion(desc: SimpleMavenDescriptor): ULong {
    val (_, _, version) = desc

    return rankVersion(version)
}

internal fun rankVersion(version: String) : ULong {
    return VERSION_REGEX.matchEntire(version)?.groups?.let { components ->
        val major = components["major"]!!.value.toUByteOrNull() ?: return@let null
        val minor = components["minor"]!!.value.toUByteOrNull() ?: return@let null
        val patch = components["patch"]?.value?.toUByteOrNull() ?: 0U
        val scheme = components["scheme"]?.value

        val base = writeBase(major, minor, patch)

        if (scheme != null) rankScheme(base, scheme) else base
    } ?: DATE_SCHEME_REGEX.matchEntire(version)?.let { _ ->
        rankDate(version).toULong() // This isn't great
    } ?: 0U
}
