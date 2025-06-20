package dev.extframework.boot.audit

public interface Auditor<T: Any> {
    public val type: Class<T>

    public fun audit(
        event: T,
    ) : T
}

public fun <T: Any> Auditor(
    type: Class<T>,
    auditor: (T) -> T
): Auditor<T> {
    return object : Auditor<T> {
        override val type: Class<T> = type

        override fun audit(event: T): T=
            auditor(event)
    }
}

public inline fun <reified T: Any> Auditor(
    crossinline auditor: (T) -> T
): Auditor<T> {
    return object : Auditor<T> {
        override val type: Class<T> = T::class.java

        override fun audit(event: T): T =
            auditor(event)
    }
}

public fun <T: Any> List<Auditor<T>>.audit(ctx: T) : T =
    fold(ctx) {acc, it ->
        it.audit(acc)
    }
