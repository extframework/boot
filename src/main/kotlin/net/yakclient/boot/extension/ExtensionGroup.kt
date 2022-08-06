package net.yakclient.boot.extension

import net.yakclient.boot.container.Container

public class ExtensionGroup(
    public val extensions: List<Container<*>>
) {
    public abstract inner class GroupController {

    }
}