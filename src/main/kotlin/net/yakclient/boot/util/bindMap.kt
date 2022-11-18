package net.yakclient.boot.util

import arrow.core.Either
import arrow.core.continuations.either

public fun <A, B> Collection<Either<B, A>>.bindMap(): Either<B, List<A>> = either.eager {
   map { either ->
        either.orNull() ?: shift<A>((either as Either.Left).value)
    }
}