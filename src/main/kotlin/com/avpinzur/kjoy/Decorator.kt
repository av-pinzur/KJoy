package com.avpinzur.kjoy

fun interface SimpleDecorator<T, R> {
    fun apply(original: (T) -> R): (T) -> R
}

fun interface SuspendDecorator<T, R> {
    fun apply(original: suspend (T) -> R): suspend (T) -> R
}

interface Decorator<T1, T2, R> : SimpleDecorator<T1, R>, SuspendDecorator<T2, R> {
    companion object {
        fun <T, T1 : T, T2 : T, P, R> of(beforeDecorator: (T) -> P, afterDecorator: (P, Result<R>) -> R) =
            object : Decorator<T1, T2, R> {
                override fun apply(original: (T1) -> R): (T1) -> R = { arg ->
                    val payload = beforeDecorator(arg)
                    val originalResult = runCatching { original(arg) }
                    afterDecorator(payload, originalResult)
                }

                override fun apply(original: suspend (T2) -> R): suspend (T2) -> R = { arg ->
                    val payload = beforeDecorator(arg)
                    val originalResult = runCatching { original(arg) }
                    afterDecorator(payload, originalResult)
                }
            }

        fun <T1, T2, R> of(
            simpleDecorator: SimpleDecorator<T1, R>,
            suspendDecorator: SuspendDecorator<T2, R>
        ) = object : Decorator<T1, T2, R> {
            override fun apply(original: (T1) -> R): (T1) -> R = simpleDecorator.apply(original)
            override fun apply(original: suspend (T2) -> R): suspend (T2) -> R = suspendDecorator.apply(original)
        }
    }
}

interface InvocationDecorator<I> {
    fun apply(original: InvocationHandler<I>): InvocationHandler<I>

    companion object {
        fun <I> of(
            simpleDecorator: SimpleDecorator<SimpleInvocation<I>, Any?>,
            suspendDecorator: SuspendDecorator<SuspendInvocation<I>, Any?>
        ) = of(Decorator.of(simpleDecorator, suspendDecorator))

        fun <I> of(
            decorator: Decorator<SimpleInvocation<I>, SuspendInvocation<I>, Any?>
        ) = object : InvocationDecorator<I> {
            override fun apply(original: InvocationHandler<I>) = object : InvocationHandler<I> {
                val simpleDecorated = decorator.apply(original::handleSimple)
                val suspendDecorated = decorator.apply(original::handleSuspend)

                override fun handleSimple(invocation: SimpleInvocation<I>): Any? =
                    simpleDecorated(invocation)

                override suspend fun handleSuspend(invocation: SuspendInvocation<I>): Any? =
                    suspendDecorated(invocation)
            }
        }
    }
}

fun <T, T1 : T, T2 : T, R> loggingDecorator(
    write: (String) -> Unit = ::println,
    spanDescription: (T) -> String
) = Decorator.of<T, T1, T2, String, R>(
    beforeDecorator = { arg ->
        val desc = spanDescription(arg)
        write("Starting: $desc")
        desc
    },
    afterDecorator = { desc, result ->
        write("Finished: $desc")
        result.getOrThrow()
    }
)

// @Suppress("")
// fun <T1, T2, R> T1.merge(other: T2): R where R: T1, R: T2 {
//     TODO()
// }
//
// fun interface StringHolder {
//    fun s(): String
//
//    companion object {
//        fun of(s: String) = StringHolder { s }
//    }
// }
//
// fun interface IntHolder {
//    fun  i(): Int
//
//    companion object {
//        fun of(i: Int) = IntHolder { i }
//    }
// }
//
// val s = StringHolder.of("foo")
// val i = IntHolder.of(123)
// val both = s.merge(i)
// val foo = both.s()
