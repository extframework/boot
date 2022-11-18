package net.yakclient.boot.loader

internal val String.packageFormat get(): String = substring(0, lastIndexOf('.').let { if (it == -1) 0 else it })

internal val String.dotClassFormat get() : String = "${replace('.', '/')}.class"