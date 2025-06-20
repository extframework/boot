package dev.extframework.boot.monad

public sealed class Either<out A, out B> {
    public val isThis: Boolean = this is This
    public val isThat: Boolean = this is That

    public class This<T>(
        public val item: T
    ) : Either<T, Nothing>()

    public class That<T>(
        public val item: T
    ) : Either<Nothing, T>()

    public fun getThis() : A {
        return (this as This).item
    }

    public fun getThat() : B {
        return (this as That).item
    }
}

public fun <A, B, T> Either<A,B>.map(
    mapThis: (A) -> T,
    mapThat: (B) -> T
) : T {
    if (isThis) return mapThis(getThis())
    return mapThat(getThat())
}