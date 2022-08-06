package net.yakclient.boot.container.volume

public data class RouterRules(
    public val rules: List<Rule>
) {
    public constructor(vararg rules: Rule) : this(listOf(*rules))

   public data class Rule(
        val classifier: PathClassifier,
        val associatedVolume: ContainerVolume,
   )
}